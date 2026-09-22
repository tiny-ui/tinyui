package app.tinyui.sample

/** `python3 -m http.server 8000` in `dist/ota`'s parent on the development machine, as the emulator or simulator reaches it. */
expect fun otaBaseUrl(): String

expect suspend fun httpGet(url: String): ByteArray
