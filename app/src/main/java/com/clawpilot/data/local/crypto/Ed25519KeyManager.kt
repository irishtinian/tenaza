package com.clawpilot.data.local.crypto

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.security.SecureRandom

/**
 * Gestor de claves Ed25519 usando BouncyCastle.
 * Necesario porque Android no soporta Ed25519 en los providers estándar de JCA.
 */
class Ed25519KeyManager(private val context: Context) {

    companion object {
        private const val PRIV_KEY_FILE = "device_ed25519_priv.key"
        private const val PUB_KEY_FILE = "device_ed25519_pub.key"
    }

    private var cachedPrivateKey: Ed25519PrivateKeyParameters? = null
    private var cachedPublicKey: Ed25519PublicKeyParameters? = null

    fun generateKeyPair() {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val priv = keyPair.private as Ed25519PrivateKeyParameters
        val pub = keyPair.public as Ed25519PublicKeyParameters

        File(context.filesDir, PRIV_KEY_FILE).writeBytes(priv.encoded)
        File(context.filesDir, PUB_KEY_FILE).writeBytes(pub.encoded)
        cachedPrivateKey = priv
        cachedPublicKey = pub
    }

    fun hasKeyPair(): Boolean {
        return File(context.filesDir, PRIV_KEY_FILE).exists() &&
                File(context.filesDir, PUB_KEY_FILE).exists()
    }

    /**
     * Clave pública como 32 bytes raw en base64url (sin padding).
     */
    fun getPublicKeyBase64Url(): String {
        val pub = getPublicKey()
        return Base64.encodeToString(pub.encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Firma datos con Ed25519. Devuelve base64url sin padding.
     */
    fun sign(data: ByteArray): String {
        val priv = getPrivateKey()
        val signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(data, 0, data.size)
        val sig = signer.generateSignature()
        return Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Device ID = SHA-256 hex de los 32 bytes raw de la public key.
     */
    fun getDeviceId(): String {
        val pub = getPublicKey()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(pub.encoded).joinToString("") { "%02x".format(it) }
    }

    fun deleteKeyPair() {
        File(context.filesDir, PRIV_KEY_FILE).delete()
        File(context.filesDir, PUB_KEY_FILE).delete()
        cachedPrivateKey = null
        cachedPublicKey = null
    }

    private fun getPrivateKey(): Ed25519PrivateKeyParameters {
        cachedPrivateKey?.let { return it }
        if (!hasKeyPair()) { generateKeyPair() }
        val bytes = File(context.filesDir, PRIV_KEY_FILE).readBytes()
        val key = Ed25519PrivateKeyParameters(bytes, 0)
        cachedPrivateKey = key
        return key
    }

    private fun getPublicKey(): Ed25519PublicKeyParameters {
        cachedPublicKey?.let { return it }
        if (!hasKeyPair()) { generateKeyPair() }
        val bytes = File(context.filesDir, PUB_KEY_FILE).readBytes()
        val key = Ed25519PublicKeyParameters(bytes, 0)
        cachedPublicKey = key
        return key
    }
}
