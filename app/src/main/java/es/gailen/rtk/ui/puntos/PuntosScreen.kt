package es.gailen.rtk.ui.puntos

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.datos.puntos.PuntoMedido
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

@Composable
fun PuntosScreen(viewModel: PuntosViewModel = hiltViewModel()) {
    val puntos by viewModel.puntos.collectAsState()
    var enEdicion by remember { mutableStateOf<PuntoMedido?>(null) }

    if (puntos.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Aún no hay puntos guardados", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val contexto = LocalContext.current

    val lanzadorCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { viewModel.exportar(it, FormatoExport.CSV) { ok -> aviso(contexto, "CSV", ok) } }
    }
    val lanzadorGeoJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri ->
        uri?.let { viewModel.exportar(it, FormatoExport.GEOJSON) { ok -> aviso(contexto, "GeoJSON", ok) } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { lanzadorCsv.launch("puntos_rtk.csv") }, modifier = Modifier.weight(1f)) {
                Text("Exportar CSV")
            }
            Button(onClick = { lanzadorGeoJson.launch("puntos_rtk.geojson") }, modifier = Modifier.weight(1f)) {
                Text("Exportar GeoJSON")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            items(puntos, key = { it.id }) { punto ->
                FilaPunto(
                    punto = punto,
                    onEditar = { enEdicion = punto },
                    onBorrar = { viewModel.borrar(punto.id) },
                )
            }
        }
    }

    enEdicion?.let { punto ->
        DialogoEditar(
            punto = punto,
            onGuardar = { nombre, notas -> viewModel.actualizar(punto, nombre, notas); enEdicion = null },
            onCerrar = { enEdicion = null },
        )
    }
}

@Composable
private fun FilaPunto(punto: PuntoMedido, onEditar: () -> Unit, onBorrar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(punto.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "%.7f, %.7f".format(Locale.US, punto.lat, punto.lon),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "cota ${"%.3f".format(Locale.US, punto.cotaSuelo)} m · σH " +
                        "%.3f m · fix %.0f%%".format(
                            Locale.US,
                            kotlin.math.hypot(punto.sigmaNorte, punto.sigmaEste), punto.porcentajeFix,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    FORMATO_FECHA.format(Instant.ofEpochMilli(punto.fechaHoraUtc)) + " UTC · " + punto.calidad,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = onBorrar) { Icon(Icons.Filled.Delete, contentDescription = "Borrar") }
        }
    }
}

private fun aviso(contexto: Context, formato: String, ok: Boolean) {
    val texto = if (ok) "$formato exportado" else "Error al exportar $formato"
    Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show()
}

@Composable
private fun DialogoEditar(punto: PuntoMedido, onGuardar: (String, String) -> Unit, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(punto.nombre) }
    var notas by remember { mutableStateOf(punto.notas) }
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = { onGuardar(nombre, notas) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        title = { Text("Editar punto") },
        text = {
            Column {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas") })
            }
        },
    )
}
