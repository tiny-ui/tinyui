package app.tinyui.updates

import okio.Path

/** okio declares `SYSTEM_TEMPORARY_DIRECTORY` per platform, not in common code. */
internal expect fun temporaryDirectory(): Path
