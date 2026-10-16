package es.gailen.rtk.ui.diagnostico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.datos.ntrip.EstadoNtrip
import java.util.Locale

@Composable
fun DiagnosticoScreen(viewModel: DiagnosticoViewModel = hiltViewModel()) {
    val estado by viewModel.estadoNtrip.collectAsState()
    val stats by viewModel.estadisticas.collectAsState()
    val resumen by viewModel.resumen.collectAsState()
    val consola by viewModel.consola.collectAsState()
    val distancia by viewModel.distanciaBase.collectAsState()
    val perfil by viewModel.perfil.collectAsState()

    val conectado = estado == EstadoNtrip.CONECTADO ||
        estado == EstadoNtrip.CONECTANDO || estado == EstadoNtrip.REINTENTANDO

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // --- NTRIP ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("NTRIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Campo("Perfil", perfil?.nombre ?: "—")
                Campo("Servidor", perfil?.let { "${it.host}/${it.montaje}" } ?: "—")
                Campo("Estado", estado.etiqueta)
                Campo("Bytes recibidos", "%,d".format(stats.bytesTotales))
                Campo("Tasa", "${stats.tasaBps} B/s")
                stats.ultimoError?.let { Campo("Último error", it) }
                Spacer(Modifier.height(10.dp))
                if (conectado) {
                    OutlinedButton(onClick = viewModel::desconectar, modifier = Modifier.fillMaxWidth()) {
                        Text("Desconectar")
                    }
                } else {
                    Button(onClick = viewModel::conectar, modifier = Modifier.fillMaxWidth()) {
                        Text("Conectar")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Base (1005/1006) ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Estación base (1005/1006)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val base = resumen.estacionBase
                Campo("ID estación", base?.idEstacion?.toString() ?: "—")
                Campo("Lat/Lon base", base?.let { "%.6f, %.6f".format(Locale.US, it.geodesica.lat, it.geodesica.lon) } ?: "—")
                Campo("Distancia a la base", distancia?.let { formatearDistancia(it) } ?: "—")
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Contadores RTCM ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Mensajes RTCM por tipo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Campo("Tramas totales", resumen.tramasTotales.toString())
                Spacer(Modifier.height(4.dp))
                if (resumen.contadores.isEmpty()) {
                    Text("— sin datos —", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    resumen.contadores.forEach { (tipo, n) ->
                        Campo(tipo.toString(), n.toString())
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Consola NMEA ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Consola NMEA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (consola.isEmpty()) {
                        Text("— sin datos —", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        consola.takeLast(30).forEach { linea ->
                            Text(
                                linea,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontWeight = FontWeight.Medium)
    }
}

private fun formatearDistancia(m: Double): String =
    if (m < 1000) "%.1f m".format(Locale.US, m) else "%.3f km".format(Locale.US, m / 1000)
