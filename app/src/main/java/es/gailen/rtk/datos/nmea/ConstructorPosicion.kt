package es.gailen.rtk.datos.nmea

import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.dominio.Precision
import java.time.Instant

/**
 * Combina las sentencias de una misma época NMEA (GGA + RMC + GST) en una [Posicion].
 * El GGA es obligatorio (aporta calidad y posición); RMC aporta fecha/hora, rumbo y
 * velocidad; GST aporta la precisión estimada.
 */
object ConstructorPosicion {

    /**
     * @param sentencias sentencias ya parseadas de una época.
     * @param tiempoPorDefecto instante a usar si no hay RMC con fecha (p. ej. el reloj del sistema).
     * @return la [Posicion] resultante, o null si no hay GGA en la época.
     */
    fun construir(sentencias: List<SentenciaNmea>, tiempoPorDefecto: Instant = Instant.now()): Posicion? {
        val gga = sentencias.filterIsInstance<SentenciaGga>().lastOrNull() ?: return null
        val rmc = sentencias.filterIsInstance<SentenciaRmc>().lastOrNull()
        val gst = sentencias.filterIsInstance<SentenciaGst>().lastOrNull()

        val precision = gst?.let {
            if (it.sigmaLat != null && it.sigmaLon != null)
                Precision(it.sigmaLat, it.sigmaLon, it.sigmaAltura ?: Double.NaN)
            else null
        }

        return Posicion(
            tiempoUtc = rmc?.tiempoUtc ?: tiempoPorDefecto,
            lat = gga.lat,
            lon = gga.lon,
            alturaElipsoidal = gga.alturaElipsoidal,
            calidad = gga.calidad,
            satelites = gga.satelites,
            hdop = gga.hdop,
            edadCorrecciones = gga.edadCorrecciones,
            rumboGrados = rmc?.rumboGrados,
            velocidadMs = rmc?.velocidadMs,
            precisionEstimada = precision,
        )
    }
}
