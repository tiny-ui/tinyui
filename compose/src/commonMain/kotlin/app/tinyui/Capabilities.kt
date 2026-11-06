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

/*
 * The names the framework answers itself, one enum per way a page reaches them (docs/native-api.md §2–§13). They are declared
 * here and nowhere else: HostBridge's `when` over each enum has no `else`, so a name without a handler does not compile.
 */

/** J2: answered at once, inside the entry that asks. */
internal enum class J2(val wire: String) {
    DeviceInfo("device.info"), StoreGet("store.get"), StorageGet("storage.get"), StorageSet("storage.set"),
    I18nT("i18n.t"), I18nLocale("i18n.locale"), I18nTimeZone("i18n.timeZone"), I18nZoneOffset("i18n.zoneOffset"), SessionGet("session.get");

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun of(name: String): J2? = byWire[name]
    }
}

/** J3: answered later, with a resolve or a reject. A name that is not here is looked up among the host's capabilities. */
internal enum class J3(val wire: String) {
    TimerSchedule("timer.schedule"), HttpRequest("http.request"), SessionSignIn("session.signIn"),
    UiToast("ui.toast"), UiAlert("ui.alert"), UiConfirm("ui.confirm"),
    CryptoRsaEncrypt("crypto.rsaEncrypt"), ClipboardSetText("clipboard.setText"), LinkingOpenUrl("linking.openUrl");

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun of(name: String): J3? = byWire[name]
    }
}

/** J4: not answered. */
internal enum class J4(val wire: String) {
    TimerCancel("timer.cancel"), NavigationPush("navigation.push"), NavigationPop("navigation.pop"),
    StoreSet("store.set"), StoreSubscribe("store.subscribe"), StorageRemove("storage.remove"), StorageClear("storage.clear"),
    EventsEmit("events.emit"), EventsSubscribe("events.subscribe"), I18nSubscribe("i18n.subscribe"), SessionSubscribe("session.subscribe"),
    AnalyticsTrack("analytics.track");

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun of(name: String): J4? = byWire[name]
    }
}

/** Every name above; a host capability may not take one. */
internal val FrameworkNames: Set<String> = (J2.entries.map { it.wire } + J3.entries.map { it.wire } + J4.entries.map { it.wire }).toSet()

/** What every host offers, listed in the mount manifest ahead of the host's own; `session.signIn` only comes with a session. */
internal val FrameworkCapabilities: Set<String> = FrameworkNames - J3.SessionSignIn.wire
