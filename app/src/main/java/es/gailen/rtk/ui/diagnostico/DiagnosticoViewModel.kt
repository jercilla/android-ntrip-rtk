package es.gailen.rtk.ui.diagnostico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.nmea.GeneradorGga
import es.gailen.rtk.datos.ntrip.ClienteNtrip
import es.gailen.rtk.datos.ntrip.ConfigNtrip
import es.gailen.rtk.datos.ntrip.EstadoNtrip
import es.gailen.rtk.datos.ntrip.EstadisticasNtrip
import es.gailen.rtk.datos.rtcm.ContadorRtcm
import es.gailen.rtk.datos.rtcm.EstacionBase
import es.gailen.rtk.datos.rtcm.ResumenRtcm
import es.gailen.rtk.dominio.ModoGga
import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.geo.GeodesiaEcef
import es.gailen.rtk.geo.Geodesica
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nombre y montaje del perfil NTRIP activo, para mostrarlos. */
data class PerfilResumen(val nombre: String, val montaje: String, val host: String)

@HiltViewModel
class DiagnosticoViewModel @Inject constructor(
    private val cliente: ClienteNtrip,
    private val gestor: GestorFuente,
    private val ajustesRepo: AjustesRepository,
    private val registro: RegistroDebugHttp,
) : ViewModel() {

    val estadoNtrip: StateFlow<EstadoNtrip> = cliente.estado
    val estadisticas: StateFlow<EstadisticasNtrip> = cliente.estadisticas

    private val contador = ContadorRtcm()
    private val _resumen = MutableStateFlow(ResumenRtcm())
    val resumen: StateFlow<ResumenRtcm> = _resumen.asStateFlow()

    private val _consola = MutableStateFlow<List<String>>(emptyList())
    val consola: StateFlow<List<String>> = _consola.asStateFlow()

    private val _distanciaBase = MutableStateFlow<Double?>(null)
    val distanciaBase: StateFlow<Double?> = _distanciaBase.asStateFlow()

    val perfil: StateFlow<PerfilResumen?> =
        combine(ajustesRepo.ajustes, ajustesRepo.perfiles) { a, perfiles ->
            perfiles.firstOrNull { it.id == a.perfilNtripActivoId }?.let {
                PerfilResumen(it.nombre, it.puntoMontaje, "${it.host}:${it.puerto}")
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Pipe RTCM: cuenta tipos/base y reenvía al receptor (no-op salvo USB).
        viewModelScope.launch {
            var ultimoLog = 0
            cliente.datosRtcm.collect { bytes ->
                val r = contador.procesar(bytes)
                _resumen.value = r
                gestor.enviarCorrecciones(bytes)
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
        // Consola NMEA (últimas líneas de la fuente activa).
        viewModelScope.launch {
            gestor.nmea.collect { linea ->
                _consola.value = (_consola.value + linea).takeLast(MAX_CONSOLA)
            }
        }
        // Distancia a la base a partir de 1005/1006 y la posición actual.
        viewModelScope.launch {
            combine(_resumen, gestor.posicion) { r, p -> distancia(r.estacionBase, p) }
                .collect { _distanciaBase.value = it }
        }
    }

    fun conectar() {
        contador.reiniciar()
        _resumen.value = ResumenRtcm()
        viewModelScope.launch {
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
            cliente.iniciar(viewModelScope, config) { ggaActual() }
        }
    }

    fun desconectar() = cliente.detener()

    /** GGA a partir de la posición actual de la fuente, o null si aún no hay coordenadas. */
    private fun ggaActual(): String? {
        val p = gestor.posicion.value ?: return null
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

    override fun onCleared() {
        cliente.detener()
    }

    private companion object {
        const val MAX_CONSOLA = 60
    }
}
