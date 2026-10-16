package es.gailen.rtk.ui.diagnostico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.ntrip.EstadisticasNtrip
import es.gailen.rtk.datos.ntrip.EstadoNtrip
import es.gailen.rtk.datos.ntrip.GestorNtrip
import es.gailen.rtk.datos.rtcm.ResumenRtcm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nombre y montaje del perfil NTRIP activo, para mostrarlos. */
data class PerfilResumen(val nombre: String, val montaje: String, val host: String)

@HiltViewModel
class DiagnosticoViewModel @Inject constructor(
    private val gestorNtrip: GestorNtrip,
    gestorFuente: GestorFuente,
    ajustesRepo: AjustesRepository,
) : ViewModel() {

    val estadoNtrip: StateFlow<EstadoNtrip> = gestorNtrip.estado
    val estadisticas: StateFlow<EstadisticasNtrip> = gestorNtrip.estadisticas
    val resumen: StateFlow<ResumenRtcm> = gestorNtrip.resumen
    val distanciaBase: StateFlow<Double?> = gestorNtrip.distanciaBase

    private val _consola = MutableStateFlow<List<String>>(emptyList())
    val consola: StateFlow<List<String>> = _consola.asStateFlow()

    val perfil: StateFlow<PerfilResumen?> =
        combine(ajustesRepo.ajustes, ajustesRepo.perfiles) { a, perfiles ->
            perfiles.firstOrNull { it.id == a.perfilNtripActivoId }?.let {
                PerfilResumen(it.nombre, it.puntoMontaje, "${it.host}:${it.puerto}")
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            gestorFuente.nmea.collect { linea ->
                _consola.value = (_consola.value + linea).takeLast(MAX_CONSOLA)
            }
        }
    }

    fun conectar() = gestorNtrip.conectar()
    fun desconectar() = gestorNtrip.desconectar()

    private companion object {
        const val MAX_CONSOLA = 60
    }
}
