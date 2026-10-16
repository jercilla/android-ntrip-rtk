package es.gailen.rtk.ui.estado

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.ui.theme.AmbarFloat
import es.gailen.rtk.ui.theme.RojoSinFix
import es.gailen.rtk.ui.theme.VerdeFix
import java.util.Locale

@Composable
fun EstadoScreen(viewModel: EstadoViewModel = hiltViewModel()) {
    val tipo by viewModel.tipoFuente.collectAsState()
    val estadoFuente by viewModel.estadoFuente.collectAsState()
    val posicion by viewModel.posicion.collectAsState()
    val sesionActiva by viewModel.sesionActiva.collectAsState()

    val contexto = LocalContext.current
    var permiso by remember { mutableStateOf(tienePermisoUbicacion(contexto)) }
    val lanzadorPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        permiso = concedido
        if (concedido) viewModel.conectar()
    }
    // Permiso de notificaciones (Android 13+): se pide al iniciar la sesión.
    val lanzadorNotif = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.iniciarSesion() }

    LaunchedEffect(tipo, permiso) {
        if (tipo == FuentePosicionTipo.GPS_MOVIL) {
            if (permiso) viewModel.conectar()
            else lanzadorPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TarjetaCalidad(posicion, estadoFuente)

        Spacer(Modifier.height(12.dp))
        TarjetaSesion(
            activa = sesionActiva,
            onToggle = { activar ->
                if (activar) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !tienePermiso(contexto, Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        lanzadorNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.iniciarSesion()
                    }
                } else {
                    viewModel.pararSesion()
                }
            },
            onBateria = { abrirAjustesBateria(contexto) },
        )

        if (tipo == FuentePosicionTipo.GPS_MOVIL && !permiso) {
            Spacer(Modifier.height(12.dp))
            TarjetaPermiso { lanzadorPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        }

        Spacer(Modifier.height(12.dp))
        TarjetaDatos(posicion)

        Spacer(Modifier.height(12.dp))
        TarjetaFuente(tipo, estadoFuente)
    }
}

@Composable
private fun TarjetaCalidad(posicion: Posicion?, estado: EstadoFuente) {
    val calidad = posicion?.calidad
    val color = calidad?.let(::colorCalidad) ?: Color.Gray
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = calidad?.etiqueta ?: textoEstado(estado),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = if (posicion == null) "Sin posición todavía"
                else "Época: ${posicion.tiempoUtc}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun TarjetaPermiso(onConceder: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Se necesita permiso de ubicación", style = MaterialTheme.typography.titleSmall)
            Text(
                "La fuente «GPS del móvil» necesita acceso a la ubicación precisa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onConceder) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Text("Conceder permiso", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun TarjetaSesion(activa: Boolean, onToggle: (Boolean) -> Unit, onBateria: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Sesión en segundo plano", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (activa) "Activa: sigue con la pantalla apagada" else "Inactiva",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = activa, onCheckedChange = onToggle)
            }
            TextButton(onClick = onBateria) { Text("Excluir de la optimización de batería") }
            Text(
                "En Xiaomi (MIUI/HyperOS): activa además el «Inicio automático» de la app y pon la batería en «Sin restricciones».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TarjetaDatos(posicion: Posicion?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Campo("Latitud", grados(posicion?.lat))
            Campo("Longitud", grados(posicion?.lon))
            Campo("Altura elipsoidal", fmt(posicion?.alturaElipsoidal, "%.3f", " m"))
            Campo("Satélites", posicion?.satelites?.toString() ?: "—")
            Campo("HDOP", fmt(posicion?.hdop, "%.1f"))
            Campo("Edad correcciones", fmt(posicion?.edadCorrecciones, "%.1f", " s"))
            Campo("Precisión horiz. (σ)", fmt(posicion?.precisionEstimada?.horizontal, "%.3f", " m"))
            Campo("Rumbo", fmt(posicion?.rumboGrados, "%.1f", "°"))
            Campo("Velocidad", fmt(posicion?.velocidadMs, "%.2f", " m/s"))
        }
    }
}

@Composable
private fun TarjetaFuente(tipo: FuentePosicionTipo, estado: EstadoFuente) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Campo("Fuente", tipo.etiqueta)
            Campo("Estado", textoEstado(estado))
            Text(
                "La fuente se cambia en Ajustes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontWeight = FontWeight.Medium)
    }
}

// --- helpers ---

private fun colorCalidad(c: CalidadFix): Color = when (c) {
    CalidadFix.SIN_FIX, CalidadFix.AUTONOMO -> RojoSinFix
    CalidadFix.DGPS, CalidadFix.RTK_FLOAT -> AmbarFloat
    CalidadFix.RTK_FIX -> VerdeFix
}

private fun textoEstado(e: EstadoFuente): String = when (e) {
    EstadoFuente.DESCONECTADA -> "Desconectada"
    EstadoFuente.CONECTANDO -> "Conectando…"
    EstadoFuente.CONECTADA -> "Conectada"
    EstadoFuente.ERROR -> "Error"
}

private fun grados(v: Double?): String =
    if (v == null) "—" else String.format(Locale.US, "%.7f°", v)

private fun fmt(v: Double?, patron: String, sufijo: String = ""): String =
    if (v == null || v.isNaN()) "—" else String.format(Locale.US, patron, v) + sufijo

private fun tienePermisoUbicacion(contexto: Context): Boolean =
    tienePermiso(contexto, Manifest.permission.ACCESS_FINE_LOCATION)

private fun tienePermiso(contexto: Context, permiso: String): Boolean =
    ContextCompat.checkSelfPermission(contexto, permiso) == PackageManager.PERMISSION_GRANTED

private fun abrirAjustesBateria(contexto: Context) {
    runCatching {
        contexto.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
