package es.gailen.rtk.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinos de navegación de la app. [enBarra] marca los que aparecen en la barra
 * de navegación inferior; Diagnóstico se abre desde la barra superior.
 */
enum class Destino(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector,
    val enBarra: Boolean,
) {
    ESTADO("estado", "Estado", Icons.Filled.GpsFixed, true),
    MEDICION("medicion", "Medir", Icons.Outlined.AddLocationAlt, true),
    PUNTOS("puntos", "Puntos", Icons.Filled.Place, true),
    REPLANTEO("replanteo", "Replanteo", Icons.Filled.Explore, true),
    AJUSTES("ajustes", "Ajustes", Icons.Filled.Settings, true),
    DIAGNOSTICO("diagnostico", "Diagnóstico", Icons.Filled.BugReport, false);

    companion object {
        val INICIO = ESTADO
        fun porRuta(ruta: String?): Destino = entries.firstOrNull { it.ruta == ruta } ?: INICIO
    }
}
