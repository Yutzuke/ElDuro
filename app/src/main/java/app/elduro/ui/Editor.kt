package app.elduro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.elduro.core.Categorias
import app.elduro.core.Dinero
import app.elduro.core.Duro
import app.elduro.core.EntradaRapida
import app.elduro.core.Fechas
import app.elduro.core.Fuente
import app.elduro.core.Prefs
import app.elduro.core.TablaTasas
import app.elduro.data.Cartera
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

private val TIPOS_EDITABLES = listOf(Tipo.GASTO, Tipo.ENTRADA, Tipo.TRANSFERENCIA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorMovimiento(
    e: EstadoApp, inicial: Movimiento?, carteras: List<Cartera>, tabla: TablaTasas, plantilla: Movimiento? = null,
    onCerrar: () -> Unit, onGuardar: (Movimiento) -> Unit, onBorrar: (Movimiento) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val libroCarteras = carteras.filter { it.libro == e.libro && !it.archivada }
    val todas = carteras.filter { !it.archivada }
    var aprendido by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { aprendido = withContext(Dispatchers.IO) { Duro.aprendido(e.db) } }

    val base = inicial?.let { m -> carteras.firstOrNull { it.id == m.carteraId } }
        ?: libroCarteras.firstOrNull { it.id == Prefs.carteraRapida(e.ctx, e.libro) }
        ?: libroCarteras.firstOrNull { it.nombre.contains("digital", true) && it.moneda == app.elduro.data.Moneda.BS }
        ?: libroCarteras.firstOrNull()

    var texto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(inicial?.tipo ?: Tipo.GASTO) }
    var cartera by remember { mutableStateOf(base) }
    var destino by remember { mutableStateOf(inicial?.carteraDestinoId?.let { id -> carteras.firstOrNull { it.id == id } }) }
    var monto by remember { mutableStateOf(inicial?.let { Dinero.csv(abs(it.monto)) } ?: "") }
    var tasa by remember { mutableStateOf(inicial?.tasa?.let { Dinero.tasa(it) } ?: "") }
    var categoria by remember { mutableStateOf(inicial?.categoria ?: plantilla?.categoria ?: "") }
    var categoriaManual by remember { mutableStateOf(inicial != null || plantilla != null) }
    var descripcion by remember { mutableStateOf(inicial?.descripcion ?: plantilla?.descripcion ?: "") }
    var fecha by remember { mutableStateOf(inicial?.fecha ?: hoy()) }
    var verFecha by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    val esAbono = inicial?.deudaId != null
    val especial = inicial != null && (inicial.tipo !in TIPOS_EDITABLES || esAbono)   // ajuste / apertura / abono

    fun tasaPorDefecto(): String = tabla.en(Fuente.BINANCE, fecha)?.valor?.let { Dinero.tasa(it) } ?: ""

    fun aplicarTexto(t: String) {
        texto = t
        if (t.isBlank()) return
        val l = EntradaRapida.leer(t)
        tipo = l.tipo
        l.monto?.let { monto = Dinero.csv(it) }
        descripcion = l.descripcion
        val c = EntradaRapida.cartera(libroCarteras, base, l.moneda, l.forma) ?: return
        cartera = c
        if (l.tipo == Tipo.TRANSFERENCIA) {
            destino = EntradaRapida.destino(todas.filter { it.libro == e.libro }, c, l) ?: destino
            tasa = l.tasa?.let { Dinero.tasa(it) } ?: if (destino?.moneda != c.moneda) tasaPorDefecto() else ""
        }
        if (!categoriaManual) categoria = Categorias.adivinar(l.descripcion, l.tipo == Tipo.ENTRADA, aprendido) ?: ""
    }

    val montoC = Dinero.parse(monto)?.let { abs(it) }
    val tasaD = Dinero.parseDouble(tasa)
    val montoDest = if (tipo == Tipo.TRANSFERENCIA && cartera != null && destino != null && montoC != null)
        EntradaRapida.montoDestino(montoC, cartera!!.moneda, destino!!.moneda, tasaD) else null
    val necesitaTasa = tipo == Tipo.TRANSFERENCIA && cartera != null && destino != null &&
            (cartera!!.moneda == app.elduro.data.Moneda.BS) != (destino!!.moneda == app.elduro.data.Moneda.BS)
    val valido = montoC != null && montoC > 0 && cartera != null &&
            (tipo != Tipo.TRANSFERENCIA || esAbono || (destino != null && destino!!.id != cartera!!.id && montoDest != null))

    fun construir(): Movimiento {
        val c = cartera!!
        val firmado = when (tipo) {
            Tipo.ENTRADA -> montoC!!
            Tipo.GASTO, Tipo.TRANSFERENCIA -> -montoC!!
            else -> if ((inicial?.monto ?: 0) < 0) -montoC!! else montoC!!
        }
        if (esAbono) {
            val ini = inicial!!
            val firm = if (ini.monto < 0) -montoC!! else montoC!!
            // misma moneda: lo descontado sigue al monto; otra moneda: se escala con la tasa original
            val md = ini.montoDestino?.let { old -> if (old == abs(ini.monto)) montoC else if (ini.monto != 0L) old * montoC / abs(ini.monto) else old }
            return ini.copy(fecha = fecha, carteraId = c.id, monto = firm, montoDestino = md, descripcion = descripcion.trim())
        }
        return Movimiento(
            id = inicial?.id ?: 0, uid = inicial?.uid ?: Duro.uid(), fecha = fecha, tipo = tipo, carteraId = c.id, monto = firmado,
            carteraDestinoId = if (tipo == Tipo.TRANSFERENCIA) destino?.id else null,
            montoDestino = if (tipo == Tipo.TRANSFERENCIA) montoDest else null,
            tasa = if (necesitaTasa) tasaD else null,
            categoria = if (tipo == Tipo.TRANSFERENCIA) "" else categoria.trim(),
            descripcion = descripcion.trim(), creado = inicial?.creado ?: System.currentTimeMillis(),
        )
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding().navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(when {
                inicial == null && plantilla != null -> "Comprado · ${plantilla.descripcion}"
                inicial == null -> "Nuevo movimiento · ${e.libro}"
                esAbono -> "Editar pago de deuda"
                else -> "Editar ${inicial.tipo.etiqueta.lowercase()}"
            }, style = MaterialTheme.typography.titleLarge)

            if (inicial == null && plantilla == null) {
                OutlinedTextField(texto, ::aplicarTexto, Modifier.fillMaxWidth().focusRequester(foco),
                    label = { Text("Escribe rápido") }, placeholder = { Text("-8000 pollo · +15$ venta · 20$ > bs 1048") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
                // vista previa
                if (valido) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    val m = construir()
                    val c = cartera!!
                    val conv = tabla.convertir(m.monto, c.moneda, fecha, e.vista)
                    Text(
                        "${tipo.etiqueta} · ${c.nombre}" + (if (tipo == Tipo.TRANSFERENCIA) " → ${destino?.nombre}" else "") +
                                "\n" + Dinero.fmt(m.monto, c.moneda, true) +
                                (if (tipo == Tipo.TRANSFERENCIA && m.montoDestino != null) " → " + Dinero.fmt(m.montoDestino, destino!!.moneda, true) else "") +
                                (if (tipo != Tipo.TRANSFERENCIA && conv?.tasa != null) "  ≈ " + Dinero.fmtVista(conv.valor, e.vista) else "") +
                                (if (categoria.isNotBlank() && tipo != Tipo.TRANSFERENCIA) "  · $categoria" else ""),
                        Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                }
            }

            if (!especial) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TIPOS_EDITABLES.forEachIndexed { i, t ->
                    SegmentedButton(selected = tipo == t, onClick = {
                        tipo = t
                        if (t == Tipo.TRANSFERENCIA && destino == null && cartera != null)
                            destino = EntradaRapida.destino(libroCarteras, cartera!!, EntradaRapida.leer(""))
                        if (!categoriaManual) categoria = Categorias.adivinar(descripcion, t == Tipo.ENTRADA, aprendido) ?: ""
                    }, shape = SegmentedButtonDefaults.itemShape(i, TIPOS_EDITABLES.size)) { Text(if (t == Tipo.TRANSFERENCIA) "Transf." else t.etiqueta) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(monto, { monto = it }, Modifier.weight(1f), label = { Text("Monto (${cartera?.moneda?.simbolo ?: ""})") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedButton(onClick = { verFecha = true }, Modifier.padding(top = 8.dp)) { Text(Fechas.dmy(fecha)) }
            }

            SelectorCartera(if (tipo == Tipo.TRANSFERENCIA) "Desde" else "Cartera", cartera, libroCarteras) {
                cartera = it; if (inicial == null) Prefs.setCarteraRapida(e.ctx, e.libro, it.id)
                if (tipo == Tipo.TRANSFERENCIA && destino?.moneda != it.moneda && tasa.isBlank()) tasa = tasaPorDefecto()
            }

            if (tipo == Tipo.TRANSFERENCIA && !esAbono) {
                SelectorCartera("Hacia", destino, todas.filter { it.id != cartera?.id }, mostrarLibro = true) {
                    destino = it; if (it.moneda != cartera?.moneda && tasa.isBlank()) tasa = tasaPorDefecto()
                }
                if (necesitaTasa) {
                    OutlinedTextField(tasa, { tasa = it }, Modifier.fillMaxWidth(), label = { Text("Tasa usada (Bs por $)") },
                        supportingText = {
                            val b = tabla.en(Fuente.BCV, fecha)?.valor; val n = tabla.en(Fuente.BINANCE, fecha)?.valor
                            Text("BCV ${b?.let { Dinero.tasa(it) } ?: "—"} · Binance ${n?.let { Dinero.tasa(it) } ?: "—"}" +
                                    (montoDest?.let { " · llegan ${Dinero.fmt(it, destino!!.moneda)}" } ?: ""))
                        },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            } else if (!especial) {
                SelectorTexto("Categoría", categoria, if (tipo == Tipo.ENTRADA) Categorias.ENTRADA else Categorias.GASTO) {
                    categoria = it; categoriaManual = true
                }
            }

            OutlinedTextField(descripcion, {
                descripcion = it
                if (!categoriaManual) categoria = Categorias.adivinar(it, tipo == Tipo.ENTRADA, aprendido) ?: categoria
            }, Modifier.fillMaxWidth(), label = { Text("Descripción") }, singleLine = true)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inicial != null) OutlinedButton(onClick = { onBorrar(inicial) }) { Text("Borrar", color = Colores.gasto()) }
                Button(onClick = { onGuardar(construir()) }, enabled = valido, modifier = Modifier.weight(1f)) { Text("Guardar") }
            }
        }
    }

    if (verFecha) {
        val st = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(fecha).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { verFecha = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { fecha = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                verFecha = false
            }) { Text("Aceptar") }
        }, dismissButton = { TextButton(onClick = { verFecha = false }) { Text("Cancelar") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorCartera(etiqueta: String, actual: Cartera?, opciones: List<Cartera>, mostrarLibro: Boolean = false, onElegir: (Cartera) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    fun nombre(c: Cartera) = if (mostrarLibro) "${c.libro} · ${c.nombre}" else c.nombre
    ExposedDropdownMenuBox(abierto, { abierto = it }) {
        OutlinedTextField(actual?.let(::nombre) ?: "", {}, readOnly = true, label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(abierto, { abierto = false }) {
            opciones.forEach { c ->
                DropdownMenuItem(text = { Text("${nombre(c)}  (${c.moneda.simbolo} · ${c.forma.etiqueta.lowercase()})") }, onClick = { onElegir(c); abierto = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorTexto(etiqueta: String, actual: String, opciones: List<String>, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(abierto, { abierto = it }) {
        OutlinedTextField(actual, { onElegir(it) }, label = { Text(etiqueta) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(abierto, { abierto = false }) {
            opciones.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onElegir(o); abierto = false }) }
        }
    }
}
