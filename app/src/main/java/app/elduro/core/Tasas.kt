package app.elduro.core

import app.elduro.data.ElDuroDb
import app.elduro.data.Moneda
import app.elduro.data.Tasa
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TreeMap

object Fuente {
    const val BCV = "BCV"
    const val BINANCE = "BINANCE"      // precio para comprar USDT (lo que pagas)
    const val BINANCE_V = "BINANCE_V"  // precio al vender USDT (lo que recibes)
}

/** Resultado de buscar una tasa: valor y si es estimada (no es del día pedido). */
data class TasaUsada(val valor: Double, val fecha: String, val origen: String, val estimada: Boolean)

/**
 * Tabla de tasas en memoria. Nunca falla: si no hay tasa del día usa la anterior más cercana
 * (o la siguiente) y la marca como estimada. Cuando llega la real, los cálculos cambian solos.
 */
class TablaTasas(tasas: List<Tasa>) {
    private val porFuente = HashMap<String, TreeMap<String, Tasa>>()

    init {
        val prio = mapOf("manual" to 3, "auto" to 2, "paralelo" to 1)
        for (t in tasas) {
            val m = porFuente.getOrPut(t.fuente) { TreeMap() }
            val prev = m[t.fecha]
            if (prev == null || (prio[t.origen] ?: 0) > (prio[prev.origen] ?: 0)) m[t.fecha] = t
        }
    }

    fun en(fuente: String, fecha: String): TasaUsada? {
        val m = porFuente[fuente] ?: return null
        m[fecha]?.let { return TasaUsada(it.valor, it.fecha, it.origen, false) }
        val t = m.floorEntry(fecha)?.value ?: m.ceilingEntry(fecha)?.value ?: return null
        return TasaUsada(t.valor, t.fecha, t.origen, true)
    }

    fun ultima(fuente: String): Tasa? = porFuente[fuente]?.lastEntry()?.value
    fun historial(fuente: String): List<Tasa> = porFuente[fuente]?.values?.reversed().orEmpty()

    /** Bs por $ que se usa para una vista ($BCV → BCV, $Binance → Binance). */
    private fun fuenteDe(vista: Vista, moneda: Moneda): String = when (vista) {
        Vista.USD_BCV -> Fuente.BCV
        Vista.USD_BINANCE -> Fuente.BINANCE
        Vista.BS -> if (moneda == Moneda.USDT) Fuente.BINANCE else Fuente.BCV
    }

    /**
     * Convierte [centimos] de [moneda] a la [vista] con la tasa de [fecha].
     * Devuelve null si nunca se ha registrado una tasa de esa fuente.
     */
    fun convertir(centimos: Long, moneda: Moneda, fecha: String, vista: Vista): Conversion? {
        val v = centimos / 100.0
        val enDolares = moneda != Moneda.BS
        if (vista != Vista.BS && enDolares) return Conversion(v, null)
        if (vista == Vista.BS && !enDolares) return Conversion(v, null)
        val t = en(fuenteDe(vista, moneda), fecha) ?: return null
        return Conversion(if (vista == Vista.BS) v * t.valor else v / t.valor, t)
    }

    fun brecha(fecha: String): Double? {
        val b = en(Fuente.BCV, fecha)?.valor ?: return null
        val n = en(Fuente.BINANCE, fecha)?.valor ?: return null
        return (n / b - 1) * 100
    }
}

data class Conversion(val valor: Double, val tasa: TasaUsada?) {
    val estimada get() = tasa?.estimada == true
}

/** Clasificación de la brecha, igual que el monitor de Binance del usuario. */
fun etiquetaBrecha(b: Double): String = when {
    b >= 19 -> "Mercado Cachete"
    b >= 15 -> "Mercado Caliente"
    else -> "Normal"
}

/** Consulta de tasas por internet. Cada fuente falla por separado sin afectar a las demás. */
object ConsultaTasas {
    private const val UA = "ElDuro/0.1 (Android)"

    data class Informe(val ok: List<String>, val fallos: List<String>)

    fun actualizar(db: ElDuroDb): Informe {
        val ok = mutableListOf<String>(); val fallos = mutableListOf<String>()
        val hoy = Fechas.hoy()
        val nuevas = mutableListOf<Tasa>()
        runCatching { bcvDolarApi() }.recoverCatching { bcvWeb() }
            .onSuccess { nuevas += Tasa(hoy, Fuente.BCV, "auto", it); ok += "BCV" }
            .onFailure { fallos += "BCV (${it.message ?: it.javaClass.simpleName})" }
        runCatching { binance("BUY") }
            .onSuccess { nuevas += Tasa(hoy, Fuente.BINANCE, "auto", it); ok += "Binance" }
            .onFailure { fallos += "Binance (${it.message ?: it.javaClass.simpleName})" }
        runCatching { binance("SELL") }.onSuccess { nuevas += Tasa(hoy, Fuente.BINANCE_V, "auto", it) }
        if (nuevas.isNotEmpty()) db.tasas().upsertAll(nuevas)
        return Informe(ok, fallos)
    }

    /** Rellena el histórico BCV (oficial) y el paralelo como aproximación de Binance para fechas pasadas. */
    fun historico(db: ElDuroDb, desde: String): Int {
        val lista = mutableListOf<Tasa>()
        val bcv = JSONArray(get("https://ve.dolarapi.com/v1/historicos/dolares/oficial"))
        for (i in 0 until bcv.length()) {
            val o = bcv.getJSONObject(i); val f = o.getString("fecha").take(10)
            if (f >= desde) lista += Tasa(f, Fuente.BCV, "auto", o.getDouble("promedio"))
        }
        runCatching {
            val par = JSONArray(get("https://ve.dolarapi.com/v1/historicos/dolares/paralelo"))
            for (i in 0 until par.length()) {
                val o = par.getJSONObject(i); val f = o.getString("fecha").take(10)
                if (f >= desde && !o.isNull("promedio")) lista += Tasa(f, Fuente.BINANCE, "paralelo", o.getDouble("promedio"))
            }
        }
        db.tasas().upsertAll(lista)
        return lista.size
    }

    private fun bcvDolarApi(): Double {
        val o = JSONObject(get("https://ve.dolarapi.com/v1/dolares/oficial"))
        return o.getDouble("promedio").also { require(it > 0) }
    }

    private fun bcvWeb(): Double {
        val html = get("https://www.bcv.org.ve/")
        val m = Regex("""id="dolar"[\s\S]{0,600}?<strong>\s*([\d.,]+)\s*</strong>""").find(html) ?: error("sin dato en bcv.org.ve")
        return m.groupValues[1].replace(".", "").replace(',', '.').toDouble()
    }

    /** Mediana de los 5 primeros anuncios P2P USDT/VES. */
    private fun binance(tradeType: String): Double {
        val body = """{"asset":"USDT","fiat":"VES","tradeType":"$tradeType","page":1,"rows":5,"payTypes":[],"publisherType":null}"""
        val r = JSONObject(post("https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search", body))
        val data = r.getJSONArray("data")
        val precios = (0 until data.length()).map { data.getJSONObject(it).getJSONObject("adv").getString("price").toDouble() }.sorted()
        require(precios.isNotEmpty()) { "sin anuncios" }
        return precios[precios.size / 2]
    }

    private fun get(url: String): String = http(url, null)
    private fun post(url: String, json: String): String = http(url, json)

    private fun http(url: String, json: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json, text/html")
        if (json != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toByteArray()) }
        }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
