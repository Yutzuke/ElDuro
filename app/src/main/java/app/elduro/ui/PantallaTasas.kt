package app.elduro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.elduro.core.CarpetaSync
import app.elduro.core.ConsultaTasas
import app.elduro.core.Dinero
import app.elduro.core.Fechas
import app.elduro.core.Fuente
import app.elduro.core.TablaTasas
import app.elduro.data.Movimiento
import app.elduro.data.Tasa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaTasas(e: EstadoApp, tabla: TablaTasas, tasas: List<Tasa>, movs: List<Movimiento>, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var dialogo by remember { mutableStateOf<String?>(null) }   // fecha a editar
    val fechas = remember(tasas) { tasas.map { it.fecha }.distinct().sortedDescending().take(60) }
    // mejor tasa por (fecha, fuente) con su origen
    val mejor = remember(tasas) {
        val prio = mapOf("manual" to 3, "auto" to 2, "paralelo" to 1)
        tasas.groupBy { it.fecha to it.fuente }.mapValues { (_, l) -> l.maxBy { prio[it.origen] ?: 0 } }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TarjetaTasas(e, tabla) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dialogo = hoy() }) { Text("Anotar tasa") }
                OutlinedButton(onClick = {
                    e.io(scope) {
                        val desde = movs.minOfOrNull { it.fecha } ?: hoy()
                        val n = ConsultaTasas.historico(e.db, desde.take(8) + "01")
                        withContext(Dispatchers.Main) { e.avisos.showSnackbar("Histórico: $n tasas desde ${Fechas.dmy(desde)}") }
                    }
                }) { Text("Rellenar histórico") }
            }
        }
        item {
            Text("La app nunca depende de internet: si no hay tasa del día usa la última conocida (🟡 estimada) y corrige " +
                    "los cálculos sola cuando llega la real. Una tasa anotada a mano (✏️) siempre tiene prioridad.",
                style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
        }
        item {
            Row(Modifier.padding(top = 8.dp)) {
                Text("Fecha", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("BCV", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text("Binance", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text("Brecha", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            HorizontalDivider()
        }
        items(fechas, key = { it }) { f ->
            val b = mejor[f to Fuente.BCV]; val n = mejor[f to Fuente.BINANCE]
            Row(Modifier.fillMaxWidth().clickable { dialogo = f }.padding(vertical = 6.dp)) {
                Text(Fechas.dmy(f), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(b?.let { marca(it) + Dinero.tasa(it.valor) } ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                Text(n?.let { marca(it) + Dinero.tasa(it.valor) } ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                val br = if (b != null && n != null) (n.valor / b.valor - 1) * 100 else null
                Text(br?.let { "${Dinero.num(it)}%" } ?: "", Modifier.weight(0.7f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
                    color = if ((br ?: 0.0) >= 15) Colores.aviso() else Colores.neutro())
            }
        }
        item { Text("✏️ manual · 🟡 paralelo (aprox. de Binance en fechas pasadas) · sin marca: consultada", style = MaterialTheme.typography.labelSmall, color = Colores.neutro()) }
    }

    dialogo?.let { f -> DialogoTasa(e, f, mejor) { dialogo = null } }
}

private fun marca(t: Tasa) = when (t.origen) { "manual" -> "✏️"; "paralelo" -> "🟡"; else -> "" }

@Composable
private fun DialogoTasa(e: EstadoApp, fecha: String, mejor: Map<Pair<String, String>, Tasa>, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fuente by remember { mutableStateOf(Fuente.BINANCE) }
    var f by remember { mutableStateOf(Fechas.dmy(fecha)) }
    var valor by remember { mutableStateOf("") }
    val iso = CarpetaSync.normalizarFecha(f)
    val actual = iso?.let { mejor[it to fuente] }
    AlertDialog(onDismissRequest = onCerrar, title = { Text("Tasa manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(fuente == Fuente.BCV, { fuente = Fuente.BCV }, { Text("BCV") })
                    FilterChip(fuente == Fuente.BINANCE, { fuente = Fuente.BINANCE }, { Text("Binance") })
                }
                OutlinedTextField(f, { f = it }, label = { Text("Fecha (dd/mm/aaaa)") }, singleLine = true, isError = iso == null)
                OutlinedTextField(valor, { valor = it }, label = { Text("Bs por $") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { actual?.let { Text("Ahora: ${Dinero.tasa(it.valor)} (${it.origen})") } })
                if (actual?.origen == "manual") TextButton(onClick = {
                    e.io(scope) { e.db.tasas().delete(actual.fecha, actual.fuente, "manual") }; onCerrar()
                }) { Text("Quitar la manual y volver a la automática", color = Colores.gasto()) }
            }
        },
        confirmButton = {
            val v = Dinero.parseDouble(valor)
            TextButton(enabled = iso != null && v != null && v > 0, onClick = {
                e.io(scope) { e.db.tasas().upsert(Tasa(iso!!, fuente, "manual", v!!)) }; onCerrar()
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}
