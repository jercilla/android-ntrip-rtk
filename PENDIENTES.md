# Pendientes de afinado (para el final del MVP)

Lista viva de cosas a pulir una vez estén los hitos principales. No bloquean el avance.

## UX / configuración

- [ ] **Reducir las opciones a las estrictamente necesarias.** Revisar Ajustes y la app
  entera y quitar/ocultar todo ajuste que no aporte en el uso real de campo. Menos es más:
  dejar solo lo que de verdad se toca. (Revisar: baudrate, intervalos, modos GGA, perfiles…)

- [ ] **Sección de "Comprobación del terminal".** La pérdida de conexión en segundo plano
  es crítica, así que conviene una pantalla/sección que **verifique y guíe** para dejar el
  móvil bien configurado, con estado (✓/✗) y botón para corregir cada punto:
  - Permiso de ubicación concedido y **preciso**.
  - Permiso de notificaciones (Android 13+).
  - App **excluida de la optimización de batería** / batería "sin restricciones".
  - **Inicio automático** activado (MIUI/HyperOS).
  - (Opcional) mantener pantalla encendida durante medición/replanteo.
  Idea: un "chequeo" que detecte lo que falta y lleve al ajuste correspondiente.

## Seguridad / despliegue

- [ ] **Quitar/endurecer la telemetría de depuración** antes de cualquier publicación:
  va por HTTP en claro y con IP del servidor por defecto en el código
  (`AjustesApp.URL_DEBUG_POR_DEFECTO`). Hacerla opt-in y/o solo en build debug.
- [ ] Revisar `network_security_config` (hoy permite tráfico en claro global por desarrollo).

## Hardware / OTG

- [ ] **Probar la `FuenteUsbSerie` por OTG** con el GPS USB genérico (Prolific PL2303,
  NMEA a **4800** baud). Pendiente de un **adaptador OTG USB-C de datos** correcto: el
  adaptador pasivo microUSB→USB-C no activa el modo host (ID pin vs CC pins), así que el
  móvil no da corriente. El código del Hito 8 ya está hecho; falta el enlace físico.

## Pruebas / simulador

- [ ] El NMEA simulado (`assets/simulacion_rtk.nmea`) formatea la latitud con ~2 cm de
  resolución (`%08.5f` de minutos), lo que "cuantiza" el jitter de los fix y hace que las
  σ salgan irrealmente pequeñas (p. ej. σN=0). Regenerar con más decimales si se quiere que
  las desviaciones simuladas se vean realistas. No afecta al cálculo (que es correcto).

## Funcionalidad diferida (del propio diseño)

- [ ] Elegir punto de montaje **desde la sourcetable** en Ajustes (el `descargarSourcetable`
  ya existe; falta la UI).
- [ ] **Registro de sesión a fichero** (NMEA/RTCM crudos) para analizar después.
- [ ] Conversión a **altitud ortométrica** (geoide EGM08-REDNAP) — punto de extensión.
