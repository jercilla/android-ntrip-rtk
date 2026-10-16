package es.gailen.rtk.datos.fuente

import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fuente marcador de posición para tipos aún no implementados (p. ej. USB-serie, Hito 8).
 * Se queda en estado ERROR al conectar y no emite posiciones.
 */
class FuenteNoDisponible(val motivo: String) : FuentePosicion {

    private val _estado = MutableStateFlow(EstadoFuente.DESCONECTADA)
    override val estado: StateFlow<EstadoFuente> = _estado.asStateFlow()

    override val posiciones: Flow<Posicion> = emptyFlow()
    override val nmeaCrudo: Flow<String> = emptyFlow()

    override suspend fun conectar() {
        _estado.value = EstadoFuente.ERROR
    }

    override suspend fun desconectar() {
        _estado.value = EstadoFuente.DESCONECTADA
    }
}
