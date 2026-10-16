package es.gailen.rtk.dominio

/** Ajustes generales de la app (no secretos). Se persisten en DataStore. */
data class AjustesApp(
    val fuente: FuentePosicionTipo = FuentePosicionTipo.SIMULADA,
    val baudrateUsb: Int = 115200,
    val perfilNtripActivoId: String = PerfilesPorDefecto.ID_APBI3M,
    val alturaJalonMetros: Double = 0.0,
    val duracionMedicionSegundos: Int = 60,
    val aceptarFloat: Boolean = false,
    /** URL del servidor de desarrollo para enviar telemetría de depuración. Vacío = desactivado. */
    val urlDebug: String = URL_DEBUG_POR_DEFECTO,
) {
    companion object {
        /** Baudrates habituales para el conversor USB-serie. */
        val BAUDRATES = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800)

        /** Servidor de desarrollo por defecto (tools/devserver). Editable en Ajustes. */
        const val URL_DEBUG_POR_DEFECTO = "http://192.168.1.142:8000"
    }
}
