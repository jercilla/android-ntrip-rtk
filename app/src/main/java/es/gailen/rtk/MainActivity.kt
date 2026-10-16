package es.gailen.rtk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.gailen.rtk.ui.navigation.NavegacionRtk
import es.gailen.rtk.ui.theme.RTKEuskadiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RTKEuskadiTheme {
                NavegacionRtk()
            }
        }
    }
}
