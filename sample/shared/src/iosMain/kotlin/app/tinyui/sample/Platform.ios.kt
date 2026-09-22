package app.tinyui.sample

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun otaBaseUrl(): String = "http://localhost:8000/ota/"

@OptIn(ExperimentalForeignApi::class)
actual suspend fun httpGet(url: String): ByteArray = suspendCancellableCoroutine { continuation ->
    val task = NSURLSession.sharedSession.dataTaskWithURL(NSURL(string = url)) { data, response, error ->
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        when {
            error != null -> continuation.resumeWithException(Exception(error.localizedDescription))
            status != 200 || data == null -> continuation.resumeWithException(Exception("HTTP $status $url"))
            else -> continuation.resume(data.toByteArray())
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) } }
}
