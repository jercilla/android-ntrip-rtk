package es.gailen.rtk.replanteo

import es.gailen.rtk.dominio.replanteo.CalculoReplanteo
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculoReplanteoTest {

    private val R = 6_378_137.0
    private fun dLat(m: Double) = Math.toDegrees(m / R)
    private fun dLon(m: Double, lat: Double) = Math.toDegrees(m / (R * Math.cos(Math.toRadians(lat))))

    private val lat0 = 43.0
    private val lon0 = -2.0

    @Test
    fun `objetivo 1 m al norte`() {
        val g = CalculoReplanteo.guia(lat0, lon0, 100.0, lat0 + dLat(1.0), lon0, 101.5)
        assertEquals(1.0, g.norte, 1e-3)
        assertEquals(0.0, g.este, 1e-3)
        assertEquals(1.0, g.distanciaHorizontal, 1e-3)
        assertEquals(0.0, g.rumbo, 1e-2)
        assertEquals(1.5, g.desnivel!!, 1e-6)
    }

    @Test
    fun `objetivo al este da rumbo 90`() {
        val g = CalculoReplanteo.guia(lat0, lon0, null, lat0, lon0 + dLon(2.0, lat0), null)
        assertEquals(0.0, g.norte, 1e-3)
        assertEquals(2.0, g.este, 1e-3)
        assertEquals(90.0, g.rumbo, 1e-2)
        assertEquals(2.0, g.distanciaHorizontal, 1e-3)
    }

    @Test
    fun `objetivo al sur da rumbo 180`() {
        val g = CalculoReplanteo.guia(lat0, lon0, null, lat0 - dLat(5.0), lon0, null)
        assertEquals(-5.0, g.norte, 1e-3)
        assertEquals(180.0, g.rumbo, 1e-2)
    }

    @Test
    fun `objetivo al oeste da rumbo 270`() {
        val g = CalculoReplanteo.guia(lat0, lon0, null, lat0, lon0 - dLon(3.0, lat0), null)
        assertEquals(270.0, g.rumbo, 1e-2)
        assertEquals(3.0, g.distanciaHorizontal, 1e-3)
    }

    @Test
    fun `desnivel nulo si falta una altura`() {
        val g = CalculoReplanteo.guia(lat0, lon0, 100.0, lat0 + dLat(1.0), lon0, null)
        assertEquals(null, g.desnivel)
    }

    @Test
    fun `objetivo noreste da rumbo 45`() {
        val g = CalculoReplanteo.guia(lat0, lon0, null, lat0 + dLat(1.0), lon0 + dLon(1.0, lat0), null)
        assertEquals(45.0, g.rumbo, 0.1)
        assertEquals(Math.sqrt(2.0), g.distanciaHorizontal, 1e-3)
    }
}
