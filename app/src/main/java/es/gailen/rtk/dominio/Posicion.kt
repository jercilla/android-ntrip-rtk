package es.gailen.rtk.dominio

import java.time.Instant

/** Desviaciones típicas de posición (de la sentencia NMEA GST), en metros. */
data class Precision(
    val sigmaLat: Double,
    val sigmaLon: Double,
    val sigmaAltura: Double,
) {
    /** Precisión horizontal combinada (sqrt(σlat² + σlon²)), en metros. */
    val horizontal: Double get() = kotlin.math.hypot(sigmaLat, sigmaLon)
}

/**
 * Una solución de posición instantánea, normalizada e independiente de la fuente.
 * Las alturas son **elipsoidales** (ETRS89); la conversión a ortométrica queda fuera del MVP.
 */
data class Posicion(
    val tiempoUtc: Instant,
    val lat: Double?,                // null mientras no hay fix (calidad SIN_FIX)
    val lon: Double?,
    val alturaElipsoidal: Double?,   // m = altitud MSL + separación geoidal del GGA
    val calidad: CalidadFix,
    val satelites: Int? = null,
    val hdop: Double? = null,
    val edadCorrecciones: Double? = null,  // s, del campo GGA
    val rumboGrados: Double? = null,       // de RMC, si el receptor se mueve
    val velocidadMs: Double? = null,       // m/s, de RMC
    val precisionEstimada: Precision? = null,  // de GST si está disponible
) {
    /** True si hay coordenadas (hay algún tipo de fix). */
    val tieneCoordenadas: Boolean get() = lat != null && lon != null
}
