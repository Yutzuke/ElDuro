package app.elduro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.elduro.core.CarpetaSync
import app.elduro.core.Categorias
import app.elduro.core.Control
import app.elduro.core.Dinero
import app.elduro.core.Duro
import app.elduro.core.EstadoDeuda
import app.elduro.core.Fechas
import app.elduro.core.Finanzas
import app.elduro.core.Fuente
import app.elduro.core.TablaTasas
import app.elduro.data.Cartera
import app.elduro.data.Deuda
import app.elduro.data.Faltante
import app.elduro.data.Frecuencia
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Presupuesto
import app.elduro.data.Recurrente
import app.elduro.data.Sentido
import app.elduro.data.Tipo
import java.time.YearMonth

/** Datos que comparten las secciones de Control. */
class DatosControl(
    val carteras: List<Cartera>, val movs: List<Movimiento>, val tabla: TablaTasas,
    val deudas: List<Deuda>, val recurrentes: List<Recurrente>, val presupuestos: List<Presupuesto>, val faltantes: List<Faltante>,
)

private enum class Seccion(val t: String) { DEUDAS("Deudas"), FALTANTES("Faltantes"), RECURRENTES("Recurrentes"), PRESUPUESTOS("Presup.") }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantallaControl(e: EstadoApp, d: DatosControl, modifier: Modifier, onComprar: (Faltante) -> Unit) {
    var sec by rememberSaveable { mutableStateOf(Seccion.DEUDAS) }
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Seccion.entries.forEachIndexed { i, s ->
                SegmentedButton(sec == s, { sec = s }, SegmentedButtonDefaults.itemShape(i, Seccion.entries.size)) { Text(s.t, maxLines = 1) }
            }
        }
        when (sec) {
            Seccion.DEUDAS -> SeccionDeudas(e, d)
            Seccion.FALTANTES -> SeccionFaltantes(e, d, onComprar)
            Seccion.RECURRENTES -> SeccionRecurrentes(e, d)
            Seccion.PRESUPUESTOS -> SeccionPresupuestos(e, d)
        }
    }
}

// ============================ DEUDAS ============================

@Composable
private fun SeccionDeudas(e: EstadoApp, d: DatosControl) {
    var editar by remember { mutableStateOf<Deuda?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var abonar by remember { mutableStateOf<EstadoDeuda?>(null) }
    var verCerradas by remember { mutableStateOf(false) }
    val estados = remember(d.deudas, d.movs, e.libro) { Control.estados(d.deudas.filter { it.libro == e.libro }, d.movs) }
    val visibles = estados.filter { verCerradas || !it.deuda.cerrada }
    fun total(s: Sentido) = estados.filter { it.deuda.sentido == s && !it.deuda.cerrada }
        .sumOf { d.tabla.convertir(it.saldo, it.deuda.moneda, hoy(), e.vista)?.valor ?: 0.0 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Debo", style = MaterialTheme.typography.labelMedium, color = Colores.neutro())
                    Text(Dinero.fmtVista(total(Sentido.DEBO), e.vista), color = Colores.gasto(), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Me deben", style = MaterialTheme.typography.labelMedium, color = Colores.neutro())
                    Text(Dinero.fmtVista(total(Sentido.ME_DEBEN), e.vista), color = Colores.entrada(), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { nueva = true }) { Text("Nueva") }
            }
        }
        if (visibles.isEmpty()) item { Text("Sin deudas. Anota tarjetas, compras a cuotas o lo que te deben.", color = Colores.neutro()) }
        items(visibles, key = { it.deuda.uid }) { s -> TarjetaDeuda(s, e, d.tabla, onAbonar = { abonar = s }, onEditar = { editar = s.deuda }) }
        item { FilterChip(verCerradas, { verCerradas = !verCerradas }, { Text("Ver saldadas") }) }
    }
    if (nueva || editar != null) DialogoDeuda(e, editar, d.carteras.filter { it.libro == e.libro && !it.archivada }) { nueva = false; editar = null }
    abonar?.let { s -> DialogoAbono(e, s, d.carteras.filter { !it.archivada && it.libro == e.libro }, d.tabla) { abonar = null } }
}

@Composable
private fun TarjetaDeuda(s: EstadoDeuda, e: EstadoApp, tabla: TablaTasas, onAbonar: () -> Unit, onEditar: () -> Unit) {
    val dd = s.deuda
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dd.nombre, fontWeight = FontWeight.SemiBold)
                    Text(dd.sentido.etiqueta + (if (dd.esGasto) " · compra a crédito" else "") + " · desde ${Fechas.dmy(dd.fecha)}",
                        style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Dinero.fmt(s.saldo, dd.moneda), fontWeight = FontWeight.Bold, color = if (dd.sentido == Sentido.DEBO) Colores.gasto() else Colores.entrada())
                    tabla.convertir(s.saldo, dd.moneda, hoy(), e.vista)?.takeIf { it.tasa != null }?.let {
                        Text("≈ " + Dinero.fmtVista(it.valor, e.vista), style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                    }
                }
            }
            LinearProgressIndicator(progress = { if (dd.total > 0) (s.pagado.toFloat() / dd.total).coerceIn(0f, 1f) else 0f },
                Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text("Pagado ${Dinero.fmt(s.pagado, dd.moneda)} de ${Dinero.fmt(dd.total, dd.moneda)}", style = MaterialTheme.typography.bodySmall)
            if (dd.cuotas > 0) {
                val dias = s.diasParaVencer()
                Text("Cuotas ${s.cuotasPagadas}/${dd.cuotas} de ${Dinero.fmt(s.cuota ?: 0, dd.moneda)}" +
                        (s.proximoVence?.let { " · próxima ${Fechas.dmy(it)}" + when { dias == null -> ""; dias < 0 -> " (vencida hace ${-dias} d)"; dias == 0L -> " (HOY)"; else -> " (en $dias d)" } } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = if ((dias ?: 99) <= 2) Colores.aviso() else MaterialTheme.colorScheme.onSurface)
            }
            if (dd.notas.isNotBlank()) Text(dd.notas, style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.saldada) OutlinedButton(onClick = onAbonar) { Text(if (dd.sentido == Sentido.DEBO) "Pagar" else "Cobrar") }
                TextButton(onClick = onEditar) { Text("Editar") }
            }
        }
    }
}

@Composable
private fun DialogoDeuda(e: EstadoApp, d: Deuda?, carteras: List<Cartera>, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sentido by remember { mutableStateOf(d?.sentido ?: Sentido.DEBO) }
    var nombre by remember { mutableStateOf(d?.nombre ?: "") }
    var moneda by remember { mutableStateOf(d?.moneda ?: Moneda.BS) }
    var total by remember { mutableStateOf(d?.let { Dinero.csv(it.total) } ?: "") }
    var fecha by remember { mutableStateOf(Fechas.dmy(d?.fecha ?: hoy())) }
    var cuotas by remember { mutableStateOf(d?.cuotas?.takeIf { it > 0 }?.toString() ?: "") }
    var frec by remember { mutableStateOf(d?.frecuenciaDias ?: 14) }
    var primer by remember { mutableStateOf(d?.primerVence?.let { Fechas.dmy(it) } ?: "") }
    var esGasto by remember { mutableStateOf(d?.esGasto ?: false) }
    var categoria by remember { mutableStateOf(d?.categoria ?: "") }
    var notas by remember { mutableStateOf(d?.notas ?: "") }
    var cerrada by remember { mutableStateOf(d?.cerrada ?: false) }
    var salida by remember { mutableStateOf<Cartera?>(null) }
    var registrarSalida by remember { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf(false) }
    val fIso = CarpetaSync.normalizarFecha(fecha); val pIso = if (primer.isBlank()) null else CarpetaSync.normalizarFecha(primer)
    val t = Dinero.parse(total)?.let { kotlin.math.abs(it) }
    val ok = nombre.isNotBlank() && t != null && t > 0 && fIso != null && (primer.isBlank() || pIso != null)

    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (d == null) "Nueva deuda · ${e.libro}" else "Editar deuda") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Sentido.entries.forEach { s -> FilterChip(sentido == s, { sentido = s }, { Text(s.etiqueta) }) } }
                OutlinedTextField(nombre, { nombre = it }, label = { Text(if (sentido == Sentido.DEBO) "A quién / qué (ej. Tarjeta BDV)" else "Quién te debe") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Moneda.entries.forEach { m -> FilterChip(moneda == m, { moneda = m }, { Text(m.simbolo) }) } }
                OutlinedTextField(total, { total = it }, label = { Text("Monto total (${moneda.simbolo})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (dd/mm/aaaa)") }, singleLine = true, isError = fIso == null)
                Text("Plan de cuotas (opcional)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(cuotas, { cuotas = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Nº cuotas") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(primer, { primer = it }, Modifier.weight(1.4f), label = { Text("1ª vence") }, singleLine = true, isError = primer.isNotBlank() && pIso == null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "Semanal", 14 to "14 días", 0 to "Mensual").forEach { (dias, t2) -> FilterChip(frec == dias, { frec = dias }, { Text(t2) }) }
                }
                if (sentido == Sentido.DEBO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(esGasto, { esGasto = it }); Text("Compra a crédito: contar como gasto de ese mes", style = MaterialTheme.typography.bodySmall)
                    }
                    if (esGasto) SelectorTexto("Categoría del gasto", categoria, Categorias.GASTO) { categoria = it }
                } else if (d == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(registrarSalida, { registrarSalida = it }); Text("Presté ese dinero ahora (sale de una cartera)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (registrarSalida) SelectorCartera("Sale de", salida, carteras.filter { it.moneda == moneda }) { salida = it }
                }
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas") })
                if (d != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cerrada, { cerrada = it }); Text("Saldada / cerrada") }
                if (d != null) TextButton(onClick = { confirmarBorrar = true }) { Text("Borrar deuda", color = Colores.gasto()) }
            }
        },
        confirmButton = {
            TextButton(enabled = ok && (!registrarSalida || salida != null), onClick = {
                val nueva = Deuda(
                    id = d?.id ?: 0, uid = d?.uid ?: Duro.uid(), libro = d?.libro ?: e.libro, sentido = sentido, nombre = nombre.trim(),
                    moneda = moneda, total = t!!, fecha = fIso!!, cuotas = cuotas.toIntOrNull() ?: 0, frecuenciaDias = frec,
                    primerVence = pIso, esGasto = sentido == Sentido.DEBO && esGasto, categoria = categoria.trim(), notas = notas.trim(), cerrada = cerrada,
                )
                e.io(scope) { Control.crearDeuda(e.db, nueva, if (registrarSalida) salida else null) }
                onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })

    if (confirmarBorrar && d != null) AlertDialog(onDismissRequest = { confirmarBorrar = false },
        text = { Text("¿Borrar «${d.nombre}»? Los pagos ya registrados se conservan como transferencias sueltas.") },
        confirmButton = { TextButton(onClick = { e.io(scope) { Control.borrarDeuda(e.db, d) }; confirmarBorrar = false; onCerrar() }) { Text("Borrar", color = Colores.gasto()) } },
        dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } })
}

@Composable
fun DialogoAbono(e: EstadoApp, s: EstadoDeuda, carteras: List<Cartera>, tabla: TablaTasas, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    val d = s.deuda
    var cartera by remember { mutableStateOf(carteras.firstOrNull { it.moneda == d.moneda && it.forma == app.elduro.data.Forma.DIGITAL } ?: carteras.firstOrNull { it.moneda == d.moneda } ?: carteras.firstOrNull()) }
    val sugerido = minOf(s.cuota ?: s.saldo, s.saldo)
    var monto by remember { mutableStateOf("") }
    var tasa by remember { mutableStateOf(tabla.en(Fuente.BINANCE, hoy())?.valor?.let { Dinero.tasa(it) } ?: "") }
    var fecha by remember { mutableStateOf(Fechas.dmy(hoy())) }
    val c = cartera
    val distinta = c != null && (c.moneda == Moneda.BS) != (d.moneda == Moneda.BS)
    // monto por defecto: lo que hay que pagar expresado en la moneda de la cartera
    val tasaD = Dinero.parseDouble(tasa)
    val porDefecto = if (c == null) null else Control.aplicado(sugerido, d.moneda, c.moneda, tasaD)
    val m = Dinero.parse(monto)?.let { kotlin.math.abs(it) } ?: porDefecto
    val aplicado = if (c != null && m != null) Control.aplicado(m, c.moneda, d.moneda, tasaD) else null
    val fIso = CarpetaSync.normalizarFecha(fecha)
    AlertDialog(onDismissRequest = onCerrar, title = { Text((if (d.sentido == Sentido.DEBO) "Pagar · " else "Cobrar · ") + d.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saldo: ${Dinero.fmt(s.saldo, d.moneda)}" + (s.cuota?.let { " · cuota ${Dinero.fmt(it, d.moneda)}" } ?: ""))
                SelectorCartera(if (d.sentido == Sentido.DEBO) "Pagar desde" else "Entra en", c, carteras) { cartera = it }
                OutlinedTextField(monto, { monto = it }, label = { Text("Monto (${c?.moneda?.simbolo ?: ""})") }, placeholder = { porDefecto?.let { Text(Dinero.csv(it)) } },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (distinta) OutlinedTextField(tasa, { tasa = it }, label = { Text("Tasa (Bs por $)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha") }, singleLine = true, isError = fIso == null)
                aplicado?.let { Text("Se descuentan ${Dinero.fmt(it, d.moneda)} de la deuda", style = MaterialTheme.typography.bodySmall, color = Colores.neutro()) }
                Text("No cuenta como gasto: es una transferencia hacia la deuda.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            }
        },
        confirmButton = {
            TextButton(enabled = c != null && m != null && m > 0 && aplicado != null && fIso != null, onClick = {
                e.io(scope) { Control.abonar(e.db, s, c!!, m!!, aplicado!!, tasaD, fIso!!) }; onCerrar()
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}

// ============================ FALTANTES ============================

@Composable
private fun SeccionFaltantes(e: EstadoApp, d: DatosControl, onComprar: (Faltante) -> Unit) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    val lista = d.faltantes.filter { it.libro == e.libro }
    fun agregar() {
        val t = texto.trim(); if (t.isEmpty()) return
        e.io(scope) { e.db.faltantes().upsert(Faltante(uid = Duro.uid(), libro = e.libro, texto = t)) }; texto = ""
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(texto, { texto = it }, Modifier.weight(1f), label = { Text("Falta comprar…") }, singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { agregar() }),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done))
                IconButton(onClick = { agregar() }) { Icon(Icons.Filled.Add, "Añadir") }
            }
            Text("Al marcarlo como comprado se abre el gasto ya rellenado.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
        }
        items(lista, key = { it.uid }) { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f.comprado, { v ->
                    e.io(scope) { e.db.faltantes().upsert(f.copy(comprado = v)) }
                    if (v) onComprar(f)
                })
                Text(f.texto, Modifier.weight(1f), textDecoration = if (f.comprado) TextDecoration.LineThrough else null,
                    color = if (f.comprado) Colores.neutro() else MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { e.io(scope) { e.db.faltantes().delete(f.id) } }) { Icon(Icons.Filled.Delete, "Quitar", tint = Colores.neutro()) }
            }
        }
        if (lista.any { it.comprado }) item {
            TextButton(onClick = { e.io(scope) { lista.filter { it.comprado }.forEach { e.db.faltantes().delete(it.id) } } }) { Text("Quitar los comprados") }
        }
        if (lista.isEmpty()) item { Text("Lista vacía en ${e.libro}.", color = Colores.neutro()) }
    }
}

// ============================ RECURRENTES ============================

@Composable
private fun SeccionRecurrentes(e: EstadoApp, d: DatosControl) {
    val scope = rememberCoroutineScope()
    var editar by remember { mutableStateOf<Recurrente?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    val porId = d.carteras.associateBy { it.id }
    val lista = d.recurrentes.filter { it.libro == e.libro }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Servicios, alquiler, sueldo… Los automáticos se registran solos; los demás te los propone el día que tocan.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                OutlinedButton(onClick = { nuevo = true }) { Text("Nuevo") }
            }
        }
        items(lista, key = { it.uid }) { r ->
            val c = porId[r.carteraId]
            val vence = r.proxima <= hoy()
            Card(Modifier.fillMaxWidth(), colors = if (vence && r.activa) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(r.descripcion, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(Dinero.fmt(if (r.tipo == Tipo.GASTO) -r.monto else r.monto, c?.moneda ?: Moneda.BS, true),
                            color = if (r.tipo == Tipo.GASTO) Colores.gasto() else Colores.entrada(), fontWeight = FontWeight.Bold)
                    }
                    Text("${r.frecuencia.etiqueta} · ${c?.nombre ?: "?"} · ${r.categoria}" + (if (r.automatico) " · automático" else "") + (if (!r.activa) " · pausado" else ""),
                        style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                    Text("Próxima: ${Fechas.dmy(r.proxima)}" + if (vence) " — toca registrar" else "", style = MaterialTheme.typography.bodySmall,
                        color = if (vence) Colores.aviso() else MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { e.io(scope) { Control.registrar(e.db, r, r.proxima) } }) { Text("Registrar") }
                        TextButton(onClick = { e.io(scope) { Control.saltar(e.db, r) } }) { Text("Saltar") }
                        TextButton(onClick = { editar = r }) { Text("Editar") }
                    }
                }
            }
        }
        if (lista.isEmpty()) item { Text("Sin recurrentes en ${e.libro}.", color = Colores.neutro()) }
    }
    if (nuevo || editar != null) DialogoRecurrente(e, editar, d.carteras.filter { it.libro == e.libro && !it.archivada }) { nuevo = false; editar = null }
}

@Composable
private fun DialogoRecurrente(e: EstadoApp, r: Recurrente?, carteras: List<Cartera>, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(r?.tipo ?: Tipo.GASTO) }
    var desc by remember { mutableStateOf(r?.descripcion ?: "") }
    var monto by remember { mutableStateOf(r?.let { Dinero.csv(it.monto) } ?: "") }
    var cartera by remember { mutableStateOf(carteras.firstOrNull { it.id == r?.carteraId } ?: carteras.firstOrNull()) }
    var categoria by remember { mutableStateOf(r?.categoria ?: "Servicios") }
    var frec by remember { mutableStateOf(r?.frecuencia ?: Frecuencia.MENSUAL) }
    var proxima by remember { mutableStateOf(Fechas.dmy(r?.proxima ?: hoy())) }
    var auto by remember { mutableStateOf(r?.automatico ?: false) }
    var activa by remember { mutableStateOf(r?.activa ?: true) }
    val m = Dinero.parse(monto)?.let { kotlin.math.abs(it) }; val p = CarpetaSync.normalizarFecha(proxima)
    AlertDialog(onDismissRequest = onCerrar, title = { Text(if (r == null) "Nuevo recurrente" else "Editar recurrente") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(Tipo.GASTO, Tipo.ENTRADA).forEach { t -> FilterChip(tipo == t, { tipo = t }, { Text(t.etiqueta) }) } }
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (ej. Internet)") }, singleLine = true)
                OutlinedTextField(monto, { monto = it }, label = { Text("Monto (${cartera?.moneda?.simbolo ?: ""})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                SelectorCartera("Cartera", cartera, carteras) { cartera = it }
                SelectorTexto("Categoría", categoria, if (tipo == Tipo.ENTRADA) Categorias.ENTRADA else Categorias.GASTO) { categoria = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Frecuencia.entries.forEach { f -> FilterChip(frec == f, { frec = f }, { Text(f.etiqueta) }) } }
                OutlinedTextField(proxima, { proxima = it }, label = { Text("Próxima fecha") }, singleLine = true, isError = p == null)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(auto, { auto = it }); Text("Registrar automáticamente", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(activa, { activa = it }); Text("Activo") }
                if (r != null) TextButton(onClick = { e.io(scope) { e.db.recurrentes().delete(r.id) }; onCerrar() }) { Text("Borrar", color = Colores.gasto()) }
            }
        },
        confirmButton = {
            TextButton(enabled = desc.isNotBlank() && m != null && m > 0 && cartera != null && p != null, onClick = {
                val nuevo = Recurrente(r?.id ?: 0, r?.uid ?: Duro.uid(), e.libro, tipo, cartera!!.id, m!!, categoria.trim(), desc.trim(), frec, p!!, auto, activa)
                e.io(scope) { e.db.recurrentes().upsert(nuevo) }; onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}

// ============================ PRESUPUESTOS ============================

@Composable
private fun SeccionPresupuestos(e: EstadoApp, d: DatosControl) {
    val porId = d.carteras.associateBy { it.id }
    val movsLibro = remember(d.movs, porId, e.libro) { Finanzas.delLibro(d.movs, porId, e.libro) }
    val avances = remember(d.presupuestos, movsLibro, d.deudas, e.libro, d.tabla) {
        Control.avances(d.presupuestos, movsLibro, d.deudas, porId, e.libro, YearMonth.now(), d.tabla).associateBy { it.p.categoria }
    }
    val gastadoUsd = remember(movsLibro, d.tabla, d.deudas) {
        Finanzas.resumen(movsLibro, porId, e.libro, YearMonth.now(), d.tabla, app.elduro.core.Vista.USD_BCV, d.deudas).porCategoria.toMap()
    }
    var editar by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tope mensual por categoría (${Fechas.mes(YearMonth.now().toString())}). Te aviso al 80 % y al 100 %. Toca una para fijarlo.",
            style = MaterialTheme.typography.bodySmall, color = Colores.neutro()) }
        items(Categorias.GASTO) { cat ->
            val a = avances[cat]
            Column(Modifier.fillMaxWidth().clickable { editar = cat }.padding(vertical = 6.dp)) {
                Row {
                    Text(cat, Modifier.weight(1f), fontWeight = if (a != null) FontWeight.SemiBold else FontWeight.Normal)
                    Text(if (a != null) "${Dinero.num(a.gastado)} de ${Dinero.fmt(a.p.monto, a.p.moneda)}"
                         else "${Dinero.fmtVista(gastadoUsd[cat] ?: 0.0, app.elduro.core.Vista.USD_BCV)} · sin tope",
                        style = MaterialTheme.typography.bodySmall, color = if (a == null) Colores.neutro() else if (a.fraccion >= 1) Colores.gasto() else if (a.fraccion >= 0.8) Colores.aviso() else MaterialTheme.colorScheme.onSurface)
                }
                if (a != null) LinearProgressIndicator(progress = { a.fraccion.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
                    color = if (a.fraccion >= 1) Colores.gasto() else if (a.fraccion >= 0.8) Colores.aviso() else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
    editar?.let { cat -> DialogoPresupuesto(e, cat, d.presupuestos.firstOrNull { it.libro == e.libro && it.categoria == cat }) { editar = null } }
}

@Composable
private fun DialogoPresupuesto(e: EstadoApp, cat: String, p: Presupuesto?, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var monto by remember { mutableStateOf(p?.let { Dinero.csv(it.monto) } ?: "") }
    var moneda by remember { mutableStateOf(p?.moneda ?: Moneda.USD) }
    val m = Dinero.parse(monto)?.let { kotlin.math.abs(it) }
    AlertDialog(onDismissRequest = onCerrar, title = { Text("Presupuesto · $cat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(moneda == Moneda.USD, { moneda = Moneda.USD }, { Text("$ (BCV)") }); FilterChip(moneda == Moneda.BS, { moneda = Moneda.BS }, { Text("Bs") })
                }
                OutlinedTextField(monto, { monto = it }, label = { Text("Tope mensual") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (p != null) TextButton(onClick = { e.io(scope) { e.db.presupuestos().delete(e.libro, cat) }; onCerrar() }) { Text("Quitar tope", color = Colores.gasto()) }
            }
        },
        confirmButton = { TextButton(enabled = m != null && m > 0, onClick = { e.io(scope) { e.db.presupuestos().upsert(Presupuesto(e.libro, cat, m!!, moneda)) }; onCerrar() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } })
}
