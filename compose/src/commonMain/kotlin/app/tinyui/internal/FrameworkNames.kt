package app.tinyui.internal

/** Names the framework dispatches itself (J2 / J3 / J4); a host capability may not take one. */
internal val FrameworkNames = setOf(
    "device.info", "store.get", "store.set", "store.subscribe", "storage.get", "storage.set", "storage.remove", "storage.clear",
    "i18n.t", "i18n.locale", "i18n.timeZone", "i18n.zoneOffset", "i18n.subscribe", "session.get", "session.subscribe", "session.signIn",
    "navigation.push", "navigation.pop", "events.emit", "events.subscribe", "http.request", "timer.schedule", "timer.cancel",
    "ui.toast", "ui.alert", "ui.confirm", "linking.openUrl", "clipboard.setText", "analytics.track", "crypto.rsaEncrypt",
)

/** What every host offers, listed in the mount manifest ahead of the host's own; `session.signIn` only comes with a session. */
internal val FrameworkCapabilities: Set<String> = FrameworkNames - "session.signIn"
