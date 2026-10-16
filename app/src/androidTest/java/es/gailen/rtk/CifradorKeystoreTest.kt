package es.gailen.rtk

import androidx.test.ext.junit.runners.AndroidJUnit4
import es.gailen.rtk.datos.seguridad.CifradorKeystore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requiere dispositivo o emulador: el Android Keystore no existe en la JVM de los
 * tests unitarios. Comprueba el ciclo cifrar → descifrar y que el blob no sea legible.
 */
@RunWith(AndroidJUnit4::class)
class CifradorKeystoreTest {

    private val cifrador = CifradorKeystore()

    @Test
    fun ciclo_cifrar_descifrar_devuelve_el_original() {
        val original = "usuario_de_prueba:contraseña-ñ-€-123"
        val blob = cifrador.cifrar(original)
        assertNotEquals("El blob no debe coincidir con el texto plano", original, blob)
        assertEquals(original, cifrador.descifrar(blob))
    }

    @Test
    fun dos_cifrados_del_mismo_texto_difieren_por_el_IV() {
        val texto = "misma_entrada"
        assertNotEquals(cifrador.cifrar(texto), cifrador.cifrar(texto))
    }
}
