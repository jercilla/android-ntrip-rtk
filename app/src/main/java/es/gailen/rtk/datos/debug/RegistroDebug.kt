package es.gailen.rtk.datos.debug

/**
 * Registro de telemetría de depuración. Envía líneas etiquetadas al servidor de
 * desarrollo (ver tools/devserver) para poder diagnosticar la app en el dispositivo
 * sin depender de que el usuario lea la pantalla. Implementación real: [RegistroDebugHttp].
 */
fun interface RegistroDebug {
    fun registra(etiqueta: String, mensaje: String)
}
