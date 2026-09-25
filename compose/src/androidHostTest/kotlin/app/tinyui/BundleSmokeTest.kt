package app.tinyui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wang.harlon.quickjs.JsEngine
import wang.harlon.quickjs.JsEngineConfig
import wang.harlon.quickjs.JsValue
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Loads what `tinyui build` produced for the sample through the real engine with the embedded runtime; skips when the sample was not built. */
class BundleSmokeTest {
    private val out = File("../sample/shared/build/tinyui-cli/sample")

    @Test
    fun builtBundlesLoadAndTheRuntimeComesUp() {
        if (!out.isDirectory) return
        JsEngine(JsEngineConfig(moduleScheme = PageHost.MODULE_SCHEME)).use { engine ->
            for (fn in listOf("__host_apply", "__host_query", "__host_call", "__host_send", "__host_report")) engine.registerFunction(fn) { JsValue.Undefined }
            assertEquals("tinyui-core", engine.registerModule(TinyUI.runtime.core))
            assertEquals("tinyui-native", engine.registerModule(TinyUI.runtime.native))
            engine.evaluateModule("import \"tinyui-core\"; export const v = typeof globalThis.__tinyui.mount;").use {
                assertEquals(JsValue.Str("function"), it.get("v"))
            }
            engine.runBytecode(out.resolve("pages/counter.bin").readBytes()).let { if (it is AutoCloseable) it.close() }
        }
    }

    /** A rejected `await` in the todos page's `loadMore` is E7; its frames map back to the `.tsx` through the built map. */
    @Test
    fun stacksFromABuiltPageMapBackToTheSource() = runBlocking {
        if (!out.isDirectory) return@runBlocking
        val bundle = Bundle.load { path -> out.resolve(path).takeIf { it.isFile }?.readBytes() }
        val loaded = bundle.page("sample/todos")
        val errors = mutableListOf<PageError>()
        val sink = object : PageSink {
            override fun error(error: PageError) { errors += error }
            override fun log(line: String) {}
        }
        val api = HttpChannel { request ->
            if (request.url.endsWith("page=1")) HttpResponse(200, emptyMap(), """{"items":[{"id":1,"title":"a","done":false}],"next":2}""")
            else HttpResponse(500, emptyMap(), "")
        }
        val host = PageHost(loaded.module, TinyUIHost(ComponentRegistry().registerBuiltins(), sink, channels = mapOf("todos" to api)), sourceMaps = loaded.sourceMaps)
        host.start()
        try {
            // the list only exists once the first page has loaded; node ids are dense, so scan them
            fun lazyColumn(): Int? = (1..host.tree.size * 2).firstOrNull { host.tree.node(it)?.type == "LazyColumn" }
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                while (lazyColumn() == null) delay(20)
                host.dispatch(lazyColumn()!!, "onReachEnd", "{}")
                while (errors.none { it.kind == "E7" }) delay(20)
            } }
            val e = errors.single { it.kind == "E7" }
            assertEquals(bundle.manifest.buildId("sample/todos"), e.buildId)
            val own = e.frames.firstOrNull { it.mapped && it.file == "src/pages/todos.tsx" } ?: error("no mapped frame in ${e.frames}; stack=${e.jsStack}")
            val source = File("../sample/js/src/pages/todos.tsx").readLines()
            assertTrue("await todos.get" in source[own.line - 1], "frame $own points at the await; stack=${e.jsStack}")
        } finally {
            host.close()
        }
    }
}
