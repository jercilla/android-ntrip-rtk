package es.gailen.rtk.dominio

/**
 * Calidad de la solución de posición, según el campo de calidad de la sentencia NMEA GGA.
 */
enum class CalidadFix(val codigoGga: Int, val etiqueta: String) {
    SIN_FIX(0, "Sin fix"),
    AUTONOMO(1, "Autónomo"),
    DGPS(2, "DGPS"),
    RTK_FIX(4, "RTK fix"),
    RTK_FLOAT(5, "RTK float");

    companion object {
        /** Mapea el código de calidad del GGA a [CalidadFix]. Valores desconocidos → [SIN_FIX]. */
        fun desdeCodigoGga(codigo: Int): CalidadFix =
            entries.firstOrNull { it.codigoGga == codigo } ?: SIN_FIX
    }
}
