package es.gailen.rtk.datos.ntrip

/** Una entrada STR de la sourcetable NTRIP (un punto de montaje). */
data class EntradaSourcetable(
    val puntoMontaje: String,
    val identificador: String,
    val formato: String,            // p. ej. RTCM 3.2
    val detallesFormato: String,    // mensajes: 1005(1),1077(1),...
    val sistemas: String,           // GPS+GLO+GAL+BDS
    val red: String,
    val pais: String,
    val lat: Double?,
    val lon: Double?,
    val requiereNmea: Boolean,      // campo NMEA = 1
    val autenticacion: String,      // N / B / D
    val pago: Boolean,              // fee: Y/N
    val bitrate: Int?,
) {
    /** Compatible con el LC29H: RTCM 3 y, preferiblemente, multiconstelación MSM (acaba en 3M). */
    val esRtcm3: Boolean get() = formato.contains("RTCM 3", ignoreCase = true) ||
        formato.contains("RTCM3", ignoreCase = true)
}

/**
 * Parser de la sourcetable NTRIP. Procesa las líneas `STR;` (puntos de montaje) hasta
 * `ENDSOURCETABLE`. Ignora las líneas `CAS;`/`NET;` (no se usan para elegir montaje).
 */
object ParserSourcetable {

    fun parsear(texto: String): List<EntradaSourcetable> {
        val entradas = mutableListOf<EntradaSourcetable>()
        for (linea in texto.lineAsSequence()) {
            val l = linea.trim()
            if (l == FIN) break
            if (!l.startsWith("STR;")) continue
            parsearStr(l)?.let(entradas::add)
        }
        return entradas
    }

    private fun parsearStr(linea: String): EntradaSourcetable? {
        val c = linea.split(";")
        if (c.size < 12 || c[0] != "STR") return null
        fun campo(i: Int) = c.getOrNull(i)?.trim().orEmpty()
        return EntradaSourcetable(
            puntoMontaje = campo(1),
            identificador = campo(2),
            formato = campo(3),
            detallesFormato = campo(4),
            sistemas = campo(6),
            red = campo(7),
            pais = campo(8),
            lat = campo(9).toDoubleOrNull(),
            lon = campo(10).toDoubleOrNull(),
            requiereNmea = campo(11) == "1",
            autenticacion = campo(15),
            pago = campo(16).equals("Y", ignoreCase = true),
            bitrate = campo(17).toIntOrNull(),
        )
    }

    private const val FIN = "ENDSOURCETABLE"

    private fun String.lineAsSequence(): Sequence<String> =
        splitToSequence('\n').map { it.removeSuffix("\r") }
}
