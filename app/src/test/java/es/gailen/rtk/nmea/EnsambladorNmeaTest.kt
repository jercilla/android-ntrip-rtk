package es.gailen.rtk.nmea

import es.gailen.rtk.datos.nmea.EnsambladorNmea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsambladorNmeaTest {

    @Test
    fun `reensambla una linea partida en varias lecturas`() {
        val e = EnsambladorNmea()
        assertTrue(e.agregar("\$GNGGA,101530.00,4315".toByteArray()).isEmpty())
        assertTrue(e.agregar(".1234,N,00256".toByteArray()).isEmpty())
        val r = e.agregar(".5678,W*47\r\n".toByteArray())
        assertEquals(listOf("\$GNGGA,101530.00,4315.1234,N,00256.5678,W*47"), r)
    }

    @Test
    fun `separa varias lineas en un mismo trozo`() {
        val e = EnsambladorNmea()
        val r = e.agregar("\$AAA*01\r\n\$BBB*02\r\n".toByteArray())
        assertEquals(listOf("\$AAA*01", "\$BBB*02"), r)
    }

    @Test
    fun `CRLF no produce lineas vacias`() {
        val e = EnsambladorNmea()
        val r = e.agregar("UNA\r\n\r\nDOS\n".toByteArray())
        assertEquals(listOf("UNA", "DOS"), r)
    }

    @Test
    fun `una linea sin terminar queda pendiente`() {
        val e = EnsambladorNmea()
        val r = e.agregar("PENDIENTE_SIN_SALTO".toByteArray())
        assertTrue(r.isEmpty())
    }

    @Test
    fun `descarta lineas mas largas que el maximo`() {
        val e = EnsambladorNmea(maxLongitudLinea = 8)
        // 12 caracteres sin salto: desborda y se descarta
        assertTrue(e.agregar("123456789012".toByteArray()).isEmpty())
        // tras el salto, se recupera y procesa la siguiente línea con normalidad
        val r = e.agregar("\nHOLA\n".toByteArray())
        assertEquals(listOf("HOLA"), r)
    }

    @Test
    fun `reiniciar vacia el buffer pendiente`() {
        val e = EnsambladorNmea()
        e.agregar("BASURA_A_MEDIAS".toByteArray())
        e.reiniciar()
        val r = e.agregar("LIMPIA\n".toByteArray())
        assertEquals(listOf("LIMPIA"), r)
    }
}
