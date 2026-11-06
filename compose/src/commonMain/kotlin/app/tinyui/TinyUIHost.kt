package app.tinyui

import app.tinyui.components.ComponentRegistry
import app.tinyui.host.AnalyticsSink
import app.tinyui.host.CapabilityRegistry
import app.tinyui.host.EventBus
import app.tinyui.host.HttpChannel
import app.tinyui.host.InMemoryStore
import app.tinyui.host.KtorChannel
import app.tinyui.host.LinkOpener
import app.tinyui.host.Navigator
import app.tinyui.host.SessionSource
import app.tinyui.host.Store
import app.tinyui.page.MissingKeys
import app.tinyui.page.PageSink
import kotlinx.coroutines.flow.StateFlow
import okio.Path

/**
 * What one App gives every TinyUI page, built once at startup and not changed after (docs/native-api.md §1).
 * Its host components, capability names, channel names and whether it has a [session] are the host contract
 * [HostSnapshot] renders; the other standard interfaces fall back to defaults when absent.
 *
 * @param channels HTTP channels pages pick by name (`http.client("app")`); `default` is the framework's own and cannot be registered.
 * @param locale the user's language (BCP 47); the system language when null.
 * @param timeZone the zone dates are shown in (IANA id, e.g. `Asia/Shanghai`); the system zone when null.
 * @param dataDir where `storage` keeps each package's file; `storage` answers E_UNSUPPORTED when null. Not the `Updates` directory.
 */
class TinyUIHost(
    val components: ComponentRegistry,
    val sink: PageSink,
    val capabilities: CapabilityRegistry = CapabilityRegistry(),
    val channels: Map<String, HttpChannel> = emptyMap(),
    val locale: StateFlow<String>? = null,
    val timeZone: StateFlow<String>? = null,
    val session: SessionSource? = null,
    val analytics: AnalyticsSink? = null,
    val links: LinkOpener? = null,
    val navigator: Navigator = Navigator.None,
    val dataDir: Path? = null,
) {
    /** App-level key-value shared by every page and the host's own code (docs/native-api.md §4). */
    val store: Store = InMemoryStore()

    /** App-level bus: host events to pages, business events from pages (docs/native-api.md §5). */
    val events: EventBus = EventBus()

    /** Capability names pages may rely on beyond the framework's: the registered ones, and `session.signIn` with a [session]. */
    val capabilityNames: Set<String> get() = capabilities.names + if (session != null) setOf(SESSION_SIGN_IN) else emptySet()

    internal val storages = Storages(dataDir, sink)
    internal val missingKeys = MissingKeys()
    internal val defaultChannel: HttpChannel by lazy { KtorChannel(defaultHttpClient()) }
    /** The platform's zone when [timeZone] is null; replaceable so tests can move it. */
    internal var systemZone: () -> String = ::systemTimeZone

    init {
        for (name in channels.keys) {
            require(name.matches(CHANNEL_NAME)) { "channel name \"$name\" must match ${CHANNEL_NAME.pattern}" }
            require(name != DEFAULT_CHANNEL) { "channel \"$DEFAULT_CHANNEL\" is the framework's own" }
        }
        components.freeze()
        capabilities.freeze()
    }

    internal fun channel(name: String): HttpChannel? = if (name == DEFAULT_CHANNEL) defaultChannel else channels[name]

    internal fun currentLocale(): String = locale?.value ?: systemLocale()

    internal fun currentTimeZone(): String = timeZone?.value ?: systemZone()

    internal companion object {
        const val DEFAULT_CHANNEL = "default"
        const val SESSION_SIGN_IN = "session.signIn"
        val CHANNEL_NAME = Regex("[a-z][a-z0-9-]*")
    }
}
