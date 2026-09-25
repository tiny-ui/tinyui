package app.tinyui

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** J4 `navigation.push` / `navigation.pop`; results travel back as K5 `navigation.result` (docs/app-model.md). */
interface Navigator {
    fun push(page: String, paramsJson: String)
    fun pop(resultJson: String?)

    object None : Navigator {
        override fun push(page: String, paramsJson: String) {}
        override fun pop(resultJson: String?) {}
    }
}

/** A store value with the monotonic version of its write; listeners drop what arrives out of order. */
class StoreValue(val json: String, val version: Long)

/** App-wide key → JSON text, in memory; the truth for cross-page state (docs/native-api.md §4). */
interface Store {
    fun getJson(key: String): String?
    fun setJson(key: String, json: String)
    /** [listener] receives every write to [key], possibly out of order across threads; the returned handle removes it. */
    fun observe(key: String, listener: (StoreValue) -> Unit): AutoCloseable
}

inline fun <reified T> Store.set(key: String, value: T) = setJson(key, Json.encodeToString(serializer<T>(), value))

/** Writes every value of [flow] to [key] until [scope] ends. */
fun <T> Store.bind(key: String, flow: Flow<T>, scope: CoroutineScope, serializer: KSerializer<T>): Job =
    scope.launch { flow.collect { setJson(key, Json.encodeToString(serializer, it)) } }

inline fun <reified T> Store.bind(key: String, flow: Flow<T>, scope: CoroutineScope): Job = bind(key, flow, scope, serializer<T>())

/** Copy-on-write: pages subscribe on their engine threads while the host writes from anywhere. */
@OptIn(ExperimentalAtomicApi::class)
internal class InMemoryStore : Store {
    private val values = AtomicReference<Map<String, StoreValue>>(emptyMap())
    private val listeners = Listeners<StoreValue>()

    override fun getJson(key: String): String? = values.load()[key]?.json

    override fun setJson(key: String, json: String) {
        var written: StoreValue? = null
        values.update { map -> StoreValue(json, (map[key]?.version ?: 0) + 1).also { written = it }.let { map + (key to it) } }
        listeners.notify(key, written!!)
    }

    override fun observe(key: String, listener: (StoreValue) -> Unit): AutoCloseable = listeners.add(key, listener)
}

/** One request of J3 `http.request`; [body] is the text to send, its `Content-Type` among [headers]. */
class HttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String?, val timeoutMs: Long?)

class HttpResponse(val status: Int, val headers: Map<String, String>, val body: String)

/** Where a page's requests go out (docs/native-api.md §6): the built-in `default`, or one the host registers with its identity. */
fun interface HttpChannel {
    /** Any status is a response; throw [HostException] for E3 codes, anything else counts as E_NET. */
    suspend fun request(request: HttpRequest): HttpResponse
}

/** An E3 failure with one of the codes in docs/native-api.md §14. */
class HostException(val code: String, message: String) : Exception(message)

/** The login session a host provides (docs/native-api.md §9); [userId] is a stable, non-secret id, never a token; null when logged out or not yet known. */
data class Session(val loggedIn: Boolean, val userId: String?) {
    companion object {
        val LoggedOut = Session(false, null)
    }
}

/** [signIn] runs the host's login flow and returns when it ends, whatever the outcome. */
class SessionSource(val state: StateFlow<Session>, val signIn: suspend (source: String) -> Unit)

/** Where `analytics.track` goes (docs/native-api.md §12); [props] hold String, Long, Double, Boolean or null. */
fun interface AnalyticsSink {
    fun track(name: String, props: Map<String, Any?>, page: PageContext)
}

/** Opens a link for `linking.openUrl` (docs/native-api.md §11); throw [HostException] `E_UNSUPPORTED` when nothing can. */
fun interface LinkOpener {
    suspend fun open(url: String, page: PageContext)

    companion object {
        /** The platform's own handling: Android `ACTION_VIEW`, iOS `UIApplication.open`. */
        val System: LinkOpener = LinkOpener { url, page -> page.openWithSystem(url) }
    }
}

/** One bus for host events and business events; pages subscribe per topic (docs/app-model.md §3). */
class EventBus {
    private val listeners = Listeners<String>()

    fun emit(topic: String, payloadJson: String) = listeners.notify(topic, payloadJson)

    fun subscribe(topic: String, listener: (String) -> Unit): AutoCloseable = listeners.add(topic, listener)
}

/** Names the framework dispatches itself (J2 / J3 / J4); a host capability may not take one. */
internal val FrameworkNames = setOf(
    "device.info", "store.get", "store.set", "store.subscribe", "storage.get", "storage.set", "storage.remove", "storage.clear",
    "i18n.t", "i18n.locale", "i18n.subscribe", "session.get", "session.subscribe", "session.signIn",
    "navigation.push", "navigation.pop", "events.emit", "events.subscribe", "http.request", "timer.schedule", "timer.cancel",
    "ui.toast", "ui.alert", "ui.confirm", "linking.openUrl", "analytics.track",
)

/** What every host offers, listed in the mount manifest ahead of the host's own; `session.signIn` only comes with a session. */
internal val FrameworkCapabilities: Set<String> = FrameworkNames - "session.signIn"

@OptIn(ExperimentalAtomicApi::class)
internal class Listeners<T> {
    private val byKey = AtomicReference<Map<String, List<(T) -> Unit>>>(emptyMap())

    fun add(key: String, listener: (T) -> Unit): AutoCloseable {
        byKey.update { it + (key to (it[key].orEmpty() + listener)) }
        return AutoCloseable { byKey.update { it + (key to (it[key].orEmpty() - listener)) } }
    }

    fun notify(key: String, value: T) {
        byKey.load()[key]?.forEach { it(value) }
    }
}

internal expect fun platformInfo(): Map<String, String>

/** BCP 47, e.g. `zh-Hans-CN`. */
internal expect fun systemLocale(): String
