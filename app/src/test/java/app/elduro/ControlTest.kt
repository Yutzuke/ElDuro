package app.elduro

import app.elduro.core.Control
import app.elduro.data.Deuda
import app.elduro.data.Frecuencia
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Sentido
import app.elduro.data.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlTest {
    private val cashea = Deuda(id = 1, uid = "d1", libro = "Personal", sentido = Sentido.DEBO, nombre = "Cashea", moneda = Moneda.USD,
        total = 6500, fecha = "2026-10-01", cuotas = 3, frecuenciaDias = 14, primerVence = "2026-10-15")

    private fun pago(m: Long, md: Long = -m, deuda: Long = 1) =
        Movimiento(uid = "p$m$md", fecha = "2026-10-15", tipo = Tipo.TRANSFERENCIA, carteraId = 9, monto = m, montoDestino = md, deudaId = deuda)

    @Test fun cuotas() {
        var e = Control.estados(listOf(cashea), emptyList()).single()
        assertEquals(6500L, e.saldo); assertEquals(2166L, e.cuota); assertEquals("2026-10-15", e.proximoVence)
        e = Control.estados(listOf(cashea), listOf(pago(-2166))).single()
        assertEquals(1, e.cuotasPagadas); assertEquals("2026-10-29", e.proximoVence); assertEquals(4334L, e.saldo)
        e = Control.estados(listOf(cashea), listOf(pago(-6500))).single()
        assertEquals(0L, e.saldo); assertNull(e.proximoVence)
    }

    @Test fun pagoEnOtraMoneda() {
        // pagar 2.098 Bs a tasa 1.049 descuenta 2 $
        assertEquals(200L, Control.aplicado(209800, Moneda.BS, Moneda.USD, 1049.0))
        val e = Control.estados(listOf(cashea), listOf(pago(-209800, 200))).single()
        assertEquals(6300L, e.saldo)
    }

    @Test fun meDeben() {
        val prestamo = cashea.copy(id = 2, uid = "d2", sentido = Sentido.ME_DEBEN, cuotas = 0)
        val salida = pago(-6500, 6500, deuda = 2)          // presté: no descuenta
        val cobro = pago(2000, 2000, deuda = 2)            // me pagaron 20 $
        val e = Control.estados(listOf(prestamo), listOf(salida, cobro)).single()
        assertEquals(4500L, e.saldo)
    }

    @Test fun mensual() {
        val d = cashea.copy(frecuenciaDias = 0, primerVence = "2026-01-31")
        assertEquals("2026-02-28", Control.estados(listOf(d), listOf(pago(-2166))).single().proximoVence)
        assertEquals("2026-03-10", Control.siguiente("2026-02-10", Frecuencia.MENSUAL))
        assertEquals("2026-02-24", Control.siguiente("2026-02-10", Frecuencia.QUINCENAL))
    }
}
