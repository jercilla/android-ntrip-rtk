package es.gailen.rtk.datos.rtcm

/**
 * Lee campos de bits MSB-first de un array de bytes, como exige RTCM 3.
 * [posicion] es el desplazamiento actual en bits desde el inicio de [datos].
 */
class LectorBits(private val datos: ByteArray, private var posicion: Int = 0) {

    /** Lee [n] bits (1..64) como entero sin signo. */
    fun leerU(n: Int): Long {
        var valor = 0L
        repeat(n) {
            val byteIdx = posicion ushr 3
            val bitIdx = 7 - (posicion and 7)
            val bit = (datos[byteIdx].toInt() ushr bitIdx) and 1
            valor = (valor shl 1) or bit.toLong()
            posicion++
        }
        return valor
    }

    /** Lee [n] bits como entero con signo (complemento a dos). */
    fun leerS(n: Int): Long {
        val sinSigno = leerU(n)
        val bitSigno = 1L shl (n - 1)
        return if (sinSigno and bitSigno != 0L) sinSigno - (1L shl n) else sinSigno
    }

    fun saltar(n: Int) {
        posicion += n
    }
}
