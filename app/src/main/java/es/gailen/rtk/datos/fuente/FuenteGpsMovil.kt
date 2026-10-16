package es.gailen.rtk.datos.fuente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import es.gailen.rtk.datos.debug.RegistroDebug
import es.gailen.rtk.datos.nmea.GeneradorGga
import es.gailen.rtk.dominio.CalidadFix
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.Posicion
import es.gailen.rtk.dominio.Precision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Fuente basada en el GPS interno del móvil (`LocationManager`, provider GPS). Es una
 * fuente de **pruebas**: el chip es de una sola frecuencia, así que la calidad se mapea
 * siempre a [CalidadFix.AUTONOMO] (precisión métrica, nunca RTK). Sintetiza un GGA
 * válido para poder enviarlo al caster en los perfiles que lo requieran.
 */
class FuenteGpsMovil(
    private val context: Context,
    private val periodoMs: Long = 1_000L,
    private val registro: RegistroDebug? = null,
) : FuentePosicion {

    private val gestor = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _estado = MutableStateFlow(EstadoFuente.DESCONECTADA)
    override val estado: StateFlow<EstadoFuente> = _estado.asStateFlow()

    private val _posiciones = MutableSharedFlow<Posicion>(replay = 1, extraBufferCapacity = 8)
    override val posiciones: Flow<Posicion> = _posiciones.asSharedFlow()

    private val _nmea = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val nmeaCrudo: Flow<String> = _nmea.asSharedFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = procesar(location)
        // Implementados (vacíos) por compatibilidad con API 26, donde no son default.
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override suspend fun conectar() {
        if (!tienePermiso()) {
            registro?.registra("gps", "SIN PERMISO ACCESS_FINE_LOCATION -> ERROR")
            _estado.value = EstadoFuente.ERROR
            return
        }
        _estado.value = EstadoFuente.CONECTANDO

        // Preferimos el GPS; como fuente de PRUEBAS añadimos red/fused como respaldo
        // para ver datos también en interior. Con el módulo real se usará solo el serie.
        val candidatos = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }.filter { gestor.allProviders.contains(it) }

        registro?.registra(
            "gps",
            "permiso=OK providersDisponibles=$candidatos " +
                "habilitados=${candidatos.filter { gestor.isProviderEnabled(it) }}",
        )

        var registrado = false
        try {
            for (provider in candidatos) {
                // Mostrar algo de inmediato con la última posición conocida.
                val ultima = gestor.getLastKnownLocation(provider)
                registro?.registra("gps", "lastKnown[$provider]=${ultima?.let { "${it.latitude},${it.longitude}" } ?: "null"}")
                ultima?.let(::procesar)
                if (gestor.isProviderEnabled(provider)) {
                    gestor.requestLocationUpdates(provider, periodoMs, 0f, listener, Looper.getMainLooper())
                    registrado = true
                }
            }
        } catch (e: SecurityException) {
            registro?.registra("gps", "SecurityException al pedir updates: ${e.message}")
            _estado.value = EstadoFuente.ERROR
            return
        }
        _estado.value = if (registrado) EstadoFuente.CONECTADA else EstadoFuente.ERROR
        registro?.registra("gps", "estado=${_estado.value} (registrado=$registrado)")
    }

    override suspend fun desconectar() {
        gestor.removeUpdates(listener)
        _estado.value = EstadoFuente.DESCONECTADA
    }

    private var ultimoFixGpsMs = 0L

    private fun procesar(location: Location) {
        // Preferimos el GPS: si hay un fix GPS reciente, ignoramos red/fused (menos precisos).
        val esGps = location.provider == LocationManager.GPS_PROVIDER
        val ahora = System.currentTimeMillis()
        if (esGps) {
            ultimoFixGpsMs = ahora
        } else if (ahora - ultimoFixGpsMs < PREFERENCIA_GPS_MS) {
            return
        }

        val instante = Instant.ofEpochMilli(location.time)
        val altura = if (location.hasAltitude()) location.altitude else null
        @Suppress("DEPRECATION")
        val satelites = location.extras?.getInt("satellites")?.takeIf { it > 0 }
        val precision = if (location.hasAccuracy()) {
            val h = location.accuracy.toDouble()
            val v = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else h
            // El móvil da precisión horizontal combinada; la repartimos como σlat ≈ σlon.
            val sigma = h / Math.sqrt(2.0)
            Precision(sigma, sigma, v)
        } else null

        val posicion = Posicion(
            tiempoUtc = instante,
            lat = location.latitude,
            lon = location.longitude,
            alturaElipsoidal = altura,  // Android da altura sobre el elipsoide WGS84
            calidad = CalidadFix.AUTONOMO,
            satelites = satelites,
            hdop = null,                // no disponible vía LocationManager
            edadCorrecciones = null,
            rumboGrados = if (location.hasBearing()) location.bearing.toDouble() else null,
            velocidadMs = if (location.hasSpeed()) location.speed.toDouble() else null,
            precisionEstimada = precision,
        )
        _posiciones.tryEmit(posicion)

        val gga = GeneradorGga.generar(
            lat = location.latitude,
            lon = location.longitude,
            alturaElipsoidal = altura,
            satelites = satelites,
            hdop = null,
            calidad = CalidadFix.AUTONOMO,
            instante = instante,
        )
        _nmea.tryEmit(gga)
    }

    private fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        /** Mientras haya un fix GPS más reciente que esto, se ignoran red/fused. */
        const val PREFERENCIA_GPS_MS = 5_000L
    }
}
