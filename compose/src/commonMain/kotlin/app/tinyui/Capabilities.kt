package app.tinyui

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

/** Names the framework dispatches itself (J2 / J3 / J4); a host capability may not take one. */
internal val FrameworkNames = setOf(
    "device.info", "store.get", "store.set", "store.subscribe", "storage.get", "storage.set", "storage.remove", "storage.clear",
    "i18n.t", "i18n.locale", "i18n.timeZone", "i18n.zoneOffset", "i18n.subscribe", "session.get", "session.subscribe", "session.signIn",
    "navigation.push", "navigation.pop", "events.emit", "events.subscribe", "http.request", "timer.schedule", "timer.cancel",
    "ui.toast", "ui.alert", "ui.confirm", "linking.openUrl", "clipboard.setText", "analytics.track", "crypto.rsaEncrypt",
)

/** What every host offers, listed in the mount manifest ahead of the host's own; `session.signIn` only comes with a session. */
internal val FrameworkCapabilities: Set<String> = FrameworkNames - "session.signIn"
