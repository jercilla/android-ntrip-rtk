package es.gailen.rtk.rtcm

import es.gailen.rtk.datos.rtcm.Crc24Q
import es.gailen.rtk.datos.rtcm.DecodificadorRtcm
import es.gailen.rtk.datos.rtcm.ParserRtcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserRtcmTest {

    private fun hex(s: String): ByteArray =
        s.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    // Fixtures generados con CRC-24Q correcto (base: lat 43.263, lon -2.935, h 100, id 1234, antena 1.5).
    private val f1005 = hex("d3 00 13 3e d4 d2 03 8a d1 33 70 c0 3f 72 05 b7 68 0a 20 24 71 8e fb 52 61")
    private val f1006 = hex("d3 00 15 3e e4 d2 03 8a d1 33 70 c0 3f 72 05 b7 68 0a 20 24 71 8e 3a 98 1b 01 2c")
    private val f1075 = hex("d3 00 0b 43 34 d2 00 00 00 00 00 00 00 00 d8 72 b8")

    @Test
    fun `CRC-24Q coincide con el vector conocido`() {
        // Valor de referencia de CRC-24Q para "123456789".
        assertEquals(0xCDE703, Crc24Q.calcular("123456789".toByteArray()))
    }

    @Test
    fun `tipo de mensaje de los fixtures reales del brief`() {
        // Cabecera real recibida de APBI3M (brief, sección 8): d3 00 15 3e e0 ... = tipo 1006, long 21.
        assertEquals(1006, ParserRtcm.tipoDe(hex("3e e0")))
        // d3 01 3a 43 30 ... = tipo 1075, long 314.
        assertEquals(1075, ParserRtcm.tipoDe(hex("43 30")))
        assertEquals(314, ((0x01 and 0x03) shl 8) or 0x3a)
    }

    @Test
    fun `encuadra y decodifica un 1006`() {
        val tramas = ParserRtcm().agregar(f1006)
        assertEquals(1, tramas.size)
        assertEquals(1006, tramas[0].tipo)

        val base = DecodificadorRtcm.decodificarEstacion(tramas[0])!!
        assertEquals(1234, base.idEstacion)
        assertEquals(43.263000, base.geodesica.lat, 1e-5)
        assertEquals(-2.935000, base.geodesica.lon, 1e-5)
        assertEquals(100.000, base.geodesica.altura, 1e-2)
        assertEquals(1.500, base.alturaAntena!!, 1e-3)
    }

    @Test
    fun `el 1005 no tiene altura de antena`() {
        val trama = ParserRtcm().agregar(f1005).single()
        assertEquals(1005, trama.tipo)
        val base = DecodificadorRtcm.decodificarEstacion(trama)!!
        assertEquals(43.263000, base.geodesica.lat, 1e-5)
        assertNull(base.alturaAntena)
    }

    @Test
    fun `un 1075 se encuadra pero no es estacion base`() {
        val trama = ParserRtcm().agregar(f1075).single()
        assertEquals(1075, trama.tipo)
        assertNull(DecodificadorRtcm.decodificarEstacion(trama))
    }

    @Test
    fun `dos tramas seguidas en un mismo buffer`() {
        val tramas = ParserRtcm().agregar(f1006 + f1075)
        assertEquals(listOf(1006, 1075), tramas.map { it.tipo })
    }

    @Test
    fun `trama partida entre dos lecturas`() {
        val parser = ParserRtcm()
        val corte = 10
        assertTrue(parser.agregar(f1006.copyOfRange(0, corte)).isEmpty())
        val tramas = parser.agregar(f1006.copyOfRange(corte, f1006.size))
        assertEquals(1, tramas.size)
        assertEquals(1006, tramas[0].tipo)
    }

    @Test
    fun `basura antes del preambulo se descarta`() {
        val tramas = ParserRtcm().agregar(hex("aa bb cc 11") + f1006)
        assertEquals(1, tramas.size)
        assertEquals(1006, tramas[0].tipo)
    }

    @Test
    fun `CRC invalido se descarta y resincroniza con la siguiente trama`() {
        val corrupta = f1006.copyOf()
        corrupta[8] = (corrupta[8] + 1).toByte()  // altera el payload -> CRC no coincide
        val tramas = ParserRtcm().agregar(corrupta + f1005)
        // La corrupta se descarta; la válida posterior se recupera por resincronización.
        assertTrue(tramas.any { it.tipo == 1005 })
    }
}
