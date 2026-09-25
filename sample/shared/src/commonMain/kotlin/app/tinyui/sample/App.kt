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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import app.tinyui.Bundle
import app.tinyui.TinyUI
import app.tinyui.sample.res.Res
import app.tinyui.updates.Updates
import app.tinyui.updates.UpdatesPage

/** The two packages the App embeds, each under its own resource directory (docs/updates.md §3). */
private val packages = listOf("sample", "sample-extra")

private val pages = listOf("sample/todos", "sample-extra/hello", "sample-extra/features")

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
    val updates by produceState<Updates?>(null) {
        value = withContext(Dispatchers.Default) {
            val bundles = packages.map { embedded(it) }
            // debug builds ship the maps (-Ptinyui.maps); stacks on the failure screen only when they came along
            TinyUI.debug = !bundles.first().page("sample/todos").sourceMaps.isEmpty
            Updates(bundles, hostVersion = HOST_VERSION, dir = updatesDir, installId = "sample-install", fetch = { path -> httpGet(otaBaseUrl() + path) }) {
                println("TinyUI updates $it")
            }
        }
    }
    // docs/updates.md §4.1: check before any page mounts, so a rollback reaches this install on the next start
    LaunchedEffect(updates) { updates?.check() }
    var page by remember { mutableStateOf(pages.first()) }
    val host = remember(updatesDir) { sampleHost(updatesDir.parent!! / "tinyui-data") }
    val locale by sampleLocale.collectAsState()
    // pages name theme tokens only (docs/components.md §6); flipping the scheme here restyles them with no patch
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    for (p in pages) TextButton(onClick = { page = p }) { Text(p.substringAfter('/')) }
                    TextButton(onClick = { sampleLocale.value = if (locale == "en") "zh" else "en" }) { Text(if (locale == "en") "中文" else "EN") }
                }
                val u = updates
                if (u == null) Text("loading…", Modifier.padding(16.dp)) else UpdatesPage(u, page, host, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
