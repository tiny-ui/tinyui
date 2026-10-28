package app.tinyui

import android.content.ClipData
import android.os.Build
import androidx.compose.ui.platform.ClipEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.text.input.PlatformImeOptions
import okio.FileSystem

internal actual fun platformInfo(): Map<String, String> = mapOf(
    "os" to "android",
    "osVersion" to Build.VERSION.RELEASE.orEmpty(),
    "model" to Build.MODEL.orEmpty(),
)

internal actual fun systemLocale(): String = Locale.getDefault().toLanguageTag()

internal actual fun systemTimeZone(): String = TimeZone.getDefault().id

// getTimeZone answers GMT for an id it does not know rather than failing
internal actual fun zoneOffsetMinutes(zone: String, epochMs: Long): Int? =
    TimeZone.getTimeZone(zone).takeIf { it.id == zone }?.getOffset(epochMs)?.div(60_000)

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText(null, text))

// Android autofill takes the content type from semantics (the component sets it), so nothing goes through the IME options
internal actual fun oneTimeCodeImeOptions(oneTimeCode: Boolean): PlatformImeOptions? = null

internal actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
