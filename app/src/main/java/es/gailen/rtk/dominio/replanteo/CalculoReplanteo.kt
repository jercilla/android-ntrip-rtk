package es.gailen.rtk.dominio.replanteo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Guía de replanteo desde la posición actual hasta el objetivo. Desplazamientos en metros
 * (norte +N/−S, este +E/−O), distancia horizontal, rumbo 0-360° desde el norte y desnivel
 * (positivo = el objetivo está por encima).
 */
data class GuiaReplanteo(
    val norte: Double,
    val este: Double,
    val distanciaHorizontal: Double,
    val rumbo: Double,
    val desnivel: Double?,
)

/**
 * Cálculo de replanteo para distancias cortas (error muy inferior al cm a cientos de metros),
 * con la fórmula del documento de diseño (sección 6.3).
 */
object CalculoReplanteo {

    private const val R = 6_378_137.0

    fun guia(
        latActual: Double,
        lonActual: Double,
        alturaActual: Double?,
        latObjetivo: Double,
        lonObjetivo: Double,
        alturaObjetivo: Double?,
    ): GuiaReplanteo {
        val latMedia = Math.toRadians((latActual + latObjetivo) / 2)
        val norte = Math.toRadians(latObjetivo - latActual) * R
        val este = Math.toRadians(lonObjetivo - lonActual) * R * cos(latMedia)
        val distancia = hypot(norte, este)
        val rumbo = (Math.toDegrees(atan2(este, norte)) + 360) % 360
        val desnivel = if (alturaActual != null && alturaObjetivo != null)
            alturaObjetivo - alturaActual else null
        return GuiaReplanteo(norte, este, distancia, rumbo, desnivel)
    }
}
