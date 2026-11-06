package app.tinyui.host

import app.tinyui.FrameworkNames

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
