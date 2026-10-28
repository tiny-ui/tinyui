package app.tinyui

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

internal actual fun platformRsaEncrypt(plain: ByteArray, key: RsaPublicKey): ByteArray {
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(key.spki))
    return Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.ENCRYPT_MODE, publicKey)
        doFinal(plain)
    }
}
