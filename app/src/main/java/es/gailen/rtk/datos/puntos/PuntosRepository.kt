package es.gailen.rtk.datos.puntos

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Acceso a los puntos guardados. */
@Singleton
class PuntosRepository @Inject constructor(private val dao: PuntoDao) {

    val puntos: Flow<List<PuntoMedido>> = dao.observarTodos()

    suspend fun guardar(punto: PuntoMedido): Long = dao.insertar(punto)

    suspend fun actualizar(punto: PuntoMedido) = dao.actualizar(punto)

    suspend fun borrar(id: Long) = dao.borrarPorId(id)

    suspend fun porId(id: Long): PuntoMedido? = dao.porId(id)
}
