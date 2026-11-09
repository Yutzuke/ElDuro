package app.elduro.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.elduro.core.Dinero
import app.elduro.core.Duro
import app.elduro.core.Finanzas
import app.elduro.core.TablaTasas
import app.elduro.data.Cartera
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaCarteras(e: EstadoApp, carteras: List<Cartera>, movs: List<Movimiento>, tabla: TablaTasas, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val saldos = remember(movs) { Finanzas.saldos(movs) }
    var verArchivadas by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf<Cartera?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var arqueo by remember { mutableStateOf<Cartera?>(null) }
    val lista = carteras.filter { it.libro == e.libro && (verArchivadas || !it.archivada) }
    val total = lista.filter { !it.archivada }.sumOf { c -> tabla.convertir(saldos[c.id] ?: 0, c.moneda, hoy(), e.vista)?.valor ?: 0.0 }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total ${e.libro}", style = MaterialTheme.typography.labelLarge, color = Colores.neutro())
                    Text(Dinero.fmtVista(total, e.vista), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { nueva = true }) { Text("Nueva cartera") }
            }
        }
        items(lista, key = { it.id }) { c ->
            val s = saldos[c.id] ?: 0L
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.nombre + if (c.archivada) " (archivada)" else "", fontWeight = FontWeight.SemiBold)
                            Text("${c.moneda.etiqueta} · ${c.forma.etiqueta}", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Dinero.fmt(s, c.moneda), fontWeight = FontWeight.Bold, color = if (s < 0) Colores.gasto() else MaterialTheme.colorScheme.onSurface)
                            tabla.convertir(s, c.moneda, hoy(), e.vista)?.takeIf { it.tasa != null }?.let {
                                Text("≈ " + Dinero.fmtVista(it.valor, e.vista), style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { arqueo = c }) { Text(if (c.forma == Forma.EFECTIVO) "Arqueo" else "Conciliar") }
                        TextButton(onClick = { editar = c }) { Text("Editar") }
                    }
                }
            }
        }
        item {
            FilterChip(selected = verArchivadas, onClick = { verArchivadas = !verArchivadas }, label = { Text("Ver archivadas") })
            Text("Arqueo: cuentas el efectivo y anotas lo que hay. Conciliar: copias el saldo que muestra el banco o Binance. " +
                    "La diferencia queda registrada como descuadre.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro(),
                modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (nueva || editar != null) DialogoCartera(e, editar, onCerrar = { nueva = false; editar = null })
    arqueo?.let { c ->
        DialogoArqueo(c, saldos[c.id] ?: 0L, onCerrar = { arqueo = null }) { real ->
            e.io(scope) {
                val dif = Duro.arqueo(e.db, c, saldos[c.id] ?: 0L, real, hoy())
                withContext(Dispatchers.Main) {
                    e.avisos.showSnackbar(if (dif == 0L) "Cuadra perfecto ✔" else "Descuadre registrado: ${Dinero.fmt(dif, c.moneda, true)}")
                }
            }
            arqueo = null
        }
    }
}

@Composable
private fun DialogoArqueo(c: Cartera, saldo: Long, onCerrar: () -> Unit, onConfirmar: (Long) -> Unit) {
    var real by remember { mutableStateOf("") }
    val r = Dinero.parse(real)
    AlertDialog(onDismissRequest = onCerrar,
        title = { Text((if (c.forma == Forma.EFECTIVO) "Arqueo de " else "Conciliar ") + c.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Según ElDuro: ${Dinero.fmt(saldo, c.moneda)}")
                OutlinedTextField(real, { real = it }, label = { Text(if (c.forma == Forma.EFECTIVO) "Lo que contaste" else "Saldo real en el banco/app") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (r != null) {
                    val d = r - saldo
                    Text(if (d == 0L) "✔ Cuadra" else "Diferencia: ${Dinero.fmt(d, c.moneda, true)} (${if (d > 0) "sobra" else "falta"})",
                        color = if (d == 0L) Colores.entrada() else Colores.aviso(), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = { r?.let(onConfirmar) }, enabled = r != null) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}

@Composable
private fun DialogoCartera(e: EstadoApp, c: Cartera?, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(c?.nombre ?: "") }
    var moneda by remember { mutableStateOf(c?.moneda ?: Moneda.BS) }
    var forma by remember { mutableStateOf(c?.forma ?: Forma.DIGITAL) }
    var apertura by remember { mutableStateOf("") }
    var archivada by remember { mutableStateOf(c?.archivada ?: false) }
    AlertDialog(onDismissRequest = onCerrar,
        title = { Text(if (c == null) "Nueva cartera · ${e.libro}" else "Editar cartera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre (ej. Caja fuerte, Banesco)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Moneda.entries.forEach { m -> FilterChip(moneda == m, { if (c == null) moneda = m }, { Text(m.simbolo) }, enabled = c == null || moneda == m) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Forma.entries.forEach { f -> FilterChip(forma == f, { forma = f }, { Text(f.etiqueta) }) }
                }
                if (c == null) OutlinedTextField(apertura, { apertura = it }, label = { Text("Saldo actual (apertura)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(archivada, { archivada = it }); Text("Archivada (oculta, conserva su historial)")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank(), onClick = {
                val nueva = (c ?: Cartera(libro = e.libro, nombre = "", moneda = moneda, forma = forma, orden = 50))
                    .copy(nombre = nombre.trim(), moneda = moneda, forma = forma, archivada = archivada)
                e.io(scope) { Duro.crearCartera(e.db, nueva, Dinero.parse(apertura) ?: 0L) }
                onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}
