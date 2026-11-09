package app.elduro.data

import android.content.Context
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

@Database(entities = [Cartera::class, Movimiento::class, Tasa::class, Aprendizaje::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ElDuroDb : RoomDatabase() {
    abstract fun carteras(): CarteraDao
    abstract fun movimientos(): MovimientoDao
    abstract fun tasas(): TasaDao
    abstract fun aprendizaje(): AprendizajeDao

    companion object {
        @Volatile private var inst: ElDuroDb? = null
        fun get(c: Context): ElDuroDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, ElDuroDb::class.java, "elduro.db").build().also { inst = it }
        }
    }
}
