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
    /** Abono/cobro de una deuda: [montoDestino] es lo que se descuenta de la deuda (en su moneda). */
    val deudaId: Long? = null,
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

enum class Sentido(val etiqueta: String) { DEBO("Debo"), ME_DEBEN("Me deben") }

/**
 * Deuda u obligación. Su saldo = [total] − abonos (movimientos con deudaId).
 * Si [esGasto], la compra a crédito cuenta como gasto del mes de [fecha]; los pagos son transferencias.
 */
@Entity(indices = [Index(value = ["uid"], unique = true)])
data class Deuda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val libro: String,
    val sentido: Sentido,
    val nombre: String,
    val moneda: Moneda,
    val total: Long,
    val fecha: String,
    val cuotas: Int = 0,               // 0 = sin plan de cuotas
    val frecuenciaDias: Int = 0,        // 7, 14, 15, 30…
    val primerVence: String? = null,
    val esGasto: Boolean = false,
    val categoria: String = "",
    val notas: String = "",
    val cerrada: Boolean = false,
)

enum class Frecuencia(val etiqueta: String, val dias: Int) {
    SEMANAL("Semanal", 7), QUINCENAL("Cada 14 días", 14), MENSUAL("Mensual", 0);
}

/** Movimiento que se repite (servicios, sueldo, alquiler). */
@Entity(indices = [Index(value = ["uid"], unique = true)])
data class Recurrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val libro: String,
    val tipo: Tipo,                     // GASTO o ENTRADA
    val carteraId: Long,
    val monto: Long,                    // positivo
    val categoria: String,
    val descripcion: String,
    val frecuencia: Frecuencia,
    val proxima: String,
    val automatico: Boolean = false,    // true: se registra solo; false: se propone
    val activa: Boolean = true,
)

/** Tope mensual de gasto por categoría, en [moneda] (USD se compara en $ BCV). */
@Entity(primaryKeys = ["libro", "categoria"])
data class Presupuesto(
    val libro: String,
    val categoria: String,
    val monto: Long,
    val moneda: Moneda,
)

@Entity(indices = [Index(value = ["uid"], unique = true)])
data class Faltante(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val libro: String,
    val texto: String,
    val comprado: Boolean = false,
    val creado: Long = System.currentTimeMillis(),
)
