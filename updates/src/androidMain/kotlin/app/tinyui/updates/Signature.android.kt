package app.tinyui.updates

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** SubjectPublicKeyInfo header for an uncompressed P-256 point: the raw point becomes X.509 by prefixing it. */
private val P256_SPKI_PREFIX = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
)

internal actual fun verifyEcdsaP256(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
    val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(P256_SPKI_PREFIX + publicKey))
    return Signature.getInstance("SHA256withECDSA").run {
        initVerify(key)
        update(data)
        verify(signature)
    }
}
