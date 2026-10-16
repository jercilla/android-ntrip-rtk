package es.gailen.rtk.dominio.medicion

import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.Posicion

/**
 * Acumula épocas durante una medición estática. Solo acepta épocas con la calidad
 * requerida (RTK fix, o también float si [aceptarFloat]); cuenta el total ofrecido para
 * calcular el porcentaje de fix. No mezcla épocas no válidas.
 */
class AcumuladorMedicion(private val aceptarFloat: Boolean = false) {

    private val aceptadas = mutableListOf<Posicion>()
    private var totales = 0

    val numEpocasUsadas: Int get() = aceptadas.size
    val numEpocasTotales: Int get() = totales

    /** Indica si una calidad es aceptable para medir. */
    fun aceptable(calidad: CalidadFix): Boolean =
        calidad == CalidadFix.RTK_FIX || (aceptarFloat && calidad == CalidadFix.RTK_FLOAT)

    /**
     * Ofrece una época. Cuenta para el total; se acumula solo si tiene coordenadas y la
     * calidad es aceptable. Devuelve true si se aceptó.
     */
    fun ofrecer(posicion: Posicion): Boolean {
        totales++
        val ok = posicion.lat != null && posicion.lon != null && aceptable(posicion.calidad)
        if (ok) aceptadas.add(posicion)
        return ok
    }

    fun resultado(): ResultadoMedicion? = CalculoMedicion.calcular(aceptadas, totales)

    fun reiniciar() {
        aceptadas.clear()
        totales = 0
    }
}
