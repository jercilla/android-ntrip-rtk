package es.gailen.rtk.datos.puntos

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PuntoMedido::class], version = 1, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {
    abstract fun puntoDao(): PuntoDao
}
