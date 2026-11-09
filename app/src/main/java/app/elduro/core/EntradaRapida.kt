package app.elduro.core

import app.elduro.data.Cartera
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Tipo
import kotlin.math.roundToLong

/**
 * Interpreta textos como:
 *   "-8000 pollo"            gasto de 8.000 en la cartera elegida
 *   "+15$ venta placas"      entrada de 15 $
 *   "8.000 ef comida"        gasto en efectivo
 *   "20$ > bs 1048 cambio"   transferencia de 20 $ a Bs a tasa 1.048
 *   "500 bs dig > ef"        retiro: de Bs digital a Bs efectivo
 */
object EntradaRapida {
    data class Lectura(
        val tipo: Tipo,
        val monto: Long?,                // céntimos, positivo
        val moneda: Moneda?,
        val forma: Forma?,
        val monedaDestino: Moneda?,
        val formaDestino: Forma?,
        val tasa: Double?,
        val descripcion: String,
    )

    private val monedas = mapOf(
        "$" to Moneda.USD, "usd" to Moneda.USD, "dolar" to Moneda.USD, "dolares" to Moneda.USD, "dólares" to Moneda.USD,
        "bs" to Moneda.BS, "bs." to Moneda.BS, "ves" to Moneda.BS, "bolivares" to Moneda.BS, "bolívares" to Moneda.BS,
        "usdt" to Moneda.USDT,
    )
    private val formas = mapOf(
        "ef" to Forma.EFECTIVO, "efe" to Forma.EFECTIVO, "efec" to Forma.EFECTIVO, "efectivo" to Forma.EFECTIVO, "cash" to Forma.EFECTIVO,
        "dig" to Forma.DIGITAL, "digital" to Forma.DIGITAL, "pm" to Forma.DIGITAL, "banco" to Forma.DIGITAL,
    )

    fun leer(texto: String): Lectura {
        var t = texto.trim()
        val signo = t.firstOrNull()?.takeIf { it == '+' || it == '-' || it == '−' }
        if (signo != null) t = t.drop(1)
        t = t.replace(">", " > ").replace("→", " > ")
            .replace(Regex("""(?i)(\d)(\$|usdt|usd|bs)(?![a-z])"""), "$1 $2")
            .replace(Regex("""\$(\d)"""), "\$ $1")
        val tokens = t.split(Regex("\\s+")).filter { it.isNotEmpty() }

        var monto: Long? = null
        var moneda: Moneda? = null; var forma: Forma? = null
        var mDest: Moneda? = null; var fDest: Forma? = null; var tasa: Double? = null
        var destino = false
        val desc = mutableListOf<String>()
        for (tok in tokens) {
            val k = tok.lowercase()
            when {
                k == ">" -> destino = true
                monedas.containsKey(k) -> if (destino) mDest = monedas[k] else moneda = monedas[k]
                formas.containsKey(k) -> if (destino) fDest = formas[k] else forma = formas[k]
                !destino && monto == null && Dinero.parse(tok) != null -> monto = Dinero.parse(tok)?.let { kotlin.math.abs(it) }
                destino && tasa == null && Dinero.parseDouble(tok) != null -> tasa = Dinero.parseDouble(tok)
                else -> desc += tok
            }
        }
        val tipo = when {
            destino -> Tipo.TRANSFERENCIA
            signo == '+' -> Tipo.ENTRADA
            else -> Tipo.GASTO
        }
        return Lectura(tipo, monto, moneda, forma, mDest, fDest, tasa, desc.joinToString(" "))
    }

    /** Busca la cartera del libro que mejor encaja; [base] da los valores por defecto. */
    fun cartera(carteras: List<Cartera>, base: Cartera?, moneda: Moneda?, forma: Forma?, excluir: Long? = null): Cartera? {
        val vivas = carteras.filter { !it.archivada && it.id != excluir }
        val m = moneda ?: base?.moneda; val f = forma ?: base?.forma
        if (moneda == null && forma == null && base != null && base.id != excluir) return base
        return vivas.firstOrNull { it.moneda == m && it.forma == f }
            ?: vivas.firstOrNull { it.moneda == m }
            ?: vivas.firstOrNull { it.forma == f }
            ?: vivas.firstOrNull()
    }

    /** Destino por defecto: misma moneda → la otra forma (retiro/depósito); otra moneda → misma forma. */
    fun destino(carteras: List<Cartera>, origen: Cartera, l: Lectura): Cartera? {
        val m = l.monedaDestino ?: origen.moneda
        val f = l.formaDestino ?: if (m == origen.moneda) (if (origen.forma == Forma.EFECTIVO) Forma.DIGITAL else Forma.EFECTIVO) else origen.forma
        return cartera(carteras, null, m, f, excluir = origen.id)
    }

    /** Monto que llega a destino. tasa = Bs por $. */
    fun montoDestino(monto: Long, de: Moneda, a: Moneda, tasa: Double?): Long? = when {
        de == a -> monto
        de != Moneda.BS && a != Moneda.BS -> monto            // $ ↔ USDT 1:1
        tasa == null || tasa <= 0 -> null
        de == Moneda.BS -> (monto / tasa).roundToLong()
        else -> (monto * tasa).roundToLong()
    }
}
