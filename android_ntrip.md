# Handoff: App Android de posicionamiento RTK (GNSS centimétrico de bajo coste)

## 1. Contexto y objetivo

Construir una app Android nativa que, junto con un **módulo GNSS RTK externo Quectel LC29H(DA)** conectado por USB OTG y las **correcciones NTRIP gratuitas de la Red GNSS de Euskadi**, permita:

1. **Medir puntos** con precisión centimétrica (estático, promediando durante un tiempo configurable).
2. **Guardar** esos puntos con sus metadatos.
3. **Replantear** (*stakeout*): volver a un punto guardado y guiar al usuario con distancia y dirección hasta él.
4. **Exportar** los puntos (CSV y GeoJSON).

El módulo hace el cálculo RTK. La app actúa de puente y de interfaz: recibe correcciones RTCM3 por NTRIP, las reenvía al módulo por USB-serie, lee la posición NMEA que devuelve y la presenta.

El hardware aún no ha llegado. **La app debe desarrollarse y probarse antes**, usando fuentes de posición alternativas (GPS del móvil, simulador NMEA y un GPS USB genérico). El cambio al módulo real debe ser trivial.

```
Caster NTRIP (Euskadi) ──RTCM3──► App ──USB-serie──► LC29H(DA)
        ▲                          ▲                     │
        └──── GGA (si se pide) ────┴────── NMEA ─────────┘
```

## 2. Stack y restricciones

- **Kotlin**, **Jetpack Compose**, coroutines y `Flow`.
- **minSdk 26**, targetSdk el actual estable.
- **Room** para puntos y sesiones; **DataStore** para ajustes.
- **Credenciales NTRIP cifradas** con Android Keystore. Nunca en el código, en logs ni en el control de versiones.
- **USB-serie:** librería [`usb-serial-for-android`](https://github.com/mik3y/usb-serial-for-android) (soporta CP210x, CH34x, FTDI, CDC-ACM).
- **Sin dependencias de pago** ni servicios de terceros.
- Arquitectura en capas (UI → ViewModel → dominio → datos) con inyección de dependencias (Hilt o manual, a elección).
- Toda la UI en **español**.

## 3. Hardware objetivo

### Módulo GNSS
- **Quectel LC29H, variante DA** (RTK rover, 1 Hz), sobre placa **Waveshare LC29H(DA) GPS/RTK HAT**.
- Doble banda L1/L5, GPS/QZSS L1 C/A + L5, Galileo E1 + E5a, BeiDou B1I + B2a, GLONASS L1.
- La placa lleva conversor **CP2102N** USB-UART y puerto micro USB. Se conecta al móvil con cable OTG.
- UART por defecto: **115200 baudios, 8N1**. Dejar el baudrate configurable.
- Entrada: **RTCM3** (acepta MSM). Salida: **NMEA** (GGA, RMC, GSA, GSV…).
- Calidad GGA: `0` sin fix, `1` autónomo, `2` DGPS, `4` **RTK fix**, `5` **RTK float**.
- **No inventar comandos de configuración propietarios** (PQTM/PAIR). Para el MVP se usa la configuración por defecto del módulo. Si hace falta configurar algo, consultar la especificación de protocolo oficial de Quectel para el LC29H y dejarlo documentado.

### Móviles de prueba
Poco M4 Pro 5G (Android 11-12, MIUI), Redmi 12 y Redmi 15 (HyperOS). Todos con GNSS de una sola frecuencia: su GPS interno solo sirve como fuente de pruebas. **MIUI/HyperOS mata procesos en segundo plano agresivamente**: hay que usar servicio en primer plano y guiar al usuario para excluir la app de la optimización de batería.

### Hardware de pruebas antes del módulo
El usuario tiene un **GPS USB genérico** que envía NMEA por USB-serie. Sirve para probar la conexión OTG, la lectura NMEA y el reenvío de RTCM (que ignorará).

## 4. Fuentes de correcciones (NTRIP)

### Perfil por defecto: Red GNSS de Euskadi, estación individual
- Host: `www.gps2.euskadi.eus` (IPs de respaldo: `77.228.156.141`, `150.241.41.141`).
- **Puerto 2103**: soluciones individuales por estación.
- Punto de montaje por defecto: **`APBI3M`** (RTCM 3, GPS+GLO+GAL+BDS, MSM). Alternativa cercana: `LEMZ3M`.
- No requiere enviar GGA.
- Requiere usuario y contraseña (los introduce el usuario en ajustes).
- **Verificado:** el flujo de `APBI3M` empieza con mensaje **1006** (coordenadas de la base) y contiene **1075** (GPS MSM5). Unos 1,5 KB/s.

### Perfil 2: Red GNSS de Euskadi, estación cercana o red
- Mismo host, **puerto 2101**.
- Puntos de montaje: `CERCANA3M` (estación más próxima), `VRS3M` e `IMAX3M` (red).
- **Requieren enviar GGA** al caster (en la sourcetable, campo NMEA = `1`).

### Perfil 3: Diputación Foral de Bizkaia (sin registro)
- IP `80.245.2.245`, puerto `2101`, usuario `anonimo`, contraseña `1234`.
- Punto de montaje: `CERCANA3M` (requiere GGA).

### Puntos de montaje válidos para el LC29H
Solo **RTCM 3**, preferiblemente los terminados en **`3M`** (multiconstelación MSM). Descartar los terminados en `2`, `C`, `+`, `L` o `R` (RTCM 2, CMR, CMR+, Leica, RAW).

### Aviso sobre la sourcetable
Las coordenadas de varias estaciones en la sourcetable de Euskadi son **incorrectas** (SOPU, GERN y BRZR aparecen con las mismas coordenadas; KAST3M figura con las de ELCI). **No usarlas** para cálculos. La posición real de la base se obtiene del mensaje RTCM **1005/1006** del propio flujo.

## 5. Arquitectura y módulos

### 5.1 Fuente de posición intercambiable

```kotlin
data class Posicion(
    val tiempoUtc: Instant,
    val lat: Double, val lon: Double,      // grados, ETRS89
    val alturaElipsoidal: Double?,         // m (altitud MSL + separación geoidal del GGA)
    val calidad: CalidadFix,               // SIN_FIX, AUTONOMO, DGPS, RTK_FLOAT, RTK_FIX
    val satelites: Int?,
    val hdop: Double?,
    val edadCorrecciones: Double?,         // s, del campo GGA
    val precisionEstimada: Precision?      // de GST si está disponible
)

interface FuentePosicion {
    val estado: StateFlow<EstadoFuente>    // DESCONECTADA, CONECTANDO, CONECTADA, ERROR
    val posiciones: Flow<Posicion>
    val nmeaCrudo: Flow<String>            // para la consola de depuración y para reenviar GGA
    suspend fun conectar()
    suspend fun desconectar()
    suspend fun enviarCorrecciones(rtcm: ByteArray) {}  // solo USB
}
```

Implementaciones:
1. **`FuenteGpsMovil`**: `LocationManager` (provider GPS). Mapea a calidad `AUTONOMO`. Sintetiza una GGA válida para poder enviarla al caster en los perfiles que lo requieran.
2. **`FuenteSimulada`**: reproduce un archivo NMEA desde `assets/` a 1 Hz, en bucle. Incluir archivos de ejemplo que recorran los estados sin fix → autónomo → float → fix, con posiciones que se muevan unos centímetros alrededor de un punto fijo.
3. **`FuenteUsbSerie`**: `usb-serial-for-android`. Detección del dispositivo, solicitud de permiso USB, apertura con baudrate configurable, lectura continua, escritura de RTCM. Reconexión al desenchufar y volver a enchufar. Filtro de dispositivos USB en el manifest para abrir la app al conectar el módulo.

### 5.2 Parser NMEA
- Validación de checksum. Descartar sentencias corruptas.
- Talkers `GP`, `GN`, `GL`, `GA`, `GB`, `BD`, `GQ`.
- Sentencias mínimas: **GGA** (posición, calidad, satélites, HDOP, altitud, separación geoidal, edad de correcciones), **RMC** (fecha, rumbo y velocidad sobre el suelo), **GST** (desviaciones típicas de lat, lon y altura, si el receptor la emite), **GSA** y **GSV** (para la pantalla de satélites).
- Reensamblado de líneas a partir de un flujo de bytes fragmentado (USB entrega trozos arbitrarios).

### 5.3 Cliente NTRIP
- Soportar **NTRIP v1 y v2** (cabecera `Ntrip-Version: Ntrip/2.0`; respuestas `ICY 200 OK` y `HTTP/1.1 200 OK`; v2 puede usar *chunked transfer encoding*).
- `User-Agent: NTRIP <nombreApp>/<versión>`. Autenticación Basic.
- **Descarga y parseo de la sourcetable** (líneas `STR;`, `CAS;`, `NET;`, fin en `ENDSOURCETABLE`) para que el usuario elija punto de montaje desde la app. Mostrar formato, constelaciones y si requiere NMEA.
- **Envío de GGA** cada 10 s (configurable) cuando el punto de montaje lo requiera, usando la GGA de la fuente activa.
- Reconexión con *backoff* exponencial (1 s → máx. 60 s). Detectar timeouts de datos (sin bytes durante 15 s → reconectar).
- Manejo explícito de errores: 401 (credenciales), 404 (punto de montaje inexistente), red caída, DNS (probar IPs de respaldo si falla el host).
- Estadísticas: bytes recibidos, tasa, tiempo conectado, mensajes por tipo.

### 5.4 Parser de tramas RTCM3 (solo inspección)
No decodifica observaciones; el RTK lo hace el módulo. Solo:
- Encuadre: preámbulo `0xD3`, 6 bits reservados + 10 bits de longitud, payload, **CRC-24Q**. Resincronizar si el CRC falla.
- Tipo de mensaje (primeros 12 bits del payload). Contadores por tipo para la pantalla de diagnóstico.
- **Decodificar 1005/1006**: ID de estación, coordenadas ECEF de la base (y altura de antena en 1006) → convertir a lat/lon/altura (GRS80) → **distancia de la línea base** respecto a la posición actual.
- El reenvío al módulo es del flujo de bytes tal cual llega. No reencapsular.

### 5.5 Servicio de sesión en primer plano
- `ForegroundService` (tipo `location` y `connectedDevice`) que mantiene la fuente y el cliente NTRIP vivos con la pantalla apagada.
- Notificación persistente con estado: calidad del fix, satélites, edad de correcciones.
- Expone el estado como `StateFlow` a la UI.

## 6. Funcionalidades

### 6.1 Pantalla principal (estado en vivo)
- Calidad del fix con código de color: rojo (sin fix / autónomo), amarillo (DGPS / float), verde (fix).
- Lat, lon, altura elipsoidal; satélites; HDOP; edad de correcciones; precisión estimada (GST) si existe.
- Estado NTRIP: conectado, bytes/s, punto de montaje, **distancia a la base** (de 1005/1006).
- Estado de la fuente (USB conectado, GPS del móvil, simulador).

### 6.2 Medición de punto (estático)
- Botón "Medir punto": promedia posiciones durante **N segundos** (por defecto 60, configurable).
- Por defecto **solo acepta épocas con RTK fix**. Opción para aceptar float.
- Si se pierde el fix durante la medición: pausar el contador y avisar (no mezclar épocas).
- Resultado: media y desviación típica en metros (norte, este, altura) en un sistema local ENU alrededor del primer punto; número de épocas usadas; porcentaje de fix.
- **Altura del jalón**: valor configurable (m) que se resta a la altura medida para obtener la cota del punto en el suelo. Guardar el valor usado en cada punto.
- Formulario: nombre o código del punto, notas.

### 6.3 Replanteo
- Seleccionar un punto guardado y mostrar en tiempo real:
  - **Distancia** horizontal, **rumbo** (0-360° desde el norte) y **desnivel**.
  - Desplazamiento descompuesto: "X m al norte/sur, Y m al este/oeste".
- Vista **lejana** (más de 3 m): flecha hacia el objetivo, orientada con el rumbo de desplazamiento GNSS (RMC) cuando el usuario se mueve, o con la brújula del móvil como respaldo (indicando que es aproximada).
- Vista **cercana** (menos de 3 m): **diana** con la posición relativa en metros norte/este y escala adaptativa (1 m, 30 cm, 10 cm). No depender de la brújula.
- Indicador: rojo (más de 1 m), amarillo (más de 10 cm), verde (menos de 3 cm). **El verde solo con RTK fix.**
- Cálculo (válido para distancias cortas, error muy inferior al cm a cientos de metros):

```kotlin
val R = 6378137.0
val latMedia = Math.toRadians((latAct + latObj) / 2)
val norte = Math.toRadians(latObj - latAct) * R
val este  = Math.toRadians(lonObj - lonAct) * R * cos(latMedia)
val distancia = hypot(norte, este)
val rumbo = (Math.toDegrees(atan2(este, norte)) + 360) % 360
```

### 6.4 Gestión y exportación de puntos
- Lista de puntos con búsqueda; detalle; borrar; editar nombre y notas.
- Exportar todo o una selección a **CSV** y **GeoJSON** mediante el selector de documentos del sistema (Storage Access Framework) o la hoja de compartir.
- Campos mínimos por punto: id, nombre, notas, lat, lon, altura elipsoidal, altura del jalón, cota del suelo, desviaciones N/E/U, épocas, % fix, calidad, perfil NTRIP y punto de montaje, distancia a la base, fecha y hora UTC.
- Sistema de referencia: **ETRS89** (el de las correcciones de la red). Indicarlo en el CSV y en el GeoJSON.
- Las alturas son **elipsoidales**. La conversión a altitud ortométrica (geoide EGM08-REDNAP) queda **fuera del MVP**; dejar el punto de extensión.

### 6.5 Ajustes
- Fuente de posición: GPS del móvil / simulada / USB (con baudrate).
- Perfiles NTRIP (los tres de la sección 4 precargados, editables, y posibilidad de añadir más): host, IPs de respaldo, puerto, usuario, contraseña, punto de montaje (elegible desde la sourcetable), envío de GGA (auto según sourcetable o forzado), intervalo de GGA.
- Altura del jalón por defecto. Duración por defecto de la medición. Aceptar float sí/no.

### 6.6 Diagnóstico
- Consola NMEA en vivo (últimas N líneas).
- Contadores RTCM por tipo de mensaje, bytes totales y tasa.
- Registro de sesión opcional a archivo (NMEA crudo y RTCM crudo) para analizar después.

## 7. Permisos y sistema
- `ACCESS_FINE_LOCATION` (fuente GPS del móvil y servicio de tipo location), `INTERNET`, `FOREGROUND_SERVICE` y sus tipos, `POST_NOTIFICATIONS`.
- Permiso USB por diálogo del sistema (`UsbManager.requestPermission`) y filtro `USB_DEVICE_ATTACHED` con VID/PID del CP210x (10C4:EA60) y otros conversores comunes.
- Pantalla o aviso que guíe para excluir la app de la optimización de batería (MIUI/HyperOS).
- Mantener la pantalla encendida opcionalmente durante medición y replanteo.

## 8. Pruebas

- **Tests unitarios** (sin dispositivo):
  - Parser NMEA: checksums válidos e inválidos, cada sentencia, líneas fragmentadas, talkers distintos, campos vacíos.
  - Encuadre RTCM3: CRC-24Q, resincronización, tramas partidas entre lecturas. Usar como fixture esta cabecera real recibida de `APBI3M`: `d3 00 15 3e e0 …` (tipo 1006, longitud 21) y `d3 01 3a 43 30 …` (tipo 1075, longitud 314).
  - Decodificación 1005/1006 y conversión ECEF → geodésicas (comparar con valores de referencia conocidos).
  - Parseo de la sourcetable, incluido el campo NMEA y el `ENDSOURCETABLE`.
  - Cálculos de replanteo y de promediado (media, desviación, descarte de épocas no fix).
- **Prueba de integración NTRIP** contra un servidor NTRIP falso en local (respuestas v1 y v2, 401, 404, cortes de conexión).
- **Pruebas manuales:** simulador recorriendo todos los estados; GPS del móvil caminando; GPS USB genérico por OTG.

## 9. Hitos

1. Esqueleto del proyecto, navegación, ajustes, almacenamiento de credenciales cifrado.
2. `FuentePosicion` + `FuenteGpsMovil` + `FuenteSimulada` + parser NMEA + pantalla de estado.
3. Cliente NTRIP + sourcetable + parser RTCM (1005/1006, contadores) + pantalla de diagnóstico.
4. Servicio en primer plano que une fuente y NTRIP.
5. Medición de puntos + Room + lista de puntos.
6. Replanteo.
7. Exportación CSV/GeoJSON.
8. `FuenteUsbSerie` probada con el GPS USB genérico (reenvío de RTCM incluido).
9. (Con hardware) Prueba de campo con el LC29H(DA): alcanzar fix con `APBI3M`.

## 10. Criterios de aceptación del MVP

- Con la fuente simulada, la app recorre todos los estados, mide un punto solo con épocas fix y permite replantearlo, con colores y avisos correctos.
- Con credenciales válidas, conecta a `www.gps2.euskadi.eus:2103/APBI3M`, muestra los tipos RTCM recibidos y la distancia a la base calculada desde 1006.
- Con un perfil que requiere GGA (`CERCANA3M`), recibe datos tras enviar la GGA.
- La sesión sobrevive a apagar la pantalla durante al menos 30 minutos en un móvil Xiaomi con la optimización de batería desactivada.
- Exporta CSV y GeoJSON válidos y abribles en QGIS.
- Ninguna credencial aparece en el código, en los logs ni en los archivos exportados.

## 11. Fuera del alcance (por ahora)

- RTK calculado en el propio móvil (RTKLIB, medidas brutas `GnssMeasurement`).
- Conexión por Bluetooth (dejar la interfaz preparada para una futura `FuenteBluetooth`).
- Conversión a altitud ortométrica y a proyecciones UTM (dejar punto de extensión; UTM 30N ETRS89 sería lo siguiente).
- Posicionamiento con balizas UWB o medidor láser para zonas sin cielo.
- Mock location para otras apps.
- iOS.
