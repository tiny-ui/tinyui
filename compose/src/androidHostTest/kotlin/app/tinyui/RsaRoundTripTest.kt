package app.tinyui

import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** The padding is random, so the only check that means anything is decrypting with the private key. */
class RsaRoundTripTest {
    @Test
    fun thePrivateKeyGetsTheTextBack() {
        val text = "Passw0rd!中文"
        val cipher = Base64.decode(rsaEncrypt(text, RsaTestKeys.PUBLIC))
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(RsaTestKeys.PRIVATE_PKCS8)))
        val plain = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, privateKey); doFinal(cipher) }
        assertEquals(text, plain.decodeToString())
    }
}
