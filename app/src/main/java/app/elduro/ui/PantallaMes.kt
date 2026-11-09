package app.elduro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.elduro.core.Dinero
import app.elduro.core.Fechas
import app.elduro.core.Finanzas
import app.elduro.core.Fuente
import app.elduro.core.TablaTasas
import app.elduro.core.Vista
import app.elduro.core.etiquetaBrecha
import app.elduro.data.Cartera
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo

@Composable
fun PantallaMes(
    e: EstadoApp, carteras: List<Cartera>, movs: List<Movimiento>, tabla: TablaTasas,
    modifier: Modifier, onEditar: (Movimiento) -> Unit,
) {
    val porId = remember(carteras) { carteras.associateBy { it.id } }
    val delLibro = remember(movs, porId, e.libro) { Finanzas.delLibro(movs, porId, e.libro) }
    val res = remember(delLibro, porId, e.libro, e.mes, tabla, e.vista) { Finanzas.resumen(delLibro, porId, e.libro, e.mes, tabla, e.vista) }
    val ym = e.mes.toString()
    val delMes = remember(delLibro, ym) { delLibro.filter { it.fecha.startsWith(ym) } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!e.acceso) item { AvisoPermiso(e) }
        item { TarjetaTasas(e, tabla) }
        item { SelectorMes(e) }
        item { TarjetaResumen(res, e.vista) }
        item { TarjetaCarteras(res, e.vista) }
        if (res.porCategoria.isNotEmpty()) item { TarjetaCategorias(res, e.vista) }
        item {
            Text("Movimientos (${delMes.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (delMes.isEmpty()) Text("Toca + para anotar. Ej.: «-8000 pollo», «+15$ venta», «20$ > bs 1048».",
                color = Colores.neutro(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        val dias = delMes.groupBy { it.fecha }
        dias.forEach { (fecha, lista) ->
            item(key = "d$fecha") {
                Text(Fechas.corta(fecha).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
            items(lista, key = { it.uid }) { m -> FilaMovimiento(m, porId, tabla, e.vista, e.libro) { onEditar(m) } }
        }
    }
}

@Composable
private fun AvisoPermiso(e: EstadoApp) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Falta el acceso a archivos", fontWeight = FontWeight.Bold)
            Text("Sin él, ElDuro funciona pero no puede guardar los CSV en NodeSync ni sincronizarse con el PC.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { e.pedirPermiso() }, modifier = Modifier.padding(top = 8.dp)) { Text("Conceder acceso") }
        }
    }
}

@Composable
fun TarjetaTasas(e: EstadoApp, tabla: TablaTasas) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hoy = hoy()
    val bcv = tabla.en(Fuente.BCV, hoy); val bin = tabla.en(Fuente.BINANCE, hoy)
    val brecha = tabla.brecha(hoy)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ValorTasa("BCV", bcv?.valor, bcv?.estimada == true)
                    ValorTasa("Binance", bin?.valor, bin?.estimada == true)
                    if (brecha != null) Column {
                        Text("Brecha", style = MaterialTheme.typography.labelSmall)
                        Text("${Dinero.num(brecha)}%", fontWeight = FontWeight.Bold,
                            color = if (brecha >= 15) Colores.aviso() else MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                val etiqueta = brecha?.let { etiquetaBrecha(it) }?.takeIf { it != "Normal" }
                Text(listOfNotNull(etiqueta?.let { "🔥 $it" }, e.estadoTasas.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            if (e.consultando) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { e.refrescarTasas(scope, forzar = true) }) { Icon(Icons.Filled.Refresh, "Actualizar tasas") }
        }
    }
}

@Composable
private fun ValorTasa(nombre: String, v: Double?, estimada: Boolean) {
    Column {
        Text(nombre + if (estimada) " ·est." else "", style = MaterialTheme.typography.labelSmall)
        Text(v?.let { Dinero.tasa(it) } ?: "—", fontWeight = FontWeight.Bold, color = if (estimada) Colores.aviso() else Color.Unspecified)
    }
}

@Composable
private fun SelectorMes(e: EstadoApp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { e.mes = e.mes.minusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Mes anterior") }
        Text(Fechas.mes(e.mes.toString()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { e.mes = e.mes.plusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "Mes siguiente") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), ) {
        Vista.entries.forEach { v -> FilterChip(selected = e.vista == v, onClick = { e.cambiarVista(v) }, label = { Text(v.etiqueta) }) }
    }
}

@Composable
private fun TarjetaResumen(r: Finanzas.Resumen, v: Vista) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Bloque("Entradas", Dinero.fmtVista(r.entradas, v), Colores.entrada(), Modifier.weight(1f))
                Bloque("Gastos", Dinero.fmtVista(-r.gastos, v), Colores.gasto(), Modifier.weight(1f))
            }
            HorizontalDivider()
            val res = r.resultado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resultado del mes", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(Dinero.fmtVista(res, v, signo = true), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (res >= 0) Colores.entrada() else Colores.gasto())
            }
            if (res < 0 && r.gastos > 0) Text("⚠ Gastaste más de lo que entró", color = Colores.gasto(), style = MaterialTheme.typography.bodySmall)
            if (kotlin.math.abs(r.ajustes) > 0.004) Linea("Descuadres (arqueos)", Dinero.fmtVista(r.ajustes, v, true))
            if (kotlin.math.abs(r.cambiaria) > 0.004) Linea("Ganancia/pérdida en cambios", Dinero.fmtVista(r.cambiaria, v, true))
            if (r.estimadas > 0) Text("🟡 ${r.estimadas} conversiones con tasa estimada (se corrigen solas al llegar la real)",
                style = MaterialTheme.typography.bodySmall, color = Colores.aviso())
            if (r.sinTasa > 0) Text("⚠ ${r.sinTasa} movimientos sin tasa para convertir: actualiza o anota una tasa",
                style = MaterialTheme.typography.bodySmall, color = Colores.gasto())
        }
    }
}

@Composable
private fun Bloque(t: String, v: String, c: Color, m: Modifier) {
    Column(m) {
        Text(t, style = MaterialTheme.typography.labelMedium, color = Colores.neutro())
        Text(v, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c)
    }
}

@Composable
private fun Linea(t: String, v: String) {
    Row { Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(v, style = MaterialTheme.typography.bodyMedium) }
}

/** Tabla moneda × forma con los saldos al cierre del mes. */
@Composable
private fun TarjetaCarteras(r: Finanzas.Resumen, v: Vista) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Saldos al cierre", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row { Celda("", true, primera = true); Celda("Efectivo", true); Celda("Digital", true) }
            val monedas = r.celdas.map { it.moneda }.distinct()
            for (m in monedas) {
                Row {
                    Celda(m.simbolo, true, primera = true)
                    for (f in Forma.entries) {
                        val c = r.celdas.firstOrNull { it.moneda == m && it.forma == f }
                        Celda(c?.let { Dinero.fmt(it.centimos, m).removeSuffix(" ${m.simbolo}") } ?: "—", false,
                            color = if ((c?.centimos ?: 0) < 0) Colores.gasto() else Color.Unspecified)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row {
                Text("Total en ${v.etiqueta}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(Dinero.fmtVista(r.patrimonio, v), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Celda(t: String, cabecera: Boolean, color: Color = Color.Unspecified, primera: Boolean = false) {
    Text(t, Modifier.weight(if (primera) 0.6f else 1f).padding(vertical = 2.dp),
        style = if (cabecera) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (cabecera) Colores.neutro() else color, textAlign = if (primera) TextAlign.Start else TextAlign.End)
}

@Composable
private fun TarjetaCategorias(r: Finanzas.Resumen, v: Vista) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gastos por categoría", style = MaterialTheme.typography.titleSmall)
            val max = r.porCategoria.maxOf { it.second }
            for ((cat, valor) in r.porCategoria.take(8)) {
                Column {
                    Row {
                        Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${Dinero.fmtVista(valor, v)} · ${(valor / r.gastos * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = { (valor / max).toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
fun FilaMovimiento(m: Movimiento, porId: Map<Long, Cartera>, tabla: TablaTasas, vista: Vista, libro: String, onClick: () -> Unit) {
    val c = porId[m.carteraId]; val d = m.carteraDestinoId?.let { porId[it] }
    // en una transferencia que entra desde otro libro, mostramos el lado que llega
    val entraDeFuera = m.tipo == Tipo.TRANSFERENCIA && c?.libro != libro
    val (monto, moneda) = if (entraDeFuera) (m.montoDestino ?: 0L) to (d?.moneda ?: Moneda.BS) else m.monto to (c?.moneda ?: Moneda.BS)
    val color = when (m.tipo) {
        Tipo.ENTRADA -> Colores.entrada(); Tipo.GASTO -> Colores.gasto()
        Tipo.AJUSTE -> Colores.aviso(); else -> MaterialTheme.colorScheme.onSurface
    }
    val icono = when (m.tipo) { Tipo.ENTRADA -> "↓"; Tipo.GASTO -> "↑"; Tipo.TRANSFERENCIA -> "⇄"; Tipo.AJUSTE -> "≠"; Tipo.APERTURA -> "◎" }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(icono, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.descripcion.ifBlank { m.tipo.etiqueta }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val sub = when (m.tipo) {
                Tipo.TRANSFERENCIA -> "${c?.nombre ?: "?"} → ${if (d != null && d.libro != c?.libro) "${d.libro}/" else ""}${d?.nombre ?: "?"}" +
                        (m.tasa?.let { " · tasa ${Dinero.tasa(it)}" } ?: "")
                else -> listOf(m.categoria, c?.nombre.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Colores.neutro(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Dinero.fmt(monto, moneda, signo = true), color = color, fontWeight = FontWeight.SemiBold)
            if (m.tipo == Tipo.TRANSFERENCIA && d != null && m.montoDestino != null && d.moneda != moneda)
                Text("→ " + Dinero.fmt(m.montoDestino, d.moneda), style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            else {
                val conv = tabla.convertir(monto, moneda, m.fecha, vista)
                if (conv?.tasa != null) Text("≈ " + Dinero.fmtVista(conv.valor, vista) + if (conv.estimada) " ·est." else "",
                    style = MaterialTheme.typography.bodySmall, color = if (conv.estimada) Colores.aviso() else Colores.neutro())
            }
        }
    }
}
