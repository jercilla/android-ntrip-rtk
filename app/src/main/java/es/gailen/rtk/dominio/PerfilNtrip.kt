package es.gailen.rtk.dominio

import kotlinx.serialization.Serializable

/** Cuándo enviar la sentencia GGA al caster NTRIP. */
enum class ModoGga(val etiqueta: String) {
    AUTO("Automático (según sourcetable)"),
    FORZADO("Forzado (enviar siempre)"),
    NUNCA("No enviar"),
}

/**
 * Configuración de un perfil NTRIP. **No contiene credenciales**: el usuario y la
 * contraseña se guardan cifrados aparte (ver `AlmacenCredenciales`), de modo que
 * esta parte se puede serializar en claro sin exponer secretos.
 */
@Serializable
data class PerfilNtrip(
    val id: String,
    val nombre: String,
    val host: String,
    val ipsRespaldo: List<String> = emptyList(),
    val puerto: Int,
    val puntoMontaje: String,
    val modoGga: ModoGga = ModoGga.AUTO,
    val intervaloGgaSegundos: Int = 10,
    /** Los perfiles precargados no se pueden borrar, solo editar. */
    val editable: Boolean = true,
)

/** Credenciales de acceso al caster. Nunca se serializan en claro ni se registran en logs. */
data class CredencialesNtrip(
    val usuario: String,
    val password: String,
)

/**
 * Perfiles NTRIP precargados (sección 4 del documento de diseño).
 * Las coordenadas de las estaciones de la sourcetable de Euskadi son poco fiables;
 * la posición real de la base se obtiene del mensaje RTCM 1005/1006 del flujo.
 */
object PerfilesPorDefecto {
    const val ID_APBI3M = "euskadi-individual-apbi3m"
    const val ID_CERCANA = "euskadi-red-cercana3m"
    const val ID_BIZKAIA = "bizkaia-cercana3m"

    val lista: List<PerfilNtrip> = listOf(
        PerfilNtrip(
            id = ID_APBI3M,
            nombre = "Euskadi · estación individual (APBI3M)",
            host = "www.gps2.euskadi.eus",
            ipsRespaldo = listOf("77.228.156.141", "150.241.41.141"),
            puerto = 2103,
            puntoMontaje = "APBI3M",
            modoGga = ModoGga.NUNCA,
            intervaloGgaSegundos = 10,
            editable = false,
        ),
        PerfilNtrip(
            id = ID_CERCANA,
            nombre = "Euskadi · red/estación cercana (CERCANA3M)",
            host = "www.gps2.euskadi.eus",
            ipsRespaldo = listOf("77.228.156.141", "150.241.41.141"),
            puerto = 2101,
            puntoMontaje = "CERCANA3M",
            modoGga = ModoGga.AUTO,
            intervaloGgaSegundos = 10,
            editable = false,
        ),
        PerfilNtrip(
            id = ID_BIZKAIA,
            nombre = "Bizkaia · Diputación Foral (CERCANA3M)",
            host = "80.245.2.245",
            ipsRespaldo = emptyList(),
            puerto = 2101,
            puntoMontaje = "CERCANA3M",
            modoGga = ModoGga.AUTO,
            intervaloGgaSegundos = 10,
            editable = false,
        ),
        // Bizkaia admite acceso anónimo público (usuario «anonimo», contraseña «1234»).
        // No se incrustan aquí: se introducen una vez en Ajustes y quedan cifradas.
    )
}
