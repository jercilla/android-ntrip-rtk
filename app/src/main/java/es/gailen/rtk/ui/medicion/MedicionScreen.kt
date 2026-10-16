package es.gailen.rtk.ui.medicion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.runtime.Composable
import es.gailen.rtk.ui.comun.PantallaEnConstruccion

@Composable
fun MedicionScreen() {
    PantallaEnConstruccion(
        titulo = "Medición de punto",
        icono = Icons.Outlined.AddLocationAlt,
        hito = "Hito 5 (promediado estático y guardado)",
    )
}
