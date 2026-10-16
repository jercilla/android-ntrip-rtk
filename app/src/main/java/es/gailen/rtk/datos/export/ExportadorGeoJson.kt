package es.gailen.rtk.datos.export

import es.gailen.rtk.datos.puntos.PuntoMedido
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Exporta puntos a GeoJSON (RFC 7946). Las coordenadas van en orden [lon, lat, altura]
 * en WGS84/CRS84; a este nivel ETRS89 ≈ WGS84, y se indica el sistema en las propiedades.
 * Alturas **elipsoidales**. Abrible en QGIS.
 */
object ExportadorGeoJson {

    private val FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private val json = Json { prettyPrint = true }

    fun generar(puntos: List<PuntoMedido>): String {
        val obj = buildJsonObject {
            put("type", "FeatureCollection")
            put("name", "puntos_rtk")
            putJsonArray("features") {
                for (p in puntos) add(feature(p))
            }
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }

    private fun feature(p: PuntoMedido) = buildJsonObject {
        put("type", "Feature")
        putJsonObject("geometry") {
            put("type", "Point")
            putJsonArray("coordinates") {
                add(JsonPrimitive(p.lon))
                add(JsonPrimitive(p.lat))
                add(JsonPrimitive(p.alturaElipsoidal))
            }
        }
        putJsonObject("properties") {
            put("id", p.id)
            put("nombre", p.nombre)
            put("notas", p.notas)
            put("altura_elipsoidal", p.alturaElipsoidal)
            put("altura_jalon", p.alturaJalon)
            put("cota_suelo", p.cotaSuelo)
            put("sigma_n", p.sigmaNorte)
            put("sigma_e", p.sigmaEste)
            put("sigma_u", p.sigmaAltura)
            put("epocas", p.epocas)
            put("porcentaje_fix", p.porcentajeFix)
            put("calidad", p.calidad)
            put("perfil_ntrip", p.perfilNtrip?.let { JsonPrimitive(it) } ?: JsonNull)
            put("punto_montaje", p.puntoMontaje?.let { JsonPrimitive(it) } ?: JsonNull)
            put("distancia_base", p.distanciaBase?.let { JsonPrimitive(it) } ?: JsonNull)
            put("fecha_utc", FECHA.format(Instant.ofEpochMilli(p.fechaHoraUtc)))
            put("sistema_referencia", p.sistemaReferencia)
        }
    }
}
