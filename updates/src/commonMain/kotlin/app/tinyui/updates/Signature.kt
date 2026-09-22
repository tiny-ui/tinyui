package app.tinyui.updates

import okio.ByteString.Companion.decodeBase64

/** ECDSA P-256 / SHA-256 over raw bytes; key is an X9.63 point, signature DER, both base64 (docs/updates.md §7). */
internal fun interface SignatureVerifier {
    fun verify(publicKey: String, data: ByteArray, signature: String): Boolean
}

internal object PlatformSignatureVerifier : SignatureVerifier {
    override fun verify(publicKey: String, data: ByteArray, signature: String): Boolean {
        val key = publicKey.decodeBase64()?.toByteArray() ?: return false
        val sig = signature.decodeBase64()?.toByteArray() ?: return false
        if (key.size != 65 || key[0] != 0x04.toByte() || sig.isEmpty()) return false
        return runCatching { verifyEcdsaP256(key, data, sig) }.getOrDefault(false)
    }
}

internal expect fun verifyEcdsaP256(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
