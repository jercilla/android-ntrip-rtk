package es.gailen.rtk

import es.gailen.rtk.dominio.CalidadFix
import org.junit.Assert.assertEquals
import org.junit.Test

class CalidadFixTest {

    @Test
    fun `mapea los codigos GGA conocidos`() {
        assertEquals(CalidadFix.SIN_FIX, CalidadFix.desdeCodigoGga(0))
        assertEquals(CalidadFix.AUTONOMO, CalidadFix.desdeCodigoGga(1))
        assertEquals(CalidadFix.DGPS, CalidadFix.desdeCodigoGga(2))
        assertEquals(CalidadFix.RTK_FIX, CalidadFix.desdeCodigoGga(4))
        assertEquals(CalidadFix.RTK_FLOAT, CalidadFix.desdeCodigoGga(5))
    }

    @Test
    fun `codigos desconocidos caen en SIN_FIX`() {
        assertEquals(CalidadFix.SIN_FIX, CalidadFix.desdeCodigoGga(9))
        assertEquals(CalidadFix.SIN_FIX, CalidadFix.desdeCodigoGga(-1))
    }
}
