package es.gailen.rtk.datos.fuente

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.FuentePosicionTipo
import javax.inject.Inject
import javax.inject.Singleton

/** Crea la [FuentePosicion] correspondiente al tipo seleccionado en ajustes. */
@Singleton
class FabricaFuente @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ajustesRepo: AjustesRepository,
    private val registro: RegistroDebugHttp,
) {
    fun crear(tipo: FuentePosicionTipo): FuentePosicion = when (tipo) {
        FuentePosicionTipo.SIMULADA -> FuenteSimulada(context)
        FuentePosicionTipo.GPS_MOVIL -> FuenteGpsMovil(context, registro = registro)
        FuentePosicionTipo.USB_SERIE -> FuenteUsbSerie(context, ajustesRepo, registro)
    }
}
