package es.gailen.rtk.datos.fuente

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import es.gailen.rtk.datos.ajustes.AjustesRepository
import es.gailen.rtk.datos.debug.RegistroDebug
import es.gailen.rtk.datos.nmea.ConstructorPosicion
import es.gailen.rtk.datos.nmea.EnsambladorNmea
import es.gailen.rtk.datos.nmea.ParserNmea
import es.gailen.rtk.datos.nmea.SentenciaGga
import es.gailen.rtk.datos.nmea.SentenciaGst
import es.gailen.rtk.datos.nmea.SentenciaRmc
import es.gailen.rtk.dominio.EstadoFuente
import es.gailen.rtk.dominio.FuentePosicion
import es.gailen.rtk.dominio.Posicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fuente basada en un conversor USB-serie (usb-serial-for-android): CP210x, CH34x, FTDI,
 * CDC-ACM. Detecta el dispositivo, pide permiso USB, abre con el baudrate configurado,
 * lee NMEA y reenvía las correcciones RTCM (que un GPS genérico ignorará). Es el camino
 * hacia el módulo LC29H; con el GPS USB genérico probamos toda la cadena OTG.
 */
class FuenteUsbSerie(
    private val context: Context,
    private val ajustesRepo: AjustesRepository,
    private val registro: RegistroDebug? = null,
) : FuentePosicion {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ensamblador = EnsambladorNmea()

    private val _estado = MutableStateFlow(EstadoFuente.DESCONECTADA)
    override val estado: StateFlow<EstadoFuente> = _estado.asStateFlow()

    private val _posiciones = MutableSharedFlow<Posicion>(replay = 1, extraBufferCapacity = 8)
    override val posiciones: Flow<Posicion> = _posiciones.asSharedFlow()

    private val _nmea = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val nmeaCrudo: Flow<String> = _nmea.asSharedFlow()

    private var puerto: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var receptorPermiso: BroadcastReceiver? = null

    // Estado del ensamblado por épocas (hilo del SerialInputOutputManager, un solo hilo).
    private var ultimoRmc: SentenciaRmc? = null
    private var ultimoGst: SentenciaGst? = null
    private var bytesLeidos = 0L
    private var lineasTotales = 0

    private val usbManager get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    override suspend fun conectar() {
        _estado.value = EstadoFuente.CONECTANDO
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        registro?.registra("usb", "drivers encontrados=${drivers.size} " +
            drivers.joinToString { "${it.javaClass.simpleName}(${it.device.vendorId}:${it.device.productId})" })
        if (drivers.isEmpty()) {
            _estado.value = EstadoFuente.ERROR
            return
        }
        val driver = drivers.first()
        val baud = ajustesRepo.ajustes.first().baudrateUsb
        if (usbManager.hasPermission(driver.device)) {
            abrir(driver, baud)
        } else {
            registro?.registra("usb", "sin permiso; solicitando diálogo USB")
            solicitarPermiso(driver, baud)
        }
    }

    private fun solicitarPermiso(driver: UsbSerialDriver, baud: Int) {
        val filtro = IntentFilter(ACCION_PERMISO)
        receptorPermiso = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACCION_PERMISO) return
                val concedido = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                registro?.registra("usb", "permiso ${if (concedido) "concedido" else "denegado"}")
                if (concedido) scope.launch { abrir(driver, baud) } else _estado.value = EstadoFuente.ERROR
            }
        }
        ContextCompat.registerReceiver(context, receptorPermiso, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(ACCION_PERMISO).setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usbManager.requestPermission(driver.device, pending)
    }

    private fun abrir(driver: UsbSerialDriver, baud: Int) {
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: run { registro?.registra("usb", "openDevice devolvió null"); _estado.value = EstadoFuente.ERROR; return }
            val port = driver.ports.first()
            port.open(connection)
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            puerto = port
            ensamblador.reiniciar()
            ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) = procesarBytes(data)
                override fun onRunError(e: Exception) {
                    registro?.registra("usb", "error de lectura: ${e.message}")
                    _estado.value = EstadoFuente.ERROR
                }
            }).also { it.start() }
            _estado.value = EstadoFuente.CONECTADA
            registro?.registra("usb", "abierto baud=$baud puertos=${driver.ports.size}")
        } catch (e: Exception) {
            registro?.registra("usb", "error al abrir: ${e.message}")
            _estado.value = EstadoFuente.ERROR
        }
    }

    private fun procesarBytes(data: ByteArray) {
        bytesLeidos += data.size
        for (linea in ensamblador.agregar(data)) {
            _nmea.tryEmit(linea)
            lineasTotales++
            // Telemetría: primeras líneas verbatim + resumen periódico (para diagnosticar el baud/NMEA).
            if (lineasTotales <= 6) registro?.registra("usb", "nmea[$lineasTotales]: $linea")
            else if (lineasTotales % 40 == 0) {
                registro?.registra("usb", "bytes=$bytesLeidos lineas=$lineasTotales ultima='$linea'")
            }
            when (val s = ParserNmea.parsear(linea)) {
                is SentenciaRmc -> ultimoRmc = s
                is SentenciaGst -> ultimoGst = s
                is SentenciaGga -> {
                    if (lineasTotales <= 60) {
                        registro?.registra("usb", "GGA calidad=${s.calidad} sat=${s.satelites} lat=${s.lat} lon=${s.lon}")
                    }
                    val sentencias = listOfNotNull(s, ultimoRmc, ultimoGst)
                    ConstructorPosicion.construir(sentencias)?.let { _posiciones.tryEmit(it) }
                }
                else -> Unit
            }
        }
    }

    override suspend fun desconectar() {
        ioManager?.stop()
        ioManager = null
        runCatching { puerto?.close() }
        puerto = null
        receptorPermiso?.let { runCatching { context.unregisterReceiver(it) } }
        receptorPermiso = null
        _estado.value = EstadoFuente.DESCONECTADA
        registro?.registra("usb", "desconectado (bytes leídos=$bytesLeidos)")
    }

    override suspend fun enviarCorrecciones(rtcm: ByteArray) {
        val p = puerto ?: return
        runCatching { p.write(rtcm, 1_000) }
            .onFailure { registro?.registra("usb", "error al escribir RTCM: ${it.message}") }
    }

    private companion object {
        const val ACCION_PERMISO = "es.gailen.rtk.USB_PERMISO"
    }
}
