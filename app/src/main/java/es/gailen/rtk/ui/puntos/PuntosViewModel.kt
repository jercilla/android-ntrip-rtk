package es.gailen.rtk.ui.puntos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.gailen.rtk.datos.debug.RegistroDebugHttp
import es.gailen.rtk.datos.export.ExportadorCsv
import es.gailen.rtk.datos.export.ExportadorGeoJson
import es.gailen.rtk.datos.puntos.PuntoMedido
import es.gailen.rtk.datos.puntos.PuntosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FormatoExport { CSV, GEOJSON }

@HiltViewModel
class PuntosViewModel @Inject constructor(
    private val repo: PuntosRepository,
    @ApplicationContext private val contexto: Context,
    private val registro: RegistroDebugHttp,
) : ViewModel() {

    val puntos: StateFlow<List<PuntoMedido>> =
        repo.puntos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun borrar(id: Long) = viewModelScope.launch { repo.borrar(id) }

    fun actualizar(punto: PuntoMedido, nombre: String, notas: String) = viewModelScope.launch {
        repo.actualizar(punto.copy(nombre = nombre.ifBlank { punto.nombre }, notas = notas))
    }

    fun exportar(uri: Uri, formato: FormatoExport, onResultado: (Boolean) -> Unit) {
        val lista = puntos.value
        viewModelScope.launch {
            val contenido = when (formato) {
                FormatoExport.CSV -> ExportadorCsv.generar(lista)
                FormatoExport.GEOJSON -> ExportadorGeoJson.generar(lista)
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // "wt" = escribir truncando (evita restos de un fichero previo).
                    contexto.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(contenido.toByteArray(Charsets.UTF_8))
                        it.flush()
                    } ?: error("openOutputStream devolvió null")
                }.isSuccess
            }
            registro.registra(
                "export",
                "$formato puntos=${lista.size} bytes=${contenido.toByteArray().size} ok=$ok " +
                    "inicio='${contenido.take(40).replace("\n", " ")}'",
            )
            onResultado(ok)
        }
    }
}
