package app.tinyui.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

actual fun otaBaseUrl(): String = "http://10.0.2.2:8000/ota/"

actual suspend fun httpGet(url: String): ByteArray = withContext(Dispatchers.IO) {
    // the emulator inherits the host's HTTP proxy, which has no route to the emulator-only 10.0.2.2
    val connection = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode} $url")
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}
