package es.gailen.rtk.datos.rtcm

import es.gailen.rtk.geo.Ecef
import es.gailen.rtk.geo.GeodesiaEcef

/**
 * Reensambla y valida tramas RTCM 3 a partir de un flujo de bytes fragmentado.
 * Encuadre: preámbulo 0xD3, 6 bits reservados + 10 bits de longitud, payload, CRC-24Q.
 * Si el CRC falla, resincroniza avanzando un byte y buscando el siguiente 0xD3.
 */
class ParserRtcm(private val maxBuffer: Int = 8_192) {

    private var buffer = ByteArray(0)

    /** Procesa bytes nuevos y devuelve las tramas completas y válidas encontradas. */
    fun agregar(datos: ByteArray, longitud: Int = datos.size): List<TramaRtcm> {
        buffer = if (buffer.isEmpty()) datos.copyOf(longitud) else buffer + datos.copyOf(longitud)
        val tramas = mutableListOf<TramaRtcm>()
        var i = 0
        while (true) {
            // Buscar el preámbulo.
            while (i < buffer.size && buffer[i].toInt() and 0xFF != PREAMBULO) i++
            if (i >= buffer.size) { i = buffer.size; break }
            if (buffer.size - i < 3) break  // faltan bytes de cabecera
            val longMsg = ((buffer[i + 1].toInt() and 0x03) shl 8) or (buffer[i + 2].toInt() and 0xFF)
            val total = 3 + longMsg + 3
            if (buffer.size - i < total) break  // trama incompleta: esperar más bytes
            val crcCalc = Crc24Q.calcular(buffer, i, 3 + longMsg)
            val crcTrama = ((buffer[i + 3 + longMsg].toInt() and 0xFF) shl 16) or
                ((buffer[i + 4 + longMsg].toInt() and 0xFF) shl 8) or
                (buffer[i + 5 + longMsg].toInt() and 0xFF)
            if (crcCalc == crcTrama) {
                val payload = buffer.copyOfRange(i + 3, i + 3 + longMsg)
                val tipo = ((payload[0].toInt() and 0xFF) shl 4) or ((payload[1].toInt() and 0xFF) ushr 4)
                tramas.add(TramaRtcm(tipo, payload))
                i += total
            } else {
                i++  // resincronizar
            }
        }
        // Compactar: conservar lo no consumido; evitar crecer sin límite ante basura.
        buffer = if (i >= buffer.size) ByteArray(0) else buffer.copyOfRange(i, buffer.size)
        if (buffer.size > maxBuffer) buffer = buffer.copyOfRange(buffer.size - maxBuffer, buffer.size)
        return tramas
    }

    fun reiniciar() {
        buffer = ByteArray(0)
    }

    companion object {
        const val PREAMBULO = 0xD3

        /** Lee el tipo de mensaje (primeros 12 bits del payload). */
        fun tipoDe(payload: ByteArray): Int =
            ((payload[0].toInt() and 0xFF) shl 4) or ((payload[1].toInt() and 0xFF) ushr 4)
    }
}

/** Decodifica la posición de la base de los mensajes 1005 y 1006. */
object DecodificadorRtcm {

    private const val RESOLUCION_ECEF = 1e-4  // 0,0001 m

    fun esEstacionBase(tipo: Int): Boolean = tipo == 1005 || tipo == 1006

    /** Devuelve la [EstacionBase] si la trama es 1005/1006; null en otro caso. */
    fun decodificarEstacion(trama: TramaRtcm): EstacionBase? {
        if (!esEstacionBase(trama.tipo)) return null
        val lector = LectorBits(trama.payload)
        lector.saltar(12)                       // DF002: número de mensaje
        val id = lector.leerU(12).toInt()        // DF003: id de estación
        lector.saltar(6)                         // DF021: año ITRF
        lector.saltar(4)                         // DF022/023/024/141: indicadores
        val x = lector.leerS(38) * RESOLUCION_ECEF
        lector.saltar(2)                         // DF142 + reservado
        val y = lector.leerS(38) * RESOLUCION_ECEF
        lector.saltar(2)                         // DF364: quarter cycle
        val z = lector.leerS(38) * RESOLUCION_ECEF
        val alturaAntena = if (trama.tipo == 1006) lector.leerU(16) * RESOLUCION_ECEF else null

        val ecef = Ecef(x, y, z)
        return EstacionBase(
            idEstacion = id,
            ecef = ecef,
            geodesica = GeodesiaEcef.ecefAGeodesica(ecef),
            alturaAntena = alturaAntena,
        )
    }
}
