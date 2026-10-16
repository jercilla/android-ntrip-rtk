package es.gailen.rtk.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import es.gailen.rtk.ui.ajustes.AjustesScreen
import es.gailen.rtk.ui.diagnostico.DiagnosticoScreen
import es.gailen.rtk.ui.estado.EstadoScreen
import es.gailen.rtk.ui.medicion.MedicionScreen
import es.gailen.rtk.ui.puntos.PuntosScreen
import es.gailen.rtk.ui.replanteo.ReplanteoScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavegacionRtk() {
    val navController = rememberNavController()
    val entradaActual by navController.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route
    val destinoActual = Destino.porRuta(rutaActual)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destinoActual.etiqueta) },
                actions = {
                    // Acceso al diagnóstico desde cualquier pantalla.
                    IconButton(onClick = { navController.navegarA(Destino.DIAGNOSTICO) }) {
                        Icon(Icons.Filled.BugReport, contentDescription = Destino.DIAGNOSTICO.etiqueta)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destino.entries.filter { it.enBarra }.forEach { destino ->
                    val seleccionado = entradaActual?.destination?.hierarchy
                        ?.any { it.route == destino.ruta } == true
                    NavigationBarItem(
                        selected = seleccionado,
                        onClick = { navController.navegarA(destino) },
                        icon = { Icon(destino.icono, contentDescription = destino.etiqueta) },
                        label = { Text(destino.etiqueta) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destino.INICIO.ruta,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destino.ESTADO.ruta) { EstadoScreen() }
            composable(Destino.MEDICION.ruta) { MedicionScreen() }
            composable(Destino.PUNTOS.ruta) { PuntosScreen() }
            composable(Destino.REPLANTEO.ruta) { ReplanteoScreen() }
            composable(Destino.AJUSTES.ruta) { AjustesScreen() }
            composable(Destino.DIAGNOSTICO.ruta) { DiagnosticoScreen() }
        }
    }
}

/** Navega a [destino] evitando acumular copias en la pila y conservando el estado. */
private fun androidx.navigation.NavController.navegarA(destino: Destino) {
    navigate(destino.ruta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
