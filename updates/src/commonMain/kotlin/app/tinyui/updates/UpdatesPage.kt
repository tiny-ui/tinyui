package app.tinyui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tinyui.HostServices
import app.tinyui.LoadedPage
import app.tinyui.PageFailure
import app.tinyui.PageFailureScreen
import app.tinyui.PageHost
import app.tinyui.PageSink
import app.tinyui.TinyUIPage
import app.tinyui.schema.ComponentRegistry

/**
 * [TinyUIPage] for a page of one of [updates]' packages: [name] is the module name, the part before the first
 * `/` names the package. A page from an installed package that fails with E2 / E6 is remounted from the
 * embedded package, which the whole package falls back to (docs/updates.md §4.5).
 */
@Composable
fun UpdatesPage(
    updates: Updates,
    name: String,
    registry: ComponentRegistry,
    sink: PageSink,
    services: HostServices = HostServices.Default,
    propsJson: String = "{}",
    modifier: Modifier = Modifier,
    error: @Composable (PageFailure) -> Unit = { PageFailureScreen(it) },
    onHost: (PageHost) -> Unit = {},
) {
    val pkg = name.substringBefore('/')
    var bundle by remember(updates, name) { mutableStateOf(updates.current(pkg)) }
    val loaded by produceState<LoadedPage?>(null, bundle, name) { value = bundle.page(name) }
    val page = loaded ?: return
    TinyUIPage(
        page.runtime, page.module, registry, sink, services, propsJson, modifier, page.sourceMaps,
        error = { failure ->
            // only the failures that mean the package cannot run here (docs/updates.md §4.5)
            if ((failure.kind == "E2" || failure.kind == "E6") && page.bundle !== updates.embedded(pkg)) {
                LaunchedEffect(failure) {
                    updates.rollBack(pkg, page.module.name, failure)
                    bundle = updates.embedded(pkg)
                }
            } else {
                error(failure)
            }
        },
        onHost = onHost,
    )
}
