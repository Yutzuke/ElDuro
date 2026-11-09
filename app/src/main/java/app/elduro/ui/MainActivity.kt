package app.elduro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.elduro.core.CarpetaSync
import app.elduro.core.Control
import app.elduro.core.Exportar
import app.elduro.core.Vigilante
import app.elduro.core.ConsultaTasas
import app.elduro.core.Duro
import app.elduro.core.Fechas
import app.elduro.core.Libros
import app.elduro.core.Prefs
import app.elduro.core.TablaTasas
import app.elduro.core.Vista
import app.elduro.data.ElDuroDb
import app.elduro.data.Faltante
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Estado compartido por todas las pantallas. */
class EstadoApp(val ctx: Context) {
    val db = ElDuroDb.get(ctx)
    var libro by mutableStateOf(Prefs.libroActual(ctx))
    var vista by mutableStateOf(Prefs.vista(ctx))
    var mes by mutableStateOf(YearMonth.now())
    var consultando by mutableStateOf(false)
    var estadoTasas by mutableStateOf("")
    var acceso by mutableStateOf(Prefs.tieneAcceso())
    var sync by mutableStateOf(textoSync())
    /** Se incrementa al crear/renombrar/archivar libros para recalcular la lista. */
    var versionLibros by mutableStateOf(0)
    val avisos = SnackbarHostState()

    fun cambiarLibro(l: String) { libro = l; Prefs.setLibro(ctx, l) }
    fun cambiarVista(v: Vista) { vista = v; Prefs.setVista(ctx, v) }

    fun textoSync(): String {
        val t = Prefs.ultimaSync(ctx)
        val err = Prefs.ultimoError(ctx)
        return when {
            err != null -> "⚠ $err"
            t == 0L -> "Sin sincronizar"
            else -> "Carpeta al día · " + DateUtils.getRelativeTimeSpanString(t).toString().lowercase()
        }
    }

    fun io(scope: CoroutineScope, bloque: suspend () -> Unit) = scope.launch(Dispatchers.IO) {
        runCatching { bloque() }.onFailure { e -> withContext(Dispatchers.Main) { avisos.showSnackbar("Error: ${e.message ?: e.javaClass.simpleName}") } }
    }

    fun guardar(scope: CoroutineScope, m: Movimiento) = io(scope) { Duro.guardar(db, m) }
    fun borrar(scope: CoroutineScope, m: Movimiento) = io(scope) {
        Duro.borrar(db, m)
        withContext(Dispatchers.Main) {
            val r = avisos.showSnackbar("Movimiento borrado", actionLabel = "Deshacer", withDismissAction = true,
                duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) io(scope) { Duro.guardar(db, m.copy(id = 0)) }
        }
    }

    /** Consulta tasas. Si falla, la app sigue con las últimas conocidas. */
    fun refrescarTasas(scope: CoroutineScope, forzar: Boolean) {
        if (consultando) return
        val edad = System.currentTimeMillis() - Prefs.ultimaConsulta(ctx)
        if (!forzar && edad < 3 * 3600_000L) {
            estadoTasas = "Consultadas " + DateUtils.getRelativeTimeSpanString(Prefs.ultimaConsulta(ctx)).toString().lowercase(); return
        }
        consultando = true
        scope.launch(Dispatchers.IO) {
            val inf = runCatching { ConsultaTasas.actualizar(db) }.getOrElse { ConsultaTasas.Informe(emptyList(), listOf(it.message ?: "sin conexión")) }
            if (inf.ok.isNotEmpty()) Prefs.setUltimaConsulta(ctx, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                consultando = false
                estadoTasas = when {
                    inf.fallos.isEmpty() -> "Actualizadas ahora"
                    inf.ok.isEmpty() -> "Sin conexión: usando las últimas tasas guardadas"
                    else -> "Actualizado: ${inf.ok.joinToString()} · falló: ${inf.fallos.joinToString()}"
                }
                if (forzar && inf.fallos.isNotEmpty()) avisos.showSnackbar(estadoTasas)
            }
        }
    }

    fun sincronizar(scope: CoroutineScope, mostrar: Boolean) = scope.launch(Dispatchers.IO) {
        val r = runCatching { CarpetaSync.sincronizar(ctx) }
        withContext(Dispatchers.Main) {
            acceso = Prefs.tieneAcceso(); sync = textoSync()
            r.onSuccess { if (mostrar || it.importados.isNotEmpty() || it.avisos.isNotEmpty()) avisos.showSnackbar(it.texto().lines().first { l -> l.isNotBlank() }) }
             .onFailure { avisos.showSnackbar("Error al sincronizar: ${it.message}") }
        }
    }

    /** Exporta el mes y libro actuales y abre el menú de compartir. */
    fun exportar(scope: CoroutineScope, excel: Boolean) = io(scope) {
        val f = if (excel) Exportar.xlsxMes(ctx, libro, mes) else Exportar.pdfMes(ctx, libro, mes, vista)
        withContext(Dispatchers.Main) {
            avisos.showSnackbar("Guardado en ${f.parentFile?.name}/${f.name}")
        }
        compartir(f, if (excel) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf")
    }

    fun compartir(f: java.io.File, mime: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun pedirPermiso() {
        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var estado: EstadoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        estado = EstadoApp(applicationContext)
        CarpetaSync.iniciar(applicationContext)
        Vigilante.programar(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { runCatching { Duro.sembrar(estado.db) } }
        if (android.os.Build.VERSION.SDK_INT >= 33 && !Prefs.sp(this).getBoolean("pidio_notis", false) &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Prefs.sp(this).edit().putBoolean("pidio_notis", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { TemaElDuro { Raiz(estado) } }
    }

    override fun onResume() {
        super.onResume()
        estado.acceso = Prefs.tieneAcceso()
        estado.sincronizar(lifecycleScope, mostrar = false)
        estado.refrescarTasas(lifecycleScope, forzar = false)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { Control.procesarRecurrentes(estado.db) }
            runCatching { Exportar.zipSiToca(applicationContext) }
        }
    }
}

private enum class Pestana(val titulo: String) { MES("Mes"), CARTERAS("Carteras"), CONTROL("Control"), TASAS("Tasas"), AJUSTES("Ajustes") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Raiz(e: EstadoApp) {
    val scope = rememberCoroutineScope()
    var pestana by remember { mutableStateOf(Pestana.MES) }
    var editor by remember { mutableStateOf<Movimiento?>(null) }
    var plantilla by remember { mutableStateOf<Movimiento?>(null) }
    var faltanteEnCompra by remember { mutableStateOf<Faltante?>(null) }
    var editorAbierto by remember { mutableStateOf(false) }

    val carteras by e.db.carteras().flow().collectAsState(emptyList())
    val movs by e.db.movimientos().flow().collectAsState(emptyList())
    val tasasLista by e.db.tasas().flow().collectAsState(emptyList())
    val tabla = remember(tasasLista) { TablaTasas(tasasLista) }
    val deudas by e.db.deudas().flow().collectAsState(emptyList())
    val recurrentes by e.db.recurrentes().flow().collectAsState(emptyList())
    val presupuestos by e.db.presupuestos().flow().collectAsState(emptyList())
    val faltantes by e.db.faltantes().flow().collectAsState(emptyList())
    val datos = DatosControl(carteras, movs, tabla, deudas, recurrentes, presupuestos, faltantes)
    val libros = remember(carteras, e.versionLibros) { Libros.activos(e.ctx, carteras) }
    LaunchedEffect(libros, e.libro) { if (e.libro !in libros) e.cambiarLibro(libros.first()) }

    LaunchedEffect(Unit) { e.sync = e.textoSync() }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                if (libros.size <= 3) SingleChoiceSegmentedButtonRow {
                    libros.forEachIndexed { i, l ->
                        SegmentedButton(selected = e.libro == l, onClick = { e.cambiarLibro(l) },
                            shape = SegmentedButtonDefaults.itemShape(i, libros.size)) { Text(l, maxLines = 1) }
                    }
                } else {
                    var abierto by remember { mutableStateOf(false) }
                    androidx.compose.material3.OutlinedButton(onClick = { abierto = true }) { Text("${e.libro}  ▾", maxLines = 1) }
                    androidx.compose.material3.DropdownMenu(abierto, { abierto = false }) {
                        libros.forEach { l -> androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { e.cambiarLibro(l); abierto = false }) }
                    }
                }
            }, actions = {
                Text("ElDuro", modifier = Modifier.padding(end = 16.dp), fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
            })
        },
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(selected = pestana == p, onClick = { pestana = p }, label = { Text(p.titulo) }, icon = {
                        Icon(when (p) {
                            Pestana.MES -> Icons.Filled.DateRange; Pestana.CARTERAS -> Icons.Filled.AccountBox
                            Pestana.CONTROL -> Icons.Filled.CheckCircle
                            Pestana.TASAS -> Icons.Filled.Refresh; Pestana.AJUSTES -> Icons.Filled.Settings
                        }, null)
                    })
                }
            }
        },
        floatingActionButton = {
            if (pestana == Pestana.MES || pestana == Pestana.CARTERAS)
                FloatingActionButton(onClick = { editor = null; plantilla = null; editorAbierto = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Filled.Add, "Nuevo movimiento")
                }
        },
        snackbarHost = { SnackbarHost(e.avisos) },
    ) { pad ->
        val mod = Modifier.padding(pad)
        when (pestana) {
            Pestana.MES -> PantallaMes(e, datos, mod) { editor = it; plantilla = null; editorAbierto = true }
            Pestana.CARTERAS -> PantallaCarteras(e, carteras, movs, tabla, mod)
            Pestana.CONTROL -> PantallaControl(e, datos, mod) { f ->
                faltanteEnCompra = f; editor = null
                plantilla = Movimiento(uid = "", fecha = hoy(), tipo = Tipo.GASTO, carteraId = 0, monto = 0, categoria = "Tienda", descripcion = f.texto)
                editorAbierto = true
            }
            Pestana.TASAS -> PantallaTasas(e, tabla, tasasLista, movs, mod)
            Pestana.AJUSTES -> PantallaAjustes(e, carteras, mod)
        }
    }

    if (editorAbierto) {
        EditorMovimiento(
            e = e, inicial = editor, carteras = carteras, tabla = tabla, plantilla = plantilla,
            onCerrar = {
                editorAbierto = false
                // si cancela la compra de un faltante, vuelve a quedar pendiente
                faltanteEnCompra?.let { f -> e.io(scope) { e.db.faltantes().upsert(f.copy(comprado = false)) } }; faltanteEnCompra = null
            },
            onGuardar = { e.guardar(scope, it); editorAbierto = false; faltanteEnCompra = null },
            onBorrar = { e.borrar(scope, it); editorAbierto = false },
        )
    }
}

fun hoy() = Fechas.hoy()
