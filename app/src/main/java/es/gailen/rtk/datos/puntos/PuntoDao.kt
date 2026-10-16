package es.gailen.rtk.datos.puntos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PuntoDao {

    @Query("SELECT * FROM puntos ORDER BY fechaHoraUtc DESC")
    fun observarTodos(): Flow<List<PuntoMedido>>

    @Query("SELECT * FROM puntos WHERE id = :id")
    suspend fun porId(id: Long): PuntoMedido?

    @Insert
    suspend fun insertar(punto: PuntoMedido): Long

    @Update
    suspend fun actualizar(punto: PuntoMedido)

    @Delete
    suspend fun borrar(punto: PuntoMedido)

    @Query("DELETE FROM puntos WHERE id = :id")
    suspend fun borrarPorId(id: Long)
}
