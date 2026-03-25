package com.tenaza.data.local.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Gestiona el par de claves ECDSA en el Android Keystore.
 * La clave privada nunca sale del enclave seguro del hardware.
 * La clave pública se usa en el handshake de emparejamiento con el gateway.
 */
class KeyStoreManager {

    companion object {
        private const val ALIAS = "tenaza_device_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    /**
     * Genera un par de claves ECDSA P-256 en el Android Keystore.
     * Si ya existe un par con el mismo alias, lo reemplaza.
     */
    fun generateEcdsaKeyPair(alias: String = ALIAS): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        kpg.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setUserAuthenticationRequired(false) // Sin biometría para la clave de dispositivo
                .build()
        )
        return kpg.generateKeyPair()
    }

    /**
     * Devuelve la clave pública del dispositivo en formato Base64 (sin wrapping).
     * Si no existe par de claves, lo genera primero.
     */
    fun getPublicKeyBase64(): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val publicKey = ks.getCertificate(ALIAS)?.publicKey
            ?: generateEcdsaKeyPair(ALIAS).public
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Comprueba si ya existe un par de claves en el Keystore.
     */
    fun hasKeyPair(): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.containsAlias(ALIAS)
    }

    /**
     * Elimina el par de claves del Keystore (usado al desemparejar el dispositivo).
     */
    fun deleteKeyPair() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.deleteEntry(ALIAS)
    }

    /**
     * Firma los datos con la clave privada del dispositivo usando SHA256withECDSA.
     * Usado en el handshake de autenticación con el gateway.
     */
    fun sign(data: ByteArray): ByteArray {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(ALIAS, null)
            ?: throw IllegalStateException("No existe clave privada en el Keystore. Llama a generateEcdsaKeyPair() primero.")
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initSign(privateKey as java.security.PrivateKey)
        sig.update(data)
        return sig.sign()
    }
}
