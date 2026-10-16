package es.gailen.rtk.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Posición geodésica: latitud/longitud en grados y altura elipsoidal en metros. */
data class Geodesica(val lat: Double, val lon: Double, val altura: Double)

/** Coordenadas geocéntricas ECEF (m). */
data class Ecef(val x: Double, val y: Double, val z: Double) {
    fun distanciaA(otro: Ecef): Double =
        sqrt((x - otro.x) * (x - otro.x) + (y - otro.y) * (y - otro.y) + (z - otro.z) * (z - otro.z))
}

/**
 * Conversiones entre ECEF y geodésicas sobre el elipsoide GRS80/WGS84 (idénticos a
 * efectos prácticos). Se usan para decodificar la posición de la base (RTCM 1005/1006)
 * y para la distancia de la línea base.
 */
object GeodesiaEcef {

    private const val A = 6_378_137.0                 // semieje mayor (m)
    private const val F = 1.0 / 298.257222101         // achatamiento GRS80
    private const val E2 = F * (2 - F)                // excentricidad al cuadrado

    fun geodesicaAEcef(g: Geodesica): Ecef {
        val lat = Math.toRadians(g.lat)
        val lon = Math.toRadians(g.lon)
        val n = A / sqrt(1 - E2 * sin(lat) * sin(lat))
        val x = (n + g.altura) * cos(lat) * cos(lon)
        val y = (n + g.altura) * cos(lat) * sin(lon)
        val z = (n * (1 - E2) + g.altura) * sin(lat)
        return Ecef(x, y, z)
    }

    /** ECEF → geodésicas por el método iterativo de Bowring (converge en pocas vueltas). */
    fun ecefAGeodesica(e: Ecef): Geodesica {
        val lon = atan2(e.y, e.x)
        val p = sqrt(e.x * e.x + e.y * e.y)
        if (p < 1e-9) {
            // Polos
            val lat = if (e.z >= 0) 90.0 else -90.0
            return Geodesica(lat, Math.toDegrees(lon), kotlin.math.abs(e.z) - A * sqrt(1 - E2))
        }
        var lat = atan2(e.z, p * (1 - E2))
        var altura = 0.0
        repeat(6) {
            val n = A / sqrt(1 - E2 * sin(lat) * sin(lat))
            altura = p / cos(lat) - n
            lat = atan2(e.z, p * (1 - E2 * n / (n + altura)))
        }
        return Geodesica(Math.toDegrees(lat), Math.toDegrees(lon), altura)
    }
}
