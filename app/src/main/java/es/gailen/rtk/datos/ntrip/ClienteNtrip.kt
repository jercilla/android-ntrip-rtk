package es.gailen.rtk.datos.ntrip

import es.gailen.rtk.datos.debug.RegistroDebugHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Cliente NTRIP (v1 `ICY 200 OK` y v2 `HTTP/1.1 200 OK`). Conecta a un punto de montaje,
 * recibe el flujo RTCM3 crudo, envía GGA si el montaje lo requiere, y reconecta con
 * backoff exponencial. Prueba las IPs de respaldo si el host principal falla.
 *
 * El de-chunking de v2 no se implementa: el parser RTCM resincroniza por CRC, así que
 * los bytes de control de *chunked* (poco habituales en estos casters) se descartan solos.
 */
class ClienteNtrip @Inject constructor(
    private val registro: RegistroDebugHttp,
) {
    private val _estado = MutableStateFlow(EstadoNtrip.DESCONECTADO)
    val estado: StateFlow<EstadoNtrip> = _estado.asStateFlow()

    private val _estadisticas = MutableStateFlow(EstadisticasNtrip())
    val estadisticas: StateFlow<EstadisticasNtrip> = _estadisticas.asStateFlow()

    private val _datosRtcm = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val datosRtcm: SharedFlow<ByteArray> = _datosRtcm.asSharedFlow()

    private var job: Job? = null
    private var proveedorGga: (() -> String?)? = null

    /** Inicia la conexión (y su reconexión) en [scope]. [proveedorGga] da la GGA actual. */
    fun iniciar(scope: CoroutineScope, config: ConfigNtrip, proveedorGga: () -> String?) {
        detener()
        this.proveedorGga = proveedorGga
        job = scope.launch(Dispatchers.IO) { bucleConexion(config) }
    }

    fun detener() {
        job?.cancel()
        job = null
        _estado.value = EstadoNtrip.DESCONECTADO
        _estadisticas.value = _estadisticas.value.copy(conectadoDesdeMs = null)
    }

    private suspend fun bucleConexion(config: ConfigNtrip) {
        var backoff = 1_000L
        val hosts = buildList { add(config.host); addAll(config.ipsRespaldo) }
        while (coroutineContext.isActive) {
            try {
                conectarYLeer(config, hosts)
                backoff = 1_000L  // desconexión limpia del servidor: reintento rápido
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorAutenticacion) {
                registro?.registra("ntrip", "401 credenciales inválidas; no se reintenta")
                _estado.value = EstadoNtrip.ERROR_AUTENTICACION
                return
            } catch (e: ErrorMontaje) {
                registro?.registra("ntrip", "montaje no encontrado (sourcetable/404); no se reintenta")
                _estado.value = EstadoNtrip.ERROR_MONTAJE
                return
            } catch (e: Exception) {
                registro?.registra("ntrip", "error: ${e.message}")
                _estado.value = EstadoNtrip.ERROR
                _estadisticas.value = _estadisticas.value.copy(ultimoError = e.message, conectadoDesdeMs = null)
            }
            if (!coroutineContext.isActive) break
            _estado.value = EstadoNtrip.REINTENTANDO
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun conectarYLeer(config: ConfigNtrip, hosts: List<String>) {
        _estado.value = EstadoNtrip.CONECTANDO
        val socket = abrirSocket(hosts, config.puerto)
        socket.use { s ->
            val salida = s.getOutputStream()
            val entrada = s.getInputStream()
            salida.write(peticion(config, s.inetAddress?.hostAddress ?: config.host).toByteArray())
            salida.flush()

            val cabecera = leerCabecera(entrada)
            val estadoLinea = cabecera.firstOrNull().orEmpty()
            registro?.registra("ntrip", "respuesta: $estadoLinea")
            when {
                estadoLinea.contains(" 401") -> throw ErrorAutenticacion()
                estadoLinea.startsWith("SOURCETABLE") || estadoLinea.contains(" 404") -> throw ErrorMontaje()
                estadoLinea.contains(" 200") || estadoLinea.startsWith("ICY 200") -> Unit
                else -> throw IOException("Respuesta NTRIP inesperada: $estadoLinea")
            }

            _estado.value = EstadoNtrip.CONECTADO
            val ahoraConexion = System.currentTimeMillis()
            _estadisticas.value = EstadisticasNtrip(conectadoDesdeMs = ahoraConexion)

            s.soTimeout = 1_000
            val buffer = ByteArray(8 * 1024)
            var ultimoByteMs = ahoraConexion
            var ultimaGgaMs = 0L
            var bytesVentana = 0L
            var inicioVentana = ahoraConexion
            var total = _estadisticas.value.bytesTotales

            while (coroutineContext.isActive) {
                val ahora = System.currentTimeMillis()
                if (config.enviarGga && ahora - ultimaGgaMs >= config.intervaloGgaSegundos * 1000L) {
                    proveedorGga?.invoke()?.let { gga ->
                        runCatching {
                            salida.write((gga + "\r\n").toByteArray())
                            salida.flush()
                            registro?.registra("ntrip", "GGA enviada")
                        }
                    }
                    ultimaGgaMs = ahora
                }
                val n = try {
                    entrada.read(buffer)
                } catch (e: SocketTimeoutException) {
                    if (ahora - ultimoByteMs > 15_000) throw IOException("Sin datos durante 15 s")
                    continue
                }
                if (n < 0) throw IOException("El servidor cerró la conexión")
                if (n > 0) {
                    ultimoByteMs = ahora
                    total += n
                    bytesVentana += n
                    _datosRtcm.emit(buffer.copyOf(n))
                    if (ahora - inicioVentana >= 1_000) {
                        val tasa = (bytesVentana * 1000 / (ahora - inicioVentana)).toInt()
                        _estadisticas.value = _estadisticas.value.copy(
                            bytesTotales = total, tasaBps = tasa, conectadoDesdeMs = ahoraConexion,
                        )
                        bytesVentana = 0
                        inicioVentana = ahora
                    }
                }
            }
        }
    }

    /** Descarga y parsea la sourcetable (GET / sin autenticación). */
    suspend fun descargarSourcetable(host: String, puerto: Int): List<EntradaSourcetable> {
        val socket = abrirSocket(listOf(host), puerto)
        return socket.use { s ->
            val salida = s.getOutputStream()
            salida.write(
                ("GET / HTTP/1.0\r\nHost: $host:$puerto\r\nUser-Agent: $USER_AGENT\r\n" +
                    "Ntrip-Version: Ntrip/2.0\r\nAccept: */*\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            salida.flush()
            s.soTimeout = 8_000
            val texto = s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
            ParserSourcetable.parsear(texto.substringAfter("\r\n\r\n", texto))
        }
    }

    private fun abrirSocket(hosts: List<String>, puerto: Int): Socket {
        var ultima: Exception? = null
        for (h in hosts) {
            try {
                return Socket().apply { connect(InetSocketAddress(h, puerto), 8_000) }
            } catch (e: Exception) {
                ultima = e
                registro?.registra("ntrip", "no se pudo conectar a $h:$puerto (${e.message})")
            }
        }
        throw ultima ?: IOException("Sin hosts a los que conectar")
    }

    private fun peticion(config: ConfigNtrip, host: String): String {
        val auth = Base64.getEncoder().encodeToString("${config.usuario}:${config.password}".toByteArray())
        return buildString {
            append("GET /${config.puntoMontaje} HTTP/1.1\r\n")
            append("Host: $host:${config.puerto}\r\n")
            append("Ntrip-Version: Ntrip/2.0\r\n")
            append("User-Agent: $USER_AGENT\r\n")
            append("Authorization: Basic $auth\r\n")
            append("Accept: */*\r\n")
            append("\r\n")
        }
    }

    /** Lee la cabecera HTTP/ICY hasta la línea en blanco; devuelve sus líneas. */
    private fun leerCabecera(entrada: InputStream): List<String> {
        val sb = StringBuilder()
        var b = entrada.read()
        while (b != -1) {
            sb.append(b.toChar())
            if (sb.length >= 4 && sb.endsWith("\r\n\r\n")) break
            if (sb.length > 64 * 1024) break
            b = entrada.read()
        }
        return sb.toString().split("\r\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private class ErrorAutenticacion : IOException()
    private class ErrorMontaje : IOException()

    companion object {
        const val USER_AGENT = "NTRIP RTKEuskadi/0.1"
    }
}
