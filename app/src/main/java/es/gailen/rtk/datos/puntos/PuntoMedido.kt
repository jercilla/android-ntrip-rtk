package es.gailen.rtk.datos.puntos

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un punto medido y guardado. Alturas **elipsoidales** (ETRS89). La cota del suelo es la
 * altura elipsoidal menos la altura del jalón. Campos mínimos según la sección 6.4.
 */
@Entity(tableName = "puntos")
data class PuntoMedido(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val notas: String = "",
    val lat: Double,
    val lon: Double,
    val alturaElipsoidal: Double,
    val alturaJalon: Double,
    val cotaSuelo: Double,
    val sigmaNorte: Double,
    val sigmaEste: Double,
    val sigmaAltura: Double,
    val epocas: Int,
    val porcentajeFix: Double,
    val calidad: String,              // CalidadFix.name
    val perfilNtrip: String? = null,
    val puntoMontaje: String? = null,
    val distanciaBase: Double? = null,
    val fechaHoraUtc: Long,           // epoch millis UTC
    val sistemaReferencia: String = "ETRS89",
)
