package app.elduro.core

import app.elduro.data.Cartera
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import java.time.LocalDate
import java.time.YearMonth

/** Cálculos del mes para un libro. Todo se recalcula a partir de los movimientos y la tabla de tasas. */
object Finanzas {

    data class Celda(val moneda: Moneda, val forma: Forma, val centimos: Long)

    data class Resumen(
        val entradas: Double,
        val gastos: Double,                 // positivo
        val ajustes: Double,                // con signo (descuadres)
        val cambiaria: Double,              // ganancia/pérdida en cambios de divisa
        val porCategoria: List<Pair<String, Double>>,
        val celdas: List<Celda>,            // saldos al cierre por moneda × forma
        val patrimonio: Double,             // saldos convertidos a la vista
        val estimadas: Int,                 // movimientos convertidos con tasa estimada
        val sinTasa: Int,                   // movimientos que no se pudieron convertir
    ) {
        val resultado get() = entradas - gastos
    }

    /** Saldo de cada cartera hasta [hasta] (incluido). */
    fun saldos(movs: List<Movimiento>, hasta: String? = null): Map<Long, Long> {
        val s = HashMap<Long, Long>()
        for (m in movs) {
            if (hasta != null && m.fecha > hasta) continue
            s[m.carteraId] = (s[m.carteraId] ?: 0) + m.monto
            val d = m.carteraDestinoId; val md = m.montoDestino
            if (d != null && md != null) s[d] = (s[d] ?: 0) + md
        }
        return s
    }

    fun delLibro(movs: List<Movimiento>, carteras: Map<Long, Cartera>, libro: String) =
        movs.filter { carteras[it.carteraId]?.libro == libro || carteras[it.carteraDestinoId]?.libro == libro }

    fun resumen(
        movsLibro: List<Movimiento>, carteras: Map<Long, Cartera>, libro: String,
        mes: YearMonth, tasas: TablaTasas, vista: Vista,
    ): Resumen {
        val ym = mes.toString()
        val fin = mes.atEndOfMonth().toString()
        val hoy = LocalDate.now().toString()
        var entradas = 0.0; var gastos = 0.0; var ajustes = 0.0; var cambiaria = 0.0
        var est = 0; var sin = 0
        val cats = HashMap<String, Double>()

        fun conv(c: Long, mon: Moneda, fecha: String): Double? {
            val r = tasas.convertir(c, mon, fecha, vista)
            if (r == null) { sin++; return null }
            if (r.estimada) est++
            return r.valor
        }

        for (m in movsLibro) {
            if (!m.fecha.startsWith(ym)) continue
            val c = carteras[m.carteraId] ?: continue
            val propio = c.libro == libro
            when (m.tipo) {
                Tipo.ENTRADA -> if (propio) conv(m.monto, c.moneda, m.fecha)?.let { entradas += it }
                Tipo.GASTO -> if (propio) conv(-m.monto, c.moneda, m.fecha)?.let { gastos += it; cats.merge(m.categoria.ifBlank { "Otros" }, it, Double::plus) }
                Tipo.AJUSTE -> if (propio) conv(m.monto, c.moneda, m.fecha)?.let { ajustes += it }
                Tipo.TRANSFERENCIA -> {
                    val d = carteras[m.carteraDestinoId] ?: continue
                    if (propio && d.libro == libro && d.moneda != c.moneda && m.montoDestino != null) {
                        val sale = conv(m.monto, c.moneda, m.fecha); val entra = conv(m.montoDestino, d.moneda, m.fecha)
                        if (sale != null && entra != null) cambiaria += entra + sale
                    }
                }
                Tipo.APERTURA -> {}
            }
        }

        val corte = if (fin < hoy) fin else hoy
        val saldos = saldos(movsLibro, fin)
        val delLibro = carteras.values.filter { it.libro == libro }
        val celdas = delLibro.groupBy { it.moneda to it.forma }.map { (k, cs) ->
            Celda(k.first, k.second, cs.sumOf { saldos[it.id] ?: 0L })
        }.sortedWith(compareBy({ it.moneda.ordinal }, { it.forma.ordinal }))
        var patrimonio = 0.0
        for (c in celdas) tasas.convertir(c.centimos, c.moneda, corte, vista)?.let { patrimonio += it.valor }

        return Resumen(entradas, gastos, ajustes, cambiaria,
            cats.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value },
            celdas, patrimonio, est, sin)
    }
}
