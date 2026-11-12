package app.tinyui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import wang.harlon.quickjs.JsValue

/**
 * What a page's JS asks of the host (docs/native-api.md): J2 [query], J3 [call] and J4 [send], which [PageHost] registers on
 * the engine. What is answered later goes back through [pageHost] as K entries.
 */
internal class HostBridge(private val pageHost: PageHost, private val page: PageModule, private val scope: CoroutineScope) {
    private val host: TinyUIHost get() = pageHost.host
    private val sink: PageSink get() = host.sink
    private val context: PageContext get() = pageHost.context
    private val ui: PageUI get() = pageHost.ui

    /** The package's `storage`; [PageHost.start] reads it first, so the file is loaded before any J2 asks for it. */
    val storage by lazy { host.storages.of(page.pkg) }
    private val timers = HashMap<Int, Job>()
    private val subscriptions = HashMap<String, AutoCloseable>()
    private val storeVersions = HashMap<String, Long>()
    /** The system zone the page last got, once it subscribed without a host zone; under the runtime lock like [timers]. */
    private var systemZoneSeen: String? = null

    /** J2 whitelist (docs/native-api.md §2); anything else answers null. */
    fun query(name: String, argsJson: String): String {
        val a = args(argsJson)
        return when (J2.of(name)) {
            J2.DeviceInfo -> JsonObject(platformInfo().mapValues { JsonPrimitive(it.value) }).toString()
            J2.StoreGet -> a.str("key")?.let(host.store::getJson) ?: "null"
            J2.StorageGet -> a.str("key")?.let { storage?.get(it) } ?: "null"
            J2.StorageSet -> {
                val key = a.str("key") ?: return JsonPrimitive("E_INVALID").toString()
                val target = storage ?: return JsonPrimitive("E_UNSUPPORTED").toString()
                target.set(key, a.str("value") ?: "null")?.let { JsonPrimitive(it).toString() } ?: "null"
            }
            J2.I18nT -> JsonPrimitive(translate(a.str("key") ?: "")).toString()
            J2.I18nLocale -> JsonPrimitive(host.currentLocale()).toString()
            J2.I18nTimeZone -> JsonPrimitive(host.currentTimeZone()).toString()
            J2.I18nZoneOffset -> JsonPrimitive(zoneOffset(a["ms"]?.jsonPrimitive?.doubleOrNull ?: 0.0)).toString()
            J2.SessionGet -> sessionJson(host.session?.state?.value ?: Session.LoggedOut)
            null -> "null"
        }
    }

    private fun translate(key: String): String =
        page.i18n.lookup(key, host.currentLocale()) ?: key.also { if (host.missingKeys.firstTime("${page.pkg}:$it")) sink.log("i18n: ${page.pkg} has no \"$it\" in any language") }

    /** A zone the platform does not know is reported once per host and shown in the system zone instead. */
    private fun zoneOffset(epochMs: Double): Int {
        val zone = host.currentTimeZone()
        return zoneOffsetMinutes(zone, epochMs.toLong()) ?: run {
            if (host.missingKeys.firstTime("timeZone:$zone")) sink.log("i18n: unknown time zone \"$zone\", using the system zone")
            zoneOffsetMinutes(host.systemZone(), epochMs.toLong()) ?: 0
        }
    }

    private fun sessionJson(s: Session): String = buildJsonObject { put("loggedIn", s.loggedIn); put("userId", s.userId) }.toString()

    /** J3 (docs/native-api.md §6–§13); results come back through [resolve] / [reject] as one transaction each. */
    fun call(name: String, cbId: Int, argsJson: String) {
        val a = args(argsJson)
        val known = J3.of(name)
        when (known) {
            J3.TimerSchedule -> schedule(cbId, a)
            J3.HttpRequest -> settle(cbId) { http(a) }
            J3.SessionSignIn -> settle(cbId) {
                val session = host.session ?: throw HostException("E_UNSUPPORTED", "the host provides no session")
                withContext(Dispatchers.Main) { session.signIn(a.str("source") ?: "") }
                ""
            }
            J3.UiToast -> settle(cbId) {
                val message = a.str("message") ?: throw HostException("E_INVALID", "ui.toast needs a message")
                val long = a.str("duration") == "long"
                val result = withContext(Dispatchers.Main) {
                    ui.snackbar.showSnackbar(message, a.str("action"), duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
                }
                JsonPrimitive(if (result == SnackbarResult.ActionPerformed) "action" else "dismissed").toString()
            }
            J3.UiAlert, J3.UiConfirm -> settle(cbId) {
                val texts = DialogTexts.of(host.currentLocale())
                val confirmed = ui.ask(
                    DialogRequest(
                        title = a.str("title"),
                        message = a.str("message") ?: throw HostException("E_INVALID", "$name needs a message"),
                        confirm = a.str("confirm") ?: texts.ok,
                        cancel = if (known == J3.UiConfirm) a.str("cancel") ?: texts.cancel else null,
                    ),
                )
                if (known == J3.UiConfirm) confirmed.toString() else ""
            }
            J3.CryptoRsaEncrypt -> settle(cbId) {
                // strings only: a number would be encrypted as its decimal text, which is not what the page holds
                val text = (a["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw HostException("E_INVALID", "crypto.rsaEncrypt needs text as a string")
                val key = (a["publicKey"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw HostException("E_INVALID", "crypto.rsaEncrypt needs a publicKey string")
                // off the engine's lane: RSA is cheap but not free on a slow phone
                JsonPrimitive(withContext(Dispatchers.Default) { rsaEncrypt(text, key) }).toString()
            }
            J3.ClipboardSetText -> settle(cbId) {
                context.copy(a.str("text") ?: throw HostException("E_INVALID", "clipboard.setText needs text"))
                ""
            }
            J3.LinkingOpenUrl -> settle(cbId) {
                val url = a.str("url")?.takeIf { URL_SCHEME.containsMatchIn(it) } ?: throw HostException("E_INVALID", "linking.openUrl needs an absolute URL")
                withContext(Dispatchers.Main) { (host.links ?: LinkOpener.System).open(url, context) }
                ""
            }
            null -> when (val capability = host.capabilities[name]) {
                null -> scope.launch { pageHost.reject(cbId, "E_UNSUPPORTED", "$name is not available") }
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
            tags = when (val t = a["tags"]) {
                null, JsonNull -> emptyMap()
                is JsonObject -> t.mapValues { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw HostException("E_INVALID", "http tag \"$k\" must be a string") }
                else -> throw HostException("E_INVALID", "http tags must be an object of strings")
            },
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
            pageHost.resolve(cbId, block())
        } catch (e: HttpStatusException) {
            pageHost.reject(cbId, "E_HTTP", e.message ?: "", e.details)
        } catch (e: HostException) {
            pageHost.reject(cbId, e.code, e.message ?: "")
        } catch (e: Exception) {
            pageHost.reject(cbId, "E_NET", e.message ?: e.toString())
        }
    }

    /** J4 (docs/native-api.md §3–§12). */
    fun send(name: String, argsJson: String) {
        val a = args(argsJson)
        when (J4.of(name)) {
            J4.TimerCancel -> a["cbId"]?.jsonPrimitive?.intOrNull?.let { timers.remove(it)?.cancel() }
            J4.NavigationPush -> host.navigator.push(a.str("page") ?: return, a["params"]?.toString() ?: "{}", context)
            J4.NavigationReplace -> host.navigator.replace(a.str("page") ?: return, a["params"]?.toString() ?: "{}", a.str("since"), context)
            J4.NavigationPop -> host.navigator.pop(a["result"]?.toString(), a.str("since"), context)
            J4.StoreSet -> { val key = a.str("key") ?: return; host.store.setJson(key, a.str("value") ?: "null") }
            // one host listener per key / topic; JS fans out to its own handlers
            J4.StoreSubscribe -> { val key = a.str("key") ?: return; subscriptions.getOrPut("store:$key") { host.store.observe(key) { v -> storeChanged(key, v) } } }
            J4.StorageRemove -> storage?.remove(a.str("key") ?: return)
            J4.StorageClear -> storage?.clear()
            J4.EventsEmit -> host.events.emit(a.str("topic") ?: return, a["payload"]?.toString() ?: "{}")
            J4.EventsSubscribe -> { val topic = a.str("topic") ?: return; subscriptions.getOrPut("events:$topic") { host.events.subscribe(topic) { json -> pageHost.emit(topic, json) } } }
            J4.I18nSubscribe -> {
                host.locale?.let { flow ->
                    subscriptions.getOrPut("i18n") { follow(flow) { pageHost.emit("i18n.locale", buildJsonObject { put("locale", it) }.toString()) } }
                }
                val zone = host.timeZone
                if (zone != null) {
                    subscriptions.getOrPut("i18n.timeZone") { follow(zone) { pageHost.emit("i18n.timeZone", buildJsonObject { put("timeZone", it) }.toString()) } }
                } else if (systemZoneSeen == null) {
                    systemZoneSeen = host.systemZone()
                }
            }
            J4.SessionSubscribe -> host.session?.let { source ->
                subscriptions.getOrPut("session") { follow(source.state) { pageHost.emit("session", sessionJson(it)) } }
            }
            J4.AnalyticsTrack -> track(a)
            null -> sink.log("unknown J4 $name")
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
        pageHost.entry("emit", JsValue.Str("store:$key"), JsValue.Str("""{"value":${value.json}}"""), before = {
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
            pageHost.entry("resolve", JsValue.Num(cbId), JsValue.Str(""), before = { timers.remove(cbId) })
        }
    }

    /**
     * Without a host zone the page follows the system's, which has no change callback in common code: coming back on screen
     * (the user may have just been in Settings) is when it is checked again.
     */
    fun systemZoneMoved() {
        if (host.timeZone != null) return
        val zone = host.systemZone()
        var same = false
        pageHost.entry("emit", JsValue.Str("i18n.timeZone"), JsValue.Str(buildJsonObject { put("timeZone", zone) }.toString()), before = {
            if (systemZoneSeen == null || systemZoneSeen == zone) same = true else systemZoneSeen = zone
        }, skipWhen = { same })
    }

    /** The page is going: every host listener it holds is let go. Under the runtime lock, like [subscriptions] everywhere else. */
    fun close() {
        subscriptions.values.forEach { runCatching { it.close() } }
        subscriptions.clear()
    }

    private fun args(json: String): JsonObject = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        private val URL_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }
}
