package app.elduro.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import app.elduro.data.Aprendizaje
import app.elduro.data.Cartera
import app.elduro.data.ElDuroDb
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import java.io.File
import java.util.UUID

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("elduro", Context.MODE_PRIVATE)

    fun vista(c: Context): Vista = runCatching { Vista.valueOf(sp(c).getString("vista", null)!!) }.getOrDefault(Vista.USD_BCV)
    fun setVista(c: Context, v: Vista) = sp(c).edit().putString("vista", v.name).apply()

    /** Carpeta de datos. Por defecto <microSD>/NodeSync/ElDuro (sincronizada por Syncthing con el PC). */
    fun carpeta(c: Context): File = sp(c).getString("carpeta", null)?.let(::File) ?: carpetaPorDefecto(c)
    fun setCarpeta(c: Context, f: File) = sp(c).edit().putString("carpeta", f.path).apply()

    fun carpetaPorDefecto(c: Context): File {
        val sm = c.getSystemService(android.os.storage.StorageManager::class.java)
        val vols = sm.storageVolumes.mapNotNull { it.directory }.filter { it.path != Environment.getExternalStorageDirectory().path }
        vols.firstOrNull { File(it, "NodeSync").isDirectory }?.let { return File(it, "NodeSync/ElDuro") }
        val interna = Environment.getExternalStorageDirectory()
        if (File(interna, "NodeSync").isDirectory) return File(interna, "NodeSync/ElDuro")
        return File(interna, "ElDuro")
    }

    fun ultimaConsulta(c: Context) = sp(c).getLong("ultima_consulta", 0)
    fun setUltimaConsulta(c: Context, t: Long) = sp(c).edit().putLong("ultima_consulta", t).apply()
    fun ultimaSync(c: Context) = sp(c).getLong("ultima_sync", 0)
    fun ultimoError(c: Context): String? = sp(c).getString("ultimo_error", null)
    fun setSync(c: Context, error: String?) = sp(c).edit().putLong("ultima_sync", System.currentTimeMillis()).putString("ultimo_error", error).apply()

    fun libroActual(c: Context): String = sp(c).getString("libro", Duro.LIBROS.first())!!
    fun setLibro(c: Context, l: String) = sp(c).edit().putString("libro", l).apply()
    fun carteraRapida(c: Context, libro: String): Long = sp(c).getLong("rapida_$libro", -1)
    fun setCarteraRapida(c: Context, libro: String, id: Long) = sp(c).edit().putLong("rapida_$libro", id).apply()

    fun tieneAcceso(): Boolean = Environment.isExternalStorageManager()
}

/** Operaciones de escritura. Llamar fuera del hilo principal. */
object Duro {
    val LIBROS = listOf("Personal", "Tienda")

    fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    @Synchronized
    fun sembrar(db: ElDuroDb) = db.runInTransaction {
        if (db.carteras().all().isNotEmpty()) return@runInTransaction
        var orden = 0
        fun c(libro: String, nombre: String, m: Moneda, f: Forma) = db.carteras().insert(Cartera(libro = libro, nombre = nombre, moneda = m, forma = f, orden = orden++))
        c("Personal", "Bs efectivo", Moneda.BS, Forma.EFECTIVO)
        c("Personal", "Bs digital", Moneda.BS, Forma.DIGITAL)
        c("Personal", "$ efectivo", Moneda.USD, Forma.EFECTIVO)
        c("Personal", "$ digital", Moneda.USD, Forma.DIGITAL)
        c("Personal", "USDT Binance", Moneda.USDT, Forma.DIGITAL)
        orden = 0
        c("Tienda", "Bs efectivo", Moneda.BS, Forma.EFECTIVO)
        c("Tienda", "Bs digital", Moneda.BS, Forma.DIGITAL)
        c("Tienda", "$ efectivo", Moneda.USD, Forma.EFECTIVO)
        c("Tienda", "$ digital", Moneda.USD, Forma.DIGITAL)
    }

    fun guardar(db: ElDuroDb, m: Movimiento) {
        db.movimientos().upsert(m)
        if (m.categoria.isNotBlank() && (m.tipo == Tipo.GASTO || m.tipo == Tipo.ENTRADA))
            db.aprendizaje().upsertAll(Categorias.palabras(m.descripcion).map { Aprendizaje(it, m.categoria) })
    }

    fun borrar(db: ElDuroDb, m: Movimiento) = db.movimientos().delete(m)

    fun crearCartera(db: ElDuroDb, c: Cartera, apertura: Long) {
        val id = db.carteras().upsert(c).let { if (c.id != 0L) c.id else it }
        if (apertura != 0L) db.movimientos().insert(
            Movimiento(uid = uid(), fecha = Fechas.hoy(), tipo = Tipo.APERTURA, carteraId = id, monto = apertura, descripcion = "Saldo de apertura")
        )
    }

    /** Arqueo (efectivo) o conciliación (digital): registra la diferencia como ajuste. */
    fun arqueo(db: ElDuroDb, c: Cartera, saldoApp: Long, real: Long, fecha: String): Long {
        val dif = real - saldoApp
        if (dif == 0L) return 0
        val que = if (c.forma == Forma.EFECTIVO) "Arqueo" else "Conciliación"
        val txt = if (dif > 0) "sobran" else "faltan"
        db.movimientos().insert(
            Movimiento(uid = uid(), fecha = fecha, tipo = Tipo.AJUSTE, carteraId = c.id, monto = dif, categoria = "Descuadre",
                descripcion = "$que: $txt ${Dinero.fmt(kotlin.math.abs(dif), c.moneda)}")
        )
        return dif
    }

    fun aprendido(db: ElDuroDb): Map<String, String> = db.aprendizaje().all().associate { it.palabra to it.categoria }
}

/**
 * Libros (Personal, Tienda y los que cree el usuario). La lista sale de las carteras existentes
 * más el orden guardado en preferencias; los archivados se ocultan pero conservan todo.
 */
object Libros {
    private const val ORDEN = "libros_orden"
    private const val ARCHIVADOS = "libros_archivados"

    private fun orden(c: Context) = Prefs.sp(c).getString(ORDEN, null)?.split("\n")?.filter { it.isNotBlank() } ?: Duro.LIBROS
    private fun setOrden(c: Context, l: List<String>) = Prefs.sp(c).edit().putString(ORDEN, l.distinct().joinToString("\n")).apply()

    fun archivados(c: Context): Set<String> = Prefs.sp(c).getStringSet(ARCHIVADOS, emptySet()).orEmpty()

    fun todos(c: Context, carteras: List<Cartera>): List<String> = (orden(c) + carteras.map { it.libro }).distinct()
    fun activos(c: Context, carteras: List<Cartera>): List<String> = todos(c, carteras).filter { it !in archivados(c) }.ifEmpty { Duro.LIBROS.take(1) }

    fun validar(c: Context, db: ElDuroDb, nombre: String, excepto: String? = null): String? {
        val n = nombre.trim()
        return when {
            n.isEmpty() -> "Escribe un nombre"
            n.any { it in "/\\:*?\"<>|" } || n.startsWith(".") -> "No uses / \\ : * ? \" < > | ni un punto al inicio"
            todos(c, db.carteras().all()).any { it.equals(n, true) && !it.equals(excepto, true) } -> "Ya existe un libro con ese nombre"
            else -> null
        }
    }

    /** Crea el libro con sus cuatro carteras básicas. */
    fun crear(c: Context, db: ElDuroDb, nombre: String) {
        val n = nombre.trim()
        db.runInTransaction {
            var o = 0
            for ((nom, m, f) in listOf(Triple("Bs efectivo", Moneda.BS, Forma.EFECTIVO), Triple("Bs digital", Moneda.BS, Forma.DIGITAL),
                    Triple("$ efectivo", Moneda.USD, Forma.EFECTIVO), Triple("$ digital", Moneda.USD, Forma.DIGITAL)))
                db.carteras().insert(Cartera(libro = n, nombre = nom, moneda = m, forma = f, orden = o++))
        }
        setOrden(c, orden(c) + n)
    }

    fun renombrar(c: Context, db: ElDuroDb, viejo: String, nuevo: String) {
        val n = nuevo.trim()
        CarpetaSync.renombrarLibro(c, viejo, n) {
            db.runInTransaction {
                val l = db.libros()
                l.carteras(viejo, n); l.deudas(viejo, n); l.recurrentes(viejo, n); l.presupuestos(viejo, n); l.faltantes(viejo, n)
            }
        }
        setOrden(c, todos(c, db.carteras().all()).map { if (it == viejo) n else it })
        val arch = archivados(c)
        if (viejo in arch) Prefs.sp(c).edit().putStringSet(ARCHIVADOS, arch - viejo + n).apply()
        if (Prefs.libroActual(c) == viejo) Prefs.setLibro(c, n)
        val rap = Prefs.carteraRapida(c, viejo)
        if (rap >= 0) Prefs.setCarteraRapida(c, n, rap)
    }

    fun archivar(c: Context, nombre: String, archivar: Boolean) {
        val a = archivados(c)
        Prefs.sp(c).edit().putStringSet(ARCHIVADOS, if (archivar) a + nombre else a - nombre).apply()
    }

    fun mover(c: Context, carteras: List<Cartera>, nombre: String, delta: Int) {
        val l = todos(c, carteras).toMutableList()
        val i = l.indexOf(nombre); val j = i + delta
        if (i < 0 || j !in l.indices) return
        l[i] = l[j]; l[j] = nombre
        setOrden(c, l)
    }
}
