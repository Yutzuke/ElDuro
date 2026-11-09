package app.elduro.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Moneda(val simbolo: String, val etiqueta: String) {
    BS("Bs", "Bolívares"), USD("$", "Dólares"), USDT("USDT", "USDT");
}

enum class Forma(val etiqueta: String) { EFECTIVO("Efectivo"), DIGITAL("Digital") }

/**
 * Tipo de movimiento. Sólo ENTRADA y GASTO cuentan para los totales del mes.
 * - TRANSFERENCIA: dinero que cambia de cartera (retiro, depósito, cambio de divisa).
 * - AJUSTE: descuadre detectado en un arqueo o conciliación.
 * - APERTURA: saldo con el que arranca una cartera (no es un ingreso).
 */
enum class Tipo(val etiqueta: String) {
    ENTRADA("Entrada"), GASTO("Gasto"), TRANSFERENCIA("Transferencia"), AJUSTE("Ajuste"), APERTURA("Apertura");
}

@Entity(indices = [Index(value = ["libro", "nombre"], unique = true)])
data class Cartera(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libro: String,
    val nombre: String,
    val moneda: Moneda,
    val forma: Forma,
    val orden: Int = 0,
    val archivada: Boolean = false,
)

/**
 * Montos en céntimos y con signo según su efecto en la cartera:
 * [monto] afecta a [carteraId]; en transferencias [montoDestino] (positivo) afecta a [carteraDestinoId].
 */
@Entity(indices = [Index("fecha"), Index("carteraId"), Index("carteraDestinoId"), Index(value = ["uid"], unique = true)])
data class Movimiento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val fecha: String,            // yyyy-MM-dd
    val tipo: Tipo,
    val carteraId: Long,
    val monto: Long,
    val carteraDestinoId: Long? = null,
    val montoDestino: Long? = null,
    val tasa: Double? = null,     // tasa real usada en un cambio de divisa (Bs por $)
    val categoria: String = "",
    val descripcion: String = "",
    val creado: Long = System.currentTimeMillis(),
)

/** Tasa Bs por dólar de una fuente en una fecha. origen: manual > auto > paralelo. */
@Entity(primaryKeys = ["fecha", "fuente", "origen"])
data class Tasa(
    val fecha: String,
    val fuente: String,           // BCV | BINANCE | BINANCE_V
    val origen: String,           // manual | auto | paralelo
    val valor: Double,
    val actualizado: Long = System.currentTimeMillis(),
)

/** Palabra de una descripción → categoría elegida por el usuario. */
@Entity
data class Aprendizaje(
    @PrimaryKey val palabra: String,
    val categoria: String,
)
