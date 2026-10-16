package es.gailen.rtk.datos.servicio

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import es.gailen.rtk.MainActivity
import es.gailen.rtk.R
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.fuente.GestorFuente
import es.gailen.rtk.datos.ntrip.EstadoNtrip
import es.gailen.rtk.datos.ntrip.GestorNtrip
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano que mantiene viva la sesión (fuente + NTRIP) con la pantalla
 * apagada, con una notificación persistente que muestra calidad del fix, satélites y edad
 * de correcciones. Tipo location + connectedDevice (sección 5.5 del diseño).
 */
@AndroidEntryPoint
class ServicioSesion : Service() {

    @Inject lateinit var gestorFuente: GestorFuente
    @Inject lateinit var gestorNtrip: GestorNtrip
    @Inject lateinit var controlador: ControladorSesion
    @Inject lateinit var registro: RegistroDebugHttp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificaciones: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificaciones = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        crearCanal()
        controlador.marcar(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        arrancarEnPrimerPlano(notificacion("Iniciando sesión…", "Fuente: ${gestorFuente.tipo.value.etiqueta}"))
        adquirirWakeLock()
        registro.registra("sesion", "servicio en primer plano iniciado (tipo fuente=${gestorFuente.tipo.value})")
        // Garantizar que la fuente está conectada (salvo GPS, que requiere permiso desde la UI).
        gestorFuente.conectar()
        observarEstado()
        latido()
        return START_STICKY
    }

    private fun observarEstado() {
        scope.launch {
            combine(gestorFuente.posicion, gestorNtrip.estado) { pos, ntrip -> pos to ntrip }
                .collect { (pos, ntrip) ->
                    notificaciones.notify(ID_NOTIF, notificacion(tituloDe(pos), detalleDe(pos, ntrip)))
                }
        }
    }

    /** Latido periódico para verificar por telemetría que la sesión sobrevive (p. ej. con la pantalla apagada). */
    private fun latido() {
        scope.launch {
            while (isActive) {
                val p = gestorFuente.posicion.value
                registro.registra(
                    "sesion",
                    "latido · fuente=${gestorFuente.estado.value} ntrip=${gestorNtrip.estado.value} " +
                        "cal=${p?.calidad} sat=${p?.satelites}",
                )
                delay(30_000)
            }
        }
    }

    private fun arrancarEnPrimerPlano(notif: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var tipo = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            if (tienePermisoUbicacion()) {
                tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (tipo != 0) startForeground(ID_NOTIF, notif, tipo) else startForeground(ID_NOTIF, notif)
        } else {
            startForeground(ID_NOTIF, notif)
        }
    }

    private fun tituloDe(pos: Posicion?): String =
        pos?.calidad?.etiqueta?.let { "Sesión RTK · $it" } ?: "Sesión RTK · esperando señal"

    private fun detalleDe(pos: Posicion?, ntrip: EstadoNtrip): String {
        val sat = pos?.satelites?.let { "$it sat" } ?: "— sat"
        val edad = pos?.edadCorrecciones?.let { "corr ${"%.0f".format(it)}s" } ?: "corr —"
        return "$sat · $edad · NTRIP ${ntrip.etiqueta}"
    }

    private fun notificacion(titulo: String, texto: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Sesión RTK", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantiene la sesión GNSS activa en segundo plano"
                setShowBadge(false)
            }
            notificaciones.createNotificationChannel(canal)
        }
    }

    private fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("WakelockTimeout")
    private fun adquirirWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rtk:sesion").also {
            it.setReferenceCounted(false)
            it.acquire()  // sin timeout: la sesión la controla el usuario; se libera en onDestroy
        }
    }

    override fun onDestroy() {
        registro.registra("sesion", "servicio destruido")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        controlador.marcar(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CANAL = "sesion_rtk"
        const val ID_NOTIF = 1001
    }
}
