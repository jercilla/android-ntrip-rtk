package es.gailen.rtk.ui.puntos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.puntos.PuntoMedido
import es.gailen.rtk.datos.puntos.PuntosRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PuntosViewModel @Inject constructor(
    private val repo: PuntosRepository,
) : ViewModel() {

    val puntos: StateFlow<List<PuntoMedido>> =
        repo.puntos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun borrar(id: Long) = viewModelScope.launch { repo.borrar(id) }

    fun actualizar(punto: PuntoMedido, nombre: String, notas: String) = viewModelScope.launch {
        repo.actualizar(punto.copy(nombre = nombre.ifBlank { punto.nombre }, notas = notas))
    }
}
