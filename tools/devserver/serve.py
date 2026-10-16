#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Servidor de desarrollo para el proyecto RTK Euskadi.

Dos usos:
  1. Descargar el APK al móvil desde la misma red local (página con botón + QR).
  2. Recibir datos de depuración del dispositivo mediante un endpoint HTTP simple.

Solo usa la librería estándar de Python 3 (sin dependencias). Pensado para red
local de confianza: escucha en 0.0.0.0 y no tiene autenticación.

Uso:
    python3 tools/devserver/serve.py                # puerto 8000
    python3 tools/devserver/serve.py --port 9000
    python3 tools/devserver/serve.py --apk ruta/al/app.apk

Endpoints:
    GET  /                      Página de inicio (descarga del APK + estado)
    GET  /apk                   Descarga el APK más reciente
    GET  /apk/<nombre>          Descarga un APK concreto por nombre de fichero
    GET  /debug                 Lista (JSON) de los ficheros de depuración recibidos
    GET  /debug/<fichero>       Descarga uno de esos ficheros
    GET  /health                {"ok": true}
    POST /ingest?...            Guarda el cuerpo recibido como fichero de depuración
    POST /log?sesion=...        Añade el cuerpo (línea/JSON) a <sesion>.log

Protocolo de ingesta (ver README.md):
    POST /ingest?tipo=nmea&sesion=campo1        cuerpo = bytes/texto crudos
      -> guarda tools/devserver/debug_data/campo1/<marca-de-tiempo>_nmea.log
    POST /log?sesion=campo1                      cuerpo = una línea o un JSON
      -> añade al final de tools/devserver/debug_data/campo1/campo1.log
"""

from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import re
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

APK_MIME = "application/vnd.android.package-archive"
RAIZ_PROYECTO = Path(__file__).resolve().parents[2]
DIR_DATOS = Path(__file__).resolve().parent / "debug_data"
SEGURO = re.compile(r"[^A-Za-z0-9._-]")

# Configuración global (se rellena en main()).
CONF = {
    "proyecto": RAIZ_PROYECTO,
    "datos": DIR_DATOS,
    "apk_fijo": None,  # Path opcional forzado por --apk
    "puerto": 8000,
    "ip": "127.0.0.1",
}


def sanear(nombre: str, por_defecto: str = "dato") -> str:
    """Convierte un nombre arbitrario en un nombre de fichero/segmento seguro."""
    limpio = SEGURO.sub("_", (nombre or "").strip()).strip("._")
    return limpio or por_defecto


def marca_tiempo() -> str:
    return dt.datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]


def tam_legible(n: int) -> str:
    unidades = ["B", "KB", "MB", "GB"]
    f = float(n)
    for u in unidades:
        if f < 1024 or u == unidades[-1]:
            return f"{f:.0f} {u}" if u == "B" else f"{f:.1f} {u}"
        f /= 1024
    return f"{n} B"


def ip_lan() -> str:
    """IP de esta máquina en la LAN (la que usaría el móvil)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def encontrar_apks() -> list:
    """Lista de APKs del proyecto, más reciente primero. Respeta --apk si se pasó."""
    if CONF["apk_fijo"]:
        p = Path(CONF["apk_fijo"])
        return [p] if p.is_file() else []
    base = CONF["proyecto"] / "app" / "build" / "outputs" / "apk"
    if not base.exists():
        return []
    apks = [p for p in base.rglob("*.apk") if p.is_file()]
    apks.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return apks


def ficheros_debug() -> list:
    """Lista de ficheros recibidos, más reciente primero."""
    base = CONF["datos"]
    if not base.exists():
        return []
    fs = [p for p in base.rglob("*") if p.is_file()]
    fs.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return fs


class Manejador(BaseHTTPRequestHandler):
    server_version = "RTKDevServer/1.0"
    protocol_version = "HTTP/1.1"  # permite Content-Length y conexiones persistentes

    # --- utilidades de respuesta ---

    def _enviar(self, codigo: int, cuerpo: bytes, tipo: str = "text/plain; charset=utf-8",
                extra: dict = None):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(cuerpo)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(cuerpo)

    def _json(self, codigo: int, obj):
        self._enviar(codigo, json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8"),
                     "application/json; charset=utf-8")

    def _descargar(self, ruta: Path, tipo: str, nombre_descarga: str = None):
        if not ruta.is_file():
            self._json(404, {"ok": False, "error": "no encontrado"})
            return
        tam = ruta.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(tam))
        disp = nombre_descarga or ruta.name
        self.send_header("Content-Disposition", f'attachment; filename="{disp}"')
        self.end_headers()
        if self.command == "HEAD":
            return
        with ruta.open("rb") as f:
            while True:
                trozo = f.read(64 * 1024)
                if not trozo:
                    break
                self.wfile.write(trozo)

    def log_message(self, formato, *args):  # log compacto a stderr
        sys.stderr.write(f"  {self.address_string()} - {formato % args}\n")

    # --- GET ---

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        ruta = urlparse(self.path)
        camino = unquote(ruta.path)

        if camino in ("/", "/index.html"):
            self._enviar(200, pagina_inicio().encode("utf-8"), "text/html; charset=utf-8")
        elif camino == "/health":
            self._json(200, {"ok": True})
        elif camino == "/apk" or camino == "/apk/latest":
            apks = encontrar_apks()
            if not apks:
                self._json(404, {"ok": False, "error": "no hay APK; compila con ./gradlew assembleDebug"})
            else:
                self._descargar(apks[0], APK_MIME, "rtk-euskadi.apk")
        elif camino.startswith("/apk/"):
            nombre = camino[len("/apk/"):]
            objetivo = next((p for p in encontrar_apks() if p.name == nombre), None)
            if objetivo:
                self._descargar(objetivo, APK_MIME, objetivo.name)
            else:
                self._json(404, {"ok": False, "error": "APK no encontrado"})
        elif camino == "/debug" or camino == "/debug/":
            base = CONF["datos"]
            items = []
            for p in ficheros_debug():
                st = p.stat()
                items.append({
                    "fichero": str(p.relative_to(base)),
                    "bytes": st.st_size,
                    "modificado": dt.datetime.fromtimestamp(st.st_mtime).isoformat(timespec="seconds"),
                })
            self._json(200, {"ok": True, "total": len(items), "ficheros": items})
        elif camino.startswith("/debug/"):
            rel = camino[len("/debug/"):]
            objetivo = (CONF["datos"] / rel).resolve()
            # Evitar traversal fuera del directorio de datos.
            if CONF["datos"].resolve() in objetivo.parents and objetivo.is_file():
                self._descargar(objetivo, "text/plain; charset=utf-8", objetivo.name)
            else:
                self._json(404, {"ok": False, "error": "no encontrado"})
        else:
            self._json(404, {"ok": False, "error": "ruta desconocida"})

    # --- POST (ingesta de depuración) ---

    def do_POST(self):
        ruta = urlparse(self.path)
        camino = unquote(ruta.path)
        params = parse_qs(ruta.query)

        longitud = int(self.headers.get("Content-Length", 0) or 0)
        cuerpo = self.rfile.read(longitud) if longitud > 0 else b""

        if camino == "/ingest":
            sesion = sanear(params.get("sesion", ["sesion"])[0], "sesion")
            tipo = sanear(params.get("tipo", ["dato"])[0], "dato")
            nombre = params.get("nombre", [None])[0]
            carpeta = CONF["datos"] / sesion
            carpeta.mkdir(parents=True, exist_ok=True)
            fichero = carpeta / (sanear(nombre) if nombre else f"{marca_tiempo()}_{tipo}.log")
            with fichero.open("ab") as f:
                f.write(cuerpo)
            rel = str(fichero.relative_to(CONF["datos"]))
            print(f"  ↳ ingest: +{tam_legible(len(cuerpo))} en debug_data/{rel}")
            self._json(200, {"ok": True, "bytes": len(cuerpo), "fichero": rel})

        elif camino == "/log":
            sesion = sanear(params.get("sesion", ["sesion"])[0], "sesion")
            carpeta = CONF["datos"] / sesion
            carpeta.mkdir(parents=True, exist_ok=True)
            fichero = carpeta / f"{sesion}.log"
            linea = cuerpo.decode("utf-8", "replace").rstrip("\n")
            sello = dt.datetime.now().isoformat(timespec="milliseconds")
            with fichero.open("a", encoding="utf-8") as f:
                f.write(f"{sello}\t{linea}\n")
            # Eco por stdout para poder seguir la telemetría en vivo.
            print(f"[{sello}] [{sesion}] {linea}", flush=True)
            self._json(200, {"ok": True, "fichero": str(fichero.relative_to(CONF["datos"]))})

        else:
            self._json(404, {"ok": False, "error": "ruta POST desconocida"})


def pagina_inicio() -> str:
    ip, puerto = CONF["ip"], CONF["puerto"]
    base = f"http://{ip}:{puerto}"
    url_apk = f"{base}/apk"
    apks = encontrar_apks()

    if apks:
        p = apks[0]
        st = p.stat()
        info_apk = (
            f'<p class="ok">APK: <b>{html.escape(p.name)}</b> · {tam_legible(st.st_size)} · '
            f'{dt.datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M")}</p>'
        )
        boton = f'<a class="btn" href="/apk">⬇ Descargar e instalar APK</a>'
    else:
        info_apk = ('<p class="err">No se encontró ningún APK. Compila primero:<br>'
                    '<code>./gradlew assembleDebug</code></p>')
        boton = ''

    recientes = ficheros_debug()[:10]
    if recientes:
        filas = "".join(
            f'<li><a href="/debug/{html.escape(str(p.relative_to(CONF["datos"])))}">'
            f'{html.escape(str(p.relative_to(CONF["datos"])))}</a> '
            f'<span class="dim">({tam_legible(p.stat().st_size)})</span></li>'
            for p in recientes
        )
        lista_debug = f"<ul class='files'>{filas}</ul>"
    else:
        lista_debug = '<p class="dim">Aún no se han recibido datos de depuración.</p>'

    return f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RTK Euskadi · servidor de desarrollo</title>
<style>
  :root {{ color-scheme: light dark; }}
  * {{ box-sizing: border-box; }}
  body {{ font-family: system-ui, -apple-system, sans-serif; margin: 0;
          padding: 24px 16px; max-width: 560px; margin-inline: auto;
          line-height: 1.5; }}
  h1 {{ font-size: 1.3rem; margin: 0 0 4px; }}
  .sub {{ color: #888; margin-top: 0; font-size: .9rem; }}
  .card {{ border: 1px solid #8884; border-radius: 14px; padding: 18px;
           margin: 16px 0; }}
  .btn {{ display: block; text-align: center; background: #0B6E4F; color: #fff;
          text-decoration: none; padding: 16px; border-radius: 12px;
          font-size: 1.1rem; font-weight: 600; margin-top: 12px; }}
  .btn:active {{ opacity: .85; }}
  code {{ background: #8882; padding: 2px 6px; border-radius: 6px; }}
  .url {{ font-size: 1.05rem; word-break: break-all; text-align: center;
          margin: 10px 0; }}
  #qr {{ display: flex; justify-content: center; margin: 14px 0; min-height: 10px; }}
  #qr img, #qr canvas {{ background:#fff; padding:10px; border-radius:12px; }}
  .ok {{ color: #0B6E4F; }} .err {{ color: #C62828; }} .dim {{ color: #888; }}
  ul.files {{ padding-left: 18px; font-size: .9rem; }}
  footer {{ color:#888; font-size:.8rem; margin-top:24px; }}
</style>
</head>
<body>
  <h1>RTK Euskadi · servidor de desarrollo</h1>
  <p class="sub">Red local · {html.escape(base)}</p>

  <div class="card">
    {info_apk}
    {boton}
    <div id="qr"></div>
    <p class="url"><a href="/apk">{html.escape(url_apk)}</a></p>
    <p class="dim">Escanea el QR o abre la URL en el móvil. Android pedirá permiso
    para instalar apps de orígenes desconocidos la primera vez.</p>
  </div>

  <div class="card">
    <h1 style="font-size:1rem">Datos de depuración recibidos</h1>
    {lista_debug}
  </div>

  <footer>Solo para red local de confianza. Sin autenticación.</footer>

  <script>
    // QR hacia el APK. Si no hay internet para el CDN, queda la URL de arriba.
    (function () {{
      var url = {json.dumps(url_apk)};
      var s = document.createElement('script');
      s.src = 'https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js';
      s.onload = function () {{
        try {{ new QRCode(document.getElementById('qr'),
               {{ text: url, width: 200, height: 200 }}); }} catch (e) {{}}
      }};
      document.head.appendChild(s);
    }})();
  </script>
</body>
</html>"""


def main():
    ap = argparse.ArgumentParser(description="Servidor de desarrollo RTK Euskadi (descarga de APK + ingesta de debug)")
    ap.add_argument("--port", type=int, default=8000, help="Puerto (por defecto 8000)")
    ap.add_argument("--host", default="0.0.0.0", help="Interfaz de escucha (por defecto 0.0.0.0)")
    ap.add_argument("--apk", default=None, help="Ruta a un APK concreto (si no, se busca el más reciente del proyecto)")
    ap.add_argument("--data", default=None, help="Directorio donde guardar los datos de depuración")
    args = ap.parse_args()

    CONF["apk_fijo"] = args.apk
    CONF["puerto"] = args.port
    CONF["ip"] = ip_lan()
    if args.data:
        CONF["datos"] = Path(args.data).resolve()
    CONF["datos"].mkdir(parents=True, exist_ok=True)

    servidor = ThreadingHTTPServer((args.host, args.port), Manejador)
    servidor.daemon_threads = True

    base = f"http://{CONF['ip']}:{args.port}"
    apks = encontrar_apks()
    print("=" * 56)
    print("  Servidor de desarrollo RTK Euskadi en marcha")
    print("=" * 56)
    print(f"  En el móvil (misma red):  {base}/")
    print(f"  Descarga directa del APK: {base}/apk")
    print(f"  Datos de depuración:      {CONF['datos']}")
    if apks:
        print(f"  APK actual: {apks[0].name} ({tam_legible(apks[0].stat().st_size)})")
    else:
        print("  AVISO: no hay APK todavía. Compila con ./gradlew assembleDebug")
    print(f"  Local: http://127.0.0.1:{args.port}/   ·   Ctrl+C para parar")
    print("=" * 56)
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\n  Parando…")
    finally:
        servidor.shutdown()


if __name__ == "__main__":
    main()
