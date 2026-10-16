package es.gailen.rtk.datos.nmea

import es.gailen.rtk.dominio.CalidadFix
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.Instant

/**
 * Parser de sentencias NMEA 0183 (una línea completa por llamada). Valida el checksum
 * y traduce los campos a tipos. No reensambla bytes: para eso está [EnsambladorNmea].
 */
object ParserNmea {

    /** Talkers GNSS admitidos. */
    private val TALKERS = setOf("GP", "GN", "GL", "GA", "GB", "BD", "GQ")

    /** XOR de los caracteres del payload (entre `$`/`!` y `*`). */
    fun checksumCalculado(payload: String): Int {
        var c = 0
        for (ch in payload) c = c xor ch.code
        return c
    }

    /** Comprueba el formato `$...*HH` y que el checksum coincida. */
    fun validarChecksum(linea: String): Boolean {
        val l = linea.trim()
        if (l.isEmpty() || (l[0] != '$' && l[0] != '!')) return false
        val ast = l.lastIndexOf('*')
        if (ast < 1 || ast + 3 > l.length) return false
        val esperado = l.substring(ast + 1, ast + 3).toIntOrNull(16) ?: return false
        return checksumCalculado(l.substring(1, ast)) == esperado
    }

    /**
     * Parsea una línea. Devuelve null si el checksum falla, el formato es inválido,
     * el talker no es GNSS o el tipo de sentencia no se reconoce.
     */
    fun parsear(linea: String): SentenciaNmea? {
        val l = linea.trim()
        if (!validarChecksum(l)) return null
        val ast = l.lastIndexOf('*')
        val campos = l.substring(1, ast).split(",")
        val etiqueta = campos.getOrNull(0) ?: return null
        if (etiqueta.length < 5) return null
        val talker = etiqueta.substring(0, 2)
        if (talker !in TALKERS) return null
        return when (etiqueta.substring(2)) {
            "GGA" -> parseGga(talker, campos)
            "RMC" -> parseRmc(talker, campos)
            "GST" -> parseGst(talker, campos)
            "GSA" -> parseGsa(talker, campos)
            "GSV" -> parseGsv(talker, campos)
            else -> null
        }
    }

    // --- parsers por tipo ---

    private fun parseGga(talker: String, c: List<String>) = SentenciaGga(
        talker = talker,
        horaUtc = c.campo(1),
        lat = grados(c.campo(2), c.campo(3)),
        lon = grados(c.campo(4), c.campo(5)),
        calidad = CalidadFix.desdeCodigoGga(c.campo(6)?.toIntOrNull() ?: 0),
        satelites = c.campo(7)?.toIntOrNull(),
        hdop = c.campo(8)?.toDoubleOrNull(),
        altitudMsl = c.campo(9)?.toDoubleOrNull(),
        separacionGeoidal = c.campo(11)?.toDoubleOrNull(),
        edadCorrecciones = c.campo(13)?.toDoubleOrNull(),
        idEstacion = c.campo(14),
    )

    private fun parseRmc(talker: String, c: List<String>) = SentenciaRmc(
        talker = talker,
        tiempoUtc = combinarFechaHora(c.campo(9), c.campo(1)),
        valido = c.campo(2) == "A",
        lat = grados(c.campo(3), c.campo(4)),
        lon = grados(c.campo(5), c.campo(6)),
        velocidadNudos = c.campo(7)?.toDoubleOrNull(),
        rumboGrados = c.campo(8)?.toDoubleOrNull(),
    )

    private fun parseGst(talker: String, c: List<String>) = SentenciaGst(
        talker = talker,
        sigmaLat = c.campo(6)?.toDoubleOrNull(),
        sigmaLon = c.campo(7)?.toDoubleOrNull(),
        sigmaAltura = c.campo(8)?.toDoubleOrNull(),
    )

    private fun parseGsa(talker: String, c: List<String>) = SentenciaGsa(
        talker = talker,
        pdop = c.campo(15)?.toDoubleOrNull(),
        hdop = c.campo(16)?.toDoubleOrNull(),
        vdop = c.campo(17)?.toDoubleOrNull(),
        satelitesUsados = (3..14).mapNotNull { c.campo(it)?.toIntOrNull() },
    )

    private fun parseGsv(talker: String, c: List<String>): SentenciaGsv {
        val sats = mutableListOf<SateliteGsv>()
        var i = 4
        while (i < c.size) {
            val prn = c.campo(i)?.toIntOrNull()
            if (prn != null) {
                sats.add(
                    SateliteGsv(
                        prn = prn,
                        elevacion = c.campo(i + 1)?.toIntOrNull(),
                        azimut = c.campo(i + 2)?.toIntOrNull(),
                        snr = c.campo(i + 3)?.toIntOrNull(),
                    )
                )
            }
            i += 4
        }
        return SentenciaGsv(
            talker = talker,
            mensajesTotales = c.campo(1)?.toIntOrNull(),
            numeroMensaje = c.campo(2)?.toIntOrNull(),
            satelitesALaVista = c.campo(3)?.toIntOrNull(),
            satelites = sats,
        )
    }

    // --- utilidades ---

    /** Campo i, o null si no existe o está vacío. */
    private fun List<String>.campo(i: Int): String? = getOrNull(i)?.takeIf { it.isNotEmpty() }

    /**
     * Convierte `ddmm.mmmm`/`dddmm.mmmm` + hemisferio a grados decimales con signo.
     * Vale para latitud y longitud: la parte entera de valor/100 son los grados.
     */
    fun grados(valor: String?, hemisferio: String?): Double? {
        val v = valor?.toDoubleOrNull() ?: return null
        val gradosEnteros = (v / 100).toInt()
        val minutos = v - gradosEnteros * 100
        var dec = gradosEnteros + minutos / 60.0
        if (hemisferio == "S" || hemisferio == "W") dec = -dec
        return dec
    }

    /** Combina fecha `ddmmyy` y hora `hhmmss.ss` en un [Instant] UTC. */
    fun combinarFechaHora(fecha: String?, hora: String?): Instant? {
        if (fecha == null || hora == null || fecha.length < 6 || hora.length < 6) return null
        return try {
            val dia = fecha.substring(0, 2).toInt()
            val mes = fecha.substring(2, 4).toInt()
            val anio = 2000 + fecha.substring(4, 6).toInt()
            val hh = hora.substring(0, 2).toInt()
            val mm = hora.substring(2, 4).toInt()
            val segDec = hora.substring(4).toDouble()
            val seg = segDec.toInt()
            val nanos = ((segDec - seg) * 1_000_000_000).toInt()
            LocalDateTime.of(anio, mes, dia, hh, mm, seg, nanos).toInstant(ZoneOffset.UTC)
        } catch (e: Exception) {
            null
        }
    }
}
