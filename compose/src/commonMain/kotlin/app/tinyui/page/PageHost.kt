package app.tinyui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tinyui.TinyUI
import app.tinyui.TinyUIHost
import app.tinyui.host.PageContext
import app.tinyui.host.PageLocalValue
import app.tinyui.internal.FrameworkCapabilities
import app.tinyui.node.NodeTree
import app.tinyui.node.UINode
import app.tinyui.page.internal.HostBridge
import app.tinyui.page.internal.PageScope
import app.tinyui.page.internal.PageUI
import app.tinyui.schema.ComponentRegistry
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wang.harlon.quickjs.JsEngine
import wang.harlon.quickjs.JsEngineConfig
import wang.harlon.quickjs.JsException
import wang.harlon.quickjs.JsRef
import wang.harlon.quickjs.JsRuntime
import wang.harlon.quickjs.JsValue
import wang.harlon.quickjs.ObjectTransport

/** Bytecode of the runtime modules every page imports; [TinyUI.runtime] outside of tests. */
internal class RuntimeBundle(val core: ByteArray, val native: ByteArray)

/**
 * One page's bytecode; [name] is the module name (`sample/todos`) and [buildId] comes from `manifest.json`.
 * [i18n] is its package's strings (docs/native-api.md §8).
 */
class PageModule(val name: String, val bytecode: ByteArray, val buildId: String = "", val i18n: PackageI18n = PackageI18n.EMPTY) {
    /** The package the page belongs to: the part of [name] before the first `/`. */
    val pkg: String get() = name.substringBefore('/')
}

/** Everything the host learns about a page's health, in one place (docs/adr-002 §3.5). */
/** The page's failure (E2 / E6): engine gone, error page to be rendered by the host. */
class PageFailure(val error: PageError) {
    val kind: String get() = error.kind
    val message: String get() = error.message
}

/**
 * One page: its engine, coroutine scope and node tree (docs/adr-002 §3.4). Every K entry is
 * `entry` + `flush` under one [JsRuntime.withEngine] (docs/js-runtime.html §2). JS runs on a thread
 * owned by the page (docs/adr-002 §4), closed with the engine.
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class PageHost internal constructor(
    private val runtimeBundle: RuntimeBundle,
    private val page: PageModule,
    val host: TinyUIHost,
    private val propsJson: String = "{}",
    /** This mount's objects for `host.call` capabilities (docs/native-api.md §13). */
    private val locals: List<PageLocalValue<*>> = emptyList(),
    /** K entries longer than this are interrupted and fail the page (docs/native-api.md §14). */
    private val entryTimeoutMs: Long = 5_000,
    private val sourceMaps: SourceMaps = SourceMaps.EMPTY,
) {
    constructor(
        page: PageModule,
        host: TinyUIHost,
        propsJson: String = "{}",
        locals: List<PageLocalValue<*>> = emptyList(),
        entryTimeoutMs: Long = 5_000,
        sourceMaps: SourceMaps = SourceMaps.EMPTY,
    ) : this(TinyUI.runtime, page, host, propsJson, locals, entryTimeoutMs, sourceMaps)

    private val registry: ComponentRegistry get() = host.components
    private val sink: PageSink get() = host.sink
    internal val context = PageContext(page.name, locals.associate { it.local to it.value })
    internal val ui = PageUI()
    val tree = NodeTree(registry) { report("E5", it.reason, op = it.op) }
    var failure: PageFailure? by mutableStateOf(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // the engine lock is fair, but entries launched on a multi-threaded pool can reach it out of order: one lane keeps
    // them in call order up to the lock (onChange before onComplete, onFocusChange before onCommit)
    private val entryLane = Dispatchers.Default.limitedParallelism(1)
    private val bridge = HostBridge(this, page, scope)
    private val jsThread = newSingleThreadContext("tinyui-page")
    private val runtime = JsRuntime(
        JsEngineConfig(moduleScheme = MODULE_SCHEME, logger = sink::log, onUnhandledRejection = { report("E7", it.message ?: "unhandled rejection", stack = it.jsStack) }),
        jsThread,
    )
    private var entries: Entries? = null
    /** Settles once the mount is over, whether it worked or not: K entries that arrive earlier wait for it instead of being dropped. */
    private val mountSettled = CompletableDeferred<Unit>()
    /** The last [visible]; one that arrives before the mount is applied right after it. */
    @Volatile private var wantVisible = true

    private class Entries(val self: JsRef, val fns: Map<String, JsRef>) {
        fun call(name: String, vararg args: JsValue) = fns.getValue(name).invoke(self, args.toList())
        fun close() { fns.values.forEach { it.close() }; self.close() }
    }

    fun start() {
        scope.launch {
            try {
                // K0 + K1 mount under the same timeout as every other entry: a page that spins on load fails as E6
                bridge.storage // the package's file is read before the first J2 can ask for it
                withTimeout(entryTimeoutMs) { runtime.withEngine {
                    registerHost(this)
                    step("registering tinyui-core") { registerModule(runtimeBundle.core) }
                    step("registering tinyui-native") { registerModule(runtimeBundle.native) }
                    // a registered module only runs on its first import; the runtime must be up before the page
                    evaluateModule("import \"tinyui-core\"; import \"tinyui-native\";").close()
                    val namespace = runBytecode(page.bytecode, ObjectTransport.REF) as JsRef
                    if (namespace.isPromise) { namespace.close(); error("page module is still pending after microtasks were drained") }
                    val self = evaluate("__tinyui", objects = ObjectTransport.REF) as JsRef
                    val fns = ENTRY_NAMES.associateWith { self.get(it, ObjectTransport.REF) as JsRef }
                    val e = Entries(self, fns).also { entries = it }
                    namespace.use { ns ->
                        (ns.get("default", ObjectTransport.REF) as JsRef).use { default ->
                            e.call("mount", default, JsValue.Str(propsJson), JsValue.Str(registry.manifest((FrameworkCapabilities + host.capabilityNames).toList())))
                        }
                    }
                    if (!wantVisible) e.call("visible", JsValue.Bool(false))
                    e.call("flush")
                } }
            } catch (t: TimeoutCancellationException) {
                fail("E6", IllegalStateException("page load exceeded ${entryTimeoutMs} ms and was interrupted"))
            } catch (t: Throwable) {
                fail("E6", t)
            } finally {
                mountSettled.complete(Unit)
            }
        }
    }

    fun dispatch(nodeId: Int, event: String, payload: String) = entry("dispatch", JsValue.Num(nodeId), JsValue.Str(event), JsValue.Str(payload))
    fun visible(visible: Boolean) {
        wantVisible = visible
        entry("visible", JsValue.Bool(visible))
        if (visible) bridge.systemZoneMoved()
    }

    fun resolve(cbId: Int, resultJson: String) = entry("resolve", JsValue.Num(cbId), JsValue.Str(resultJson))
    fun reject(cbId: Int, code: String, message: String) = reject(cbId, code, message, null)

    internal fun reject(cbId: Int, code: String, message: String, details: JsonObject?) =
        entry("reject", JsValue.Num(cbId), JsValue.Str(JsonObject((details ?: JsonObject(emptyMap())) + mapOf("code" to JsonPrimitive(code), "message" to JsonPrimitive(message))).toString()))
    /**
     * K5. The host calls this for `navigation.result`; bus topics arrive through the page's own subscriptions.
     * Before the page has mounted it waits for the mount; a page that failed or closed meanwhile drops it.
     */
    fun emit(topic: String, payloadJson: String) = entry("emit", JsValue.Str(topic), JsValue.Str(payloadJson))

    internal fun entry(name: String, vararg args: JsValue, before: () -> Unit = {}, skipWhen: () -> Boolean = { false }) {
        if (failure != null) return
        scope.launch(entryLane) {
            // a host emit (navigation.result to a page being remounted) can beat the mount to the engine lock
            mountSettled.await()
            // the mount may have failed after entries were set: a failed page takes no more entries
            if (failure != null) return@launch
            try {
                withTimeout(entryTimeoutMs) {
                    runtime.withEngine {
                        before()
                        if (skipWhen()) return@withEngine
                        val e = entries ?: return@withEngine
                        e.call(name, *args)
                        e.call("flush")
                    }
                }
            } catch (t: TimeoutCancellationException) {
                fail("E6", IllegalStateException("K entry $name exceeded ${entryTimeoutMs} ms and was interrupted"))
            } catch (t: Throwable) {
                fail("E2", t)
            }
        }
    }

    fun close() {
        scope.cancel() // pending entries and timers are its children
        tree.clear()
        // not a child of `scope`: cancelling the page must not cancel its own teardown; the lock orders it after any running entry
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runtime.withEngine {
                    bridge.close()
                    entries?.let { e -> runCatching { e.call("unmount") }; e.close() }
                    entries = null
                }
            } finally {
                runtime.close()
                jsThread.close()
            }
        }
    }

    private inline fun <T> step(what: String, block: () -> T): T =
        try { block() } catch (t: Throwable) { throw IllegalStateException("$what: ${t.message}", t) }

    private fun fail(kind: String, t: Throwable) {
        val error = report(kind, t.message ?: t.toString(), stack = (t as? JsException)?.jsStack)
        failure = PageFailure(error)
    }

    internal fun report(kind: String, message: String, entry: String? = null, stack: String? = null, op: String? = null): PageError {
        val error = PageError(kind, page.name, page.buildId, message, entry, stack, sourceMaps.frames(stack), op)
        sink.error(error)
        return error
    }

    private fun registerHost(engine: JsEngine) {
        engine.registerFunction("__host_apply") { args ->
            tree.apply((args[0] as JsValue.Str).value)
            JsValue.Undefined
        }
        engine.registerFunction("__host_report") { args ->
            val kind = (args[0] as? JsValue.Str)?.value
            val detail = (args[1] as? JsValue.Str)?.value ?: ""
            val fields = runCatching { Json.parseToJsonElement(detail).jsonObject }.getOrNull()
            if (kind == "E1" && fields != null) {
                report(
                    "E1",
                    fields["message"]?.jsonPrimitive?.contentOrNull ?: "",
                    entry = fields["entry"]?.jsonPrimitive?.contentOrNull ?: "?",
                    stack = fields["stack"]?.jsonPrimitive?.contentOrNull,
                )
            } else {
                report("E1", "unparseable $kind report: $detail")
            }
            JsValue.Undefined
        }
        engine.registerFunction("__host_query") { args -> JsValue.Str(bridge.query((args[0] as JsValue.Str).value, (args[1] as JsValue.Str).value)) }
        engine.registerFunction("__host_call") { args ->
            bridge.call((args[0] as JsValue.Str).value, (args[1] as JsValue.Num).value.toInt(), (args[2] as JsValue.Str).value)
            JsValue.Undefined
        }
        engine.registerFunction("__host_send") { args ->
            bridge.send((args[0] as JsValue.Str).value, (args[1] as JsValue.Str).value)
            JsValue.Undefined
        }
    }

    @Composable
    internal fun Render(node: UINode) {
        registry.Render(node.type, PageScope(this, node))
    }

    companion object {
        /** `import.meta.url` of a page module is `tinyui:<name>` (docs/adr-005-engine.md). */
        const val MODULE_SCHEME = "tinyui"
        private val ENTRY_NAMES = listOf("mount", "unmount", "visible", "dispatch", "resolve", "reject", "emit", "flush")
    }
}
