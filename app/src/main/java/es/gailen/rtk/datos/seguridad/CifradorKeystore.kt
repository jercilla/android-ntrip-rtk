package es.gailen.rtk.datos.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra y descifra cadenas cortas (credenciales NTRIP) con una clave AES-256/GCM
 * almacenada en el **Android Keystore**. La clave nunca abandona el almacén seguro
 * del dispositivo: aquí solo circulan el texto en claro y el blob cifrado en Base64.
 *
 * Formato del blob: Base64( IV[12] || ciphertext+tag ).
 */
@Singleton
class CifradorKeystore @Inject constructor() {

    /** Cifra [textoPlano] y devuelve un blob Base64 autocontenido (incluye el IV). */
    fun cifrar(textoPlano: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        val iv = cipher.iv
        val cifrado = cipher.doFinal(textoPlano.toByteArray(Charsets.UTF_8))
        val salida = ByteArray(iv.size + cifrado.size)
        System.arraycopy(iv, 0, salida, 0, iv.size)
        System.arraycopy(cifrado, 0, salida, iv.size, cifrado.size)
        return Base64.encodeToString(salida, Base64.NO_WRAP)
    }

    /** Descifra un blob generado por [cifrar]. Lanza si el blob es inválido o la clave cambió. */
    fun descifrar(blob: String): String {
        val datos = Base64.decode(blob, Base64.NO_WRAP)
        require(datos.size > TAM_IV) { "Blob cifrado demasiado corto" }
        val iv = datos.copyOfRange(0, TAM_IV)
        val cifrado = datos.copyOfRange(TAM_IV, datos.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerOCrearClave(), GCMParameterSpec(TAM_TAG_BITS, iv))
        return String(cipher.doFinal(cifrado), Charsets.UTF_8)
    }

    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    private companion object {
        const val PROVEEDOR = "AndroidKeyStore"
        const val ALIAS = "rtk_credenciales_ntrip"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val TAM_IV = 12
        const val TAM_TAG_BITS = 128
    }
}
