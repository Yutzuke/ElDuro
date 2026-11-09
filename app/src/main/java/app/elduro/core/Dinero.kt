package app.elduro.core

import app.elduro.data.Moneda
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formato venezolano: miles con punto, decimales con coma. Montos en céntimos. */
object Dinero {
    private val sym = DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = '.'; decimalSeparator = ',' }
    private val f2 = DecimalFormat("#,##0.00", sym)
    private val f0 = DecimalFormat("#,##0", sym)
    private val csv = DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT).apply { decimalSeparator = ',' })

    fun num(v: Double, decimales: Boolean = true): String = (if (decimales) f2 else f0).format(v)

    /** 12.345,50 Bs · 15,00 $ · omite decimales si son ,00 en Bs grandes. */
    fun fmt(centimos: Long, moneda: Moneda, signo: Boolean = false): String {
        val v = centimos / 100.0
        val s = if (moneda == Moneda.BS && centimos % 100 == 0L) f0.format(abs(v)) else f2.format(abs(v))
        val pre = when { centimos < 0 -> "−"; signo && centimos > 0 -> "+"; else -> "" }
        return "$pre$s ${moneda.simbolo}"
    }

    fun fmtVista(valor: Double, vista: Vista, signo: Boolean = false): String {
        val pre = when { valor < -0.004 -> "−"; signo && valor > 0.004 -> "+"; else -> "" }
        val s = f2.format(abs(valor))
        return if (vista == Vista.BS) "$pre$s Bs" else "$pre$s $"
    }

    fun tasa(v: Double): String = f2.format(v)

    /** Para CSV: 1234,5 (sin miles). */
    fun csv(centimos: Long): String = csv.format(centimos / 100.0)

    /**
     * Interpreta "8.000", "8000", "8.000,50", "15,5", "15.5", "1.048,25".
     * Con coma: la coma es decimal. Sólo puntos: miles si van en grupos de 3, si no decimal.
     */
    fun parse(texto: String): Long? {
        val t = texto.trim().replace(" ", "").replace("−", "-")
        if (t.isEmpty()) return null
        val neg = t.startsWith("-")
        val s = t.trimStart('-', '+')
        val norm = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            Regex("""\d{1,3}(\.\d{3})+""").matches(s) -> s.replace(".", "")
            else -> s
        }
        val v = norm.toDoubleOrNull() ?: return null
        val c = (v * 100).roundToLong()
        return if (neg) -c else c
    }

    fun parseDouble(texto: String): Double? = parse(texto)?.let { it / 100.0 }
}

/** Moneda en la que se muestran los totales. */
enum class Vista(val etiqueta: String) { USD_BCV("$ BCV"), USD_BINANCE("$ Binance"), BS("Bs") }

object Fechas {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val corta = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es"))
    private val mes = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("es"))
    private val dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun hoy(): String = LocalDate.now().format(ISO)
    fun corta(iso: String): String = runCatching { LocalDate.parse(iso).format(corta) }.getOrDefault(iso)
    fun dmy(iso: String): String = runCatching { LocalDate.parse(iso).format(dmy) }.getOrDefault(iso)
    fun mes(ym: String): String = runCatching { LocalDate.parse("$ym-01").format(mes).replaceFirstChar { it.uppercase() } }.getOrDefault(ym)
    fun ym(iso: String): String = iso.take(7)
}
