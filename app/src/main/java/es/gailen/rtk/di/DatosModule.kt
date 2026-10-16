package es.gailen.rtk.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.gailen.rtk.datos.ajustes.dataStore
import es.gailen.rtk.datos.puntos.BaseDatos
import es.gailen.rtk.datos.puntos.PuntoDao
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Provee las dependencias de la capa de datos. El cifrador y el repositorio usan
 * inyección por constructor (@Inject), así que aquí solo hace falta el DataStore y Json.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatosModule {

    @Provides
    @Singleton
    fun proporcionarDataStore(@ApplicationContext contexto: Context): DataStore<Preferences> =
        contexto.dataStore

    @Provides
    @Singleton
    fun proporcionarJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun proporcionarBaseDatos(@ApplicationContext contexto: Context): BaseDatos =
        Room.databaseBuilder(contexto, BaseDatos::class.java, "rtk.db").build()

    @Provides
    fun proporcionarPuntoDao(bd: BaseDatos): PuntoDao = bd.puntoDao()
}

