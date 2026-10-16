# RTK Euskadi

App Android nativa de posicionamiento RTK centimétrico de bajo coste. Actúa de puente
entre un caster **NTRIP** (Red GNSS de Euskadi) y un módulo **Quectel LC29H(DA)** por
USB OTG: recibe correcciones RTCM3, las reenvía al módulo y presenta la posición NMEA.

Especificación completa: [`android_ntrip.md`](android_ntrip.md).

## Estado

**Hito 1 completado**: esqueleto del proyecto, navegación, ajustes y almacenamiento de
credenciales cifrado. El resto de hitos (fuentes de posición, parser NMEA/RTCM, cliente
NTRIP, medición, replanteo, exportación, USB) están pendientes; sus pantallas aparecen
como *placeholder* indicando el hito correspondiente.

## Stack

- Kotlin 2.0 · Jetpack Compose (Material 3) · Coroutines/Flow
- Hilt (inyección de dependencias) · DataStore (ajustes) · kotlinx.serialization
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`
- UI en español

## Arquitectura (Hito 1)

```
ui/               Pantallas Compose y navegación
  navigation/     NavHost + barra inferior (Estado, Medir, Puntos, Replanteo, Ajustes)
  ajustes/        Pantalla funcional de ajustes + ViewModel
  estado|medicion|puntos|replanteo|diagnostico/   placeholders
dominio/          Modelos: CalidadFix, FuentePosicionTipo, PerfilNtrip, AjustesApp
datos/
  seguridad/      CifradorKeystore (AES-256/GCM en Android Keystore)
  ajustes/        AjustesRepository (DataStore) + DataStore de la app
di/               Módulo Hilt de la capa de datos
```

### Seguridad de credenciales

Las credenciales NTRIP (usuario y contraseña) se cifran con una clave AES-256/GCM
guardada en el **Android Keystore** y se almacenan como blob Base64 en DataStore, una
entrada por perfil. La configuración no secreta del perfil (host, puerto, punto de
montaje…) se guarda en claro. Los ajustes quedan **excluidos de las copias de seguridad**
(`res/xml/backup_rules.xml` y `data_extraction_rules.xml`), porque la clave del Keystore
no es exportable y el blob sería inútil en otro dispositivo.

Perfiles NTRIP precargados (sección 4 de la especificación):
- **Euskadi · APBI3M** (puerto 2103, sin GGA) — perfil por defecto
- **Euskadi · CERCANA3M** (puerto 2101, requiere GGA)
- **Bizkaia · CERCANA3M** (puerto 2101, requiere GGA). Acceso anónimo público:
  usuario `anonimo`, contraseña `1234` (se introducen en Ajustes; no van en el código).

## Compilar

Requiere el SDK de Android (configurado en `local.properties`) y acceso a red la primera
vez para que el Gradle wrapper descargue Gradle 8.14.3 y las dependencias.

```bash
./gradlew assembleDebug      # APK de depuración
./gradlew test               # tests unitarios (JVM)
./gradlew connectedCheck     # tests instrumentados (requiere dispositivo/emulador)
./gradlew installDebug       # instalar en un dispositivo conectado por adb
```

Lo normal es abrir el proyecto en **Android Studio** (que gestiona SDK y wrapper).

## Tests

- `app/src/test/` — unitarios JVM: mapeo de `CalidadFix`, perfiles por defecto.
- `app/src/androidTest/` — instrumentado: ciclo cifrar/descifrar del `CifradorKeystore`
  (necesita dispositivo porque usa el Android Keystore).
