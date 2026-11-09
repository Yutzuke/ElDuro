package app.elduro.core

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import app.elduro.data.Cartera
import app.elduro.data.ElDuroDb
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tasa
import app.elduro.data.Tipo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Espejo legible de la base de datos en la carpeta (por defecto NodeSync/ElDuro):
 *   carteras.csv · tasas.csv · <Libro>/<aaaa-mm>.csv
 * - Cada cambio en la app se escribe ~1,5 s después.
 * - Si un CSV cambió fuera de la app (p. ej. editado en el PC y llegado por Syncthing),
 *   al abrir la app se importa: ese archivo pasa a ser la verdad para su libro y mes.
 *   La versión anterior se guarda en .anteriores/ por si acaso.
 * - Si se borra un CSV, la app lo vuelve a escribir (nunca borra datos por un archivo ausente).
 */
object CarpetaSync {
    private const val TAG = "CarpetaSync"
    private const val SEP = ';'
    private const val BOM = "﻿"
    private val CAB_MOV = listOf("id", "fecha", "tipo", "cartera", "monto", "destino", "monto_destino", "tasa", "categoria", "descripcion")
    private val reMes = Regex("""\d{4}-\d{2}\.csv""")

    private val exec = Executors.newSingleThreadScheduledExecutor()
    private var pendiente: ScheduledFuture<*>? = null
    @Volatile private var iniciado = false

    data class Informe(val importados: List<String>, val escritos: Int, val avisos: List<String>) {
        fun texto() = buildString {
            append(if (importados.isEmpty()) "Sin cambios externos." else "Importado desde la carpeta: ${importados.joinToString()}.")
            append("\nArchivos escritos: $escritos.")
            if (avisos.isNotEmpty()) append("\n⚠ " + avisos.joinToString("\n⚠ "))
        }
    }

    fun iniciar(ctx: Context) {
        if (iniciado) return
        iniciado = true
        val app = ctx.applicationContext
        ElDuroDb.get(app).invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("Movimiento", "Cartera", "Tasa")) {
            override fun onInvalidated(tables: Set<String>) = programarExport(app)
        })
    }

    @Synchronized
    private fun programarExport(app: Context) {
        pendiente?.cancel(false)
        pendiente = exec.schedule({ runCatching { exportar(app) }.onFailure { Log.e(TAG, "export", it); Prefs.setSync(app, it.message) } }, 1500, TimeUnit.MILLISECONDS)
    }

    /** Importa cambios externos y luego escribe todo. Seguro de llamar al abrir la app. */
    fun sincronizar(ctx: Context): Informe {
        val app = ctx.applicationContext
        return exec.submit<Informe> {
            if (!Prefs.tieneAcceso()) { Prefs.setSync(app, "Falta el permiso de archivos"); return@submit Informe(emptyList(), 0, listOf("Falta el permiso de acceso a archivos")) }
            try {
                val avisos = mutableListOf<String>()
                val imp = importar(app, avisos)
                Duro.sembrar(ElDuroDb.get(app))
                val n = exportar(app)
                Prefs.setSync(app, null)
                Informe(imp, n, avisos)
            } catch (e: Exception) {
                Log.e(TAG, "sync", e); Prefs.setSync(app, e.message); throw e
            }
        }.get()
    }

    // ---------- hashes de lo último que escribimos / importamos ----------
    private fun hashes(c: Context) = c.getSharedPreferences("elduro_archivos", Context.MODE_PRIVATE)
    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }
    private fun clave(base: File, f: File) = f.relativeTo(base).path

    // ---------- exportar ----------
    private fun exportar(app: Context): Int {
        if (!Prefs.tieneAcceso()) return 0
        val db = ElDuroDb.get(app)
        val base = Prefs.carpeta(app).apply { mkdirs() }
        val carteras = db.carteras().all()
        val porId = carteras.associateBy { it.id }
        val archivos = LinkedHashMap<File, String>()

        archivos[File(base, "carteras.csv")] = csv(listOf("libro", "nombre", "moneda", "forma", "orden", "archivada"),
            carteras.map { listOf(it.libro, it.nombre, it.moneda.name, it.forma.name, it.orden.toString(), if (it.archivada) "si" else "") })
        archivos[File(base, "tasas.csv")] = csv(listOf("fecha", "fuente", "origen", "valor"),
            db.tasas().all().sortedWith(compareBy({ it.fecha }, { it.fuente }, { it.origen }))
                .map { listOf(it.fecha, it.fuente, it.origen, it.valor.toString().replace('.', ',')) })

        val movs = db.movimientos().all()
        movs.groupBy { (porId[it.carteraId]?.libro ?: "Sin libro") to it.fecha.take(7) }.forEach { (k, lista) ->
            archivos[File(base, "${k.first}/${k.second}.csv")] = csv(CAB_MOV, lista.map { filaMov(it, porId) })
        }
        // meses que quedaron vacíos: dejamos el archivo sólo con la cabecera
        base.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.forEach { dir ->
            dir.listFiles().orEmpty().filter { reMes.matches(it.name) && it !in archivos }.forEach { archivos[it] = csv(CAB_MOV, emptyList()) }
        }

        val h = hashes(app).edit()
        var n = 0
        for ((f, txt) in archivos) {
            val bytes = (BOM + txt).toByteArray()
            val hash = sha1(bytes)
            if (f.exists() && sha1(f.readBytes()) == hash) { h.putString(clave(base, f), hash); continue }
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, ".${f.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
            h.putString(clave(base, f), hash); n++
        }
        h.apply()
        return n
    }

    private fun filaMov(m: Movimiento, porId: Map<Long, Cartera>): List<String> {
        val c = porId[m.carteraId]
        val d = m.carteraDestinoId?.let { porId[it] }
        val dNombre = when { d == null -> ""; d.libro == c?.libro -> d.nombre; else -> "${d.libro}/${d.nombre}" }
        return listOf(m.uid, m.fecha, m.tipo.etiqueta, c?.nombre ?: "?", Dinero.csv(m.monto), dNombre,
            m.montoDestino?.let { Dinero.csv(it) } ?: "", m.tasa?.toString()?.replace('.', ',') ?: "", m.categoria, m.descripcion)
    }

    // ---------- importar ----------
    private fun importar(app: Context, avisos: MutableList<String>): List<String> {
        val db = ElDuroDb.get(app)
        val base = Prefs.carpeta(app)
        if (!base.isDirectory) return emptyList()
        val h = hashes(app)
        val cambiados = mutableListOf<File>()
        fun revisar(f: File) {
            if (!f.isFile) return
            val actual = sha1(f.readBytes())
            if (h.getString(clave(base, f), null) != actual) cambiados += f
        }
        revisar(File(base, "carteras.csv"))
        revisar(File(base, "tasas.csv"))
        base.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }.forEach { dir ->
            dir.listFiles().orEmpty().filter { reMes.matches(it.name) }.sortedBy { it.name }.forEach(::revisar)
        }
        if (cambiados.isEmpty()) return emptyList()

        val hechos = mutableListOf<String>()
        runBlocking {
            db.withTransaction {
                for (f in cambiados) {
                    runCatching {
                        when {
                            f.name == "carteras.csv" && f.parentFile == base -> importarCarteras(db, f)
                            f.name == "tasas.csv" && f.parentFile == base -> importarTasas(db, f)
                            else -> importarMes(app, db, base, f, avisos)
                        }
                        hechos += clave(base, f)
                    }.onFailure { avisos += "${f.name}: ${it.message}" }
                }
            }
        }
        val e = h.edit(); cambiados.forEach { e.remove(clave(base, it)) }; e.apply()
        return hechos
    }

    private fun importarCarteras(db: ElDuroDb, f: File) {
        val actuales = db.carteras().all().associateBy { it.libro to it.nombre }
        for (r in leerCsv(f)) {
            val libro = r["libro"].orEmpty().trim(); val nombre = r["nombre"].orEmpty().trim()
            if (libro.isEmpty() || nombre.isEmpty()) continue
            val prev = actuales[libro to nombre]
            val c = Cartera(
                id = prev?.id ?: 0, libro = libro, nombre = nombre,
                moneda = runCatching { Moneda.valueOf(r["moneda"]!!.trim().uppercase()) }.getOrElse { prev?.moneda ?: adivinarMoneda(nombre) },
                forma = runCatching { Forma.valueOf(r["forma"]!!.trim().uppercase()) }.getOrElse { prev?.forma ?: adivinarForma(nombre) },
                orden = r["orden"]?.trim()?.toIntOrNull() ?: prev?.orden ?: 0,
                archivada = r["archivada"]?.trim()?.lowercase() in setOf("si", "sí", "1", "true", "x"),
            )
            db.carteras().upsert(c)
        }
    }

    private fun importarTasas(db: ElDuroDb, f: File) {
        val enArchivo = HashSet<Triple<String, String, String>>()
        val lista = leerCsv(f).mapNotNull { r ->
            val fecha = r["fecha"]?.trim() ?: return@mapNotNull null
            val fuente = r["fuente"]?.trim()?.uppercase() ?: return@mapNotNull null
            val origen = r["origen"]?.trim()?.lowercase()?.ifEmpty { "manual" } ?: "manual"
            val v = r["valor"]?.let { Dinero.parseDouble(it) } ?: return@mapNotNull null
            if (!fecha.matches(Regex("""\d{4}-\d{2}-\d{2}""")) || v <= 0) return@mapNotNull null
            enArchivo += Triple(fecha, fuente, origen)
            Tasa(fecha, fuente, origen, v)
        }
        db.tasas().upsertAll(lista)
        // las manuales que el usuario borró del archivo se eliminan
        db.tasas().all().filter { it.origen == "manual" && Triple(it.fecha, it.fuente, it.origen) !in enArchivo }
            .forEach { db.tasas().delete(it.fecha, it.fuente, it.origen) }
    }

    private fun importarMes(app: Context, db: ElDuroDb, base: File, f: File, avisos: MutableList<String>) {
        val libro = f.parentFile!!.name
        val ym = f.name.removeSuffix(".csv")
        val carteras = db.carteras().all().toMutableList()
        fun buscar(ref: String, libroDef: String): Cartera {
            val (l, n) = if (ref.contains('/')) ref.substringBefore('/') to ref.substringAfter('/') else libroDef to ref
            carteras.firstOrNull { it.libro == l && it.nombre.equals(n.trim(), true) }?.let { return it }
            val nueva = Cartera(libro = l, nombre = n.trim(), moneda = adivinarMoneda(n), forma = adivinarForma(n), orden = 99)
            val id = db.carteras().insert(nueva)
            avisos += "Cartera nueva creada desde ${f.name}: $l/${n.trim()}"
            return nueva.copy(id = id).also { carteras += it }
        }
        val porId = carteras.associateBy { it.id }
        val antes = db.movimientos().all().filter { porId[it.carteraId]?.libro == libro && it.fecha.startsWith(ym) }

        // respaldo de lo que había antes de aplicar el archivo
        if (antes.isNotEmpty()) {
            val dir = File(base, ".anteriores").apply { mkdirs() }
            val sello = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            File(dir, "$libro-$ym-$sello.csv").writeText(BOM + csv(CAB_MOV, antes.map { filaMov(it, porId) }))
            dir.listFiles().orEmpty().sortedByDescending { it.name }.drop(60).forEach { it.delete() }
        }

        val vistos = HashSet<String>()
        for ((i, r) in leerCsv(f).withIndex()) {
            val linea = i + 2
            val fecha = normalizarFecha(r["fecha"].orEmpty()) ?: run { avisos += "${f.name} línea $linea: fecha inválida"; null } ?: continue
            val monto = Dinero.parse(r["monto"].orEmpty()) ?: run { avisos += "${f.name} línea $linea: monto inválido"; null } ?: continue
            val tipo = leerTipo(r["tipo"].orEmpty(), monto)
            val c = buscar(r["cartera"].orEmpty().ifBlank { "Bs digital" }, libro)
            val destRef = r["destino"].orEmpty().trim()
            val d = if (tipo == Tipo.TRANSFERENCIA && destRef.isNotEmpty()) buscar(destRef, libro) else null
            val uid = r["id"]?.trim()?.takeIf { it.isNotEmpty() } ?: Duro.uid()
            if (!vistos.add(uid)) continue
            val firmado = when (tipo) {
                Tipo.GASTO -> -kotlin.math.abs(monto)
                Tipo.ENTRADA -> kotlin.math.abs(monto)
                Tipo.TRANSFERENCIA -> -kotlin.math.abs(monto)
                else -> monto
            }
            val tasa = r["tasa"]?.let { Dinero.parseDouble(it) }
            val md = if (d != null) (Dinero.parse(r["monto_destino"].orEmpty())?.let { kotlin.math.abs(it) }
                ?: EntradaRapida.montoDestino(kotlin.math.abs(firmado), c.moneda, d.moneda, tasa)) else null
            val prev = db.movimientos().byUid(uid)
            db.movimientos().upsert(
                Movimiento(id = prev?.id ?: 0, uid = uid, fecha = fecha, tipo = tipo, carteraId = c.id, monto = firmado,
                    carteraDestinoId = d?.id, montoDestino = md, tasa = tasa,
                    categoria = r["categoria"].orEmpty().trim(), descripcion = r["descripcion"].orEmpty().trim(),
                    creado = prev?.creado ?: (System.currentTimeMillis() + i))
            )
        }
        val borrar = antes.map { it.uid }.filter { it !in vistos }
        if (borrar.isNotEmpty()) db.movimientos().deleteUids(borrar)
    }

    // ---------- utilidades CSV ----------
    private fun csv(cab: List<String>, filas: List<List<String>>): String = buildString {
        append(cab.joinToString(SEP.toString())).append("\r\n")
        for (f in filas) append(f.joinToString(SEP.toString(), transform = ::escapar)).append("\r\n")
    }

    private fun escapar(s: String): String =
        if (s.any { it == SEP || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Lee CSV con ; (o , o tab si el archivo lo usa), comillas y BOM. Devuelve filas como mapa cabecera→valor. */
    fun leerCsv(f: File): List<Map<String, String>> {
        val txt = f.readText().removePrefix(BOM)
        val primera = txt.lineSequence().firstOrNull().orEmpty()
        val sep = listOf(';', '\t', ',').maxByOrNull { c -> primera.count { it == c } } ?: SEP
        val filas = mutableListOf<List<String>>()
        var campo = StringBuilder(); var fila = mutableListOf<String>(); var comillas = false; var i = 0
        while (i < txt.length) {
            val ch = txt[i]
            when {
                comillas && ch == '"' && i + 1 < txt.length && txt[i + 1] == '"' -> { campo.append('"'); i++ }
                ch == '"' -> comillas = !comillas
                !comillas && ch == sep -> { fila += campo.toString(); campo = StringBuilder() }
                !comillas && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < txt.length && txt[i + 1] == '\n') i++
                    fila += campo.toString(); campo = StringBuilder()
                    if (fila.any { it.isNotBlank() }) filas += fila
                    fila = mutableListOf()
                }
                else -> campo.append(ch)
            }
            i++
        }
        fila += campo.toString(); if (fila.any { it.isNotBlank() }) filas += fila
        if (filas.isEmpty()) return emptyList()
        val cab = filas.first().map { Categorias.normal(it.trim()).replace(' ', '_') }
        return filas.drop(1).map { r -> cab.indices.associate { cab[it] to r.getOrElse(it) { "" } } }
    }

    private fun leerTipo(s: String, monto: Long): Tipo {
        val n = Categorias.normal(s.trim())
        Tipo.entries.firstOrNull { Categorias.normal(it.etiqueta) == n || it.name.lowercase() == n }?.let { return it }
        return when {
            n.startsWith("trans") || n.startsWith("cambio") -> Tipo.TRANSFERENCIA
            n.startsWith("ajus") || n.startsWith("cuadre") || n.startsWith("arqueo") -> Tipo.AJUSTE
            n.startsWith("aper") || n.startsWith("inicio") -> Tipo.APERTURA
            monto >= 0 -> Tipo.ENTRADA
            else -> Tipo.GASTO
        }
    }

    /** Acepta 2026-11-09, 09/11/2026, 9/11/26. */
    fun normalizarFecha(s: String): String? {
        val t = s.trim()
        if (t.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return t
        val m = Regex("""(\d{1,2})[/.-](\d{1,2})[/.-]?(\d{2,4})""").matchEntire(t) ?: return null
        val (d, mo, y) = m.destructured
        val anio = if (y.length == 2) "20$y" else y
        return runCatching { java.time.LocalDate.of(anio.toInt(), mo.toInt(), d.toInt()).toString() }.getOrNull()
    }

    fun adivinarMoneda(n: String): Moneda {
        val s = n.lowercase()
        return when { "usdt" in s -> Moneda.USDT; "$" in s || "usd" in s || "dolar" in s || "dólar" in s -> Moneda.USD; else -> Moneda.BS }
    }

    fun adivinarForma(n: String): Forma {
        val s = n.lowercase()
        return if ("efect" in s || "caja" in s || "cash" in s) Forma.EFECTIVO else Forma.DIGITAL
    }
}
