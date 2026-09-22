package app.tinyui.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import app.tinyui.Bundle
import app.tinyui.HostException
import app.tinyui.HostServices
import app.tinyui.LoadedPage
import app.tinyui.HttpClient
import app.tinyui.HttpRequest
import app.tinyui.HttpResponse
import app.tinyui.PageError
import app.tinyui.PageSink
import app.tinyui.TinyUI
import app.tinyui.TinyUIPage
import app.tinyui.components.registerBuiltins
import app.tinyui.sample.res.Res
import app.tinyui.schema.ComponentRegistry

/** App-level, built once (docs/adr-003 §3.3). */
private val registry = ComponentRegistry().registerBuiltins()

private val sink = object : PageSink {
    override fun error(error: PageError) = println("TinyUI $error")
    override fun log(line: String) = println("TinyUI $line")
}

/** Stands in for a backend: three pages of todos, 300 ms each. */
private object FakeTodos : HttpClient {
    override suspend fun request(request: HttpRequest): HttpResponse {
        delay(300)
        val page = Regex("page=(\\d+)").find(request.url)?.groupValues?.get(1)?.toInt() ?: throw HostException("E_HTTP", "404 ${request.url}")
        if (page > 3) throw HostException("E_HTTP", "404 page $page")
        val items = (1..8).joinToString(",") { i -> val id = (page - 1) * 8 + i; """{"id":$id,"title":"Todo #$id","done":${id % 3 == 0}}""" }
        val next = if (page < 3) "${page + 1}" else "null"
        return HttpResponse(200, """{"items":[$items],"next":$next}""")
    }
}

private val services = HostServices(http = FakeTodos, deviceInfo = mapOf("app" to "sample"))

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    var page by remember { mutableStateOf<LoadedPage?>(null) }
    LaunchedEffect(Unit) {
        val bundle = Bundle.load { path -> runCatching { Res.readBytes("files/tinyui/sample/$path") }.getOrNull() }
        val loaded = bundle.page("sample/todos")
        // debug builds ship the maps (-Ptinyui.maps); stacks on the failure screen only when they came along
        TinyUI.debug = !loaded.sourceMaps.isEmpty
        page = loaded
    }
    // pages name theme tokens only (docs/components.md §6); flipping the scheme here restyles them with no patch
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                val p = page
                if (p == null) Text("loading…") else TinyUIPage(p.runtime, p.module, registry, sink, services, sourceMaps = p.sourceMaps)
            }
        }
    }
}
