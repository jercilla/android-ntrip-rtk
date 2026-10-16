package es.gailen.rtk.datos.servicio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Arranca/para el [ServicioSesion] y expone si la sesión en primer plano está activa. */
@Singleton
class ControladorSesion @Inject constructor(
    @ApplicationContext private val contexto: Context,
) {
    private val _activa = MutableStateFlow(false)
    val activa: StateFlow<Boolean> = _activa.asStateFlow()

    fun iniciar() {
        ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioSesion::class.java))
    }

    fun detener() {
        contexto.stopService(Intent(contexto, ServicioSesion::class.java))
    }

    /** Lo llama el propio servicio al crearse/destruirse. */
    internal fun marcar(activa: Boolean) {
        _activa.value = activa
    }
}
