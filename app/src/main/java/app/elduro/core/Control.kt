package app.elduro.core

import app.elduro.data.Cartera
import app.elduro.data.Deuda
import app.elduro.data.ElDuroDb
import app.elduro.data.Frecuencia
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Presupuesto
import app.elduro.data.Recurrente
import app.elduro.data.Sentido
import app.elduro.data.Tipo
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Estado calculado de una deuda. */
data class EstadoDeuda(
    val deuda: Deuda,
    val pagado: Long,              // en la moneda de la deuda
    val abonos: List<Movimiento>,
) {
    val saldo get() = (deuda.total - pagado).coerceAtLeast(0)
    val cuota: Long? get() = if (deuda.cuotas > 0) deuda.total / deuda.cuotas else null
    val cuotasPagadas: Int get() = cuota?.let { if (it > 0) (pagado / it).toInt().coerceAtMost(deuda.cuotas) else 0 } ?: 0
    val saldada get() = saldo == 0L

    /** Próximo vencimiento según el plan de cuotas (null si no hay plan o ya se pagó). */
    val proximoVence: String? get() {
        val p = deuda.primerVence ?: return null
        if (deuda.cuotas <= 0 || saldada) return null
        val base = LocalDate.parse(p)
        val n = cuotasPagadas.toLong()
        return (if (deuda.frecuenciaDias > 0) base.plusDays(n * deuda.frecuenciaDias) else base.plusMonths(n)).toString()
    }

    fun diasParaVencer(hoy: LocalDate = LocalDate.now()): Long? = proximoVence?.let { ChronoUnit.DAYS.between(hoy, LocalDate.parse(it)) }
}

object Control {

    fun estados(deudas: List<Deuda>, movs: List<Movimiento>): List<EstadoDeuda> {
        val porDeuda = movs.filter { it.deudaId != null }.groupBy { it.deudaId!! }
        return deudas.map { d ->
            val ab = porDeuda[d.id].orEmpty().sortedBy { it.fecha }
            // en DEBO los pagos salen (monto < 0); en ME_DEBEN los cobros entran (monto > 0).
            // El préstamo inicial de ME_DEBEN (salida de dinero) no descuenta: se excluye por signo.
            val aplica = ab.filter { if (d.sentido == Sentido.DEBO) it.monto < 0 else it.monto > 0 }
            EstadoDeuda(d, aplica.sumOf { abs(it.montoDestino ?: it.monto) }, ab)
        }
    }

    /** Monto que se descuenta de la deuda al pagar [monto] desde una cartera en otra moneda. */
    fun aplicado(monto: Long, desde: Moneda, deuda: Moneda, tasa: Double?): Long? =
        EntradaRapida.montoDestino(abs(monto), desde, deuda, tasa)

    /** Registra un abono (debo) o un cobro (me deben). */
    fun abonar(db: ElDuroDb, e: EstadoDeuda, c: Cartera, monto: Long, aplicado: Long, tasa: Double?, fecha: String) {
        val d = e.deuda
        val firmado = if (d.sentido == Sentido.DEBO) -abs(monto) else abs(monto)
        db.movimientos().insert(Movimiento(
            uid = Duro.uid(), fecha = fecha, tipo = Tipo.TRANSFERENCIA, carteraId = c.id, monto = firmado,
            montoDestino = abs(aplicado), tasa = if (c.moneda != d.moneda) tasa else null, deudaId = d.id,
            descripcion = (if (d.sentido == Sentido.DEBO) "Pago a " else "Cobro de ") + d.nombre,
        ))
        val nuevoSaldo = e.saldo - abs(aplicado)
        if (nuevoSaldo <= 0 && !d.cerrada) db.deudas().upsert(d.copy(cerrada = true))
    }

    /** Crea la deuda; si es "me deben" y salió dinero, registra esa salida como transferencia (no es gasto). */
    fun crearDeuda(db: ElDuroDb, d: Deuda, salida: Cartera?) {
        val id = db.deudas().upsert(d).let { if (d.id != 0L) d.id else it }
        if (salida != null && d.sentido == Sentido.ME_DEBEN && d.id == 0L) {
            db.movimientos().insert(Movimiento(
                uid = Duro.uid(), fecha = d.fecha, tipo = Tipo.TRANSFERENCIA, carteraId = salida.id, monto = -d.total,
                montoDestino = d.total, deudaId = id, descripcion = "Préstamo a ${d.nombre}",
            ))
        }
    }

    fun borrarDeuda(db: ElDuroDb, d: Deuda) = db.runInTransaction {
        db.movimientos().soltarDeuda(d.id)
        db.deudas().delete(d.id)
    }

    // ---------- recurrentes ----------

    fun siguiente(fecha: String, f: Frecuencia): String {
        val d = LocalDate.parse(fecha)
        return (if (f == Frecuencia.MENSUAL) d.plusMonths(1) else d.plusDays(f.dias.toLong())).toString()
    }

    /** uid determinista: registrar dos veces el mismo vencimiento no duplica. */
    fun uidRecurrente(r: Recurrente, fecha: String) = "rc" + r.uid.take(6) + fecha.replace("-", "")

    fun registrar(db: ElDuroDb, r: Recurrente, fecha: String) {
        val uid = uidRecurrente(r, fecha)
        if (db.movimientos().byUid(uid) == null) db.movimientos().insert(Movimiento(
            uid = uid, fecha = fecha, tipo = r.tipo, carteraId = r.carteraId,
            monto = if (r.tipo == Tipo.GASTO) -abs(r.monto) else abs(r.monto),
            categoria = r.categoria, descripcion = r.descripcion,
        ))
        db.recurrentes().upsert(r.copy(proxima = siguiente(fecha, r.frecuencia)))
    }

    fun saltar(db: ElDuroDb, r: Recurrente) = db.recurrentes().upsert(r.copy(proxima = siguiente(r.proxima, r.frecuencia)))

    /** Registra los automáticos vencidos. Devuelve los manuales que esperan confirmación. */
    fun procesarRecurrentes(db: ElDuroDb, hoy: String = Fechas.hoy()): List<Recurrente> {
        val pendientes = mutableListOf<Recurrente>()
        for (r0 in db.recurrentes().all().filter { it.activa }) {
            var r = r0
            var vueltas = 0
            while (r.automatico && r.proxima <= hoy && vueltas++ < 60) {
                registrar(db, r, r.proxima)
                r = db.recurrentes().byUid(r.uid) ?: break
            }
            if (!r.automatico && r.proxima <= hoy) pendientes += r
        }
        return pendientes
    }

    // ---------- presupuestos ----------

    data class Avance(val p: Presupuesto, val gastado: Double, val limite: Double) {
        val fraccion get() = if (limite > 0) gastado / limite else 0.0
    }

    /** Gasto del mes por categoría comparado con su tope (USD en $ BCV, Bs en Bs). */
    fun avances(presupuestos: List<Presupuesto>, movsLibro: List<Movimiento>, deudas: List<Deuda>, carteras: Map<Long, Cartera>,
                libro: String, mes: YearMonth, tasas: TablaTasas): List<Avance> {
        val ps = presupuestos.filter { it.libro == libro }
        if (ps.isEmpty()) return emptyList()
        val porMoneda = ps.map { it.moneda }.distinct().associateWith { m ->
            Finanzas.resumen(movsLibro, carteras, libro, mes, tasas, if (m == Moneda.BS) Vista.BS else Vista.USD_BCV, deudas).porCategoria.toMap()
        }
        return ps.map { p -> Avance(p, porMoneda[p.moneda]?.get(p.categoria) ?: 0.0, p.monto / 100.0) }
            .sortedByDescending { it.fraccion }
    }
}
