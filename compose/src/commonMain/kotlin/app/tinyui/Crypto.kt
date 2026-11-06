package app.tinyui

import kotlin.io.encoding.Base64

/**
 * An RSA public key read from X.509 SubjectPublicKeyInfo DER (docs/native-api.md §16). Both forms travel on: Android's
 * KeyFactory wants [spki], iOS's SecKeyCreateWithData wants the PKCS#1 RSAPublicKey inside it ([pkcs1]).
 */
internal class RsaPublicKey private constructor(val spki: ByteArray, val pkcs1: ByteArray, val modulusBytes: Int) {
    companion object {
        private const val MIN_BITS = 1024

        // 1.2.840.113549.1.1.1
        private val RSA_ENCRYPTION = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)

        fun parse(base64: String): RsaPublicKey {
            val der = try { Base64.decode(base64.filterNot { it.isWhitespace() }) } catch (e: IllegalArgumentException) { invalid("the public key is not base64") }
            // every level read whole, so what passes here is what both platforms accept and their own errors never show
            val outer = Der(der)
            val spki = outer.sequence().also { outer.done() }
            val algorithm = spki.sequence()
            if (!algorithm.next(0x06).contentEquals(RSA_ENCRYPTION)) invalid("the public key is not an RSA key, or not X.509 SubjectPublicKeyInfo")
            // RFC 3279: the parameters are NULL, though some encoders leave them out
            if (!algorithm.atEnd()) { if (algorithm.next(0x05).isNotEmpty()) invalid("the public key's algorithm parameters are not NULL") }
            algorithm.done()
            val bits = spki.next(0x03)
            spki.done()
            if (bits.isEmpty() || bits[0] != 0.toByte()) invalid("the public key's BIT STRING is malformed")
            val pkcs1 = bits.copyOfRange(1, bits.size)
            val wrapper = Der(pkcs1)
            val rsa = wrapper.sequence().also { wrapper.done() }
            val modulus = rsa.next(0x02).dropWhile { it == 0.toByte() }
            val exponent = rsa.next(0x02).dropWhile { it == 0.toByte() }
            rsa.done()
            if (exponent.isEmpty()) invalid("the public key's exponent is zero")
            // 512 and 768 bits have been factored; 1024 stays for servers that still use it
            if (modulus.size * 8 < MIN_BITS) invalid("the public key has ${modulus.size * 8} bits; at least $MIN_BITS are required")
            return RsaPublicKey(der, pkcs1, modulus.size)
        }
    }
}

/** PKCS#1 v1.5 encryption of [text] as UTF-8; base64 out. Length and key are checked here, so both platforms fail alike. */
internal fun rsaEncrypt(text: String, publicKeyBase64: String): String {
    val key = RsaPublicKey.parse(publicKeyBase64)
    val plain = text.encodeToByteArray()
    // PKCS#1 v1.5 needs 11 bytes of padding inside the modulus
    if (plain.size > key.modulusBytes - 11) invalid("the text is ${plain.size} bytes; a ${key.modulusBytes * 8}-bit key takes at most ${key.modulusBytes - 11}")
    return Base64.encode(platformRsaEncrypt(plain, key))
}

internal expect fun platformRsaEncrypt(plain: ByteArray, key: RsaPublicKey): ByteArray

private fun invalid(message: String): Nothing = throw HostException("E_INVALID", message)

/** Just enough DER to walk SubjectPublicKeyInfo: definite lengths, one element at a time. */
private class Der(private val bytes: ByteArray, private var at: Int = 0, private val end: Int = bytes.size) {
    fun sequence(): Der {
        val (start, stop) = element(0x30)
        return Der(bytes, start, stop)
    }

    fun next(tag: Int): ByteArray {
        val (start, stop) = element(tag)
        return bytes.copyOfRange(start, stop)
    }

    fun atEnd() = at == end

    /** Nothing may follow the last element read. */
    fun done() { if (at != end) invalid("the public key has trailing data") }

    private fun element(tag: Int): Pair<Int, Int> {
        if (at + 2 > end || bytes[at].toInt() and 0xFF != tag) invalid("the public key is not valid DER")
        var length = bytes[at + 1].toInt() and 0xFF
        var start = at + 2
        if (length >= 0x80) {
            val count = length and 0x7F
            if (count == 0 || count > 3 || start + count > end) invalid("the public key is not valid DER")
            length = 0
            repeat(count) { length = (length shl 8) or (bytes[start + it].toInt() and 0xFF) }
            start += count
        }
        if (start + length > end) invalid("the public key is not valid DER")
        at = start + length
        return start to start + length
    }
}
