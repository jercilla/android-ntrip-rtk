package es.gailen.rtk.ui.estado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.fuente.FabricaFuente
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado observable de la pantalla de estado en vivo. */
data class EstadoUi(
    val tipoFuente: FuentePosicionTipo = FuentePosicionTipo.SIMULADA,
    val estadoFuente: EstadoFuente = EstadoFuente.DESCONECTADA,
    val posicion: Posicion? = null,
)

@HiltViewModel
class EstadoViewModel @Inject constructor(
    private val fabrica: FabricaFuente,
    private val registro: RegistroDebugHttp,
    ajustesRepo: AjustesRepository,
) : ViewModel() {

    val tipoFuente: StateFlow<FuentePosicionTipo> = ajustesRepo.ajustes
        .map { it.fuente }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuentePosicionTipo.SIMULADA)

    private val _estadoFuente = MutableStateFlow(EstadoFuente.DESCONECTADA)
    val estadoFuente: StateFlow<EstadoFuente> = _estadoFuente.asStateFlow()

    private val _posicion = MutableStateFlow<Posicion?>(null)
    val posicion: StateFlow<Posicion?> = _posicion.asStateFlow()

    private var fuente: FuentePosicion? = null
    private var tipoActual: FuentePosicionTipo? = null
    private val jobsFuente = mutableListOf<Job>()

    init {
        viewModelScope.launch {
            tipoFuente.collect { tipo -> configurar(tipo) }
        }
    }

    /** Cambia a la fuente [tipo]; la conexión del GPS la dispara la UI tras conceder permiso. */
    private suspend fun configurar(tipo: FuentePosicionTipo) {
        if (tipo == tipoActual) return
        tipoActual = tipo
        cerrarFuente()
        registro.registra("fuente", "cambio a tipo=$tipo")
        val nueva = fabrica.crear(tipo)
        fuente = nueva
        jobsFuente += viewModelScope.launch {
            nueva.estado.collect {
                _estadoFuente.value = it
                registro.registra("fuente", "estado=$it (tipo=$tipo)")
            }
        }
        jobsFuente += viewModelScope.launch {
            nueva.posiciones.collect {
                _posicion.value = it
                registro.registra(
                    "pos",
                    "cal=${it.calidad} lat=${it.lat} lon=${it.lon} alt=${it.alturaElipsoidal} " +
                        "sat=${it.satelites} hdop=${it.hdop} σH=${it.precisionEstimada?.horizontal}",
                )
            }
        }
        if (tipo != FuentePosicionTipo.GPS_MOVIL) conectar()
    }

    /** Conecta la fuente actual. Seguro de llamar varias veces. */
    fun conectar() {
        registro.registra("fuente", "conectar() solicitado tipo=$tipoActual")
        viewModelScope.launch { runCatching { fuente?.conectar() }.onFailure { registro.registra("fuente", "conectar() excepción: ${it.message}") } }
    }

    private suspend fun cerrarFuente() {
        jobsFuente.forEach { it.cancel() }
        jobsFuente.clear()
        runCatching { fuente?.desconectar() }
        _posicion.value = null
        _estadoFuente.value = EstadoFuente.DESCONECTADA
    }

    override fun onCleared() {
        val f = fuente ?: return
        // viewModelScope ya se cancela aquí; liberamos la fuente en un scope efímero.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { f.desconectar() }
    }
}
