package app.tinyui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** The padding is random, so the only check that means anything is decrypting with the private key. */
@OptIn(ExperimentalForeignApi::class)
class RsaRoundTripTest {
    @Test
    fun thePrivateKeyGetsTheTextBack() {
        val text = "Passw0rd!中文"
        val cipher = Base64.decode(rsaEncrypt(text, RsaTestKeys.PUBLIC))
        val attributes = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        val privateKey = SecKeyCreateWithData(data(Base64.decode(RsaTestKeys.PRIVATE_PKCS1)), attributes, null)!!
        val plain = SecKeyCreateDecryptedData(privateKey, kSecKeyAlgorithmRSAEncryptionPKCS1, data(cipher), null)!!
        assertEquals(text, CFDataGetBytePtr(plain)!!.reinterpret<UByteVar>().readBytes(CFDataGetLength(plain).toInt()).decodeToString())
    }

    private fun data(bytes: ByteArray) = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }!!
}
