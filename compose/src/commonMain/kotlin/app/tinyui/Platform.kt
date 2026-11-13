package app.tinyui

import androidx.compose.ui.platform.ClipEntry
import io.ktor.client.HttpClient

internal expect fun platformInfo(): Map<String, String>

/** BCP 47, e.g. `zh-Hans-CN`. */
internal expect fun systemLocale(): String

/** IANA id, e.g. `Asia/Shanghai`; read fresh each time (iOS caches it otherwise), so a zone changed in system settings shows up. */
internal expect fun systemTimeZone(): String

/** Minutes [zone] is ahead of UTC at [epochMs] (daylight saving included); null when the platform does not know [zone]. */
internal expect fun zoneOffsetMinutes(zone: String, epochMs: Long): Int?

internal expect fun plainTextClipEntry(text: String): ClipEntry

/** The engine each platform ships; the `default` channel adds nothing to it. */
internal expect fun defaultHttpClient(): HttpClient

/**
 * What `device.info().region` promises: ISO 3166-1 alpha-2, upper case, or empty. A locale's region can also be a
 * UN M.49 area such as `419` (Latin America); that is not a country, and a page looking it up in a country table
 * would find nothing.
 */
internal fun regionCode(raw: String?): String {
    val code = raw?.uppercase() ?: return ""
    return if (code.length == 2 && code.all { it in 'A'..'Z' }) code else ""
}
