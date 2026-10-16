package es.gailen.rtk.datos.rtcm

import es.gailen.rtk.geo.Ecef
import es.gailen.rtk.geo.Geodesica

/**
 * Una trama RTCM 3 validada (CRC correcto). Solo para inspección/diagnóstico: el RTK lo
 * calcula el módulo, y el reenvío al receptor se hace con los bytes crudos, sin reencapsular.
 */
data class TramaRtcm(
    val tipo: Int,
    val payload: ByteArray,
) {
    /** Tamaño total de la trama en el cable (preámbulo + cabecera + payload + CRC). */
    val longitudTotal: Int get() = 3 + payload.size + 3

    override fun equals(other: Any?): Boolean =
        other is TramaRtcm && tipo == other.tipo && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * tipo + payload.contentHashCode()
}

/** Posición de la estación base decodificada de un mensaje RTCM 1005/1006. */
data class EstacionBase(
    val idEstacion: Int,
    val ecef: Ecef,
    val geodesica: Geodesica,
    val alturaAntena: Double?,  // m, solo en 1006
)
