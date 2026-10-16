package es.gailen.rtk.datos.nmea

/**
 * Reensambla líneas NMEA a partir de un flujo de bytes fragmentado (USB entrega
 * trozos arbitrarios). Acumula hasta encontrar un salto de línea y entonces emite
 * la línea completa. NMEA es ASCII, así que cada byte se trata como un carácter.
 *
 * No es seguro para uso concurrente: llámalo desde un único hilo/corrutina.
 */
class EnsambladorNmea(private val maxLongitudLinea: Int = 1024) {

    private val buffer = StringBuilder()
    private var desbordada = false

    /**
     * Procesa [longitud] bytes de [datos] y devuelve las líneas completas encontradas
     * (sin `\r`/`\n` ni espacios en los extremos). Las líneas que superan
     * [maxLongitudLinea] sin salto de línea se descartan (defensa ante basura/binario).
     */
    fun agregar(datos: ByteArray, longitud: Int = datos.size): List<String> {
        val lineas = mutableListOf<String>()
        for (idx in 0 until longitud) {
            val ch = (datos[idx].toInt() and 0xFF).toChar()
            if (ch == '\n' || ch == '\r') {
                if (!desbordada && buffer.isNotEmpty()) {
                    lineas.add(buffer.toString().trim())
                }
                buffer.setLength(0)
                desbordada = false
            } else if (desbordada) {
                continue
            } else if (buffer.length >= maxLongitudLinea) {
                buffer.setLength(0)
                desbordada = true
            } else {
                buffer.append(ch)
            }
        }
        return lineas
    }

    /** Vacía el buffer interno (p. ej. tras reconectar). */
    fun reiniciar() {
        buffer.setLength(0)
        desbordada = false
    }
}
