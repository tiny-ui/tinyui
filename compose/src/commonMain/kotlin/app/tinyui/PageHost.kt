package app.tinyui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import wang.harlon.quickjs.JsEngine
import wang.harlon.quickjs.JsEngineConfig
import wang.harlon.quickjs.JsException
import wang.harlon.quickjs.JsRef
import wang.harlon.quickjs.JsRuntime
import wang.harlon.quickjs.JsValue
import wang.harlon.quickjs.ObjectTransport
import app.tinyui.node.NodeTree
import app.tinyui.node.UINode
import androidx.compose.ui.graphics.RectangleShape
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.LocalChildModifier
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue

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
    private val storage by lazy { host.storages.of(page.pkg) }
    internal val ui = PageUi()
    val tree = NodeTree(registry) { report("E5", it.reason, op = it.op) }
    var failure: PageFailure? by mutableStateOf(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timers = HashMap<Int, Job>()
    private val subscriptions = HashMap<String, AutoCloseable>()
    private val storeVersions = HashMap<String, Long>()
    private val jsThread = newSingleThreadContext("tinyui-page")
    private val runtime = JsRuntime(
        JsEngineConfig(moduleScheme = MODULE_SCHEME, logger = sink::log, onUnhandledRejection = { report("E7", it.message ?: "unhandled rejection", stack = it.jsStack) }),
        jsThread,
    )
    private var entries: Entries? = null

    private class Entries(val self: JsRef, val fns: Map<String, JsRef>) {
        fun call(name: String, vararg args: JsValue) = fns.getValue(name).invoke(self, args.toList())
        fun close() { fns.values.forEach { it.close() }; self.close() }
    }

    fun start() {
        scope.launch {
            try {
                // K0 + K1 mount under the same timeout as every other entry: a page that spins on load fails as E6
                storage // the package's file is read before the first J2 can ask for it
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
                    e.call("flush")
                } }
            } catch (t: TimeoutCancellationException) {
                fail("E6", IllegalStateException("page load exceeded ${entryTimeoutMs} ms and was interrupted"))
            } catch (t: Throwable) {
                fail("E6", t)
            }
        }
    }

    fun dispatch(nodeId: Int, event: String, payload: String) = entry("dispatch", JsValue.Num(nodeId), JsValue.Str(event), JsValue.Str(payload))
    fun visible(visible: Boolean) = entry("visible", JsValue.Bool(visible))
    fun resolve(cbId: Int, resultJson: String) = entry("resolve", JsValue.Num(cbId), JsValue.Str(resultJson))
    fun reject(cbId: Int, code: String, message: String) = reject(cbId, code, message, null)

    private fun reject(cbId: Int, code: String, message: String, details: JsonObject?) =
        entry("reject", JsValue.Num(cbId), JsValue.Str(JsonObject((details ?: JsonObject(emptyMap())) + mapOf("code" to JsonPrimitive(code), "message" to JsonPrimitive(message))).toString()))
    /** K5. The host calls this for `navigation.result`; bus topics arrive through the page's own subscriptions. */
    fun emit(topic: String, payloadJson: String) = entry("emit", JsValue.Str(topic), JsValue.Str(payloadJson))

    private fun entry(name: String, vararg args: JsValue, before: () -> Unit = {}, skipWhen: () -> Boolean = { false }) {
        if (failure != null) return
        scope.launch {
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
                    subscriptions.values.forEach { runCatching { it.close() } }
                    subscriptions.clear()
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

    private fun report(kind: String, message: String, entry: String? = null, stack: String? = null, op: String? = null): PageError {
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
        engine.registerFunction("__host_query") { args -> JsValue.Str(query((args[0] as JsValue.Str).value, (args[1] as JsValue.Str).value)) }
        engine.registerFunction("__host_call") { args ->
            call((args[0] as JsValue.Str).value, (args[1] as JsValue.Num).value.toInt(), (args[2] as JsValue.Str).value)
            JsValue.Undefined
        }
        engine.registerFunction("__host_send") { args ->
            send((args[0] as JsValue.Str).value, (args[1] as JsValue.Str).value)
            JsValue.Undefined
        }
    }

    /** J2 whitelist (docs/native-api.md §2); anything else answers null. */
    private fun query(name: String, argsJson: String): String {
        val a = args(argsJson)
        return when (name) {
            "device.info" -> JsonObject(platformInfo().mapValues { JsonPrimitive(it.value) }).toString()
            "store.get" -> a.str("key")?.let(host.store::getJson) ?: "null"
            "storage.get" -> a.str("key")?.let { storage?.get(it) } ?: "null"
            "storage.set" -> {
                val key = a.str("key") ?: return JsonPrimitive("E_INVALID").toString()
                val target = storage ?: return JsonPrimitive("E_UNSUPPORTED").toString()
                target.set(key, a.str("value") ?: "null")?.let { JsonPrimitive(it).toString() } ?: "null"
            }
            "i18n.t" -> JsonPrimitive(translate(a.str("key") ?: "")).toString()
            "i18n.locale" -> JsonPrimitive(host.currentLocale()).toString()
            "session.get" -> sessionJson(host.session?.state?.value ?: Session.LoggedOut)
            else -> "null"
        }
    }

    private fun translate(key: String): String =
        page.i18n.lookup(key, host.currentLocale()) ?: key.also { if (host.missingKeys.firstTime("${page.pkg}:$it")) sink.log("i18n: ${page.pkg} has no \"$it\" in any language") }

    private fun sessionJson(s: Session): String = buildJsonObject { put("loggedIn", s.loggedIn); put("userId", s.userId) }.toString()

    /** J3 (docs/native-api.md §6–§13); results come back through [resolve] / [reject] as one transaction each. */
    private fun call(name: String, cbId: Int, argsJson: String) {
        val a = args(argsJson)
        when (name) {
            "timer.schedule" -> schedule(cbId, a)
            "http.request" -> settle(cbId) { http(a) }
            "session.signIn" -> settle(cbId) {
                val session = host.session ?: throw HostException("E_UNSUPPORTED", "the host provides no session")
                withContext(Dispatchers.Main) { session.signIn(a.str("source") ?: "") }
                ""
            }
            "ui.toast" -> settle(cbId) {
                val message = a.str("message") ?: throw HostException("E_INVALID", "ui.toast needs a message")
                val long = a.str("duration") == "long"
                val result = withContext(Dispatchers.Main) {
                    ui.snackbar.showSnackbar(message, a.str("action"), duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
                }
                JsonPrimitive(if (result == SnackbarResult.ActionPerformed) "action" else "dismissed").toString()
            }
            "ui.alert", "ui.confirm" -> settle(cbId) {
                val texts = DialogTexts.of(host.currentLocale())
                val confirmed = ui.ask(
                    DialogRequest(
                        title = a.str("title"),
                        message = a.str("message") ?: throw HostException("E_INVALID", "$name needs a message"),
                        confirm = a.str("confirm") ?: texts.ok,
                        cancel = if (name == "ui.confirm") a.str("cancel") ?: texts.cancel else null,
                    ),
                )
                if (name == "ui.confirm") confirmed.toString() else ""
            }
            "linking.openUrl" -> settle(cbId) {
                val url = a.str("url")?.takeIf { URL_SCHEME.containsMatchIn(it) } ?: throw HostException("E_INVALID", "linking.openUrl needs an absolute URL")
                withContext(Dispatchers.Main) { (host.links ?: LinkOpener.System).open(url, context) }
                ""
            }
            else -> when (val capability = host.capabilities[name]) {
                null -> scope.launch { reject(cbId, "E_UNSUPPORTED", "$name is not available") }
                else -> settle(cbId) { capability.call(argsJson, context) ?: "" }
            }
        }
    }

    /** docs/native-api.md §6: any response comes back from the channel; non-2xx rejects E_HTTP with status, headers and body. */
    private suspend fun http(a: JsonObject): String {
        val channelName = a.str("channel") ?: TinyUIHost.DEFAULT_CHANNEL
        val channel = host.channel(channelName) ?: throw HostException("E_UNSUPPORTED", "the host has no http channel \"$channelName\"")
        val headers = (a["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty().toMutableMap()
        val body = when (val b = a["body"]) {
            null, JsonNull -> null
            is JsonPrimitive if b.isString -> b.content
            else -> {
                if (headers.keys.none { it.equals("content-type", ignoreCase = true) }) headers["Content-Type"] = "application/json"
                b.toString()
            }
        }
        val request = HttpRequest(
            method = a.str("method") ?: "GET",
            url = a.str("url") ?: throw HostException("E_INVALID", "http.request needs url"),
            headers = headers,
            body = body,
            timeoutMs = a["timeout"]?.jsonPrimitive?.longOrNull,
        )
        val response = request.timeoutMs?.let { ms ->
            withTimeoutOrNull(ms) { channel.request(request) } ?: throw HostException("E_TIMEOUT", "${request.method} ${request.url} took longer than $ms ms")
        } ?: channel.request(request)
        val fields = mapOf(
            "status" to JsonPrimitive(response.status),
            "headers" to JsonObject(response.headers.mapValues { JsonPrimitive(it.value) }),
            "body" to (runCatching { Json.parseToJsonElement(response.body) }.getOrNull() ?: JsonPrimitive(response.body)),
        )
        if (response.status !in 200..299) throw HttpStatusException(response.status, JsonObject(fields))
        return JsonObject(fields).toString()
    }

    private class HttpStatusException(val status: Int, val details: JsonObject) : Exception("HTTP $status")

    /** Runs one J3 on the page scope and answers with K3: the JSON [block] returns, or its E3 code. */
    private fun settle(cbId: Int, block: suspend () -> String) = scope.launch {
        try {
            resolve(cbId, block())
        } catch (e: HttpStatusException) {
            reject(cbId, "E_HTTP", e.message ?: "", e.details)
        } catch (e: HostException) {
            reject(cbId, e.code, e.message ?: "")
        } catch (e: Exception) {
            reject(cbId, "E_NET", e.message ?: e.toString())
        }
    }

    /** J4 (docs/native-api.md §3–§12). */
    private fun send(name: String, argsJson: String) {
        val a = args(argsJson)
        when (name) {
            "timer.cancel" -> a["cbId"]?.jsonPrimitive?.intOrNull?.let { timers.remove(it)?.cancel() }
            "navigation.push" -> host.navigator.push(a.str("page") ?: return, a["params"]?.toString() ?: "{}")
            "navigation.pop" -> host.navigator.pop(a["result"]?.toString())
            "store.set" -> { val key = a.str("key") ?: return; host.store.setJson(key, a.str("value") ?: "null") }
            // one host listener per key / topic; JS fans out to its own handlers
            "store.subscribe" -> { val key = a.str("key") ?: return; subscriptions.getOrPut("store:$key") { host.store.observe(key) { v -> storeChanged(key, v) } } }
            "storage.remove" -> storage?.remove(a.str("key") ?: return)
            "storage.clear" -> storage?.clear()
            "events.emit" -> host.events.emit(a.str("topic") ?: return, a["payload"]?.toString() ?: "{}")
            "events.subscribe" -> { val topic = a.str("topic") ?: return; subscriptions.getOrPut("events:$topic") { host.events.subscribe(topic) { json -> emit(topic, json) } } }
            "i18n.subscribe" -> host.locale?.let { flow ->
                subscriptions.getOrPut("i18n") { follow(flow) { emit("i18n.locale", buildJsonObject { put("locale", it) }.toString()) } }
            }
            "session.subscribe" -> host.session?.let { source ->
                subscriptions.getOrPut("session") { follow(source.state) { emit("session", sessionJson(it)) } }
            }
            "analytics.track" -> track(a)
            else -> sink.log("unknown J4 $name")
        }
    }

    /** K5 for every value of [flow], the current one included; stops with the page. */
    private fun <T> follow(flow: StateFlow<T>, push: (T) -> Unit): AutoCloseable {
        val job = scope.launch { flow.collect(push) }
        return AutoCloseable { job.cancel() }
    }

    private fun track(a: JsonObject) {
        val name = a.str("name") ?: return
        val props = (a["props"] as? JsonObject).orEmpty().mapValues { (_, v) ->
            val p = v as? JsonPrimitive
            when {
                p == null || p is JsonNull -> null
                p.isString -> p.content
                else -> p.booleanOrNull ?: p.longOrNull ?: p.doubleOrNull
            }
        }
        val sink = host.analytics
        if (sink == null) { if (TinyUI.debug) this.sink.log("analytics: no sink, dropped $name") } else sink.track(name, props, context)
    }

    /** Notifications may arrive out of order across threads; the version decides, applied inside the entry (under the lock). */
    private fun storeChanged(key: String, value: StoreValue) {
        var stale = false
        entry("emit", JsValue.Str("store:$key"), JsValue.Str("""{"value":${value.json}}"""), before = {
            val last = storeVersions[key] ?: -1
            if (value.version <= last) stale = true else storeVersions[key] = value.version
        }, skipWhen = { stale })
    }

    /**
     * The host side of `setTimeout` (J3 `timer.schedule` → K3 `resolve`); dies with the page.
     * [timers] is only touched while holding the runtime lock: here and in `timer.cancel` (host functions
     * run inside the calling entry) and in the `before` step of the firing entry.
     */
    private fun schedule(cbId: Int, a: JsonObject) {
        val ms = a["ms"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        timers[cbId] = scope.launch {
            delay(ms.toLong().coerceAtLeast(0))
            entry("resolve", JsValue.Num(cbId), JsValue.Str(""), before = { timers.remove(cbId) })
        }
    }

    private fun args(json: String): JsonObject = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** The scope a component renders through. */
    internal inner class Scope(override val node: UINode) : NodeScope {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String): Boolean = node.events[event] == true
        override fun dispatch(event: String, payload: String) = this@PageHost.dispatch(node.id, event, payload)

        /** docs/components.md §2: parent-scoped (weight) → size → clip → background → border → gesture → padding. */
        @Composable
        override fun modifier(clickable: Boolean): Modifier {
            var m: Modifier = LocalChildModifier.current
            when (val w = get<SizeValue>("width")) { is SizeValue.Fixed -> m = m.width(w.dp); SizeValue.Fill -> m = m.fillMaxWidth(); SizeValue.Wrap -> m = m.wrapContentWidth(); null -> {} }
            when (val h = get<SizeValue>("height")) { is SizeValue.Fixed -> m = m.height(h.dp); SizeValue.Fill -> m = m.fillMaxHeight(); SizeValue.Wrap -> m = m.wrapContentHeight(); null -> {} }
            val shape = get<Dp>("cornerRadius")?.let { RoundedCornerShape(it) } ?: RectangleShape
            if (shape != RectangleShape) m = m.clip(shape)
            color("background")?.let { m = m.background(it) }
            // 0.dp is Compose's hairline (one pixel), not "no border"
            get<Dp>("borderWidth")?.takeIf { it > 0.dp }?.let { m = m.border(it, color("borderColor") ?: MaterialTheme.colorScheme.outline, shape) }
            if (clickable && has("onClick")) {
                val role = roleOf(get<String>("role"))
                val selected = get<Boolean>("selected")
                m = if (selected != null) m.selectable(selected, role = role) { dispatch("onClick") } else m.clickable(role = role) { dispatch("onClick") }
            }
            val all = get<Dp>("padding")
            val h = get<Dp>("paddingHorizontal") ?: all
            val v = get<Dp>("paddingVertical") ?: all
            if (h != null || v != null) m = m.padding(start = h ?: 0.dp, top = v ?: 0.dp, end = h ?: 0.dp, bottom = v ?: 0.dp)
            return m
        }

        @Composable
        override fun Children() = Children { Modifier }

        @Composable
        override fun Children(childModifier: (UINode) -> Modifier) {
            for (child in node.children) key(child.id) {
                CompositionLocalProvider(LocalChildModifier provides childModifier(child)) { Render(child) }
            }
        }

        @Composable
        override fun RenderChild(child: UINode) = CompositionLocalProvider(LocalChildModifier provides Modifier) { Render(child) }
    }

    @Composable
    internal fun Render(node: UINode) {
        registry.Render(node.type, Scope(node))
    }

    companion object {
        /** `import.meta.url` of a page module is `tinyui:<name>` (docs/adr-005-engine.md). */
        const val MODULE_SCHEME = "tinyui"
        private val URL_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        private fun roleOf(name: String?): Role? = when (name) {
            "button" -> Role.Button
            "checkbox" -> Role.Checkbox
            "switch" -> Role.Switch
            "radio" -> Role.RadioButton
            "tab" -> Role.Tab
            else -> null
        }
        private val ENTRY_NAMES = listOf("mount", "unmount", "visible", "dispatch", "resolve", "reject", "emit", "flush")
    }
}
