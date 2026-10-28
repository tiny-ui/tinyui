package app.tinyui

import kotlinx.cinterop.BetaInteropApi
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
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

// SecKeyCreateWithData takes RSA public keys as PKCS#1 RSAPublicKey, not the X.509 wrapper around it
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun platformRsaEncrypt(plain: ByteArray, key: RsaPublicKey): ByteArray {
    val keyData = cfData(key.pkcs1)
    val attributes = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
    CFDictionaryAddValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)
    val plainData = cfData(plain)
    try {
        val secKey = SecKeyCreateWithData(keyData, attributes, null) ?: throw HostException("E_INVALID", "the platform rejected the public key")
        try {
            val cipher = SecKeyCreateEncryptedData(secKey, kSecKeyAlgorithmRSAEncryptionPKCS1, plainData, null)
                ?: throw HostException("E_INVALID", "RSA encryption failed")
            try {
                return CFDataGetBytePtr(cipher)!!.reinterpret<UByteVar>().readBytes(CFDataGetLength(cipher).toInt())
            } finally {
                CFRelease(cipher)
            }
        } finally {
            CFRelease(secKey)
        }
    } finally {
        CFRelease(plainData)
        CFRelease(attributes)
        CFRelease(keyData)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfData(bytes: ByteArray): CFDataRef =
    // an empty array has no element 0 to point at; an empty text is still a valid plaintext
    if (bytes.isEmpty()) CFDataCreate(null, null, 0)!!
    else bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }!!
