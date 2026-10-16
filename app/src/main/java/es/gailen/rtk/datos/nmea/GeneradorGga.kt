package es.gailen.rtk.datos.nmea

import es.gailen.rtk.dominio.CalidadFix
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * Genera una sentencia GGA válida (con checksum) a partir de una posición. La usa la
 * [es.gailen.rtk.dominio.FuentePosicion] del GPS del móvil para poder enviar GGA al
 * caster NTRIP en los puntos de montaje que lo requieren.
 *
 * La altura elipsoidal se escribe como altitud con separación geoidal 0; para el envío
 * de GGA al caster (selección de estación/VRS) basta con lat/lon.
 */
object GeneradorGga {

    fun generar(
        lat: Double,
        lon: Double,
        alturaElipsoidal: Double?,
        satelites: Int?,
        hdop: Double?,
        calidad: CalidadFix,
        instante: Instant,
        talker: String = "GP",
    ): String {
        val hora = instante.atOffset(ZoneOffset.UTC)
        val hhmmss = String.format(Locale.US, "%02d%02d%05.2f", hora.hour, hora.minute, hora.second + hora.nano / 1e9)
        val (latStr, latHemi) = formatearLat(lat)
        val (lonStr, lonHemi) = formatearLon(lon)
        val alt = alturaElipsoidal ?: 0.0
        val payload = buildString {
            append("${talker}GGA,")
            append("$hhmmss,")
            append("$latStr,$latHemi,")
            append("$lonStr,$lonHemi,")
            append("${calidad.codigoGga},")
            append(String.format(Locale.US, "%02d,", satelites ?: 0))
            append(String.format(Locale.US, "%.1f,", hdop ?: 0.0))
            append(String.format(Locale.US, "%.2f,M,", alt))
            append("0.0,M,,")  // separación geoidal 0, edad y estación vacías
        }
        return "\$$payload*%02X".format(ParserNmea.checksumCalculado(payload))
    }

    private fun formatearLat(lat: Double): Pair<String, String> {
        val hemi = if (lat >= 0) "N" else "S"
        val a = abs(lat)
        val grados = a.toInt()
        val minutos = (a - grados) * 60
        return String.format(Locale.US, "%02d%08.5f", grados, minutos) to hemi
    }

    private fun formatearLon(lon: Double): Pair<String, String> {
        val hemi = if (lon >= 0) "E" else "W"
        val a = abs(lon)
        val grados = a.toInt()
        val minutos = (a - grados) * 60
        return String.format(Locale.US, "%03d%08.5f", grados, minutos) to hemi
    }
}
