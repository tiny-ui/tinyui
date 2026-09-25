package app.tinyui

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import okio.FileSystem
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.UIKit.UIDevice

internal actual fun platformInfo(): Map<String, String> = mapOf(
    "os" to "ios",
    "osVersion" to UIDevice.currentDevice.systemVersion,
    "model" to UIDevice.currentDevice.model,
)

internal actual fun systemLocale(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: NSLocale.currentLocale.localeIdentifier.replace('_', '-')

internal actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin)
