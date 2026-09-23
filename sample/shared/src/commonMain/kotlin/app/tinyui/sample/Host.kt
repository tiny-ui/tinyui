package app.tinyui.sample

import app.tinyui.PageError
import app.tinyui.PageSink
import app.tinyui.TinyUIHost
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry

/** Raise when [tinyUIHost] changes what pages can use; `tinyui-host/<n>.txt` records each value (docs/updates.md §4.1). */
internal const val HOST_VERSION = "1"

/** App-level, built once (docs/native-api.md §7). */
internal val tinyUIHost = TinyUIHost(
    components = ComponentRegistry().registerBuiltins(),
    sink = object : PageSink {
        override fun error(error: PageError) = println("TinyUI $error")
        override fun log(line: String) = println("TinyUI $line")
    },
)
