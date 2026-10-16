package es.gailen.rtk.ui.replanteo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.Composable
import es.gailen.rtk.ui.comun.PantallaEnConstruccion

@Composable
fun ReplanteoScreen() {
    PantallaEnConstruccion(
        titulo = "Replanteo (stakeout)",
        icono = Icons.Filled.Explore,
        hito = "Hito 6 (guiado con distancia y rumbo)",
    )
}
