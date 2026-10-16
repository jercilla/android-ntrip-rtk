package es.gailen.rtk.ui.medicion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.ntrip.GestorNtrip
import es.gailen.rtk.datos.puntos.PuntoMedido
import es.gailen.rtk.datos.puntos.PuntosRepository
import es.gailen.rtk.dominio.medicion.AcumuladorMedicion
import es.gailen.rtk.dominio.medicion.ResultadoMedicion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FaseMedicion { INACTIVA, MIDIENDO, PAUSADA, COMPLETADA }

data class EstadoMedicion(
    val fase: FaseMedicion = FaseMedicion.INACTIVA,
    val objetivoEpocas: Int = 60,
    val usadas: Int = 0,
    val totales: Int = 0,
    val porcentajeFix: Double = 0.0,
    val sigmaHorizontal: Double? = null,
    val resultado: ResultadoMedicion? = null,
)

@HiltViewModel
class MedicionViewModel @Inject constructor(
    private val gestorFuente: GestorFuente,
    private val gestorNtrip: GestorNtrip,
    private val ajustesRepo: AjustesRepository,
    private val puntosRepo: PuntosRepository,
    private val registro: RegistroDebugHttp,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoMedicion())
    val estado: StateFlow<EstadoMedicion> = _estado.asStateFlow()

    /** Altura del jalón por defecto, para precargar el formulario. */
    val alturaJalonPorDefecto: StateFlow<Double> = ajustesRepo.ajustes
        .map { it.alturaJalonMetros }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private var acumulador: AcumuladorMedicion? = null
    private var objetivo = 60
    private var jobMedicion: Job? = null

    fun iniciar() {
        if (_estado.value.fase == FaseMedicion.MIDIENDO) return
        jobMedicion?.cancel()
        viewModelScope.launch {
            val ajustes = ajustesRepo.ajustes.first()
            objetivo = ajustes.duracionMedicionSegundos
            acumulador = AcumuladorMedicion(ajustes.aceptarFloat)
            _estado.value = EstadoMedicion(fase = FaseMedicion.MIDIENDO, objetivoEpocas = objetivo)
            jobMedicion = launch {
                gestorFuente.posicion.collect { pos -> if (pos != null) procesarEpoca(pos) }
            }
        }
    }

    private fun procesarEpoca(pos: es.gailen.rtk.dominio.Posicion) {
        val acc = acumulador ?: return
        val fase = _estado.value.fase
        if (fase != FaseMedicion.MIDIENDO && fase != FaseMedicion.PAUSADA) return

        val aceptada = acc.ofrecer(pos)
        val r = acc.resultado()
        val completa = acc.numEpocasUsadas >= objetivo
        _estado.value = _estado.value.copy(
            fase = if (completa) FaseMedicion.COMPLETADA else if (aceptada) FaseMedicion.MIDIENDO else FaseMedicion.PAUSADA,
            usadas = acc.numEpocasUsadas,
            totales = acc.numEpocasTotales,
            porcentajeFix = r?.porcentajeFix ?: 0.0,
            sigmaHorizontal = r?.sigmaHorizontal,
            resultado = if (completa) r else null,
        )
        if (completa) {
            jobMedicion?.cancel()
            r?.let {
                registro.registra(
                    "medicion",
                    "COMPLETADA lat=${it.lat} lon=${it.lon} alt=${it.alturaElipsoidal} " +
                        "σN=${"%.3f".format(it.sigmaNorte)} σE=${"%.3f".format(it.sigmaEste)} " +
                        "σU=${"%.3f".format(it.sigmaAltura)} épocas=${it.epocasUsadas}/${it.epocasTotales} " +
                        "fix=${"%.0f".format(it.porcentajeFix)}%",
                )
            }
        }
    }

    fun cancelar() {
        jobMedicion?.cancel()
        acumulador = null
        _estado.value = EstadoMedicion()
    }

    fun guardar(nombre: String, notas: String, alturaJalon: Double, onGuardado: () -> Unit) {
        val r = _estado.value.resultado ?: return
        viewModelScope.launch {
            val ajustes = ajustesRepo.ajustes.first()
            val perfil = ajustesRepo.perfiles.first().firstOrNull { it.id == ajustes.perfilNtripActivoId }
            val punto = PuntoMedido(
                nombre = nombre.ifBlank { "Punto" },
                notas = notas,
                lat = r.lat,
                lon = r.lon,
                alturaElipsoidal = r.alturaElipsoidal,
                alturaJalon = alturaJalon,
                cotaSuelo = r.alturaElipsoidal - alturaJalon,
                sigmaNorte = r.sigmaNorte,
                sigmaEste = r.sigmaEste,
                sigmaAltura = r.sigmaAltura,
                epocas = r.epocasUsadas,
                porcentajeFix = r.porcentajeFix,
                calidad = if (ajustes.aceptarFloat) "RTK fix/float" else "RTK fix",
                perfilNtrip = perfil?.nombre,
                puntoMontaje = perfil?.puntoMontaje,
                distanciaBase = gestorNtrip.distanciaBase.value,
                fechaHoraUtc = System.currentTimeMillis(),
            )
            val id = puntosRepo.guardar(punto)
            registro.registra(
                "medicion",
                "GUARDADO id=$id nombre='${punto.nombre}' jalon=${punto.alturaJalon} " +
                    "cota=${"%.3f".format(punto.cotaSuelo)} perfil=${punto.perfilNtrip} dist=${punto.distanciaBase}",
            )
            _estado.value = EstadoMedicion()
            onGuardado()
        }
    }
}
