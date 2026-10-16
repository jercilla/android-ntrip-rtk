package es.gailen.rtk

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de entrada de la app y raíz del grafo de dependencias de Hilt. */
@HiltAndroidApp
class RtkApp : Application()
