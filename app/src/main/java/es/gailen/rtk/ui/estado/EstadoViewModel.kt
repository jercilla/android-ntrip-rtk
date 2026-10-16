package es.gailen.rtk.ui.estado

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Expone a la pantalla de Estado la fuente activa, que gestiona [GestorFuente]
 * (de ámbito de aplicación, compartida con Diagnóstico).
 */
@HiltViewModel
class EstadoViewModel @Inject constructor(
    private val gestor: GestorFuente,
) : ViewModel() {

    val tipoFuente: StateFlow<FuentePosicionTipo> = gestor.tipo
    val estadoFuente: StateFlow<EstadoFuente> = gestor.estado
    val posicion: StateFlow<Posicion?> = gestor.posicion

    /** Conecta la fuente (la UI la llama para el GPS tras conceder el permiso). */
    fun conectar() = gestor.conectar()
}
