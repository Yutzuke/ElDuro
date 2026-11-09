package app.elduro.ui

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.elduro.BuildConfig
import app.elduro.core.CarpetaSync
import app.elduro.core.Categorias
import app.elduro.core.ConsultaTasas
import app.elduro.core.Dinero
import app.elduro.core.Duro
import app.elduro.core.Fechas
import app.elduro.core.ImportarExcel
import app.elduro.core.Libros
import app.elduro.data.Cartera
import app.elduro.core.Prefs
import app.elduro.core.Vista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PantallaAjustes(e: EstadoApp, carteras: List<Cartera>, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var carpeta by remember { mutableStateOf(Prefs.carpeta(e.ctx).path) }
    var hojas by remember { mutableStateOf<List<ImportarExcel.Hoja>?>(null) }
    var informe by remember { mutableStateOf<String?>(null) }

    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = uri?.let(::rutaDeArbol) ?: return@rememberLauncherForActivityResult
        Prefs.setCarpeta(e.ctx, File(p)); carpeta = p
        e.ctx.getSharedPreferences("elduro_archivos", 0).edit().clear().apply()
        e.sincronizar(scope, mostrar = true)
    }
    val elegirExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        e.io(scope) {
            val h = e.ctx.contentResolver.openInputStream(uri)!!.use { ImportarExcel.leer(it) }
            withContext(Dispatchers.Main) { hojas = h }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Seccion("Moneda principal") {
            Text("Los totales se muestran en esta moneda. Cada movimiento conserva su moneda original.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Vista.entries.forEach { v -> FilterChip(e.vista == v, { e.cambiarVista(v) }, { Text(v.etiqueta) }) }
            }
        }
        SeccionLibros(e, carteras)
        Seccion("Carpeta de datos") {
            Text(carpeta, fontWeight = FontWeight.Medium)
            Text("CSV legibles (carteras, tasas y un archivo por libro y mes). En NodeSync, Syncthing los lleva solos al PC; " +
                    "si los editas allí, ElDuro importa los cambios al abrirse.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            Text(e.sync, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { elegirCarpeta.launch(null) }) { Text("Cambiar") }
                Button(onClick = {
                    e.io(scope) {
                        val r = CarpetaSync.sincronizar(e.ctx)
                        withContext(Dispatchers.Main) { e.sync = e.textoSync(); informe = r.texto() }
                    }
                }, enabled = e.acceso) { Text("Sincronizar ahora") }
            }
            if (!e.acceso) OutlinedButton(onClick = { e.pedirPermiso() }) { Text("Conceder acceso a archivos") }
        }
        Seccion("Importar Balance.xlsx") {
            Text("Lee tu hoja de cálculo (secciones BOLÍVARES y DÓLARES por mes). «Inicio de caja» pasa a apertura y «cuadre» a ajuste. " +
                    "Importar dos veces no duplica.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
            OutlinedButton(onClick = { elegirExcel.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*")) }) {
                Text("Elegir archivo…")
            }
        }
        Text("ElDuro ${BuildConfig.VERSION_NAME} · datos 100 % locales", style = MaterialTheme.typography.labelSmall, color = Colores.neutro(),
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    hojas?.let { h -> DialogoImportar(e, h, Libros.activos(e.ctx, carteras), onCerrar = { hojas = null }) { informe = it; hojas = null } }
    informe?.let { t ->
        AlertDialog(onDismissRequest = { informe = null }, text = { Text(t) },
            confirmButton = { TextButton(onClick = { informe = null }) { Text("OK") } })
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            contenido()
        }
    }
}

@Composable
private fun DialogoImportar(e: EstadoApp, hojas: List<ImportarExcel.Hoja>, libros: List<String>, onCerrar: () -> Unit, onHecho: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val elegidas = remember { mutableStateListOf<String>().apply { addAll(hojas.filter { it.util && Categorias.normal(it.nombre) != "formato" }.map { it.nombre }) } }
    var libro by remember { mutableStateOf(e.libro) }
    var corregir by remember { mutableStateOf(true) }
    var trabajando by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = { if (!trabajando) onCerrar() }, title = { Text("Importar hojas") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (h in hojas) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(h.nombre in elegidas, { if (it) elegidas += h.nombre else elegidas -= h.nombre }, enabled = h.util)
                        Column {
                            Text(h.nombre, fontWeight = FontWeight.SemiBold)
                            Text(h.resumen, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (h.nombre in elegidas) h.deudas.forEach { Text("· ${it.nombre}: ${Dinero.fmt(it.monto, it.moneda)}", style = MaterialTheme.typography.bodySmall) }
                    if (h.nombre in elegidas) h.faltantes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                    h.notas.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = Colores.neutro()) }
                    if (h.nombre in elegidas) h.filas.filter { it.avisoFecha != null }.forEach {
                        Text("⚠ fila ${it.fila}: ${it.descripcion} — ${it.avisoFecha}", style = MaterialTheme.typography.bodySmall, color = Colores.aviso())
                    }
                }
                val apert = hojas.filter { it.nombre in elegidas }.flatMap { it.filas }.filter { it.tipo == app.elduro.data.Tipo.APERTURA }
                if (apert.isNotEmpty()) Text("Aperturas: " + apert.joinToString { "${Dinero.fmt(it.monto, it.moneda)} ${it.forma.etiqueta.lowercase()}" },
                    style = MaterialTheme.typography.bodySmall)
                Text("Libro destino", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { libros.forEach { l -> FilterChip(libro == l, { libro = l }, { Text(l) }) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(corregir, { corregir = it })
                    Text("Corregir fechas fuera del mes de su hoja (como se indica)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = elegidas.isNotEmpty() && !trabajando, onClick = {
                trabajando = true
                e.io(scope) {
                    val sel = hojas.filter { it.nombre in elegidas }
                    val r = ImportarExcel.importar(e.db, sel, libro, corregir)
                    val desde = sel.flatMap { it.filas }.mapNotNull { it.fecha }.minOrNull()
                        ?: sel.flatMap { it.deudas }.mapNotNull { it.fecha }.minOrNull()
                    val hist = if (desde != null) runCatching { ConsultaTasas.historico(e.db, desde) }.getOrNull() else null
                    val txt = buildString {
                        append("Movimientos: ${r.nuevos} nuevos")
                        if (r.actualizados > 0) append(", ${r.actualizados} ya existían (actualizados)")
                        append(" en $libro.")
                        if (r.deudas > 0) append("\nDeudas: ${r.deudas} (en Control → Deudas; añade cuotas si las tienen).")
                        if (r.faltantes > 0) append("\nFaltantes de la tienda: ${r.faltantes}.")
                        if (r.carteras.isNotEmpty()) append("\nCarteras creadas: ${r.carteras.joinToString()}.")
                        append(if (hist != null) "\nTasas históricas BCV cargadas desde ${Fechas.dmy(desde!!)}."
                               else "\nSin internet para el histórico: se usarán tasas estimadas hasta que lo rellenes en Tasas.")
                        append("\nRevisa las categorías: la app aprende cada vez que corriges una.")
                    }
                    withContext(Dispatchers.Main) { trabajando = false; onHecho(txt) }
                }
            }) { Text(if (trabajando) "Importando…" else "Importar") }
        },
        dismissButton = { TextButton(onClick = onCerrar, enabled = !trabajando) { Text("Cancelar") } })
}

/** Uri de árbol SAF → ruta real (memoria interna o microSD). */
fun rutaDeArbol(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
    val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$vol"
    return if (rel.isEmpty()) base else "$base/$rel"
}

@Composable
private fun SeccionLibros(e: EstadoApp, carteras: List<Cartera>) {
    val scope = rememberCoroutineScope()
    val todos = remember(carteras, e.versionLibros) { Libros.todos(e.ctx, carteras) }
    val archivados = remember(e.versionLibros) { Libros.archivados(e.ctx) }
    var dialogo by remember { mutableStateOf<String?>(null) }   // "" = nuevo; otro = renombrar ese
    Seccion("Libros") {
        Text("Cada libro tiene sus propias carteras, deudas, faltantes y presupuestos. Archivar lo oculta sin borrar nada.",
            style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
        todos.forEachIndexed { i, l ->
            val arch = l in archivados
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l, fontWeight = FontWeight.SemiBold, color = if (arch) Colores.neutro() else MaterialTheme.colorScheme.onSurface)
                    Text("${carteras.count { it.libro == l && !it.archivada }} carteras" + if (arch) " · archivado" else "",
                        style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                }
                TextButton(onClick = { Libros.mover(e.ctx, carteras, l, -1); e.versionLibros++ }, enabled = i > 0) { Text("↑") }
                TextButton(onClick = { dialogo = l }) { Text("Renombrar") }
                TextButton(enabled = arch || todos.count { it !in archivados } > 1, onClick = { Libros.archivar(e.ctx, l, !arch); e.versionLibros++ }) {
                    Text(if (arch) "Mostrar" else "Archivar")
                }
                if (todos.size > 1) TextButton(onClick = {
                    e.io(scope) {
                        val ok = Libros.puedeBorrar(e.db, l)
                        if (ok) Libros.borrar(e.ctx, e.db, l)
                        withContext(Dispatchers.Main) {
                            e.versionLibros++
                            e.avisos.showSnackbar(if (ok) "Libro «$l» borrado" else "«$l» tiene movimientos o deudas: archívalo en vez de borrarlo")
                        }
                    }
                }) { Text("✕", color = Colores.gasto()) }
            }
        }
        OutlinedButton(onClick = { dialogo = "" }) { Text("Nuevo libro") }
    }
    dialogo?.let { viejo ->
        var nombre by remember(viejo) { mutableStateOf(viejo) }
        var error by remember(viejo) { mutableStateOf<String?>(null) }
        var trabajando by remember(viejo) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!trabajando) dialogo = null },
            title = { Text(if (viejo.isEmpty()) "Nuevo libro" else "Renombrar «$viejo»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val foco = remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.material3.OutlinedTextField(nombre, { nombre = it; error = null }, label = { Text("Nombre") }, singleLine = true,
                        isError = error != null, supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.focusRequester(foco))
                    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
                    Text(if (viejo.isEmpty()) "Se crea con Bs efectivo, Bs digital, $ efectivo y $ digital."
                         else "Se renombra en todos sus datos y su carpeta de CSV en NodeSync.", style = MaterialTheme.typography.bodySmall, color = Colores.neutro())
                }
            },
            confirmButton = {
                TextButton(enabled = !trabajando && nombre.isNotBlank() && nombre.trim() != viejo, onClick = {
                    trabajando = true
                    e.io(scope) {
                        val err = Libros.validar(e.ctx, e.db, nombre, excepto = viejo.ifEmpty { null })
                        if (err == null) {
                            if (viejo.isEmpty()) Libros.crear(e.ctx, e.db, nombre) else Libros.renombrar(e.ctx, e.db, viejo, nombre)
                        }
                        withContext(Dispatchers.Main) {
                            trabajando = false
                            if (err != null) error = err
                            else {
                                if (viejo.isEmpty()) e.cambiarLibro(nombre.trim()) else e.libro = Prefs.libroActual(e.ctx)
                                e.versionLibros++; dialogo = null
                            }
                        }
                    }
                }) { Text(if (trabajando) "…" else "Guardar") }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }, enabled = !trabajando) { Text("Cancelar") } })
    }
}
