package app.tinyui

import wang.harlon.quickjs.QuickJs

/** Whether a package can run on a host (docs/updates.md §4.1): for a host's test over its embedded packages. */
object PackageCheck {
    /** Every reason [manifest]'s package cannot run on [host]; empty when it can. */
    fun problems(manifest: BuildManifest, host: TinyUIHost): List<String> = buildList {
        if (manifest.engine != QuickJs.upstreamCommit) add("built for engine ${manifest.engine}, the host embeds ${QuickJs.upstreamCommit}")
        if (!TinyUI.isCompatible(TinyUI.version, manifest.tinyui)) add("built with tinyui ${manifest.tinyui.ifEmpty { "(unknown)" }}, which does not run on this host's ${TinyUI.version} (same major, not newer)")
        val capabilities = FrameworkCapabilities + host.capabilityNames
        for ((page, uses) in manifest.requires.entries.sortedBy { it.key }) {
            uses.components.filter { host.components.schema(it) == null }.forEach { add("$page uses component $it, which the host does not register") }
            uses.capabilities.filter { it !in capabilities }.forEach { add("$page calls $it, which the host does not provide") }
            uses.channels.filter { it != TinyUIHost.DEFAULT_CHANNEL && it !in host.channels }.forEach { add("$page sends through http channel $it, which the host does not register") }
        }
    }
}
