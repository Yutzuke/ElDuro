package app.elduro

import app.elduro.core.CarpetaSync
import app.elduro.core.Dinero
import app.elduro.core.EntradaRapida
import app.elduro.core.ImportarExcel
import app.elduro.core.TablaTasas
import app.elduro.core.Vista
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Tasa
import app.elduro.data.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicaTest {
    @Test fun montos() {
        assertEquals(800000L, Dinero.parse("8.000"))
        assertEquals(800050L, Dinero.parse("8.000,50"))
        assertEquals(1550L, Dinero.parse("15,5"))
        assertEquals(1550L, Dinero.parse("15.5"))
        assertEquals(104825L, Dinero.parse("1.048,25"))
        assertEquals(-2400000L, Dinero.parse("-24000"))
        assertEquals("8.000 Bs", Dinero.fmt(800000, Moneda.BS))
        assertEquals("−15,00 $", Dinero.fmt(-1500, Moneda.USD))
    }

    @Test fun rapida() {
        var l = EntradaRapida.leer("-8000 pollo")
        assertEquals(Tipo.GASTO, l.tipo); assertEquals(800000L, l.monto); assertEquals("pollo", l.descripcion)
        l = EntradaRapida.leer("+15$ venta placas")
        assertEquals(Tipo.ENTRADA, l.tipo); assertEquals(1500L, l.monto); assertEquals(Moneda.USD, l.moneda); assertEquals("venta placas", l.descripcion)
        l = EntradaRapida.leer("8.000 ef comida")
        assertEquals(Forma.EFECTIVO, l.forma); assertEquals("comida", l.descripcion)
        l = EntradaRapida.leer("20$>bs 1048 cambio")
        assertEquals(Tipo.TRANSFERENCIA, l.tipo); assertEquals(Moneda.USD, l.moneda); assertEquals(Moneda.BS, l.monedaDestino)
        assertEquals(1048.0, l.tasa!!, 0.001); assertEquals("cambio", l.descripcion)
        assertEquals(2096000L, EntradaRapida.montoDestino(2000, Moneda.USD, Moneda.BS, 1048.0))
        l = EntradaRapida.leer("500 bs dig > ef")
        assertEquals(Forma.DIGITAL, l.forma); assertEquals(Forma.EFECTIVO, l.formaDestino)
        l = EntradaRapida.leer("-85 usdt perdida trading")
        assertEquals(Moneda.USDT, l.moneda); assertEquals("perdida trading", l.descripcion)
    }

    @Test fun fechas() {
        assertEquals("2026-10-01", ImportarExcel.convertirFecha("46296"))
        assertEquals("2026-10-14", ImportarExcel.convertirFecha("14/10/2026"))
        assertEquals("2026-10-13", ImportarExcel.convertirFecha("13/102026"))
        assertEquals("2026-11-09", CarpetaSync.normalizarFecha("9/11/26"))
        assertEquals("2026-10-11", ImportarExcel.corregir("2026-11-10", 10))
        assertEquals("2026-10-01", ImportarExcel.corregir("2026-06-06", 10))
    }

    @Test fun tasas() {
        val t = TablaTasas(listOf(
            Tasa("2026-11-01", "BCV", "auto", 880.0), Tasa("2026-11-05", "BCV", "auto", 890.0),
            Tasa("2026-11-05", "BCV", "manual", 891.0), Tasa("2026-11-05", "BINANCE", "auto", 1040.0)))
        assertEquals(891.0, t.en("BCV", "2026-11-05")!!.valor, 0.0)          // manual gana
        val est = t.en("BCV", "2026-11-09")!!
        assertTrue(est.estimada); assertEquals(891.0, est.valor, 0.0)          // usa la última conocida
        assertEquals(10.0, t.convertir(891000, Moneda.BS, "2026-11-05", Vista.USD_BCV)!!.valor, 0.001)
        assertEquals(15.0, t.convertir(1500, Moneda.USD, "2026-11-05", Vista.USD_BINANCE)!!.valor, 0.001)
    }
}
