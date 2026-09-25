package app.tinyui.sample

import app.tinyui.AnalyticsSink
import app.tinyui.HttpChannel
import app.tinyui.HttpResponse
import app.tinyui.PageError
import app.tinyui.PageSink
import app.tinyui.Session
import app.tinyui.SessionSource
import app.tinyui.TinyUIHost
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path

/** Raise when [sampleHost] changes what pages can use; `tinyui-host/<n>.txt` records each value (docs/updates.md §4.1). */
internal const val HOST_VERSION = "3"

/** The App's language setting; the language button in [App] flips it. */
internal val sampleLocale = MutableStateFlow("en")

private val signedIn = MutableStateFlow(Session.LoggedOut)

/** Stands in for a backend behind the `todos` channel: three pages of todos, 300 ms each. */
private val fakeTodos = HttpChannel { request ->
    delay(300)
    val page = Regex("page=(\\d+)").find(request.url)?.groupValues?.get(1)?.toInt()
    if (page == null || page > 3) return@HttpChannel HttpResponse(404, emptyMap(), "")
    val items = (1..8).joinToString(",") { i -> val id = (page - 1) * 8 + i; """{"id":$id,"title":"Todo #$id","done":${id % 3 == 0}}""" }
    val next = if (page < 3) "${page + 1}" else "null"
    HttpResponse(200, mapOf("content-type" to "application/json"), """{"items":[$items],"next":$next}""")
}

/** App-level, built once (docs/native-api.md §1); [dataDir] backs `storage`. */
internal fun sampleHost(dataDir: Path? = null) = TinyUIHost(
    components = ComponentRegistry().registerBuiltins(),
    sink = object : PageSink {
        override fun error(error: PageError) = println("TinyUI $error")
        override fun log(line: String) = println("TinyUI $line")
    },
    channels = mapOf("todos" to fakeTodos),
    locale = sampleLocale,
    session = SessionSource(signedIn) { delay(500); signedIn.value = Session(true, "demo") },
    analytics = AnalyticsSink { name, props, page -> println("TinyUI analytics ${page.name} $name $props") },
    dataDir = dataDir,
)
