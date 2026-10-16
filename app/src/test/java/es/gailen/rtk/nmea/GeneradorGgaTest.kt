package es.gailen.rtk.nmea

import es.gailen.rtk.datos.nmea.GeneradorGga
import es.gailen.rtk.datos.nmea.ParserNmea
import es.gailen.rtk.datos.nmea.SentenciaGga
import es.gailen.rtk.dominio.CalidadFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GeneradorGgaTest {

    @Test
    fun `el GGA generado es valido y se vuelve a parsear igual`() {
        val lat = 43.263000
        val lon = -2.935000
        val gga = GeneradorGga.generar(
            lat = lat,
            lon = lon,
            alturaElipsoidal = 114.5,
            satelites = 9,
            hdop = 1.2,
            calidad = CalidadFix.AUTONOMO,
            instante = Instant.parse("2026-01-16T10:15:30Z"),
        )
        assertTrue("checksum válido", ParserNmea.validarChecksum(gga))

        val s = ParserNmea.parsear(gga) as SentenciaGga
        assertEquals(CalidadFix.AUTONOMO, s.calidad)
        assertEquals(9, s.satelites)
        // Round-trip de coordenadas: el formato ddmm.mmmmm conserva ~cm
        assertEquals(lat, s.lat!!, 1e-5)
        assertEquals(lon, s.lon!!, 1e-5)
    }

    @Test
    fun `hemisferios sur y este`() {
        val gga = GeneradorGga.generar(
            lat = -33.45, lon = 151.20, alturaElipsoidal = null,
            satelites = null, hdop = null, calidad = CalidadFix.AUTONOMO,
            instante = Instant.parse("2026-01-16T00:00:00Z"),
        )
        val s = ParserNmea.parsear(gga) as SentenciaGga
        assertEquals(-33.45, s.lat!!, 1e-5)
        assertEquals(151.20, s.lon!!, 1e-5)
    }
}
