package es.gailen.rtk.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.dominio.AjustesApp
import es.gailen.rtk.dominio.CredencialesNtrip
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.PerfilNtrip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado observable de la pantalla de ajustes. */
data class EstadoAjustes(
    val ajustes: AjustesApp = AjustesApp(),
    val perfiles: List<PerfilNtrip> = emptyList(),
    val idsConCredenciales: Set<String> = emptySet(),
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val repo: AjustesRepository,
) : ViewModel() {

    val estado: StateFlow<EstadoAjustes> =
        combine(repo.ajustes, repo.perfiles, repo.idsConCredenciales) { ajustes, perfiles, ids ->
            EstadoAjustes(ajustes, perfiles, ids)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadoAjustes(),
        )

    // --- Ajustes generales ---
    fun cambiarFuente(tipo: FuentePosicionTipo) = lanzar { repo.actualizarFuente(tipo) }
    fun cambiarBaudrate(baudrate: Int) = lanzar { repo.actualizarBaudrate(baudrate) }
    fun cambiarAlturaJalon(metros: Double) = lanzar { repo.actualizarAlturaJalon(metros) }
    fun cambiarDuracion(segundos: Int) = lanzar { repo.actualizarDuracionMedicion(segundos) }
    fun cambiarAceptarFloat(aceptar: Boolean) = lanzar { repo.actualizarAceptarFloat(aceptar) }

    // --- Perfiles ---
    fun seleccionarPerfilActivo(id: String) = lanzar { repo.actualizarPerfilActivo(id) }
    fun eliminarPerfil(id: String) = lanzar { repo.eliminarPerfil(id) }

    /**
     * Guarda la configuración del perfil y, si procede, sus credenciales cifradas.
     * Si [usuario] y [password] vienen vacíos, no se tocan las credenciales existentes.
     * Si solo la contraseña viene vacía pero ya había credenciales, se conserva la anterior.
     */
    fun guardarPerfil(perfil: PerfilNtrip, usuario: String, password: String) = lanzar {
        repo.guardarPerfil(perfil)
        val hayEntrada = usuario.isNotBlank() || password.isNotBlank()
        if (hayEntrada) {
            val existentes = repo.leerCredenciales(perfil.id)
            val u = usuario.ifBlank { existentes?.usuario.orEmpty() }
            val p = password.ifBlank { existentes?.password.orEmpty() }
            if (u.isNotBlank()) {
                repo.guardarCredenciales(perfil.id, CredencialesNtrip(u, p))
            }
        }
    }

    fun borrarCredenciales(perfilId: String) = lanzar { repo.borrarCredenciales(perfilId) }

    /** Lee las credenciales para precargar el diálogo de edición (uso puntual). */
    suspend fun leerCredenciales(perfilId: String): CredencialesNtrip? =
        repo.leerCredenciales(perfilId)

    private fun lanzar(bloque: suspend () -> Unit) {
        viewModelScope.launch { bloque() }
    }
}
