package es.gailen.rtk.rtcm

import es.gailen.rtk.geo.Ecef
import es.gailen.rtk.geo.Geodesica
import es.gailen.rtk.geo.GeodesiaEcef
import org.junit.Assert.assertEquals
import org.junit.Test

class GeodesiaEcefTest {

    @Test
    fun `ida y vuelta geodesica - ECEF - geodesica`() {
        val g = Geodesica(43.263000, -2.935000, 100.000)
        val e = GeodesiaEcef.geodesicaAEcef(g)
        val g2 = GeodesiaEcef.ecefAGeodesica(e)
        assertEquals(g.lat, g2.lat, 1e-9)
        assertEquals(g.lon, g2.lon, 1e-9)
        assertEquals(g.altura, g2.altura, 1e-3)
    }

    @Test
    fun `ECEF de referencia en el ecuador meridiano cero`() {
        // lat 0, lon 0, h 0 -> X = semieje mayor, Y = Z = 0
        val e = GeodesiaEcef.geodesicaAEcef(Geodesica(0.0, 0.0, 0.0))
        assertEquals(6_378_137.0, e.x, 1e-3)
        assertEquals(0.0, e.y, 1e-6)
        assertEquals(0.0, e.z, 1e-6)
    }

    @Test
    fun `distancia de linea base entre dos puntos cercanos`() {
        val base = GeodesiaEcef.geodesicaAEcef(Geodesica(43.263000, -2.935000, 100.0))
        // ~1 m al norte aprox (1/111320 grados de latitud)
        val punto = GeodesiaEcef.geodesicaAEcef(Geodesica(43.263000 + 1.0 / 111_320.0, -2.935000, 100.0))
        val d = base.distanciaA(punto)
        assertEquals(1.0, d, 0.05)
    }

    @Test
    fun `distancia cero consigo mismo`() {
        val e = Ecef(4_645_948.0, -238_199.0, 4_348_893.0)
        assertEquals(0.0, e.distanciaA(e), 0.0)
    }
}
