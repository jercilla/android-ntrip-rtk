package es.gailen.rtk.datos.debug

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import es.gailen.rtk.datos.ajustes.AjustesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envía la telemetría de depuración al servidor de desarrollo mediante `POST /log`.
 * Es "fire and forget": nunca bloquea ni lanza excepciones hacia la app. Si la URL de
 * depuración está vacía, no hace nada. Serializa los envíos en un canal con descarte
 * de los más antiguos para no acumular trabajo si el servidor no responde.
 */
@Singleton
class RegistroDebugHttp @Inject constructor(
    @ApplicationContext context: Context,
    private val ajustesRepo: AjustesRepository,
) : RegistroDebug {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sesion = sanear("${Build.MANUFACTURER}_${Build.MODEL}")
    private val canal = Channel<String>(capacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            for (linea in canal) enviar(linea)
        }
    }

    override fun registra(etiqueta: String, mensaje: String) {
        canal.trySend("[$etiqueta] $mensaje")
    }

    private suspend fun enviar(linea: String) {
        val base = runCatching { ajustesRepo.ajustes.first().urlDebug }.getOrNull()?.trim().orEmpty()
        if (base.isEmpty()) return
        val url = "$base/log?sesion=${URLEncoder.encode(sesion, "UTF-8")}"
        runCatching {
            val con = URL(url).openConnection() as HttpURLConnection
            try {
                con.requestMethod = "POST"
                con.connectTimeout = 2_000
                con.readTimeout = 2_000
                con.doOutput = true
                con.outputStream.use { it.write(linea.toByteArray(Charsets.UTF_8)) }
                con.responseCode // fuerza el envío
            } finally {
                con.disconnect()
            }
        }
    }

    private fun sanear(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
