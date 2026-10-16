package es.gailen.rtk

import es.gailen.rtk.dominio.ModoGga
import es.gailen.rtk.dominio.PerfilesPorDefecto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfilesPorDefectoTest {

    @Test
    fun `el perfil por defecto es APBI3M en el puerto 2103 sin GGA`() {
        val apbi = PerfilesPorDefecto.lista.first { it.id == PerfilesPorDefecto.ID_APBI3M }
        assertEquals("www.gps2.euskadi.eus", apbi.host)
        assertEquals(2103, apbi.puerto)
        assertEquals("APBI3M", apbi.puntoMontaje)
        assertEquals(ModoGga.NUNCA, apbi.modoGga)
        assertFalse("Los perfiles precargados no se pueden borrar", apbi.editable)
    }

    @Test
    fun `los perfiles que usan red requieren GGA`() {
        val cercana = PerfilesPorDefecto.lista.first { it.id == PerfilesPorDefecto.ID_CERCANA }
        assertEquals(2101, cercana.puerto)
        assertTrue(cercana.modoGga == ModoGga.AUTO || cercana.modoGga == ModoGga.FORZADO)
    }

    @Test
    fun `los ids de los perfiles son unicos`() {
        val ids = PerfilesPorDefecto.lista.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
