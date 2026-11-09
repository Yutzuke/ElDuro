package app.elduro.core

import java.text.Normalizer

object Categorias {
    val GASTO = listOf("Comida", "Transporte", "Hogar", "Servicios", "Salud", "Tienda", "Compras", "Trading", "Comisiones", "Familia", "Otros")
    val ENTRADA = listOf("Ventas", "Servicio técnico", "Sueldo", "Trading", "Préstamo recibido", "Otros ingresos")
    val TODAS = (GASTO + ENTRADA).distinct()

    private val claves: Map<String, List<String>> = mapOf(
        "Comida" to listOf("pollo", "carne", "arroz", "comida", "pan", "harina", "queso", "huevo", "mercado", "fruta", "cafe", "pasta", "azucar", "aceite comestible", "almuerzo", "cena", "desayuno", "chuche", "refresco", "agua potable"),
        "Transporte" to listOf("gasolina", "aceite", "moto", "pasaje", "taxi", "mototaxi", "repuesto", "caucho", "mecanico", "estacionamiento"),
        "Servicios" to listOf("internet", "luz", "agua", "telefono", "recarga", "saldo", "cantv", "movistar", "digitel", "inter", "netflix"),
        "Salud" to listOf("farmacia", "medicina", "consulta", "doctor", "pastilla", "examen"),
        "Tienda" to listOf("impresion", "papeleria", "forro", "cable", "pendrive", "mercancia", "administrativo", "tienda", "local", "alquiler"),
        "Comisiones" to listOf("comision", "mantenimiento cuenta"),
        "Trading" to listOf("trading", "perdida", "futuros"),
        "Compras" to listOf("zapato", "ropa", "compra"),
        "Ventas" to listOf("venta", "vendi", "cobro"),
        "Servicio técnico" to listOf("reparacion", "pantalla", "placa", "revision"),
    )

    fun normal(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun palabras(desc: String): List<String> =
        normal(desc).split(Regex("[^a-z0-9ñ]+")).filter { it.length >= 3 }

    /** Primero lo aprendido del usuario, luego las palabras clave. */
    fun adivinar(desc: String, entrada: Boolean, aprendido: Map<String, String>): String? {
        val ps = palabras(desc)
        ps.firstNotNullOfOrNull { aprendido[it] }?.let { return it }
        val n = normal(desc)
        val validas = if (entrada) ENTRADA else GASTO
        for ((cat, ks) in claves) if (cat in validas && ks.any { n.contains(it) }) return cat
        // si no casa por tipo, cualquier coincidencia
        for ((cat, ks) in claves) if (ks.any { n.contains(it) }) return cat
        return null
    }
}
