package es.gailen.rtk.datos.nmea

import es.gailen.rtk.dominio.CalidadFix
import java.time.Instant

/**
 * Sentencia NMEA ya validada (checksum correcto) y parseada a campos tipados.
 * Los talkers esperados son GP, GN, GL, GA, GB, BD, GQ.
 */
sealed interface SentenciaNmea {
    val talker: String
    val tipo: String
}

/** GGA: posición, calidad, satélites, HDOP, altitud y edad de correcciones. */
data class SentenciaGga(
    override val talker: String,
    val horaUtc: String?,            // hhmmss.ss tal cual (sin fecha)
    val lat: Double?,                // grados decimales con signo
    val lon: Double?,
    val calidad: CalidadFix,
    val satelites: Int?,
    val hdop: Double?,
    val altitudMsl: Double?,         // m
    val separacionGeoidal: Double?,  // m
    val edadCorrecciones: Double?,   // s
    val idEstacion: String?,
) : SentenciaNmea {
    override val tipo get() = "GGA"

    /** Altura elipsoidal = altitud MSL + separación geoidal (si ambas existen). */
    val alturaElipsoidal: Double?
        get() = if (altitudMsl != null && separacionGeoidal != null)
            altitudMsl + separacionGeoidal else null
}

/** RMC: fecha/hora, validez, rumbo y velocidad sobre el suelo. */
data class SentenciaRmc(
    override val talker: String,
    val tiempoUtc: Instant?,
    val valido: Boolean,
    val lat: Double?,
    val lon: Double?,
    val velocidadNudos: Double?,
    val rumboGrados: Double?,
) : SentenciaNmea {
    override val tipo get() = "RMC"

    /** Velocidad en m/s (1 nudo = 0,514444 m/s). */
    val velocidadMs: Double? get() = velocidadNudos?.let { it * 0.514444 }
}

/** GST: desviaciones típicas de lat, lon y altura (m). */
data class SentenciaGst(
    override val talker: String,
    val sigmaLat: Double?,
    val sigmaLon: Double?,
    val sigmaAltura: Double?,
) : SentenciaNmea {
    override val tipo get() = "GST"
}

/** GSA: DOPs y satélites usados en la solución. */
data class SentenciaGsa(
    override val talker: String,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
    val satelitesUsados: List<Int>,
) : SentenciaNmea {
    override val tipo get() = "GSA"
}

/** Un satélite dentro de una sentencia GSV. */
data class SateliteGsv(
    val prn: Int,
    val elevacion: Int?,   // grados
    val azimut: Int?,      // grados
    val snr: Int?,         // dB-Hz (vacío si no se sigue)
)

/** GSV: satélites a la vista (puede venir repartida en varios mensajes). */
data class SentenciaGsv(
    override val talker: String,
    val mensajesTotales: Int?,
    val numeroMensaje: Int?,
    val satelitesALaVista: Int?,
    val satelites: List<SateliteGsv>,
) : SentenciaNmea {
    override val tipo get() = "GSV"
}
