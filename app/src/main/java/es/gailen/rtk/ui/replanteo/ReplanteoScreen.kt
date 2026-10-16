package es.gailen.rtk.ui.replanteo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.replanteo.GuiaReplanteo
import es.gailen.rtk.ui.theme.AmbarFloat
import es.gailen.rtk.ui.theme.RojoSinFix
import es.gailen.rtk.ui.theme.VerdeFix
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReplanteoScreen(viewModel: ReplanteoViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val puntos by viewModel.puntos.collectAsState()

    if (estado.objetivo == null) {
        SelectorObjetivo(puntos, onSeleccionar = viewModel::seleccionar)
    } else {
        Guiado(estado, onCambiar = viewModel::limpiar)
    }
}

@Composable
private fun SelectorObjetivo(
    puntos: List<es.gailen.rtk.datos.puntos.PuntoMedido>,
    onSeleccionar: (es.gailen.rtk.datos.puntos.PuntoMedido) -> Unit,
) {
    if (puntos.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Mide y guarda un punto primero para poder replantearlo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Elige el punto a replantear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(puntos, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth().clickable { onSeleccionar(p) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(p.nombre, fontWeight = FontWeight.Bold)
                        Text("%.7f, %.7f".format(Locale.US, p.lat, p.lon), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Guiado(estado: EstadoReplanteo, onCambiar: () -> Unit) {
    val guia = estado.guia
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                estado.objetivo?.nombre ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCambiar) { Text("Cambiar") }
        }
        Spacer(Modifier.height(8.dp))

        if (guia == null || !estado.tienePosicion) {
            Text("Esperando posición…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp))
            return
        }

        val color = colorDistancia(guia.distanciaHorizontal, estado.calidad)

        // Distancia grande en el centro, con color.
        Text(
            formatearDistancia(guia.distanciaHorizontal),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(12.dp))

        if (guia.distanciaHorizontal > 3.0) {
            VistaLejana(estado, guia, color)
        } else {
            VistaCercana(guia, color)
        }

        Spacer(Modifier.height(16.dp))
        PanelNumerico(guia)
    }
}

@Composable
private fun VistaLejana(estado: EstadoReplanteo, guia: GuiaReplanteo, color: Color) {
    // Rumbo de referencia: el del movimiento GNSS si te mueves; si no, la brújula (aprox).
    val seMueve = (estado.velocidadMs ?: 0.0) > 0.5 && estado.rumboMovimiento != null
    val azimutBrujula = rememberAzimut()
    val rumboRef = if (seMueve) estado.rumboMovimiento!!.toFloat() else azimutBrujula
    val anguloFlecha = ((guia.rumbo.toFloat() - rumboRef) % 360 + 360) % 360

    Flecha(anguloFlecha, color)
    Text(
        if (seMueve) "Flecha según tu rumbo de avance (GNSS)"
        else "Flecha según la brújula del móvil (aproximada)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VistaCercana(guia: GuiaReplanteo, color: Color) {
    val escalaMax = when {
        guia.distanciaHorizontal <= 0.10 -> 0.10
        guia.distanciaHorizontal <= 0.30 -> 0.30
        else -> 1.0
    }
    Diana(guia.norte, guia.este, escalaMax, color)
    Text("Escala: ${(escalaMax * 100).roundToInt()} cm (anillo exterior)",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Flecha(anguloGrados: Float, color: Color) {
    Canvas(Modifier.size(200.dp)) {
        rotate(anguloGrados) {
            val cx = size.width / 2
            val cy = size.height / 2
            val l = size.minDimension
            val path = Path().apply {
                moveTo(cx, cy - l * 0.40f)
                lineTo(cx - l * 0.22f, cy + l * 0.30f)
                lineTo(cx, cy + l * 0.12f)
                lineTo(cx + l * 0.22f, cy + l * 0.30f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun Diana(norte: Double, este: Double, escalaMax: Double, color: Color) {
    val gris = MaterialTheme.colorScheme.outline
    val verde = VerdeFix
    Canvas(Modifier.size(240.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radio = size.minDimension / 2 * 0.92f
        val escalaPx = (radio / escalaMax).toFloat()
        // Anillos
        drawCircle(gris, radio, Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(gris, radio * 0.66f, Offset(cx, cy), style = Stroke(width = 2f))
        drawCircle(gris, radio * 0.33f, Offset(cx, cy), style = Stroke(width = 2f))
        // Cruz
        drawLine(gris, Offset(cx, cy - radio), Offset(cx, cy + radio), strokeWidth = 2f)
        drawLine(gris, Offset(cx - radio, cy), Offset(cx + radio, cy), strokeWidth = 2f)
        // Centro = objetivo
        drawCircle(verde, 7f, Offset(cx, cy))
        // Tu posición relativa al objetivo (norte arriba, este a la derecha)
        val x = (cx - (este * escalaPx)).toFloat()
        val y = (cy + (norte * escalaPx)).toFloat()
        val xr = x.coerceIn(0f, size.width)
        val yr = y.coerceIn(0f, size.height)
        drawCircle(color, 14f, Offset(xr, yr))
    }
}

@Composable
private fun PanelNumerico(guia: GuiaReplanteo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Campo("Distancia", formatearDistancia(guia.distanciaHorizontal))
            Campo("Rumbo", "%.0f°".format(guia.rumbo))
            Campo("Desplázate", descripcionNE(guia.norte, guia.este))
            guia.desnivel?.let { Campo("Desnivel", "%+.3f m".format(Locale.US, it)) }
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

// --- helpers ---

private fun colorDistancia(d: Double, calidad: CalidadFix?): Color = when {
    d > 1.0 -> RojoSinFix
    d > 0.03 -> AmbarFloat
    else -> if (calidad == CalidadFix.RTK_FIX) VerdeFix else AmbarFloat // verde solo con RTK fix
}

private fun formatearDistancia(m: Double): String = when {
    m >= 10 -> "%.1f m".format(Locale.US, m)
    m >= 1 -> "%.2f m".format(Locale.US, m)
    else -> "%.0f cm".format(Locale.US, m * 100)
}

private fun descripcionNE(norte: Double, este: Double): String {
    val ns = if (norte >= 0) "N" else "S"
    val eo = if (este >= 0) "E" else "O"
    return "%.2f m al %s · %.2f m al %s".format(Locale.US, abs(norte), ns, abs(este), eo)
}

@Composable
private fun rememberAzimut(): Float {
    val contexto = LocalContext.current
    var azimut by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = contexto.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rot = FloatArray(9)
            private val ori = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                azimut = ((Math.toDegrees(ori[0].toDouble()).toFloat()) + 360) % 360
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return azimut
}
