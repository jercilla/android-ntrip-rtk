package es.gailen.rtk.datos.ntrip

import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.nmea.GeneradorGga
import es.gailen.rtk.datos.rtcm.ContadorRtcm
import es.gailen.rtk.datos.rtcm.EstacionBase
import es.gailen.rtk.datos.rtcm.ResumenRtcm
import es.gailen.rtk.dominio.ModoGga
import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.geo.GeodesiaEcef
import es.gailen.rtk.geo.Geodesica
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestador NTRIP de ámbito de aplicación: conecta el [ClienteNtrip], cuenta las
 * tramas con [ContadorRtcm], mantiene la estación base y la distancia, y reenvía las
 * correcciones a la fuente activa. Al ser singleton y usar su propio scope, sigue vivo
 * aunque la UI cambie de pantalla (y, con el servicio en primer plano, con pantalla apagada).
 */
@Singleton
class GestorNtrip @Inject constructor(
    private val cliente: ClienteNtrip,
    private val gestorFuente: GestorFuente,
    private val ajustesRepo: AjustesRepository,
    private val registro: RegistroDebugHttp,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contador = ContadorRtcm()

    val estado: StateFlow<EstadoNtrip> = cliente.estado
    val estadisticas: StateFlow<EstadisticasNtrip> = cliente.estadisticas

    private val _resumen = MutableStateFlow(ResumenRtcm())
    val resumen: StateFlow<ResumenRtcm> = _resumen.asStateFlow()

    private val _distanciaBase = MutableStateFlow<Double?>(null)
    val distanciaBase: StateFlow<Double?> = _distanciaBase.asStateFlow()

    private val _activo = MutableStateFlow(false)
    val activo: StateFlow<Boolean> = _activo.asStateFlow()

    init {
        scope.launch {
            var ultimoLog = 0
            cliente.datosRtcm.collect { bytes ->
                val r = contador.procesar(bytes)
                _resumen.value = r
                gestorFuente.enviarCorrecciones(bytes)
                if (r.tramasTotales - ultimoLog >= 25) {
                    ultimoLog = r.tramasTotales
                    registro.registra(
                        "rtcm",
                        "tramas=${r.tramasTotales} tipos=${r.contadores} " +
                            "base=${r.estacionBase?.idEstacion} dist=${_distanciaBase.value}",
                    )
                }
            }
        }
        scope.launch {
            combine(_resumen, gestorFuente.posicion) { r, p -> distancia(r.estacionBase, p) }
                .collect { _distanciaBase.value = it }
        }
    }

    fun conectar() {
        contador.reiniciar()
        _resumen.value = ResumenRtcm()
        _activo.value = true
        scope.launch {
            val ajustes = ajustesRepo.ajustes.first()
            val perfil = ajustesRepo.perfiles.first().firstOrNull { it.id == ajustes.perfilNtripActivoId }
                ?: run { registro.registra("ntrip", "conectar: sin perfil activo"); return@launch }
            val cred = ajustesRepo.leerCredenciales(perfil.id)
            val config = ConfigNtrip(
                host = perfil.host,
                ipsRespaldo = perfil.ipsRespaldo,
                puerto = perfil.puerto,
                puntoMontaje = perfil.puntoMontaje,
                usuario = cred?.usuario.orEmpty(),
                password = cred?.password.orEmpty(),
                enviarGga = perfil.modoGga != ModoGga.NUNCA,
                intervaloGgaSegundos = perfil.intervaloGgaSegundos,
            )
            registro.registra(
                "ntrip",
                "conectar ${config.host}:${config.puerto}/${config.puntoMontaje} " +
                    "usuario=${config.usuario.ifEmpty { "(vacío)" }} gga=${config.enviarGga}",
            )
            cliente.iniciar(scope, config) { ggaActual() }
        }
    }

    fun desconectar() {
        _activo.value = false
        cliente.detener()
    }

    private fun ggaActual(): String? {
        val p = gestorFuente.posicion.value ?: return null
        val lat = p.lat ?: return null
        val lon = p.lon ?: return null
        return GeneradorGga.generar(lat, lon, p.alturaElipsoidal, p.satelites, p.hdop, p.calidad, p.tiempoUtc)
    }

    private fun distancia(base: EstacionBase?, pos: Posicion?): Double? {
        if (base == null || pos?.lat == null || pos.lon == null) return null
        val posEcef = GeodesiaEcef.geodesicaAEcef(
            Geodesica(pos.lat, pos.lon, pos.alturaElipsoidal ?: base.geodesica.altura),
        )
        return base.ecef.distanciaA(posEcef)
    }
}
