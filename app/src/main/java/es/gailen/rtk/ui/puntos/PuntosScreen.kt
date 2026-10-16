package es.gailen.rtk.ui.puntos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import es.gailen.rtk.ui.comun.PantallaEnConstruccion

@Composable
fun PuntosScreen() {
    PantallaEnConstruccion(
        titulo = "Puntos guardados",
        icono = Icons.Filled.Place,
        hito = "Hitos 5 y 7 (lista, Room y exportación)",
    )
}
