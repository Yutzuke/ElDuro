package app.elduro.ui

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.elduro.BuildConfig
import app.elduro.core.CarpetaSync
import app.elduro.core.ConsultaTasas
import app.elduro.core.Dinero
import app.elduro.core.Duro
import app.elduro.core.Fechas
import app.elduro.core.ImportarExcel
import app.elduro.core.Prefs
import app.elduro.core.Vista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PantallaAjustes(e: EstadoApp, modifier: Modifier) {
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

    hojas?.let { h -> DialogoImportar(e, h, onCerrar = { hojas = null }) { informe = it; hojas = null } }
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
private fun DialogoImportar(e: EstadoApp, hojas: List<ImportarExcel.Hoja>, onCerrar: () -> Unit, onHecho: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val elegidas = remember { mutableStateListOf<String>().apply { addAll(hojas.filter { it.util && it.notas.isEmpty() }.map { it.nombre }) } }
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
                            Text(if (h.util) "${h.filas.size} movimientos" else "sin movimientos", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    h.notas.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = Colores.neutro()) }
                    if (h.nombre in elegidas) h.filas.filter { it.avisoFecha != null }.forEach {
                        Text("⚠ fila ${it.fila}: ${it.descripcion} — ${it.avisoFecha}", style = MaterialTheme.typography.bodySmall, color = Colores.aviso())
                    }
                }
                val apert = hojas.filter { it.nombre in elegidas }.flatMap { it.filas }.filter { it.tipo == app.elduro.data.Tipo.APERTURA }
                if (apert.isNotEmpty()) Text("Aperturas: " + apert.joinToString { "${Dinero.fmt(it.monto, it.moneda)} ${it.forma.etiqueta.lowercase()}" },
                    style = MaterialTheme.typography.bodySmall)
                Text("Libro destino", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Duro.LIBROS.forEach { l -> FilterChip(libro == l, { libro = l }, { Text(l) }) } }
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
                    val hist = if (desde != null) runCatching { ConsultaTasas.historico(e.db, desde) }.getOrNull() else null
                    val txt = buildString {
                        append("Importados ${r.nuevos} movimientos nuevos")
                        if (r.actualizados > 0) append(" y ${r.actualizados} actualizados")
                        append(" en $libro.")
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
