package app.elduro.core

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.elduro.data.Cartera
import app.elduro.data.ElDuroDb
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exportar a .xlsx (formato de la hoja Balance), reporte PDF del mes y copias ZIP. */
object Exportar {

    // ======================= XLSX =======================

    private class C(val v: Any?, val estilo: Int = 0, val f: String? = null)
    private class Hoja(val nombre: String, val anchos: List<Double>) { val filas = mutableListOf<List<C?>>() }

    private const val NORMAL = 0; private const val NEGRITA = 1; private const val FECHA = 2; private const val NUM = 3; private const val NUM_N = 4; private const val TITULO = 5

    private fun col(i: Int): String { var n = i + 1; var s = ""; while (n > 0) { val r = (n - 1) % 26; s = ('A' + r) + s; n = (n - 1) / 26 }; return s }
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun serial(iso: String) = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.parse(iso))

    private fun xmlHoja(h: Hoja): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>""")
        h.anchos.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        h.filas.forEachIndexed { r, fila ->
            append("""<row r="${r + 1}">""")
            fila.forEachIndexed { c, celda ->
                if (celda == null) return@forEachIndexed
                val ref = col(c) + (r + 1)
                val s = if (celda.estilo != 0) """ s="${celda.estilo}"""" else ""
                when {
                    celda.f != null -> append("""<c r="$ref"$s><f>${esc(celda.f)}</f></c>""")
                    celda.v is Number -> append("""<c r="$ref"$s><v>${celda.v}</v></c>""")
                    celda.v != null -> append("""<c r="$ref"$s t="inlineStr"><is><t xml:space="preserve">${esc(celda.v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun escribirXlsx(hojas: List<Hoja>, out: File) {
        val estilos = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts>
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFCDEFD9"/></patternFill></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="1" fillId="2" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
        val n = hojas.size
        ZipOutputStream(FileOutputStream(out)).use { z ->
            fun put(nombre: String, txt: String) { z.putNextEntry(ZipEntry(nombre)); z.write(txt.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                    (1..n).joinToString("") { """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    hojas.mapIndexed { i, h -> """<sheet name="${esc(h.nombre.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets><calcPr fullCalcOnLoad=\"1\"/></workbook>")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    (1..n).joinToString("") { """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>""" } +
                    """<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", estilos)
            hojas.forEachIndexed { i, h -> put("xl/worksheets/sheet${i + 1}.xml", xmlHoja(h)) }
        }
    }

    /** Una fila por cartera afectada (una transferencia ocupa dos filas: sale y entra). */
    private data class Linea(val fecha: String, val desc: String, val cartera: Cartera, val monto: Long, val categoria: String, val tipo: Tipo)

    private fun lineas(movs: List<Movimiento>, porId: Map<Long, Cartera>, libro: String): List<Linea> = movs.flatMap { m ->
        val c = porId[m.carteraId]; val d = m.carteraDestinoId?.let { porId[it] }
        buildList {
            if (c != null && c.libro == libro) add(Linea(m.fecha, m.descripcion.ifBlank { m.tipo.etiqueta } + (d?.let { " → ${it.nombre}" } ?: ""), c, m.monto, m.categoria, m.tipo))
            if (d != null && d.libro == libro && m.montoDestino != null) add(Linea(m.fecha, m.descripcion.ifBlank { m.tipo.etiqueta } + (c?.let { " ← ${it.nombre}" } ?: ""), d, m.montoDestino, m.categoria, m.tipo))
        }
    }.sortedBy { it.fecha }

    fun xlsxMes(ctx: Context, libro: String, mes: YearMonth): File {
        val db = ElDuroDb.get(ctx)
        val carteras = db.carteras().all(); val porId = carteras.associateBy { it.id }
        val todos = db.movimientos().all()
        val tasas = TablaTasas(db.tasas().all())
        val deudas = db.deudas().all()
        val delLibro = Finanzas.delLibro(todos, porId, libro)
        val ym = mes.toString()
        val lin = lineas(delLibro.filter { it.fecha.startsWith(ym) }, porId, libro)

        val balance = Hoja(Fechas.mes(ym), listOf(12.0, 42.0, 16.0, 16.0, 18.0, 16.0, 14.0))
        balance.filas += listOf(C("BALANCE DE GASTOS E INGRESOS · $libro · ${Fechas.mes(ym)}", TITULO))
        balance.filas.add(emptyList())
        for ((titulo, monedas) in listOf("BOLIVARES" to setOf(Moneda.BS), "DOLARES" to setOf(Moneda.USD), "USDT" to setOf(Moneda.USDT))) {
            val l = lin.filter { it.cartera.moneda in monedas }
            if (l.isEmpty() && titulo == "USDT") continue
            balance.filas += listOf(C(titulo, NEGRITA))
            balance.filas += listOf("FECHA", "DESCRIPCION", "$titulo EFECTIVO", "$titulo DIGITAL", "CARTERA", "CATEGORIA", "TIPO").map { C(it, NEGRITA) }
            val ini = balance.filas.size + 1
            for (x in l) {
                val v = x.monto / 100.0
                balance.filas += listOf(C(serial(x.fecha), FECHA), C(x.desc),
                    if (x.cartera.forma == Forma.EFECTIVO) C(v, NUM) else null, if (x.cartera.forma == Forma.DIGITAL) C(v, NUM) else null,
                    C(x.cartera.nombre), C(x.categoria), C(x.tipo.etiqueta))
            }
            val fin = balance.filas.size
            balance.filas += listOf(null, C("TOTAL:", NEGRITA),
                C(null, NUM_N, if (fin >= ini) "SUM(C$ini:C$fin)" else "0"), C(null, NUM_N, if (fin >= ini) "SUM(D$ini:D$fin)" else "0"))
            balance.filas += listOf(null, C("Gastos", NEGRITA),
                C(null, NUM, if (fin >= ini) "SUMIF(G$ini:G$fin,\"Gasto\",C$ini:C$fin)" else "0"), C(null, NUM, if (fin >= ini) "SUMIF(G$ini:G$fin,\"Gasto\",D$ini:D$fin)" else "0"))
            balance.filas += listOf(null, C("Entradas", NEGRITA),
                C(null, NUM, if (fin >= ini) "SUMIF(G$ini:G$fin,\"Entrada\",C$ini:C$fin)" else "0"), C(null, NUM, if (fin >= ini) "SUMIF(G$ini:G$fin,\"Entrada\",D$ini:D$fin)" else "0"))
            balance.filas.add(emptyList())
        }

        // Resumen en $ BCV y $ Binance
        val res = Hoja("Resumen", listOf(34.0, 18.0, 18.0))
        res.filas += listOf(C("Resumen · $libro · ${Fechas.mes(ym)}", TITULO)); res.filas.add(emptyList())
        res.filas += listOf(C("", NEGRITA), C("$ BCV", NEGRITA), C("$ Binance", NEGRITA))
        val rb = Finanzas.resumen(delLibro, porId, libro, mes, tasas, Vista.USD_BCV, deudas)
        val rn = Finanzas.resumen(delLibro, porId, libro, mes, tasas, Vista.USD_BINANCE, deudas)
        fun r2(x: Double) = Math.round(x * 100) / 100.0
        fun fila(t: String, a: Double?, b: Double?) { res.filas += listOf(C(t), a?.let { C(r2(it), NUM) }, b?.let { C(r2(it), NUM) }) }
        fila("Entradas", rb.entradas, rn.entradas)
        fila("Gastos", -rb.gastos, -rn.gastos)
        fila("Resultado del mes", rb.resultado, rn.resultado)
        fila("Descuadres", rb.ajustes, rn.ajustes)
        fila("Ganancia/pérdida en cambios", rb.cambiaria, rn.cambiaria)
        fila("Devaluación de los Bs (aprox.)", rb.devaluacion, rn.devaluacion)
        fila("Saldo total al cierre", rb.patrimonio, rn.patrimonio)
        res.filas.add(emptyList()); res.filas += listOf(C("Saldos al cierre", NEGRITA), C("Monto", NEGRITA), C("Moneda", NEGRITA))
        val saldos = Finanzas.saldos(delLibro, mes.atEndOfMonth().toString())
        carteras.filter { it.libro == libro && !it.archivada }.forEach { c -> res.filas += listOf(C(c.nombre), C((saldos[c.id] ?: 0) / 100.0, NUM), C(c.moneda.simbolo)) }
        res.filas.add(emptyList()); res.filas += listOf(C("Gastos por categoría", NEGRITA), C("$ BCV", NEGRITA))
        rb.porCategoria.forEach { (cat, v) -> res.filas += listOf(C(cat), C(r2(v), NUM)) }

        // Deudas
        val deu = Hoja("Deudas", listOf(12.0, 40.0, 12.0, 16.0, 16.0, 16.0, 14.0))
        deu.filas += listOf("FECHA", "DESCRIPCION", "SENTIDO", "TOTAL", "PAGADO", "SALDO", "MONEDA").map { C(it, NEGRITA) }
        Control.estados(deudas.filter { it.libro == libro && !it.cerrada }, todos).forEach { e ->
            deu.filas += listOf(C(serial(e.deuda.fecha), FECHA), C(e.deuda.nombre), C(e.deuda.sentido.etiqueta),
                C(e.deuda.total / 100.0, NUM), C(e.pagado / 100.0, NUM), C(e.saldo / 100.0, NUM), C(e.deuda.moneda.simbolo))
        }
        val hojas = mutableListOf(balance, res, deu)
        val falt = db.faltantes().all().filter { it.libro == libro && !it.comprado }
        if (falt.isNotEmpty()) hojas += Hoja("Faltantes", listOf(50.0)).apply { filas += listOf(C("PENDIENTE POR COMPRAR", NEGRITA)); falt.forEach { filas += listOf(C(it.texto)) } }

        val dir = File(Prefs.carpeta(ctx), "Exportados").apply { mkdirs() }
        return File(dir, "Balance-$libro-$ym.xlsx").also { escribirXlsx(hojas, it) }
    }

    // ======================= PDF =======================

    fun pdfMes(ctx: Context, libro: String, mes: YearMonth, vista: Vista): File {
        val db = ElDuroDb.get(ctx)
        val carteras = db.carteras().all(); val porId = carteras.associateBy { it.id }
        val todos = db.movimientos().all(); val tasas = TablaTasas(db.tasas().all()); val deudas = db.deudas().all()
        val delLibro = Finanzas.delLibro(todos, porId, libro)
        val ym = mes.toString()
        val r = Finanzas.resumen(delLibro, porId, libro, mes, tasas, vista, deudas)
        val ant = Finanzas.resumen(delLibro, porId, libro, mes.minusMonths(1), tasas, vista, deudas)
        val movs = delLibro.filter { it.fecha.startsWith(ym) }.sortedBy { it.fecha }

        val W = 595; val H = 842; val M = 42f
        val doc = PdfDocument()
        val tit = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1B5E3F.toInt(); isAntiAlias = true }
        val sub = Paint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF222222.toInt(); isAntiAlias = true }
        val txt = Paint().apply { textSize = 9.5f; color = 0xFF222222.toInt(); isAntiAlias = true }
        val gris = Paint().apply { textSize = 8.5f; color = 0xFF777777.toInt(); isAntiAlias = true }
        val der = Paint(txt).apply { textAlign = Paint.Align.RIGHT }
        val barra = Paint().apply { color = 0xFFF2C14E.toInt() }
        val fondo = Paint().apply { color = 0xFFEDEFE9.toInt() }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create()); var n = 1
        var c = page.canvas; var y = M + 10
        fun nueva() { c.drawText("ElDuro · $libro · ${Fechas.mes(ym)} · pág. $n", M, H - 20f, gris); doc.finishPage(page); n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); c = page.canvas; y = M }
        fun sitio(h: Float) { if (y + h > H - 40) nueva() }
        fun v(x: Double, signo: Boolean = false) = Dinero.fmtVista(x, vista, signo)
        fun dif(a: Double, b: Double) = if (b != 0.0) " (${if (a >= b) "+" else ""}${((a - b) / kotlin.math.abs(b) * 100).toInt()}% vs mes anterior)" else ""

        c.drawText("ElDuro — ${Fechas.mes(ym)}", M, y, tit); y += 16
        c.drawText("Libro $libro · montos en ${vista.etiqueta} · generado ${Fechas.dmy(Fechas.hoy())}", M, y, gris); y += 22
        for ((t, x, extra) in listOf(
            Triple("Entradas", r.entradas, dif(r.entradas, ant.entradas)),
            Triple("Gastos", -r.gastos, dif(r.gastos, ant.gastos)),
            Triple("Resultado del mes", r.resultado, ""),
            Triple("Descuadres (arqueos)", r.ajustes, ""),
            Triple("Ganancia/pérdida en cambios", r.cambiaria, ""),
        ) + listOfNotNull(r.devaluacion?.let { Triple("Devaluación de los Bs (aprox.)", it, "") }) + listOf(Triple("Saldo total al cierre", r.patrimonio, ""))) {
            c.drawText(t, M, y, txt); c.drawText(v(x, true), W - M - 170, y, der); c.drawText(extra, W - M - 165, y, gris); y += 14
        }
        if (r.credito > 0) { c.drawText("Incluye compras a crédito: ${v(r.credito)}", M, y, gris); y += 14 }
        y += 10
        if (r.porCategoria.isNotEmpty()) {
            c.drawText("Gastos por categoría", M, y, sub); y += 14
            val max = r.porCategoria.maxOf { it.second }
            for ((cat, x) in r.porCategoria) {
                sitio(16f)
                c.drawText(cat, M, y, txt)
                c.drawRect(M + 130, y - 8, M + 130 + 250f, y, fondo)
                c.drawRect(M + 130, y - 8, M + 130 + (250 * x / max).toFloat(), y, barra)
                c.drawText("${v(x)} · ${(x / r.gastos * 100).toInt()}%", W - M, y, der); y += 15
            }
            y += 10
        }
        sitio(30f); c.drawText("Saldos al cierre", M, y, sub); y += 14
        val saldos = Finanzas.saldos(delLibro, mes.atEndOfMonth().toString())
        for (ca in carteras.filter { it.libro == libro && !it.archivada }) {
            sitio(14f); c.drawText(ca.nombre, M, y, txt); c.drawText(Dinero.fmt(saldos[ca.id] ?: 0, ca.moneda), W - M, y, der); y += 13
        }
        val est = Control.estados(deudas.filter { it.libro == libro && !it.cerrada }, todos).filter { !it.saldada }
        if (est.isNotEmpty()) {
            y += 10; sitio(30f); c.drawText("Deudas pendientes", M, y, sub); y += 14
            for (e in est) {
                sitio(14f); c.drawText("${e.deuda.sentido.etiqueta}: ${e.deuda.nombre}" + (e.proximoVence?.let { " · vence ${Fechas.dmy(it)}" } ?: ""), M, y, txt)
                c.drawText(Dinero.fmt(e.saldo, e.deuda.moneda), W - M, y, der); y += 13
            }
        }
        y += 10; sitio(30f); c.drawText("Movimientos (${movs.size})", M, y, sub); y += 14
        for (m in movs) {
            sitio(13f)
            val ca = porId[m.carteraId]
            c.drawText(Fechas.dmy(m.fecha), M, y, gris)
            c.drawText((m.descripcion.ifBlank { m.tipo.etiqueta }).take(48), M + 58, y, txt)
            c.drawText(listOf(m.tipo.etiqueta, m.categoria, ca?.nombre.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ").take(34), M + 300, y, gris)
            c.drawText(Dinero.fmt(m.monto, ca?.moneda ?: Moneda.BS, true), W - M, y, der); y += 13
        }
        c.drawText("ElDuro · $libro · ${Fechas.mes(ym)} · pág. $n", M, H - 20f, gris)
        doc.finishPage(page)
        val dir = File(Prefs.carpeta(ctx), "Reportes").apply { mkdirs() }
        return File(dir, "$ym-$libro.pdf").also { f -> FileOutputStream(f).use { doc.writeTo(it) }; doc.close() }
    }

    // ======================= ZIP =======================

    private val excluir = setOf("Backups", ".anteriores", "Exportados", "Reportes")

    fun zip(ctx: Context): File {
        val base = Prefs.carpeta(ctx)
        val dir = File(base, "Backups").apply { mkdirs() }
        val out = File(dir, "ElDuro-${Fechas.hoy().replace("-", "")}.zip")
        val tmp = File(dir, ".${out.name}.tmp")
        ZipOutputStream(FileOutputStream(tmp)).use { z ->
            base.walkTopDown().onEnter { it == base || it.name !in excluir }.filter { it.isFile && it.extension == "csv" }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(base).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        tmp.renameTo(out)
        dir.listFiles { f -> f.name.startsWith("ElDuro-") && f.name.endsWith(".zip") }.orEmpty().sortedByDescending { it.name }.drop(30).forEach { it.delete() }
        Prefs.sp(ctx).edit().putString("ultimo_zip", Fechas.hoy()).apply()
        return out
    }

    fun zipSiToca(ctx: Context): File? =
        if (Prefs.tieneAcceso() && Prefs.sp(ctx).getString("ultimo_zip", null) != Fechas.hoy()) zip(ctx) else null

}
