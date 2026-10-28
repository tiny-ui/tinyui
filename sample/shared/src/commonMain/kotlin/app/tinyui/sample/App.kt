package app.tinyui.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tinyui.Bundle
import app.tinyui.TinyUI
import app.tinyui.sample.res.Res
import app.tinyui.updates.Updates
import app.tinyui.updates.UpdatesPage
import app.tinyui.updates.UpdatesScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException

/** The two packages the App embeds, each under its own resource directory (docs/updates.md §3). */
private val packages = listOf("sample", "sample-extra")

private val pages = listOf("sample/todos", "sample-extra/hello", "sample-extra/features", "sample-extra/form")

/**
 * One [Updates] and one [UpdatesScheduler] per process, whatever happens to the composition: a recreated Activity
 * must not add a second instance on the same directory, nor a second lifecycle observer.
 */
private object SampleUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var updates: Updates? = null

    suspend fun get(dir: Path): Updates = lock.withLock {
        updates ?: withContext(Dispatchers.Default) {
            val bundles = packages.map { embedded(it) }
            // debug builds ship the maps (-Ptinyui.maps); stacks on the failure screen only when they came along
            TinyUI.debug = !bundles.first().page("sample/todos").sourceMaps.isEmpty
            Updates(bundles, hostVersion = HOST_VERSION, dir = dir, installId = "sample-install", fetch = { path -> httpGet(otaBaseUrl() + path) }) {
                println("TinyUI updates $it")
            }
        }.also {
            updates = it
            // docs/updates.md §4.1 / §4.6: check at startup before any page mounts, again in the foreground when due
            UpdatesScheduler(it, scope).start()
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun embedded(pkg: String): Bundle = Bundle.load { path ->
    try { Res.readBytes("files/tinyui/$pkg/$path") } catch (e: MissingResourceException) { null }
}

/**
 * [updatesDir] is where installed packages live (docs/updates.md §4.1). Hot updates come from the development
 * machine: `tinyui bundle` each package, then `python3 -m http.server 8000` where `dist/ota` is, and restart the App.
 */
@Composable
fun App(updatesDir: Path) {
    val updates by produceState<Updates?>(null) { value = SampleUpdates.get(updatesDir) }
    var page by remember { mutableStateOf(pages.first()) }
    val host = remember(updatesDir) { sampleHost(updatesDir.parent!! / "tinyui-data") }
    val locale by sampleLocale.collectAsState()
    // one store per tab that keeps what leaves composition: a page switched away from stays alive, form input and
    // all; a navigation host does the same per back stack entry and clears an entry's store when it is popped
    val retained = retainRetainedValuesStoreRegistry()
    // pages name theme tokens only (docs/components.md §6); flipping the scheme here restyles them with no patch
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    for (p in pages) TextButton(onClick = { page = p }) { Text(p.substringAfter('/')) }
                    TextButton(onClick = { sampleLocale.value = if (locale == "en") "zh" else "en" }) { Text(if (locale == "en") "中文" else "EN") }
                }
                val u = updates
                if (u == null) Text("loading…", Modifier.padding(16.dp))
                else key(page) { retained.LocalRetainedValuesStoreProvider(page) { UpdatesPage(u, page, host, modifier = Modifier.fillMaxSize()) } }
            }
        }
    }
}
