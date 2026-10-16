package es.gailen.rtk.datos.fuente

import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Propietario único (de ámbito de aplicación) de la [FuentePosicion] activa. Observa el
 * tipo de fuente de los ajustes y la conmuta, y expone la posición, el estado y el NMEA
 * para que los compartan Estado y Diagnóstico. Prepara el terreno para el servicio en
 * primer plano del Hito 4.
 */
@Singleton
class GestorFuente @Inject constructor(
    private val fabrica: FabricaFuente,
    private val registro: RegistroDebugHttp,
    ajustesRepo: AjustesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tipo: StateFlow<FuentePosicionTipo> = ajustesRepo.ajustes
        .map { it.fuente }
        .stateIn(scope, SharingStarted.Eagerly, FuentePosicionTipo.SIMULADA)

    private val _estado = MutableStateFlow(EstadoFuente.DESCONECTADA)
    val estado: StateFlow<EstadoFuente> = _estado.asStateFlow()

    private val _posicion = MutableStateFlow<Posicion?>(null)
    val posicion: StateFlow<Posicion?> = _posicion.asStateFlow()

    private val _nmea = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 128)
    val nmea: SharedFlow<String> = _nmea.asSharedFlow()

    private var fuente: FuentePosicion? = null
    private var tipoActual: FuentePosicionTipo? = null
    private val jobs = mutableListOf<Job>()

    init {
        scope.launch { tipo.collect { configurar(it) } }
    }

    private suspend fun configurar(nuevo: FuentePosicionTipo) {
        if (nuevo == tipoActual) return
        tipoActual = nuevo
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { fuente?.desconectar() }
        _posicion.value = null
        _estado.value = EstadoFuente.DESCONECTADA

        val nueva = fabrica.crear(nuevo)
        fuente = nueva
        jobs += scope.launch { nueva.estado.collect { _estado.value = it } }
        jobs += scope.launch { nueva.posiciones.collect { _posicion.value = it } }
        jobs += scope.launch { nueva.nmeaCrudo.collect { _nmea.emit(it) } }
        if (nuevo != FuentePosicionTipo.GPS_MOVIL) conectar()
    }

    /** Conecta la fuente actual (el GPS lo dispara la UI tras conceder permiso). */
    fun conectar() {
        registro.registra("fuente", "conectar() tipo=$tipoActual")
        scope.launch { runCatching { fuente?.conectar() } }
    }

    /** Reenvía correcciones RTCM al receptor (solo efectivo en la fuente USB). */
    suspend fun enviarCorrecciones(rtcm: ByteArray) {
        runCatching { fuente?.enviarCorrecciones(rtcm) }
    }
}
