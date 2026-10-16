package es.gailen.rtk.medicion

import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.dominio.medicion.AcumuladorMedicion
import es.gailen.rtk.dominio.medicion.CalculoMedicion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.sqrt

class MedicionTest {

    private val R = 6_378_137.0
    private fun metrosALat(m: Double) = Math.toDegrees(m / R)

    private fun pos(lat: Double?, lon: Double?, alt: Double?, calidad: CalidadFix) =
        Posicion(
            tiempoUtc = Instant.EPOCH, lat = lat, lon = lon,
            alturaElipsoidal = alt, calidad = calidad,
        )

    @Test
    fun `media y desviacion tipica norte con dos puntos`() {
        val lat0 = 43.0
        val puntos = listOf(
            pos(lat0 + metrosALat(0.5), -2.0, 100.5, CalidadFix.RTK_FIX),
            pos(lat0 + metrosALat(-0.5), -2.0, 99.5, CalidadFix.RTK_FIX),
        )
        val r = CalculoMedicion.calcular(puntos, 2)!!
        assertEquals(lat0, r.lat, 1e-9)
        assertEquals(-2.0, r.lon, 1e-9)
        assertEquals(100.0, r.alturaElipsoidal, 1e-9)
        // residuos ±0.5 m -> sigma muestral = sqrt((0.5²+0.5²)/1) = 0.7071
        assertEquals(sqrt(0.5), r.sigmaNorte, 1e-3)
        assertEquals(sqrt(0.5), r.sigmaAltura, 1e-6)
        assertEquals(0.0, r.sigmaEste, 1e-6)
        assertEquals(2, r.epocasUsadas)
    }

    @Test
    fun `un solo punto tiene desviacion cero`() {
        val r = CalculoMedicion.calcular(listOf(pos(43.0, -2.0, 100.0, CalidadFix.RTK_FIX)), 1)!!
        assertEquals(0.0, r.sigmaNorte, 0.0)
        assertEquals(0.0, r.sigmaEste, 0.0)
        assertEquals(1, r.epocasUsadas)
    }

    @Test
    fun `sin puntos no hay resultado`() {
        assertNull(CalculoMedicion.calcular(emptyList(), 10))
    }

    @Test
    fun `el acumulador solo acepta fix por defecto`() {
        val acc = AcumuladorMedicion(aceptarFloat = false)
        assertTrue(acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.RTK_FIX)))
        assertFalse(acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.RTK_FLOAT)))
        assertFalse(acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.AUTONOMO)))
        assertFalse(acc.ofrecer(pos(null, null, null, CalidadFix.SIN_FIX)))
        assertEquals(1, acc.numEpocasUsadas)
        assertEquals(4, acc.numEpocasTotales)
        assertEquals(25.0, acc.resultado()!!.porcentajeFix, 1e-9)
    }

    @Test
    fun `con aceptarFloat tambien cuenta el float`() {
        val acc = AcumuladorMedicion(aceptarFloat = true)
        acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.RTK_FIX))
        acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.RTK_FLOAT))
        acc.ofrecer(pos(43.0, -2.0, 100.0, CalidadFix.AUTONOMO))
        assertEquals(2, acc.numEpocasUsadas)
        assertEquals(3, acc.numEpocasTotales)
    }
}
