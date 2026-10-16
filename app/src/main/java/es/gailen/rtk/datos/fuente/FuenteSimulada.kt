package es.gailen.rtk.datos.fuente

import android.content.Context
import es.gailen.rtk.datos.nmea.ConstructorPosicion
import es.gailen.rtk.datos.nmea.ParserNmea
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fuente que reproduce un fichero NMEA de `assets/` a 1 Hz, en bucle. El fichero está
 * organizado en épocas separadas por líneas en blanco; cada época se emite entera y
 * luego se espera 1 s. Permite recorrer los estados sin fix → autónomo → float → fix
 * sin hardware ni salir a la calle.
 */
class FuenteSimulada(
    private val context: Context,
    private val archivo: String = ARCHIVO_POR_DEFECTO,
    private val periodoMs: Long = 1_000L,
) : FuentePosicion {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _estado = MutableStateFlow(EstadoFuente.DESCONECTADA)
    override val estado: StateFlow<EstadoFuente> = _estado.asStateFlow()

    private val _posiciones = MutableSharedFlow<Posicion>(replay = 1, extraBufferCapacity = 8)
    override val posiciones: Flow<Posicion> = _posiciones.asSharedFlow()

    private val _nmea = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val nmeaCrudo: Flow<String> = _nmea.asSharedFlow()

    private var job: Job? = null

    override suspend fun conectar() {
        if (job?.isActive == true) return
        _estado.value = EstadoFuente.CONECTANDO
        val epocas = try {
            leerEpocas()
        } catch (e: Exception) {
            _estado.value = EstadoFuente.ERROR
            return
        }
        if (epocas.isEmpty()) {
            _estado.value = EstadoFuente.ERROR
            return
        }
        _estado.value = EstadoFuente.CONECTADA
        job = scope.launch {
            while (isActive) {
                for (epoca in epocas) {
                    if (!isActive) break
                    val sentencias = epoca.mapNotNull { linea ->
                        _nmea.emit(linea)
                        ParserNmea.parsear(linea)
                    }
                    ConstructorPosicion.construir(sentencias)?.let { _posiciones.emit(it) }
                    delay(periodoMs)
                }
            }
        }
    }

    override suspend fun desconectar() {
        job?.cancel()
        job = null
        _estado.value = EstadoFuente.DESCONECTADA
    }

    /** Lee el fichero y lo trocea en épocas (bloques separados por líneas en blanco). */
    private fun leerEpocas(): List<List<String>> {
        val lineas = context.assets.open(archivo).bufferedReader().use { it.readLines() }
        val epocas = mutableListOf<List<String>>()
        var actual = mutableListOf<String>()
        for (linea in lineas) {
            val l = linea.trim()
            if (l.isEmpty()) {
                if (actual.isNotEmpty()) {
                    epocas.add(actual)
                    actual = mutableListOf()
                }
            } else {
                actual.add(l)
            }
        }
        if (actual.isNotEmpty()) epocas.add(actual)
        return epocas
    }

    companion object {
        const val ARCHIVO_POR_DEFECTO = "simulacion_rtk.nmea"
    }
}
