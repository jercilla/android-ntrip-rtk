package es.gailen.rtk.nmea

import es.gailen.rtk.datos.nmea.ConstructorPosicion
import es.gailen.rtk.datos.nmea.ParserNmea
import es.gailen.rtk.dominio.CalidadFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConstructorPosicionTest {

    private fun parse(vararg lineas: String) = lineas.mapNotNull { ParserNmea.parsear(it) }

    @Test
    fun `combina GGA, RMC y GST en una posicion`() {
        val sentencias = parse(
            "\$GNGGA,101530.00,4315.1234,N,00256.5678,W,4,14,0.8,120.3,M,49.1,M,1.0,0000*47",
            "\$GNRMC,101530.00,A,4315.1234,N,00256.5678,W,0.03,187.4,160126,,,R*76",
            "\$GNGST,101530.00,1.2,0.9,0.7,45.0,0.012,0.015,0.020*76",
        )
        val p = ConstructorPosicion.construir(sentencias)!!
        assertEquals(CalidadFix.RTK_FIX, p.calidad)
        assertEquals(14, p.satelites)
        assertEquals(169.4, p.alturaElipsoidal!!, 1e-6)
        assertEquals(187.4, p.rumboGrados!!, 1e-9)
        assertEquals(Instant.parse("2026-01-16T10:15:30Z"), p.tiempoUtc)
        assertNotNull(p.precisionEstimada)
        assertEquals(0.012, p.precisionEstimada!!.sigmaLat, 1e-9)
    }

    @Test
    fun `epoca sin fix produce posicion sin coordenadas`() {
        val sentencias = parse("\$GPGGA,,,,,,0,00,,,,,,,*66")
        val p = ConstructorPosicion.construir(sentencias)!!
        assertEquals(CalidadFix.SIN_FIX, p.calidad)
        assertNull(p.lat)
        assertNull(p.lon)
    }

    @Test
    fun `sin GGA no hay posicion`() {
        val sentencias = parse("\$GNRMC,101530.00,A,4315.1234,N,00256.5678,W,0.03,187.4,160126,,,R*76")
        assertNull(ConstructorPosicion.construir(sentencias))
    }
}
