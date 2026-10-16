package es.gailen.rtk.dominio.medicion

import es.gailen.rtk.dominio.Posicion
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Resultado de promediar un punto en estático: coordenada media y desviaciones típicas
 * en un sistema local ENU (norte/este/altura) alrededor del punto medio, en metros.
 */
data class ResultadoMedicion(
    val lat: Double,
    val lon: Double,
    val alturaElipsoidal: Double,
    val sigmaNorte: Double,
    val sigmaEste: Double,
    val sigmaAltura: Double,
    val epocasUsadas: Int,
    val epocasTotales: Int,
) {
    /** Porcentaje de épocas aceptadas (fix) sobre el total ofrecido durante la medición. */
    val porcentajeFix: Double
        get() = if (epocasTotales == 0) 0.0 else epocasUsadas * 100.0 / epocasTotales

    /** Desviación típica horizontal combinada (m). */
    val sigmaHorizontal: Double get() = sqrt(sigmaNorte * sigmaNorte + sigmaEste * sigmaEste)
}

/**
 * Promedia una lista de posiciones (ya filtradas por calidad) en coordenada media y
 * desviaciones típicas ENU. Válido para distancias muy cortas (error << cm a cientos de m).
 */
object CalculoMedicion {

    private const val R = 6_378_137.0  // radio terrestre (m)

    fun calcular(posiciones: List<Posicion>, epocasTotales: Int): ResultadoMedicion? {
        val puntos = posiciones.filter { it.lat != null && it.lon != null }
        if (puntos.isEmpty()) return null

        val latMedia = puntos.map { it.lat!! }.average()
        val lonMedia = puntos.map { it.lon!! }.average()
        val alturas = puntos.mapNotNull { it.alturaElipsoidal }
        val altMedia = if (alturas.isEmpty()) 0.0 else alturas.average()

        val cosLat = cos(Math.toRadians(latMedia))
        val norte = puntos.map { Math.toRadians(it.lat!! - latMedia) * R }
        val este = puntos.map { Math.toRadians(it.lon!! - lonMedia) * R * cosLat }
        val up = alturas.map { it - altMedia }

        return ResultadoMedicion(
            lat = latMedia,
            lon = lonMedia,
            alturaElipsoidal = altMedia,
            sigmaNorte = desviacionTipica(norte),
            sigmaEste = desviacionTipica(este),
            sigmaAltura = desviacionTipica(up),
            epocasUsadas = puntos.size,
            epocasTotales = epocasTotales,
        )
    }

    /** Desviación típica muestral (n-1). 0 si hay menos de 2 muestras. */
    private fun desviacionTipica(valores: List<Double>): Double {
        if (valores.size < 2) return 0.0
        val media = valores.average()
        val suma = valores.sumOf { (it - media) * (it - media) }
        return sqrt(suma / (valores.size - 1))
    }
}
