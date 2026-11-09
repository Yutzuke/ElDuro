package app.elduro.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import app.elduro.data.Aprendizaje
import app.elduro.data.Cartera
import app.elduro.data.ElDuroDb
import app.elduro.data.Forma
import app.elduro.data.Moneda
import app.elduro.data.Movimiento
import app.elduro.data.Tipo
import java.io.File
import java.util.UUID

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("elduro", Context.MODE_PRIVATE)

    fun vista(c: Context): Vista = runCatching { Vista.valueOf(sp(c).getString("vista", null)!!) }.getOrDefault(Vista.USD_BCV)
    fun setVista(c: Context, v: Vista) = sp(c).edit().putString("vista", v.name).apply()

    /** Carpeta de datos. Por defecto <microSD>/NodeSync/ElDuro (sincronizada por Syncthing con el PC). */
    fun carpeta(c: Context): File = sp(c).getString("carpeta", null)?.let(::File) ?: carpetaPorDefecto(c)
    fun setCarpeta(c: Context, f: File) = sp(c).edit().putString("carpeta", f.path).apply()

    fun carpetaPorDefecto(c: Context): File {
        val sm = c.getSystemService(android.os.storage.StorageManager::class.java)
        val vols = sm.storageVolumes.mapNotNull { it.directory }.filter { it.path != Environment.getExternalStorageDirectory().path }
        vols.firstOrNull { File(it, "NodeSync").isDirectory }?.let { return File(it, "NodeSync/ElDuro") }
        val interna = Environment.getExternalStorageDirectory()
        if (File(interna, "NodeSync").isDirectory) return File(interna, "NodeSync/ElDuro")
        return File(interna, "ElDuro")
    }

    fun ultimaConsulta(c: Context) = sp(c).getLong("ultima_consulta", 0)
    fun setUltimaConsulta(c: Context, t: Long) = sp(c).edit().putLong("ultima_consulta", t).apply()
    fun ultimaSync(c: Context) = sp(c).getLong("ultima_sync", 0)
    fun ultimoError(c: Context): String? = sp(c).getString("ultimo_error", null)
    fun setSync(c: Context, error: String?) = sp(c).edit().putLong("ultima_sync", System.currentTimeMillis()).putString("ultimo_error", error).apply()

    fun libroActual(c: Context): String = sp(c).getString("libro", Duro.LIBROS.first())!!
    fun setLibro(c: Context, l: String) = sp(c).edit().putString("libro", l).apply()
    fun carteraRapida(c: Context, libro: String): Long = sp(c).getLong("rapida_$libro", -1)
    fun setCarteraRapida(c: Context, libro: String, id: Long) = sp(c).edit().putLong("rapida_$libro", id).apply()

    fun tieneAcceso(): Boolean = Environment.isExternalStorageManager()
}

/** Operaciones de escritura. Llamar fuera del hilo principal. */
object Duro {
    val LIBROS = listOf("Personal", "Tienda")

    fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    @Synchronized
    fun sembrar(db: ElDuroDb) = db.runInTransaction {
        if (db.carteras().all().isNotEmpty()) return@runInTransaction
        var orden = 0
        fun c(libro: String, nombre: String, m: Moneda, f: Forma) = db.carteras().insert(Cartera(libro = libro, nombre = nombre, moneda = m, forma = f, orden = orden++))
        c("Personal", "Bs efectivo", Moneda.BS, Forma.EFECTIVO)
        c("Personal", "Bs digital", Moneda.BS, Forma.DIGITAL)
        c("Personal", "$ efectivo", Moneda.USD, Forma.EFECTIVO)
        c("Personal", "$ digital", Moneda.USD, Forma.DIGITAL)
        c("Personal", "USDT Binance", Moneda.USDT, Forma.DIGITAL)
        orden = 0
        c("Tienda", "Bs efectivo", Moneda.BS, Forma.EFECTIVO)
        c("Tienda", "Bs digital", Moneda.BS, Forma.DIGITAL)
        c("Tienda", "$ efectivo", Moneda.USD, Forma.EFECTIVO)
        c("Tienda", "$ digital", Moneda.USD, Forma.DIGITAL)
    }

    fun guardar(db: ElDuroDb, m: Movimiento) {
        db.movimientos().upsert(m)
        if (m.categoria.isNotBlank() && (m.tipo == Tipo.GASTO || m.tipo == Tipo.ENTRADA))
            db.aprendizaje().upsertAll(Categorias.palabras(m.descripcion).map { Aprendizaje(it, m.categoria) })
    }

    fun borrar(db: ElDuroDb, m: Movimiento) = db.movimientos().delete(m)

    fun crearCartera(db: ElDuroDb, c: Cartera, apertura: Long) {
        val id = db.carteras().upsert(c).let { if (c.id != 0L) c.id else it }
        if (apertura != 0L) db.movimientos().insert(
            Movimiento(uid = uid(), fecha = Fechas.hoy(), tipo = Tipo.APERTURA, carteraId = id, monto = apertura, descripcion = "Saldo de apertura")
        )
    }

    /** Arqueo (efectivo) o conciliación (digital): registra la diferencia como ajuste. */
    fun arqueo(db: ElDuroDb, c: Cartera, saldoApp: Long, real: Long, fecha: String): Long {
        val dif = real - saldoApp
        if (dif == 0L) return 0
        val que = if (c.forma == Forma.EFECTIVO) "Arqueo" else "Conciliación"
        val txt = if (dif > 0) "sobran" else "faltan"
        db.movimientos().insert(
            Movimiento(uid = uid(), fecha = fecha, tipo = Tipo.AJUSTE, carteraId = c.id, monto = dif, categoria = "Descuadre",
                descripcion = "$que: $txt ${Dinero.fmt(kotlin.math.abs(dif), c.moneda)}")
        )
        return dif
    }

    fun aprendido(db: ElDuroDb): Map<String, String> = db.aprendizaje().all().associate { it.palabra to it.categoria }
}
