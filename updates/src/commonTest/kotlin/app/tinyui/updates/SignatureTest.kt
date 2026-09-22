package app.tinyui.updates

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real ECDSA on this platform against a manifest `tinyui bundle` signed (fixture from packages/cli, Node's crypto). */
class SignatureTest {
    @Test
    fun verifiesTheBytesTinyuiBundleSigned() {
        assertTrue(PlatformSignatureVerifier.verify(Fixture.PUBLIC_KEY, Fixture.MANIFEST.encodeToByteArray(), Fixture.SIGNATURE))
    }

    @Test
    fun rejectsAnyChangeToTheBytesTheKeyOrTheSignature() {
        val bytes = Fixture.MANIFEST.encodeToByteArray()
        assertFalse(PlatformSignatureVerifier.verify(Fixture.PUBLIC_KEY, bytes + 0x20, Fixture.SIGNATURE), "one byte appended")
        assertFalse(PlatformSignatureVerifier.verify(Fixture.PUBLIC_KEY, bytes.copyOf(bytes.size - 1), Fixture.SIGNATURE), "one byte removed")
        assertFalse(PlatformSignatureVerifier.verify(Fixture.OTHER_PUBLIC_KEY, bytes, Fixture.SIGNATURE), "another key")
        val flipped = Fixture.SIGNATURE.toCharArray().also { it[20] = if (it[20] == 'A') 'B' else 'A' }.concatToString()
        assertFalse(PlatformSignatureVerifier.verify(Fixture.PUBLIC_KEY, bytes, flipped), "signature altered")
        assertFalse(PlatformSignatureVerifier.verify("not base64!", bytes, Fixture.SIGNATURE))
        assertFalse(PlatformSignatureVerifier.verify(Fixture.PUBLIC_KEY, bytes, ""))
    }
}
