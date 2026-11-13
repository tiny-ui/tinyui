package app.tinyui

import android.content.ClipData
import android.content.res.Resources
import android.os.Build
import androidx.compose.ui.platform.ClipEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale
import java.util.TimeZone
import okio.FileSystem

internal actual fun platformInfo(): Map<String, String> = mapOf(
    "os" to "android",
    "osVersion" to Build.VERSION.RELEASE.orEmpty(),
    "model" to Build.MODEL.orEmpty(),
    "region" to systemRegion(),
)

/**
 * The region of the device's locale setting, ISO 3166-1 alpha-2; empty when it has none. Read from the system's
 * configuration, not `Locale.getDefault()`: an App that switches its own language calls `Locale.setDefault` with a
 * language-only locale, and that wipes the region for the whole process.
 */
private fun systemRegion(): String =
    runCatching { Resources.getSystem().configuration.locales[0].country.uppercase() }.getOrDefault("")

internal actual fun systemLocale(): String = Locale.getDefault().toLanguageTag()

internal actual fun systemTimeZone(): String = TimeZone.getDefault().id

// getTimeZone answers GMT for an id it does not know rather than failing
internal actual fun zoneOffsetMinutes(zone: String, epochMs: Long): Int? =
    TimeZone.getTimeZone(zone).takeIf { it.id == zone }?.getOffset(epochMs)?.div(60_000)

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText(null, text))

internal actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
