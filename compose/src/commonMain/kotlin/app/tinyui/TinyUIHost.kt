package app.tinyui

import app.tinyui.schema.ComponentRegistry

/**
 * What one App gives every TinyUI page, built once at startup and not changed after (docs/native-api.md §7).
 * Its components and capabilities are the host contract [HostSnapshot] renders.
 */
class TinyUIHost(
    val components: ComponentRegistry,
    val sink: PageSink,
    val capabilities: CapabilityRegistry = CapabilityRegistry(),
) {
    init {
        components.freeze()
        capabilities.freeze()
    }
}

/** App-level J3 `host.call` capabilities by name (docs/native-api.md §7). */
class CapabilityRegistry {
    private val entries = LinkedHashMap<String, HostCapability>()
    private var frozen = false

    val names: Set<String> get() = entries.keys

    fun register(name: String, capability: HostCapability) {
        require(name.isNotEmpty() && name.none(Char::isWhitespace)) { "capability name \"$name\" is empty or has whitespace" }
        require(name !in FrameworkCapabilities) { "capability $name is a framework name" }
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

/** The mount a capability call came from: the page's module name and the objects its [HostServices.locals] provide. */
class PageContext internal constructor(val name: String, private val locals: Map<PageLocal<*>, Any>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(local: PageLocal<T>): T? = locals[local] as T?
}

/** A typed key for an object one mount hands to capabilities, such as its screen's snackbar. */
class PageLocal<T : Any> {
    infix fun provides(value: T): PageLocalValue<T> = PageLocalValue(this, value)
}

class PageLocalValue<T : Any> internal constructor(val local: PageLocal<T>, val value: T)
