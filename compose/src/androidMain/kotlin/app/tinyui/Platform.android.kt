package app.tinyui

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale
import okio.FileSystem

internal actual fun platformInfo(): Map<String, String> = mapOf(
    "os" to "android",
    "osVersion" to Build.VERSION.RELEASE.orEmpty(),
    "model" to Build.MODEL.orEmpty(),
)

internal actual fun systemLocale(): String = Locale.getDefault().toLanguageTag()

internal actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
