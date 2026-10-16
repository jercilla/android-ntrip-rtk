package es.gailen.rtk.datos.ajustes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Nombre del fichero de preferencias. Debe coincidir con las reglas de backup del manifest. */
const val NOMBRE_DATASTORE = "ajustes_rtk"

/** Único DataStore de preferencias de la app, ligado al contexto de aplicación. */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = NOMBRE_DATASTORE)
