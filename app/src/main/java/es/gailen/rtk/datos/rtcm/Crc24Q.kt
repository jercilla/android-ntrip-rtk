package es.gailen.rtk.datos.rtcm

/**
 * CRC-24Q (Qualcomm), el usado por RTCM 3: polinomio 0x1864CFB, valor inicial 0,
 * sin reflexión ni XOR final. Se calcula sobre el preámbulo + cabecera + payload.
 */
object Crc24Q {

    private const val POLINOMIO = 0x1864CFB

    /** Calcula el CRC-24 de los primeros [longitud] bytes de [datos]. */
    fun calcular(datos: ByteArray, desde: Int = 0, longitud: Int = datos.size): Int {
        var crc = 0
        for (i in desde until desde + longitud) {
            crc = crc xor ((datos[i].toInt() and 0xFF) shl 16)
            repeat(8) {
                crc = crc shl 1
                if (crc and 0x1000000 != 0) crc = crc xor POLINOMIO
            }
        }
        return crc and 0xFFFFFF
    }
}
