package es.gailen.rtk.ui.replanteo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.puntos.PuntoMedido
import es.gailen.rtk.datos.puntos.PuntosRepository
import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.replanteo.CalculoReplanteo
import es.gailen.rtk.dominio.replanteo.GuiaReplanteo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class EstadoReplanteo(
    val objetivo: PuntoMedido? = null,
    val guia: GuiaReplanteo? = null,
    val calidad: CalidadFix? = null,
    val rumboMovimiento: Double? = null,
    val velocidadMs: Double? = null,
    val tienePosicion: Boolean = false,
)

@HiltViewModel
class ReplanteoViewModel @Inject constructor(
    private val gestorFuente: GestorFuente,
    puntosRepo: PuntosRepository,
) : ViewModel() {

    val puntos: StateFlow<List<PuntoMedido>> =
        puntosRepo.puntos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val objetivo = MutableStateFlow<PuntoMedido?>(null)

    val estado: StateFlow<EstadoReplanteo> =
        combine(objetivo, gestorFuente.posicion) { obj, pos ->
            if (obj == null) {
                EstadoReplanteo()
            } else {
                val guia = if (pos?.lat != null && pos.lon != null) {
                    CalculoReplanteo.guia(
                        pos.lat, pos.lon, pos.alturaElipsoidal, obj.lat, obj.lon, obj.alturaElipsoidal,
                    )
                } else {
                    null
                }
                EstadoReplanteo(
                    objetivo = obj,
                    guia = guia,
                    calidad = pos?.calidad,
                    rumboMovimiento = pos?.rumboGrados,
                    velocidadMs = pos?.velocidadMs,
                    tienePosicion = pos?.tieneCoordenadas == true,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoReplanteo())

    fun seleccionar(punto: PuntoMedido) { objetivo.value = punto }
    fun limpiar() { objetivo.value = null }
}
