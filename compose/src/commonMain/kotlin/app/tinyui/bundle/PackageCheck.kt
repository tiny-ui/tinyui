package app.tinyui.bundle

import app.tinyui.FrameworkCapabilities
import app.tinyui.TinyUI
import app.tinyui.TinyUIHost
import wang.harlon.quickjs.QuickJs

/** Whether a package can run on a host (docs/updates.md §4.1): for a host's test over its embedded packages. */
object PackageCheck {
    /**
     * Every reason [manifest]'s package cannot run on [host]; empty when it can. [others] are the App's other packages:
     * a page name a page navigates to outside its own package has to be a native route of the host or a page of one
     * of them.
     */
    fun problems(manifest: BuildManifest, host: TinyUIHost, others: List<BuildManifest> = emptyList()): List<String> = buildList {
        if (manifest.engine != QuickJs.upstreamCommit) add("built for engine ${manifest.engine}, the host embeds ${QuickJs.upstreamCommit}")
        if (!TinyUI.isCompatible(TinyUI.version, manifest.tinyui)) add("built with tinyui ${manifest.tinyui.ifEmpty { "(unknown)" }}, which does not run on this host's ${TinyUI.version} (same major, not newer)")
        val capabilities = FrameworkCapabilities + host.capabilityNames
        val reachable = host.routes.mapTo(HashSet()) { it.name } + others.flatMap { it.pages }
        for ((page, uses) in manifest.requires.entries.sortedBy { it.key }) {
            uses.components.filter { host.components.schema(it) == null }.forEach { add("$page uses component $it, which the host does not register") }
            uses.capabilities.filter { it !in capabilities }.forEach { add("$page calls $it, which the host does not provide") }
            uses.channels.filter { it != TinyUIHost.DEFAULT_CHANNEL && it !in host.channels }.forEach { add("$page sends through http channel $it, which the host does not register") }
            uses.routes.filter { it !in reachable }.forEach { add("$page navigates to $it, which is neither a native route of the host nor a page of another package of the App") }
        }
    }
}
