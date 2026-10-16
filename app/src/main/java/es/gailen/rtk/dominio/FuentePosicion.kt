package es.gailen.rtk.dominio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Estado del ciclo de vida de una fuente de posición. */
enum class EstadoFuente {
    DESCONECTADA,
    CONECTANDO,
    CONECTADA,
    ERROR,
}

/**
 * Fuente de posición intercambiable (sección 5.1 del diseño). Implementaciones:
 * GPS del móvil, simulador NMEA y USB-serie (módulo GNSS). El cambio entre ellas
 * debe ser trivial para el resto de la app.
 */
interface FuentePosicion {
    /** Estado de conexión observable. */
    val estado: StateFlow<EstadoFuente>

    /** Posiciones normalizadas emitidas por la fuente (típicamente 1 Hz). */
    val posiciones: Flow<Posicion>

    /** NMEA crudo, para la consola de depuración y para reenviar GGA al caster. */
    val nmeaCrudo: Flow<String>

    suspend fun conectar()

    suspend fun desconectar()

    /** Reenvía correcciones RTCM3 al receptor. Solo tiene efecto en la fuente USB. */
    suspend fun enviarCorrecciones(rtcm: ByteArray) {}
}
