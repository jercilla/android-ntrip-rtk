package es.gailen.rtk.dominio

/**
 * Origen de las posiciones que consume la app. Intercambiable tras la interfaz
 * `FuentePosicion` (ver hitos posteriores). En el MVP previo al hardware se usan
 * la fuente simulada y el GPS del móvil; con el módulo llegará [USB_SERIE].
 */
enum class FuentePosicionTipo(val etiqueta: String) {
    GPS_MOVIL("GPS del móvil"),
    SIMULADA("Simulada (NMEA de ejemplo)"),
    USB_SERIE("Módulo USB-serie"),
}
