package app.tinyui

import androidx.compose.ui.platform.UriHandler
import app.tinyui.schema.ComponentRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okio.Path

/**
 * What one App gives every TinyUI page, built once at startup and not changed after (docs/native-api.md §1).
 * Its host components, capability names, channel names and whether it has a [session] are the host contract
 * [HostSnapshot] renders; the other standard interfaces fall back to defaults when absent.
 *
 * @param channels HTTP channels pages pick by name (`http.client("app")`); `default` is the framework's own and cannot be registered.
 * @param locale the user's language (BCP 47); the system language when null.
 * @param dataDir where `storage` keeps each package's file; `storage` answers E_UNSUPPORTED when null. Not the `Updates` directory.
 */
class TinyUIHost(
    val components: ComponentRegistry,
    val sink: PageSink,
    val capabilities: CapabilityRegistry = CapabilityRegistry(),
    val channels: Map<String, HttpChannel> = emptyMap(),
    val locale: StateFlow<String>? = null,
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

    internal companion object {
        const val DEFAULT_CHANNEL = "default"
        const val SESSION_SIGN_IN = "session.signIn"
        val CHANNEL_NAME = Regex("[a-z][a-z0-9-]*")
    }
}

/** App-level J3 `host.call` capabilities by name (docs/native-api.md §13). */
class CapabilityRegistry {
    private val entries = LinkedHashMap<String, HostCapability>()
    private var frozen = false

    val names: Set<String> get() = entries.keys

    fun register(name: String, capability: HostCapability) {
        require(name.isNotEmpty() && name.none(Char::isWhitespace)) { "capability name \"$name\" is empty or has whitespace" }
        require(name !in FrameworkNames) { "capability $name is a framework name" }
        check(!frozen) { "capability $name registered after the registry went into a TinyUIHost" }
        require(name !in entries) { "capability $name already registered" }
        entries[name] = capability
    }

    internal operator fun get(name: String): HostCapability? = entries[name]

    internal fun freeze() { frozen = true }
}

/** One host-defined J3 capability: [argsJson] is the object JS passed; returns JSON text, or null for `undefined`. Throw [HostException] for E3 codes. */
fun interface HostCapability {
    suspend fun call(argsJson: String, page: PageContext): String?
}

/** The mount a call came from: the page's module name and the objects its `locals` provide. */
class PageContext internal constructor(val name: String, private val locals: Map<PageLocal<*>, Any>) {
    internal var uriHandler: UriHandler? = null

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(local: PageLocal<T>): T? = locals[local] as T?

    internal suspend fun openWithSystem(url: String) {
        val handler = uriHandler ?: throw HostException("E_UNSUPPORTED", "the page is not on screen")
        withContext(Dispatchers.Main) {
            try {
                handler.openUri(url)
            } catch (e: IllegalArgumentException) {
                throw HostException("E_UNSUPPORTED", e.message ?: "nothing can open $url")
            }
        }
    }
}

/** A typed key for an object one mount hands to capabilities, such as its screen's view model. */
class PageLocal<T : Any> {
    infix fun provides(value: T): PageLocalValue<T> = PageLocalValue(this, value)
}

class PageLocalValue<T : Any> internal constructor(val local: PageLocal<T>, val value: T)
