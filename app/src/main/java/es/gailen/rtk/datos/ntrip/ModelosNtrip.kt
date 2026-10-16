package es.gailen.rtk.datos.ntrip

/** Estado del cliente NTRIP. */
enum class EstadoNtrip(val etiqueta: String) {
    DESCONECTADO("Desconectado"),
    CONECTANDO("Conectando…"),
    CONECTADO("Conectado"),
    REINTENTANDO("Reintentando…"),
    ERROR_AUTENTICACION("Error de credenciales (401)"),
    ERROR_MONTAJE("Punto de montaje no encontrado (404)"),
    ERROR("Error de conexión"),
}

/** Estadísticas en vivo de la conexión NTRIP. */
data class EstadisticasNtrip(
    val bytesTotales: Long = 0,
    val tasaBps: Int = 0,
    val conectadoDesdeMs: Long? = null,
    val ultimoError: String? = null,
)

/** Configuración necesaria para abrir una conexión NTRIP (incluye credenciales en memoria). */
data class ConfigNtrip(
    val host: String,
    val ipsRespaldo: List<String>,
    val puerto: Int,
    val puntoMontaje: String,
    val usuario: String,
    val password: String,
    val enviarGga: Boolean,
    val intervaloGgaSegundos: Int,
)
