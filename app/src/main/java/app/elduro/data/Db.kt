package app.elduro.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun moneda(v: Moneda): String = v.name
    @TypeConverter fun moneda(v: String): Moneda = Moneda.valueOf(v)
    @TypeConverter fun forma(v: Forma): String = v.name
    @TypeConverter fun forma(v: String): Forma = Forma.valueOf(v)
    @TypeConverter fun tipo(v: Tipo): String = v.name
    @TypeConverter fun tipo(v: String): Tipo = Tipo.valueOf(v)
    @TypeConverter fun sentido(v: Sentido): String = v.name
    @TypeConverter fun sentido(v: String): Sentido = Sentido.valueOf(v)
    @TypeConverter fun frecuencia(v: Frecuencia): String = v.name
    @TypeConverter fun frecuencia(v: String): Frecuencia = Frecuencia.valueOf(v)
}

@Dao
interface DeudaDao {
    @Query("SELECT * FROM Deuda ORDER BY cerrada, fecha DESC") fun flow(): Flow<List<Deuda>>
    @Query("SELECT * FROM Deuda") fun all(): List<Deuda>
    @Query("SELECT * FROM Deuda WHERE uid = :uid") fun byUid(uid: String): Deuda?
    @Upsert fun upsert(d: Deuda): Long
    @Query("DELETE FROM Deuda WHERE id = :id") fun delete(id: Long)
}

@Dao
interface RecurrenteDao {
    @Query("SELECT * FROM Recurrente ORDER BY proxima") fun flow(): Flow<List<Recurrente>>
    @Query("SELECT * FROM Recurrente") fun all(): List<Recurrente>
    @Query("SELECT * FROM Recurrente WHERE uid = :uid") fun byUid(uid: String): Recurrente?
    @Upsert fun upsert(r: Recurrente): Long
    @Query("DELETE FROM Recurrente WHERE id = :id") fun delete(id: Long)
}

@Dao
interface PresupuestoDao {
    @Query("SELECT * FROM Presupuesto") fun flow(): Flow<List<Presupuesto>>
    @Query("SELECT * FROM Presupuesto") fun all(): List<Presupuesto>
    @Upsert fun upsert(p: Presupuesto)
    @Query("DELETE FROM Presupuesto WHERE libro = :libro AND categoria = :categoria") fun delete(libro: String, categoria: String)
    @Query("DELETE FROM Presupuesto") fun clear()
}

@Dao
interface FaltanteDao {
    @Query("SELECT * FROM Faltante ORDER BY comprado, creado") fun flow(): Flow<List<Faltante>>
    @Query("SELECT * FROM Faltante") fun all(): List<Faltante>
    @Query("SELECT * FROM Faltante WHERE uid = :uid") fun byUid(uid: String): Faltante?
    @Upsert fun upsert(f: Faltante): Long
    @Query("DELETE FROM Faltante WHERE id = :id") fun delete(id: Long)
}

/** Renombrar un libro toca todas las tablas que lo nombran. */
@Dao
interface LibroDao {
    @Query("UPDATE Cartera SET libro = :nuevo WHERE libro = :viejo") fun carteras(viejo: String, nuevo: String)
    @Query("UPDATE Deuda SET libro = :nuevo WHERE libro = :viejo") fun deudas(viejo: String, nuevo: String)
    @Query("UPDATE Recurrente SET libro = :nuevo WHERE libro = :viejo") fun recurrentes(viejo: String, nuevo: String)
    @Query("UPDATE Presupuesto SET libro = :nuevo WHERE libro = :viejo") fun presupuestos(viejo: String, nuevo: String)
    @Query("UPDATE Faltante SET libro = :nuevo WHERE libro = :viejo") fun faltantes(viejo: String, nuevo: String)
}

@Dao
interface CarteraDao {
    @Query("SELECT * FROM Cartera ORDER BY libro, orden, id") fun flow(): Flow<List<Cartera>>
    @Query("SELECT * FROM Cartera ORDER BY libro, orden, id") fun all(): List<Cartera>
    @Insert fun insert(c: Cartera): Long
    @Upsert fun upsert(c: Cartera): Long
    @Query("DELETE FROM Cartera WHERE id = :id") fun delete(id: Long)
}

@Dao
interface MovimientoDao {
    @Query("SELECT * FROM Movimiento ORDER BY fecha DESC, creado DESC") fun flow(): Flow<List<Movimiento>>
    @Query("SELECT * FROM Movimiento ORDER BY fecha, creado") fun all(): List<Movimiento>
    @Query("SELECT * FROM Movimiento WHERE uid = :uid") fun byUid(uid: String): Movimiento?
    @Query("SELECT COUNT(*) FROM Movimiento WHERE carteraId = :id OR carteraDestinoId = :id") fun countFor(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(m: Movimiento): Long
    @Upsert fun upsert(m: Movimiento)
    @Delete fun delete(m: Movimiento)
    @Query("DELETE FROM Movimiento WHERE uid IN (:uids)") fun deleteUids(uids: List<String>)
    @Query("SELECT * FROM Movimiento WHERE deudaId = :id ORDER BY fecha") fun deDeuda(id: Long): List<Movimiento>
    @Query("UPDATE Movimiento SET deudaId = NULL WHERE deudaId = :id") fun soltarDeuda(id: Long)
}

@Dao
interface TasaDao {
    @Query("SELECT * FROM Tasa") fun flow(): Flow<List<Tasa>>
    @Query("SELECT * FROM Tasa") fun all(): List<Tasa>
    @Upsert fun upsert(t: Tasa)
    @Upsert fun upsertAll(t: List<Tasa>)
    @Query("DELETE FROM Tasa WHERE fecha = :fecha AND fuente = :fuente AND origen = :origen")
    fun delete(fecha: String, fuente: String, origen: String)
}

@Dao
interface AprendizajeDao {
    @Query("SELECT * FROM Aprendizaje") fun all(): List<Aprendizaje>
    @Upsert fun upsertAll(a: List<Aprendizaje>)
}

@Database(
    entities = [Cartera::class, Movimiento::class, Tasa::class, Aprendizaje::class,
        Deuda::class, Recurrente::class, Presupuesto::class, Faltante::class],
    version = 2, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class ElDuroDb : RoomDatabase() {
    abstract fun carteras(): CarteraDao
    abstract fun movimientos(): MovimientoDao
    abstract fun tasas(): TasaDao
    abstract fun aprendizaje(): AprendizajeDao
    abstract fun deudas(): DeudaDao
    abstract fun recurrentes(): RecurrenteDao
    abstract fun presupuestos(): PresupuestoDao
    abstract fun faltantes(): FaltanteDao
    abstract fun libros(): LibroDao

    companion object {
        @Volatile private var inst: ElDuroDb? = null
        fun get(c: Context): ElDuroDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, ElDuroDb::class.java, "elduro.db").build().also { inst = it }
        }
    }
}
