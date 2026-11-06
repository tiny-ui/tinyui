package app.tinyui

import java.security.KeyFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

internal actual fun platformRsaEncrypt(plain: ByteArray, key: RsaPublicKey): ByteArray {
    // the common parse should have caught it already; a key the platform still refuses is the page's input, not the network
    val publicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(key.spki))
    } catch (e: InvalidKeySpecException) {
        throw HostException("E_INVALID", "the platform rejected the public key: ${e.message}")
    }
    return Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.ENCRYPT_MODE, publicKey)
        doFinal(plain)
    }
}
