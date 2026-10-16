package es.gailen.rtk.ui.estado

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.runtime.Composable
import es.gailen.rtk.ui.comun.PantallaEnConstruccion

@Composable
fun EstadoScreen() {
    PantallaEnConstruccion(
        titulo = "Estado en vivo",
        icono = Icons.Filled.GpsFixed,
        hito = "Hito 2 (parser NMEA y pantalla de estado)",
    )
}
