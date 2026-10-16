package es.gailen.rtk.datos.export

import es.gailen.rtk.datos.puntos.PuntoMedido
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exporta puntos a CSV. Decimales con punto (Locale.US) para que QGIS/hojas de cálculo los
 * lean bien. Sistema de referencia **ETRS89**; alturas **elipsoidales**.
 */
object ExportadorCsv {

    private val CABECERA = listOf(
        "id", "nombre", "notas", "lat", "lon", "altura_elipsoidal", "altura_jalon",
        "cota_suelo", "sigma_n", "sigma_e", "sigma_u", "epocas", "porcentaje_fix",
        "calidad", "perfil_ntrip", "punto_montaje", "distancia_base", "fecha_utc",
        "sistema_referencia",
    )

    private val FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    fun generar(puntos: List<PuntoMedido>): String {
        val sb = StringBuilder()
        sb.append(CABECERA.joinToString(",")).append("\r\n")
        for (p in puntos) {
            val fila = listOf(
                p.id.toString(),
                p.nombre,
                p.notas,
                num(p.lat, 9),
                num(p.lon, 9),
                num(p.alturaElipsoidal, 3),
                num(p.alturaJalon, 3),
                num(p.cotaSuelo, 3),
                num(p.sigmaNorte, 4),
                num(p.sigmaEste, 4),
                num(p.sigmaAltura, 4),
                p.epocas.toString(),
                num(p.porcentajeFix, 1),
                p.calidad,
                p.perfilNtrip.orEmpty(),
                p.puntoMontaje.orEmpty(),
                p.distanciaBase?.let { num(it, 3) }.orEmpty(),
                FECHA.format(Instant.ofEpochMilli(p.fechaHoraUtc)),
                p.sistemaReferencia,
            )
            sb.append(fila.joinToString(",") { escapar(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun num(v: Double, decimales: Int): String = String.format(Locale.US, "%.${decimales}f", v)

    /** Entrecomilla si el campo contiene coma, comillas o salto de línea (RFC 4180). */
    private fun escapar(campo: String): String =
        if (campo.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + campo.replace("\"", "\"\"") + "\""
        } else {
            campo
        }
}
