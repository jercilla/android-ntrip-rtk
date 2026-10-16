package es.gailen.rtk.datos.ajustes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import es.gailen.rtk.datos.seguridad.CifradorKeystore
import es.gailen.rtk.dominio.AjustesApp
import es.gailen.rtk.dominio.CredencialesNtrip
import es.gailen.rtk.dominio.FuentePosicionTipo
import es.gailen.rtk.dominio.PerfilNtrip
import es.gailen.rtk.dominio.PerfilesPorDefecto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente única de verdad de los ajustes y los perfiles NTRIP.
 *
 * - Los ajustes generales y la configuración de los perfiles se guardan en claro en DataStore.
 * - Las credenciales (usuario y contraseña) se guardan **cifradas** con [CifradorKeystore],
 *   una entrada por perfil. Nunca se exponen en los flujos de solo lectura.
 */
@Singleton
class AjustesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cifrador: CifradorKeystore,
    private val json: Json,
) {

    private val preferencias: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val ajustes: Flow<AjustesApp> = preferencias.map { p ->
        AjustesApp(
            fuente = p[Claves.FUENTE]?.let { runCatching { FuentePosicionTipo.valueOf(it) }.getOrNull() }
                ?: FuentePosicionTipo.SIMULADA,
            baudrateUsb = p[Claves.BAUDRATE] ?: 115200,
            perfilNtripActivoId = p[Claves.PERFIL_ACTIVO] ?: PerfilesPorDefecto.ID_APBI3M,
            alturaJalonMetros = p[Claves.ALTURA_JALON] ?: 0.0,
            duracionMedicionSegundos = p[Claves.DURACION] ?: 60,
            aceptarFloat = p[Claves.ACEPTAR_FLOAT] ?: false,
        )
    }

    val perfiles: Flow<List<PerfilNtrip>> = preferencias.map { p ->
        p[Claves.PERFILES]
            ?.let { runCatching { json.decodeFromString<List<PerfilNtrip>>(it) }.getOrNull() }
            ?: PerfilesPorDefecto.lista
    }

    /** Ids de perfil que ya tienen credenciales cifradas guardadas. */
    val idsConCredenciales: Flow<Set<String>> = preferencias.map { p ->
        p.asMap().keys
            .map { it.name }
            .filter { it.startsWith(Claves.PREFIJO_CRED_USUARIO) }
            .map { it.removePrefix(Claves.PREFIJO_CRED_USUARIO) }
            .toSet()
    }

    // --- Ajustes generales ---

    suspend fun actualizarFuente(tipo: FuentePosicionTipo) =
        dataStore.edit { it[Claves.FUENTE] = tipo.name }

    suspend fun actualizarBaudrate(baudrate: Int) =
        dataStore.edit { it[Claves.BAUDRATE] = baudrate }

    suspend fun actualizarPerfilActivo(id: String) =
        dataStore.edit { it[Claves.PERFIL_ACTIVO] = id }

    suspend fun actualizarAlturaJalon(metros: Double) =
        dataStore.edit { it[Claves.ALTURA_JALON] = metros }

    suspend fun actualizarDuracionMedicion(segundos: Int) =
        dataStore.edit { it[Claves.DURACION] = segundos }

    suspend fun actualizarAceptarFloat(aceptar: Boolean) =
        dataStore.edit { it[Claves.ACEPTAR_FLOAT] = aceptar }

    // --- Perfiles (configuración, sin secretos) ---

    /** Inserta o actualiza un perfil por su id. */
    suspend fun guardarPerfil(perfil: PerfilNtrip) = dataStore.edit { prefs ->
        val actuales = prefs.perfilesOEstandar()
        val nueva = actuales.filterNot { it.id == perfil.id } + perfil
        prefs[Claves.PERFILES] = json.encodeToString(nueva)
    }

    /** Elimina un perfil editable y sus credenciales. Los perfiles no editables se ignoran. */
    suspend fun eliminarPerfil(id: String) = dataStore.edit { prefs ->
        val actuales = prefs.perfilesOEstandar()
        val objetivo = actuales.firstOrNull { it.id == id }
        if (objetivo != null && objetivo.editable) {
            prefs[Claves.PERFILES] = json.encodeToString(actuales.filterNot { it.id == id })
            prefs.remove(Claves.credUsuario(id))
            prefs.remove(Claves.credPassword(id))
        }
    }

    private fun Preferences.perfilesOEstandar(): List<PerfilNtrip> =
        this[Claves.PERFILES]
            ?.let { runCatching { json.decodeFromString<List<PerfilNtrip>>(it) }.getOrNull() }
            ?: PerfilesPorDefecto.lista

    // --- Credenciales (cifradas) ---

    suspend fun guardarCredenciales(perfilId: String, credenciales: CredencialesNtrip) {
        val u = cifrador.cifrar(credenciales.usuario)
        val pw = cifrador.cifrar(credenciales.password)
        dataStore.edit {
            it[Claves.credUsuario(perfilId)] = u
            it[Claves.credPassword(perfilId)] = pw
        }
    }

    /** Devuelve las credenciales descifradas, o null si no hay o no se pueden descifrar. */
    suspend fun leerCredenciales(perfilId: String): CredencialesNtrip? {
        val p = preferencias.first()
        val u = p[Claves.credUsuario(perfilId)] ?: return null
        val pw = p[Claves.credPassword(perfilId)] ?: return null
        return runCatching {
            CredencialesNtrip(cifrador.descifrar(u), cifrador.descifrar(pw))
        }.getOrNull()
    }

    suspend fun borrarCredenciales(perfilId: String) = dataStore.edit {
        it.remove(Claves.credUsuario(perfilId))
        it.remove(Claves.credPassword(perfilId))
    }

    private object Claves {
        const val PREFIJO_CRED_USUARIO = "cred_usuario_"
        const val PREFIJO_CRED_PASSWORD = "cred_password_"

        val FUENTE = stringPreferencesKey("fuente")
        val BAUDRATE = intPreferencesKey("baudrate_usb")
        val PERFIL_ACTIVO = stringPreferencesKey("perfil_activo_id")
        val ALTURA_JALON = doublePreferencesKey("altura_jalon")
        val DURACION = intPreferencesKey("duracion_medicion")
        val ACEPTAR_FLOAT = booleanPreferencesKey("aceptar_float")
        val PERFILES = stringPreferencesKey("perfiles_json")

        fun credUsuario(id: String) = stringPreferencesKey("$PREFIJO_CRED_USUARIO$id")
        fun credPassword(id: String) = stringPreferencesKey("$PREFIJO_CRED_PASSWORD$id")
    }
}
