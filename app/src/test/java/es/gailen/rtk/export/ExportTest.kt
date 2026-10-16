package es.gailen.rtk.export

import es.gailen.rtk.datos.export.ExportadorCsv
import es.gailen.rtk.datos.export.ExportadorGeoJson
import es.gailen.rtk.datos.puntos.PuntoMedido
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    private fun punto(nombre: String = "P1") = PuntoMedido(
        id = 1, nombre = nombre, notas = "nota",
        lat = 43.2630000, lon = -2.9350000, alturaElipsoidal = 114.5,
        alturaJalon = 1.8, cotaSuelo = 112.7,
        sigmaNorte = 0.012, sigmaEste = 0.015, sigmaAltura = 0.02,
        epocas = 60, porcentajeFix = 100.0, calidad = "RTK fix",
        perfilNtrip = "Euskadi · APBI3M", puntoMontaje = "APBI3M",
        distanciaBase = 2874.5, fechaHoraUtc = 1_760_616_000_000L,
    )

    @Test
    fun `CSV tiene cabecera y una fila por punto con decimales en punto`() {
        val csv = ExportadorCsv.generar(listOf(punto(), punto("P2")))
        val lineas = csv.trim().split("\r\n")
        assertEquals(3, lineas.size) // cabecera + 2
        assertTrue(lineas[0].startsWith("id,nombre,notas,lat,lon"))
        assertTrue(lineas[1].contains("43.263000000"))
        assertTrue(lineas[1].contains("-2.935000000"))
        assertTrue(lineas[1].contains("ETRS89"))
    }

    @Test
    fun `CSV entrecomilla nombres con coma`() {
        val csv = ExportadorCsv.generar(listOf(punto("Esquina, norte")))
        assertTrue(csv.contains("\"Esquina, norte\""))
    }

    @Test
    fun `GeoJSON es un FeatureCollection valido con coordenadas lon-lat-altura`() {
        val geo = ExportadorGeoJson.generar(listOf(punto()))
        val raiz = Json.parseToJsonElement(geo) as JsonObject
        assertEquals("FeatureCollection", raiz["type"]!!.jsonPrimitive.content)
        val features = raiz["features"]!!.jsonArray
        assertEquals(1, features.size)
        val f = features[0].jsonObject
        val coords = f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(-2.935, coords[0].jsonPrimitive.content.toDouble(), 1e-9)   // lon primero
        assertEquals(43.263, coords[1].jsonPrimitive.content.toDouble(), 1e-9)   // lat después
        assertEquals(114.5, coords[2].jsonPrimitive.content.toDouble(), 1e-9)    // altura
        val props = f["properties"]!!.jsonObject
        assertEquals("P1", props["nombre"]!!.jsonPrimitive.content)
        assertEquals("ETRS89", props["sistema_referencia"]!!.jsonPrimitive.content)
    }
}
