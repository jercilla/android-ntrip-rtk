package es.gailen.rtk.ui.diagnostico

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import es.gailen.rtk.ui.comun.PantallaEnConstruccion

@Composable
fun DiagnosticoScreen() {
    PantallaEnConstruccion(
        titulo = "Diagnóstico",
        icono = Icons.Filled.BugReport,
        hito = "Hito 3 (consola NMEA y contadores RTCM)",
    )
}
