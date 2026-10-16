package es.gailen.rtk.ntrip

import es.gailen.rtk.datos.ntrip.ParserSourcetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcetableTest {

    private val tabla = """
        SOURCETABLE 200 OK
        Content-Type: text/plain

        CAS;www.gps2.euskadi.eus;2103;Euskadi;GAIN;0;ESP;0.00;0.00;0;0;;
        NET;EUSKADI;IGN;B;N;http://www.gps2.euskadi.eus;;;
        STR;APBI3M;Aralar;RTCM 3.2;1005(1),1074(1),1084(1),1094(1),1124(1);2;GPS+GLO+GAL+BDS;EUSKADI;ESP;43.00;-2.40;0;0;Septentrio;none;N;N;1500;
        STR;CERCANA3M;Cercana;RTCM 3.2;1005(1),1077(1),1087(1);2;GPS+GLO+GAL;EUSKADI;ESP;43.26;-2.93;1;0;Trimble;none;B;N;2400;
        STR;VRS3M;Red VRS;RTCM 3.2;1005,1077;2;GPS+GLO;EUSKADI;ESP;43.0;-2.0;1;0;Trimble;none;B;N;;
        ENDSOURCETABLE
        STR;NODEBERIA;...;;;;;;;;;;
    """.trimIndent()

    @Test
    fun `parsea solo las lineas STR hasta ENDSOURCETABLE`() {
        val entradas = ParserSourcetable.parsear(tabla)
        assertEquals(listOf("APBI3M", "CERCANA3M", "VRS3M"), entradas.map { it.puntoMontaje })
    }

    @Test
    fun `detecta el campo NMEA`() {
        val entradas = ParserSourcetable.parsear(tabla).associateBy { it.puntoMontaje }
        assertFalse(entradas.getValue("APBI3M").requiereNmea)   // nmea = 0
        assertTrue(entradas.getValue("CERCANA3M").requiereNmea) // nmea = 1
        assertTrue(entradas.getValue("VRS3M").requiereNmea)
    }

    @Test
    fun `reconoce RTCM 3 y campos`() {
        val apbi = ParserSourcetable.parsear(tabla).first { it.puntoMontaje == "APBI3M" }
        assertTrue(apbi.esRtcm3)
        assertEquals("GPS+GLO+GAL+BDS", apbi.sistemas)
        assertEquals(1500, apbi.bitrate)
        assertFalse(apbi.pago)
        assertEquals(43.00, apbi.lat!!, 1e-9)
    }
}
