package es.gailen.rtk.nmea

import es.gailen.rtk.datos.nmea.ParserNmea
import es.gailen.rtk.datos.nmea.SentenciaGga
import es.gailen.rtk.datos.nmea.SentenciaGsa
import es.gailen.rtk.datos.nmea.SentenciaGst
import es.gailen.rtk.datos.nmea.SentenciaGsv
import es.gailen.rtk.datos.nmea.SentenciaRmc
import es.gailen.rtk.dominio.CalidadFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ParserNmeaTest {

    private val ggaFix = "\$GNGGA,101530.00,4315.1234,N,00256.5678,W,4,14,0.8,120.3,M,49.1,M,1.0,0000*47"
    private val ggaFloat = "\$GNGGA,101531.00,4315.1240,N,00256.5680,W,5,11,1.1,120.1,M,49.1,M,2.0,0000*4F"
    private val ggaVacio = "\$GPGGA,,,,,,0,00,,,,,,,*66"
    private val rmc = "\$GNRMC,101530.00,A,4315.1234,N,00256.5678,W,0.03,187.4,160126,,,R*76"
    private val gst = "\$GNGST,101530.00,1.2,0.9,0.7,45.0,0.012,0.015,0.020*76"
    private val gsa = "\$GNGSA,A,3,01,02,03,04,05,,,,,,,,1.2,0.8,0.9*2F"
    private val gsv = "\$GPGSV,2,1,07,01,40,083,42,02,17,308,43,12,07,344,39,25,56,120,45*76"

    // --- checksum ---

    @Test
    fun `checksum valido en ejemplos clasicos`() {
        assertTrue(ParserNmea.validarChecksum("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"))
        assertTrue(ParserNmea.validarChecksum("\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"))
        assertTrue(ParserNmea.validarChecksum(ggaFix))
    }

    @Test
    fun `checksum invalido se rechaza`() {
        // Carácter alterado en el payload -> checksum no coincide
        assertFalse(ParserNmea.validarChecksum("\$GNGGA,101530.00,4315.9999,N,00256.5678,W,4,14,0.8,120.3,M,49.1,M,1.0,0000*47"))
        assertFalse(ParserNmea.validarChecksum("sin dolar ni asterisco"))
        assertFalse(ParserNmea.validarChecksum("\$GNGGA,101530.00*ZZ"))
        assertFalse(ParserNmea.validarChecksum("\$GNGGA,101530.00"))
    }

    @Test
    fun `parsear devuelve null si el checksum falla`() {
        assertNull(ParserNmea.parsear("\$GNGGA,101530.00,4315.9999,N,00256.5678,W,4,14,0.8,120.3,M,49.1,M,1.0,0000*47"))
    }

    // --- GGA ---

    @Test
    fun `GGA con RTK fix`() {
        val s = ParserNmea.parsear(ggaFix) as SentenciaGga
        assertEquals("GN", s.talker)
        assertEquals(CalidadFix.RTK_FIX, s.calidad)
        assertEquals(14, s.satelites)
        assertEquals(0.8, s.hdop!!, 1e-9)
        assertEquals(1.0, s.edadCorrecciones!!, 1e-9)
        // 4315.1234 -> 43 + 15.1234/60 ; 00256.5678 W -> -(2 + 56.5678/60)
        assertEquals(43.0 + 15.1234 / 60.0, s.lat!!, 1e-7)
        assertEquals(-(2.0 + 56.5678 / 60.0), s.lon!!, 1e-7)
        assertEquals(120.3 + 49.1, s.alturaElipsoidal!!, 1e-6)
    }

    @Test
    fun `GGA con RTK float`() {
        val s = ParserNmea.parsear(ggaFloat) as SentenciaGga
        assertEquals(CalidadFix.RTK_FLOAT, s.calidad)
        assertEquals(11, s.satelites)
    }

    @Test
    fun `GGA sin fix con campos vacios`() {
        val s = ParserNmea.parsear(ggaVacio) as SentenciaGga
        assertEquals(CalidadFix.SIN_FIX, s.calidad)
        assertNull(s.lat)
        assertNull(s.lon)
        assertNull(s.hdop)
        assertNull(s.alturaElipsoidal)
        assertEquals(0, s.satelites)
    }

    // --- RMC ---

    @Test
    fun `RMC con fecha y hora`() {
        val s = ParserNmea.parsear(rmc) as SentenciaRmc
        assertTrue(s.valido)
        assertEquals(187.4, s.rumboGrados!!, 1e-9)
        assertEquals(0.03, s.velocidadNudos!!, 1e-9)
        val esperado = LocalDateTime.of(2026, 1, 16, 10, 15, 30).toInstant(ZoneOffset.UTC)
        assertEquals(esperado, s.tiempoUtc)
    }

    // --- GST ---

    @Test
    fun `GST con desviaciones`() {
        val s = ParserNmea.parsear(gst) as SentenciaGst
        assertEquals(0.012, s.sigmaLat!!, 1e-9)
        assertEquals(0.015, s.sigmaLon!!, 1e-9)
        assertEquals(0.020, s.sigmaAltura!!, 1e-9)
    }

    // --- GSA ---

    @Test
    fun `GSA con DOPs y satelites usados`() {
        val s = ParserNmea.parsear(gsa) as SentenciaGsa
        assertEquals(1.2, s.pdop!!, 1e-9)
        assertEquals(0.8, s.hdop!!, 1e-9)
        assertEquals(0.9, s.vdop!!, 1e-9)
        assertEquals(listOf(1, 2, 3, 4, 5), s.satelitesUsados)
    }

    // --- GSV ---

    @Test
    fun `GSV con satelites a la vista`() {
        val s = ParserNmea.parsear(gsv) as SentenciaGsv
        assertEquals(2, s.mensajesTotales)
        assertEquals(1, s.numeroMensaje)
        assertEquals(7, s.satelitesALaVista)
        assertEquals(4, s.satelites.size)
        val primero = s.satelites.first()
        assertEquals(1, primero.prn)
        assertEquals(40, primero.elevacion)
        assertEquals(83, primero.azimut)
        assertEquals(42, primero.snr)
    }

    // --- talkers y tipos no soportados ---

    @Test
    fun `talker no GNSS se ignora`() {
        val payload = "INGGA,101530.00,4315.1234,N,00256.5678,W,1,10,0.9,100.0,M,49.0,M,,"
        val linea = "\$$payload*%02X".format(ParserNmea.checksumCalculado(payload))
        assertTrue(ParserNmea.validarChecksum(linea))   // checksum correcto…
        assertNull(ParserNmea.parsear(linea))           // …pero el talker IN no es GNSS
    }

    @Test
    fun `tipo de sentencia no soportado se ignora`() {
        val payload = "GPVTG,054.7,T,034.4,M,005.5,N,010.2,K"
        val linea = "\$$payload*%02X".format(ParserNmea.checksumCalculado(payload))
        assertTrue(ParserNmea.validarChecksum(linea))
        assertNull(ParserNmea.parsear(linea))           // VTG no está entre las soportadas
    }
}
