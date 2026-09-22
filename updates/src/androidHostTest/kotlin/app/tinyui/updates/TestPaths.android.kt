package app.tinyui.updates

import okio.FileSystem
import okio.Path

internal actual fun temporaryDirectory(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
