# devserver — servidor de desarrollo

Pequeño servidor HTTP (solo Python 3 estándar, sin dependencias) para el ciclo de
desarrollo con el móvil en la **misma red local**. Dos usos:

1. **Descargar el APK al móvil** sin cable: una página con botón de descarga y un QR
   que apunta directamente al APK.
2. **Recibir datos de depuración** del dispositivo (NMEA, RTCM, logs…) mediante un
   endpoint HTTP simple, que quedan guardados en `debug_data/` para analizarlos luego.

> ⚠️ Pensado para una red local de confianza: escucha en `0.0.0.0` y **no tiene
> autenticación**. No lo expongas a internet.

## Arrancar

```bash
# desde la raíz del proyecto
python3 tools/devserver/serve.py                 # puerto 8000
python3 tools/devserver/serve.py --port 9000
python3 tools/devserver/serve.py --apk ruta/al/otro.apk
```

Al arrancar imprime la URL de la LAN (p. ej. `http://192.168.1.50:8000/`). Ábrela en el
móvil o escanea el QR. El APK que sirve es el **más reciente** de
`app/build/outputs/apk/`, así que basta con recompilar (`./gradlew assembleDebug`) y
volver a abrir la página.

La primera vez, Android pedirá permiso para instalar apps de orígenes desconocidos.

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET  | `/`              | Página de inicio (descarga del APK + estado) |
| GET  | `/apk`           | Descarga el APK más reciente |
| GET  | `/apk/<nombre>`  | Descarga un APK concreto por nombre de fichero |
| GET  | `/debug`         | Lista (JSON) de los ficheros de depuración recibidos |
| GET  | `/debug/<fich>`  | Descarga uno de esos ficheros |
| GET  | `/health`        | `{"ok": true}` |
| POST | `/ingest?...`    | Guarda el cuerpo recibido como fichero |
| POST | `/log?sesion=…`  | Añade el cuerpo (una línea o JSON) a `<sesion>.log` |

## Protocolo de ingesta de depuración

Pensado para que la app (o cualquier script) suba datos del dispositivo. El servidor
los guarda bajo `debug_data/<sesion>/`.

**Volcar un flujo crudo** (NMEA, RTCM, lo que sea):

```
POST /ingest?tipo=nmea&sesion=campo1
Cuerpo: bytes/texto crudos
→ debug_data/campo1/<marca-de-tiempo>_nmea.log
```

Si se repite la misma petición con `&nombre=fijo.log`, se **añade** al mismo fichero
(útil para ir enviando por trozos). Parámetros:
- `sesion`: agrupa los ficheros de una misma salida de campo (por defecto `sesion`).
- `tipo`: etiqueta del dato (`nmea`, `rtcm`, `gga`, `trace`…). Por defecto `dato`.
- `nombre` (opcional): nombre de fichero fijo en vez de uno con marca de tiempo.

**Registrar líneas sueltas** (eventos, estado):

```
POST /log?sesion=campo1
Cuerpo: una línea de texto o un JSON
→ añade "<timestamp>\t<línea>" a debug_data/campo1/campo1.log
```

Ejemplos con `curl` (sustituye la IP por la que imprime el servidor):

```bash
# subir un volcado NMEA
curl --data-binary @captura.nmea "http://192.168.1.50:8000/ingest?tipo=nmea&sesion=campo1"

# registrar un evento
curl --data "fix RTK alcanzado, 14 sat" "http://192.168.1.50:8000/log?sesion=campo1"
```

Los nombres de `sesion`, `tipo` y `nombre` se sanean (solo `A-Z a-z 0-9 . _ -`) para
evitar escrituras fuera de `debug_data/`.

### Integración futura con la app

En el hito de diagnóstico (6.6) la app podrá enviar aquí el registro de sesión (NMEA y
RTCM crudos) en vez de, o además de, guardarlo en el dispositivo. La URL del servidor
sería un ajuste más. Este protocolo ya está listo para ese uso.

## Datos recibidos

Se guardan en `tools/devserver/debug_data/` (ignorado por git salvo el `.gitignore`).
Nunca se suben credenciales ni datos sensibles al repositorio.
