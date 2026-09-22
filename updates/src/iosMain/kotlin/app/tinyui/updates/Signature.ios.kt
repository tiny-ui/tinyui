package app.tinyui.updates

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256

@OptIn(ExperimentalForeignApi::class)
internal actual fun verifyEcdsaP256(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
    if (data.isEmpty()) return false
    val keyData = publicKey.toCFData() ?: return false
    val attributes = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
    CFDictionaryAddValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)
    val key = SecKeyCreateWithData(keyData, attributes, null)
    CFRelease(attributes)
    CFRelease(keyData)
    if (key == null) return false
    val message = data.toCFData()
    val sig = signature.toCFData()
    val ok = SecKeyVerifySignature(key, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, message, sig, null)
    if (message != null) CFRelease(message)
    if (sig != null) CFRelease(sig)
    CFRelease(key)
    return ok
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData(): CFDataRef? =
    if (isEmpty()) null else usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), size.toLong()) }
