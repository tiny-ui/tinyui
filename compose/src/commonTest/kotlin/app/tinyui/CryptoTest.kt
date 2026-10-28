package app.tinyui

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CryptoTest {
    private fun invalid(block: () -> Unit) = assertEquals("E_INVALID", assertFailsWith<HostException> { block() }.code)

    @Test
    fun readsTheModulusSizeOutOfX509() = assertEquals(256, RsaPublicKey.parse(RsaTestKeys.PUBLIC).modulusBytes)

    @Test
    fun takesThePemBodyWithItsLineBreaks() =
        assertEquals(256, RsaPublicKey.parse(RsaTestKeys.PUBLIC.chunked(64).joinToString("\n")).modulusBytes)

    @Test
    fun rejectsWhatIsNotAnX509RsaKey() {
        invalid { RsaPublicKey.parse("not base64!") }
        invalid { RsaPublicKey.parse(Base64.encode(byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01))) }
        // the bare PKCS#1 RSAPublicKey: the contract is X.509, say so instead of guessing
        invalid { RsaPublicKey.parse(Base64.encode(RsaPublicKey.parse(RsaTestKeys.PUBLIC).pkcs1)) }
    }

    @Test
    fun encryptsUpToTheKeySizeMinusElevenBytes() {
        val cipher = rsaEncrypt("a".repeat(245), RsaTestKeys.PUBLIC)
        assertEquals(256, Base64.decode(cipher).size)
        invalid { rsaEncrypt("a".repeat(246), RsaTestKeys.PUBLIC) }
        // UTF-8 bytes count, not characters: 82 × 3 bytes = 246
        invalid { rsaEncrypt("密".repeat(82), RsaTestKeys.PUBLIC) }
    }

    @Test
    fun paddingIsRandom() = assertNotEquals(rsaEncrypt("s3cret", RsaTestKeys.PUBLIC), rsaEncrypt("s3cret", RsaTestKeys.PUBLIC))
}
