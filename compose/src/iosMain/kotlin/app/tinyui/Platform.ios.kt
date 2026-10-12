package app.tinyui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import okio.FileSystem
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.timeZoneWithName
import platform.UIKit.UIDevice

internal actual fun platformInfo(): Map<String, String> = mapOf(
    "os" to "ios",
    "osVersion" to UIDevice.currentDevice.systemVersion,
    "model" to UIDevice.currentDevice.model,
)

internal actual fun systemLocale(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: NSLocale.currentLocale.localeIdentifier.replace('_', '-')

internal actual fun systemTimeZone(): String = NSTimeZone.localTimeZone.name

internal actual fun zoneOffsetMinutes(zone: String, epochMs: Long): Int? =
    NSTimeZone.timeZoneWithName(zone)?.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))?.toInt()?.div(60)

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

internal actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin)
