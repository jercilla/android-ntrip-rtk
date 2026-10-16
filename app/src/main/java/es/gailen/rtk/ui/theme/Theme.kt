package es.gailen.rtk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val EsquemaClaro = lightColorScheme(
    primary = VerdeRtk,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = VerdeRtkClaro,
    secondary = VerdeRtkOscuro,
)

private val EsquemaOscuro = darkColorScheme(
    primary = VerdeRtkClaro,
    primaryContainer = VerdeRtkOscuro,
    secondary = VerdeRtkClaro,
)

@Composable
fun RTKEuskadiTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    // Color dinámico (Material You) disponible en Android 12+.
    colorDinamico: Boolean = true,
    content: @Composable () -> Unit,
) {
    val contexto = LocalContext.current
    val esquema = when {
        colorDinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        oscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }

    MaterialTheme(
        colorScheme = esquema,
        typography = TipografiaRtk,
        content = content,
    )
}
