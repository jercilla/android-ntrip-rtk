package es.gailen.rtk.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.dominio.AjustesApp
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.ModoGga
import es.gailen.rtk.dominio.PerfilNtrip

@Composable
fun AjustesScreen(viewModel: AjustesViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    var perfilEnEdicion by remember { mutableStateOf<PerfilNtrip?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SeccionFuente(
            ajustes = estado.ajustes,
            onFuente = viewModel::cambiarFuente,
            onBaudrate = viewModel::cambiarBaudrate,
        )
        EspacioSeccion()
        SeccionMedicion(
            ajustes = estado.ajustes,
            onAltura = viewModel::cambiarAlturaJalon,
            onDuracion = viewModel::cambiarDuracion,
            onAceptarFloat = viewModel::cambiarAceptarFloat,
        )
        EspacioSeccion()
        SeccionDiagnostico(
            url = estado.ajustes.urlDebug,
            onUrl = viewModel::cambiarUrlDebug,
        )
        EspacioSeccion()
        SeccionPerfiles(
            estado = estado,
            onSeleccionar = viewModel::seleccionarPerfilActivo,
            onEditar = { perfilEnEdicion = it },
            onEliminar = viewModel::eliminarPerfil,
            onNuevo = {
                perfilEnEdicion = PerfilNtrip(
                    id = "perfil-" + System.currentTimeMillis(),
                    nombre = "Nuevo perfil",
                    host = "",
                    puerto = 2101,
                    puntoMontaje = "",
                    modoGga = ModoGga.AUTO,
                    editable = true,
                )
            },
        )
    }

    perfilEnEdicion?.let { perfil ->
        DialogoPerfil(
            perfil = perfil,
            tieneCredenciales = perfil.id in estado.idsConCredenciales,
            leerCredenciales = { viewModel.leerCredenciales(perfil.id) },
            onGuardar = { actualizado, usuario, password ->
                viewModel.guardarPerfil(actualizado, usuario, password)
                perfilEnEdicion = null
            },
            onBorrarCredenciales = { viewModel.borrarCredenciales(perfil.id) },
            onCerrar = { perfilEnEdicion = null },
        )
    }
}

@Composable
private fun SeccionFuente(
    ajustes: AjustesApp,
    onFuente: (FuentePosicionTipo) -> Unit,
    onBaudrate: (Int) -> Unit,
) {
    TituloSeccion("Fuente de posición")
    FuentePosicionTipo.entries.forEach { tipo ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFuente(tipo) },
        ) {
            RadioButton(selected = ajustes.fuente == tipo, onClick = { onFuente(tipo) })
            Text(tipo.etiqueta, modifier = Modifier.padding(start = 4.dp))
        }
    }
    if (ajustes.fuente == FuentePosicionTipo.USB_SERIE) {
        Spacer(Modifier.height(8.dp))
        SelectorDesplegable(
            etiqueta = "Baudrate",
            opciones = AjustesApp.BAUDRATES,
            seleccion = ajustes.baudrateUsb,
            textoDe = { "$it bps" },
            onSeleccion = onBaudrate,
        )
    }
}

@Composable
private fun SeccionMedicion(
    ajustes: AjustesApp,
    onAltura: (Double) -> Unit,
    onDuracion: (Int) -> Unit,
    onAceptarFloat: (Boolean) -> Unit,
) {
    TituloSeccion("Medición")
    CampoNumero(
        etiqueta = "Altura del jalón (m)",
        valorInicial = ajustes.alturaJalonMetros.toString(),
        decimal = true,
        onValor = { it.toDoubleOrNull()?.let(onAltura) },
    )
    Spacer(Modifier.height(8.dp))
    CampoNumero(
        etiqueta = "Duración de la medición (s)",
        valorInicial = ajustes.duracionMedicionSegundos.toString(),
        decimal = false,
        onValor = { it.toIntOrNull()?.let(onDuracion) },
    )
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Aceptar épocas RTK float", modifier = Modifier.weight(1f))
        Switch(checked = ajustes.aceptarFloat, onCheckedChange = onAceptarFloat)
    }
}

@Composable
private fun SeccionDiagnostico(url: String, onUrl: (String) -> Unit) {
    TituloSeccion("Diagnóstico")
    var texto by remember { mutableStateOf(url) }
    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it; onUrl(it) },
        label = { Text("URL del servidor de depuración") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "La app envía telemetría (estado de la fuente, posiciones, errores) a este servidor. Vacío = desactivado.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SeccionPerfiles(
    estado: EstadoAjustes,
    onSeleccionar: (String) -> Unit,
    onEditar: (PerfilNtrip) -> Unit,
    onEliminar: (String) -> Unit,
    onNuevo: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Perfiles NTRIP",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onNuevo) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Nuevo", modifier = Modifier.padding(start = 4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    estado.perfiles.forEach { perfil ->
        FilaPerfil(
            perfil = perfil,
            activo = perfil.id == estado.ajustes.perfilNtripActivoId,
            tieneCredenciales = perfil.id in estado.idsConCredenciales,
            onSeleccionar = { onSeleccionar(perfil.id) },
            onEditar = { onEditar(perfil) },
            onEliminar = { onEliminar(perfil.id) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FilaPerfil(
    perfil: PerfilNtrip,
    activo: Boolean,
    tieneCredenciales: Boolean,
    onSeleccionar: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSeleccionar)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            RadioButton(selected = activo, onClick = onSeleccionar)
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(perfil.nombre, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${perfil.host}:${perfil.puerto} · ${perfil.puntoMontaje}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (tieneCredenciales) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.width(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (tieneCredenciales) "  Credenciales guardadas · GGA: ${perfil.modoGga.name.lowercase()}"
                        else "  Sin credenciales · GGA: ${perfil.modoGga.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEditar) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar")
            }
            if (perfil.editable) {
                IconButton(onClick = onEliminar) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoPerfil(
    perfil: PerfilNtrip,
    tieneCredenciales: Boolean,
    leerCredenciales: suspend () -> es.gailen.rtk.dominio.CredencialesNtrip?,
    onGuardar: (PerfilNtrip, usuario: String, password: String) -> Unit,
    onBorrarCredenciales: () -> Unit,
    onCerrar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(perfil.nombre) }
    var host by remember { mutableStateOf(perfil.host) }
    var ips by remember { mutableStateOf(perfil.ipsRespaldo.joinToString(", ")) }
    var puerto by remember { mutableStateOf(perfil.puerto.toString()) }
    var montaje by remember { mutableStateOf(perfil.puntoMontaje) }
    var modoGga by remember { mutableStateOf(perfil.modoGga) }
    var intervalo by remember { mutableStateOf(perfil.intervaloGgaSegundos.toString()) }
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Precargar el usuario existente (la contraseña se deja en blanco por seguridad).
    LaunchedEffect(perfil.id) {
        leerCredenciales()?.let { usuario = it.usuario }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                val actualizado = perfil.copy(
                    nombre = nombre.trim(),
                    host = host.trim(),
                    ipsRespaldo = ips.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    puerto = puerto.toIntOrNull() ?: perfil.puerto,
                    puntoMontaje = montaje.trim(),
                    modoGga = modoGga,
                    intervaloGgaSegundos = intervalo.toIntOrNull() ?: perfil.intervaloGgaSegundos,
                )
                onGuardar(actualizado, usuario.trim(), password)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        title = { Text(if (perfil.editable) "Editar perfil" else "Editar perfil (predefinido)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ips, { ips = it }, label = { Text("IPs de respaldo (coma)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        puerto, { puerto = it.filter(Char::isDigit) },
                        label = { Text("Puerto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(montaje, { montaje = it }, label = { Text("Punto montaje") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                SelectorDesplegable(
                    etiqueta = "Envío de GGA",
                    opciones = ModoGga.entries,
                    seleccion = modoGga,
                    textoDe = { it.etiqueta },
                    onSeleccion = { modoGga = it },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    intervalo, { intervalo = it.filter(Char::isDigit) },
                    label = { Text("Intervalo GGA (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Credenciales (se guardan cifradas)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(if (tieneCredenciales) "Contraseña (vacío = sin cambios)" else "Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tieneCredenciales) {
                    TextButton(onClick = onBorrarCredenciales) { Text("Borrar credenciales guardadas") }
                }
            }
        },
    )
}

// --- Utilidades de UI ---

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun EspacioSeccion() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

/** Campo de texto numérico con estado local, para no interferir con el flujo de ajustes. */
@Composable
private fun CampoNumero(
    etiqueta: String,
    valorInicial: String,
    decimal: Boolean,
    onValor: (String) -> Unit,
) {
    var texto by remember { mutableStateOf(valorInicial) }
    OutlinedTextField(
        value = texto,
        onValueChange = {
            texto = it
            onValor(it)
        },
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectorDesplegable(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T,
    textoDe: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = textoDe(seleccion),
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(textoDe(opcion)) },
                    onClick = {
                        onSeleccion(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}
