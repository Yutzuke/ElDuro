package app.elduro.core

import android.util.Xml
import app.elduro.data.Cartera
import app.elduro.data.ElDuroDb
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.math.roundToLong

/**
 * Importa el formato de la hoja "Balance.xlsx" del usuario:
 * por hoja, una sección BOLIVARES (A6: FECHA | DESCRIPCION | EFECTIVO | DIGITAL) y otra DOLARES,
 * cada una terminada en una fila "TOTAL:".
 * - "inicio de caja" → APERTURA; "cuadre" → AJUSTE; resto → ENTRADA (+) o GASTO (−).
 * - Fechas: número de serie de Excel o texto (14/10/2026, 13/102026...).
 * - Los ids son deterministas: importar dos veces no duplica.
 */
object ImportarExcel {

    data class Fila(
        val hoja: String, val fila: Int, val columna: String,
        val fecha: String?, val fechaOriginal: String, val descripcion: String,
        val moneda: Moneda, val forma: Forma, val monto: Long, val tipo: Tipo,
        val avisoFecha: String? = null,
    )

    data class Hoja(val nombre: String, val mes: Int?, val filas: List<Fila>, val notas: List<String>) {
        val util get() = filas.isNotEmpty()
    }

    private val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    fun leer(input: InputStream): List<Hoja> {
        val partes = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.startsWith("xl/") && e.name.endsWith(".xml") || e.name.endsWith(".rels")) partes[e.name] = z.readBytes()
            }
        }
        val compartidas = partes["xl/sharedStrings.xml"]?.let(::leerCompartidas).orEmpty()
        val rels = partes["xl/_rels/workbook.xml.rels"]?.let(::leerRels).orEmpty()
        val hojas = partes["xl/workbook.xml"]?.let(::leerHojas) ?: error("No es un archivo .xlsx válido")
        return hojas.map { (nombre, rid) ->
            val ruta = rels[rid]?.let { if (it.startsWith("/")) it.drop(1) else "xl/$it" }
            val celdas = ruta?.let { partes[it] }?.let { leerCeldas(it, compartidas) }.orEmpty()
            analizar(nombre, celdas)
        }
    }

    private fun analizar(nombre: String, celdas: Map<Pair<Int, String>, String>): Hoja {
        val norm = Categorias.normal(nombre)
        val mes = meses.indexOfFirst { norm.contains(it) }.takeIf { it >= 0 }?.plus(1)
        if (norm.contains("deuda")) return Hoja(nombre, null, emptyList(), listOf("Deudas: se importarán en la fase 2 (módulo de deudas)."))
        if (norm.contains("faltante")) return Hoja(nombre, null, emptyList(), listOf("Faltantes: se importarán en la fase 2."))

        val filasN = celdas.keys.map { it.first }.distinct().sorted()
        val out = mutableListOf<Fila>()
        var moneda: Moneda? = null
        for (r in filasN) {
            val a = celdas[r to "A"]?.trim().orEmpty(); val b = celdas[r to "B"]?.trim().orEmpty()
            val c = celdas[r to "C"]?.trim().orEmpty()
            if (Categorias.normal(a) == "fecha") {
                val cn = Categorias.normal(c)
                moneda = when { cn.contains("bolivar") -> Moneda.BS; cn.contains("dolar") -> Moneda.USD; else -> null }
                continue
            }
            if (moneda == null) continue
            if (Categorias.normal(b).startsWith("total")) { moneda = null; continue }
            for ((col, forma) in listOf("C" to Forma.EFECTIVO, "D" to Forma.DIGITAL)) {
                val v = celdas[r to col]?.trim()?.toDoubleOrNull() ?: continue
                if (v == 0.0) continue
                val cent = (v * 100).roundToLong()
                val d = Categorias.normal(b)
                val tipo = when {
                    d.contains("inicio") && d.contains("caja") -> Tipo.APERTURA
                    d.contains("cuadre") -> Tipo.AJUSTE
                    cent > 0 -> Tipo.ENTRADA
                    else -> Tipo.GASTO
                }
                out += Fila(nombre, r, col, convertirFecha(a), a, b.replace(Regex("\\s+"), " "), moneda, forma, cent, tipo)
            }
        }

        // fechas ilegibles → la anterior; fuera del mes de la hoja → aviso
        var ultima: String? = null
        val anio = out.mapNotNull { it.fecha?.take(4) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val filas = out.map { f ->
            var fx = f
            if (f.fecha == null) fx = f.copy(fecha = ultima, avisoFecha = "fecha ilegible «${f.fechaOriginal}», se usa ${ultima ?: "—"}")
            else if (mes != null && f.fecha.substring(5, 7).toInt() != mes)
                fx = f.copy(avisoFecha = "${Fechas.dmy(f.fecha)} no es de ${meses[mes - 1]} → ${Fechas.dmy(corregir(f.fecha, mes))}")
            ultima = fx.fecha ?: ultima
            fx
        }
        val notas = mutableListOf<String>()
        if (Categorias.normal(nombre) == "formato") notas += "Parece la plantilla; revisa si quieres importarla."
        return Hoja(nombre, mes, filas, notas).also { if (anio == null && filas.isNotEmpty()) notas += "Sin fechas legibles." }
    }

    /** Número de serie de Excel (días desde 1899-12-30) o texto d/m/a, incluso "13/102026". */
    fun convertirFecha(s: String): String? {
        s.toDoubleOrNull()?.let { n -> if (n in 20000.0..80000.0) return LocalDate.of(1899, 12, 30).plusDays(n.toLong()).toString() }
        CarpetaSync.normalizarFecha(s)?.let { return it }
        val m = Regex("""(\d{1,2})/(\d{2})(\d{4})""").matchEntire(s.trim()) ?: return null
        return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()).toString() }.getOrNull()
    }

    data class Resultado(val nuevos: Int, val actualizados: Int, val carteras: List<String>)

    /**
     * Guarda las filas en el [libro]. Con [corregirFechas], las fechas fuera del mes de su hoja
     * se mueven al día 1 de ese mes (mismo año).
     */
    fun importar(db: ElDuroDb, hojas: List<Hoja>, libro: String, corregirFechas: Boolean): Resultado {
        val carteras = db.carteras().all().filter { it.libro == libro }.toMutableList()
        val creadas = mutableListOf<String>()
        fun cartera(m: Moneda, f: Forma): Cartera {
            carteras.firstOrNull { it.moneda == m && it.forma == f }?.let { return it }
            val nombre = "${m.simbolo} ${f.etiqueta.lowercase()}"
            val c = Cartera(libro = libro, nombre = nombre, moneda = m, forma = f, orden = carteras.size)
            return c.copy(id = db.carteras().insert(c)).also { carteras += it; creadas += nombre }
        }
        val aprendido = Duro.aprendido(db)
        var nuevos = 0; var act = 0
        for (h in hojas) for ((i, f) in h.filas.withIndex()) {
            var fecha = f.fecha ?: continue
            if (corregirFechas && h.mes != null && f.avisoFecha != null && f.fecha.substring(5, 7).toInt() != h.mes) {
                fecha = corregir(fecha, h.mes)
            }
            val uid = "xl" + MessageDigest.getInstance("SHA-1").digest("${h.nombre}|${f.fila}|${f.columna}".toByteArray())
                .joinToString("") { "%02x".format(it) }.take(10)
            val desc = when (f.tipo) { Tipo.APERTURA -> f.descripcion.ifBlank { "Inicio de caja" }; else -> f.descripcion }
            val cat = when (f.tipo) {
                Tipo.GASTO -> Categorias.adivinar(desc, false, aprendido) ?: "Otros"
                Tipo.ENTRADA -> Categorias.adivinar(desc, true, aprendido) ?: "Otros ingresos"
                Tipo.AJUSTE -> "Descuadre"
                else -> ""
            }
            val prev = db.movimientos().byUid(uid)
            db.movimientos().upsert(Movimiento(
                id = prev?.id ?: 0, uid = uid, fecha = fecha, tipo = f.tipo, carteraId = cartera(f.moneda, f.forma).id,
                monto = f.monto, categoria = prev?.categoria ?: cat, descripcion = desc.ifBlank { f.tipo.etiqueta },
                creado = prev?.creado ?: (System.currentTimeMillis() + i),
            ))
            if (prev == null) nuevos++ else act++
        }
        return Resultado(nuevos, act, creadas)
    }

    // ---------- XML ----------
    private fun parser(b: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(b.inputStream(), "UTF-8")
    }

    private fun leerCompartidas(b: ByteArray): List<String> {
        val p = parser(b); val out = mutableListOf<String>(); var actual: StringBuilder? = null; var enT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> actual = StringBuilder(); "t" -> enT = true }
                XmlPullParser.TEXT -> if (enT) actual?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "si" -> { out += actual.toString(); actual = null }; "t" -> enT = false }
            }
        }
        return out
    }

    private fun leerRels(b: ByteArray): Map<String, String> {
        val p = parser(b); val out = HashMap<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT)
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship")
                out[p.getAttributeValue(null, "Id")] = p.getAttributeValue(null, "Target")
        return out
    }

    private fun leerHojas(b: ByteArray): List<Pair<String, String>> {
        val p = parser(b); val out = mutableListOf<Pair<String, String>>()
        while (p.next() != XmlPullParser.END_DOCUMENT)
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet")
                out += p.getAttributeValue(null, "name") to p.getAttributeValue(null, "r:id")
        return out
    }

    /** (fila, columna) → valor como texto (números en su forma cruda). */
    private fun leerCeldas(b: ByteArray, compartidas: List<String>): Map<Pair<Int, String>, String> {
        val p = parser(b); val out = HashMap<Pair<Int, String>, String>()
        var ref: String? = null; var tipo: String? = null; var enV = false; var enT = false; val txt = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "c" -> { ref = p.getAttributeValue(null, "r"); tipo = p.getAttributeValue(null, "t"); txt.clear() }
                    "v" -> enV = true
                    "t" -> enT = true
                }
                XmlPullParser.TEXT -> if (enV || enT) txt.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> enV = false
                    "t" -> enT = false
                    "c" -> {
                        val r = ref
                        if (r != null && txt.isNotEmpty()) {
                            val m = Regex("([A-Z]+)(\\d+)").matchEntire(r)
                            if (m != null) {
                                val v = if (tipo == "s") compartidas.getOrElse(txt.toString().trim().toIntOrNull() ?: -1) { "" } else txt.toString()
                                out[m.groupValues[2].toInt() to m.groupValues[1]] = v
                            }
                        }
                        ref = null
                    }
                }
            }
        }
        return out
    }

    /** Si intercambiando día y mes cae en el mes de la hoja (error típico 10/11 ↔ 11/10) usa eso; si no, día 1. */
    fun corregir(iso: String, mes: Int): String {
        val d = LocalDate.parse(iso)
        if (d.dayOfMonth == mes) runCatching { return LocalDate.of(d.year, mes, d.monthValue).toString() }
        return LocalDate.of(d.year, mes, 1).toString()
    }
}
