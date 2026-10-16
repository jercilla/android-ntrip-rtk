package es.gailen.rtk.datos.rtcm

/** Resultado acumulado del análisis del flujo RTCM, para la pantalla de diagnóstico. */
data class ResumenRtcm(
    val contadores: Map<Int, Int> = emptyMap(),
    val tramasTotales: Int = 0,
    val estacionBase: EstacionBase? = null,
)

/**
 * Consume el flujo de bytes RTCM, cuenta las tramas por tipo y mantiene la última
 * posición de la base (de 1005/1006). No es seguro para uso concurrente.
 */
class ContadorRtcm {

    private val parser = ParserRtcm()
    private val contadores = LinkedHashMap<Int, Int>()
    private var tramasTotales = 0
    private var estacionBase: EstacionBase? = null

    /** Procesa bytes nuevos y devuelve el resumen actualizado. */
    fun procesar(bytes: ByteArray): ResumenRtcm {
        for (trama in parser.agregar(bytes)) {
            contadores[trama.tipo] = (contadores[trama.tipo] ?: 0) + 1
            tramasTotales++
            DecodificadorRtcm.decodificarEstacion(trama)?.let { estacionBase = it }
        }
        return resumen()
    }

    fun resumen(): ResumenRtcm = ResumenRtcm(
        contadores = contadores.toSortedMap(),
        tramasTotales = tramasTotales,
        estacionBase = estacionBase,
    )

    fun reiniciar() {
        parser.reiniciar()
        contadores.clear()
        tramasTotales = 0
        estacionBase = null
    }
}
