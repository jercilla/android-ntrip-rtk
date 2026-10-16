package es.gailen.rtk.ui.medicion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.dominio.medicion.ResultadoMedicion
import java.util.Locale

@Composable
fun MedicionScreen(viewModel: MedicionViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val alturaDefecto by viewModel.alturaJalonPorDefecto.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (estado.fase) {
            FaseMedicion.INACTIVA -> Inactiva(onMedir = viewModel::iniciar)
            FaseMedicion.MIDIENDO, FaseMedicion.PAUSADA -> EnCurso(estado, onCancelar = viewModel::cancelar)
            FaseMedicion.COMPLETADA -> Completada(
                resultado = estado.resultado!!,
                porcentajeFix = estado.porcentajeFix,
                alturaDefecto = alturaDefecto,
                onGuardar = { n, notas, alt -> viewModel.guardar(n, notas, alt) {} },
                onDescartar = viewModel::cancelar,
            )
        }
    }
}

@Composable
private fun Inactiva(onMedir: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Medición de punto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Promedia posiciones durante la duración configurada, aceptando solo épocas " +
                    "con RTK fix (o float si lo activas en Ajustes). Con la fuente simulada " +
                    "se alcanza fix; el GPS del móvil es solo autónomo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onMedir, modifier = Modifier.fillMaxWidth()) { Text("Medir punto") }
        }
    }
}

@Composable
private fun EnCurso(estado: EstadoMedicion, onCancelar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Midiendo…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val progreso = if (estado.objetivoEpocas == 0) 0f
            else (estado.usadas.toFloat() / estado.objetivoEpocas).coerceIn(0f, 1f)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Campo("Épocas fix", "${estado.usadas} / ${estado.objetivoEpocas}")
            Campo("% fix", "%.0f%%".format(estado.porcentajeFix))
            Campo("σ horizontal", estado.sigmaHorizontal?.let { "%.3f m".format(Locale.US, it) } ?: "—")
            if (estado.fase == FaseMedicion.PAUSADA) {
                Text(
                    "⏸ Esperando RTK fix… (el contador está en pausa)",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}

@Composable
private fun Completada(
    resultado: ResultadoMedicion,
    porcentajeFix: Double,
    alturaDefecto: Double,
    onGuardar: (String, String, Double) -> Unit,
    onDescartar: () -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var altura by remember { mutableStateOf(alturaDefecto.toString()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Punto medido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Campo("Latitud", "%.7f°".format(Locale.US, resultado.lat))
            Campo("Longitud", "%.7f°".format(Locale.US, resultado.lon))
            Campo("Altura elipsoidal", "%.3f m".format(Locale.US, resultado.alturaElipsoidal))
            Campo("σ N / E / U", "%.3f / %.3f / %.3f m".format(Locale.US, resultado.sigmaNorte, resultado.sigmaEste, resultado.sigmaAltura))
            Campo("Épocas", resultado.epocasUsadas.toString())
            Campo("% fix", "%.0f%%".format(porcentajeFix))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre / código") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(notas, { notas = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                altura, { altura = it }, label = { Text("Altura del jalón (m)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onGuardar(nombre, notas, altura.toDoubleOrNull() ?: 0.0) },
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
                OutlinedButton(onClick = onDescartar, modifier = Modifier.weight(1f)) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontWeight = FontWeight.Medium)
    }
}
