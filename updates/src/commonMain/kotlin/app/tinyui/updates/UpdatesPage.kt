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
import app.tinyui.PageError
import app.tinyui.PageFailure
import app.tinyui.PageFailureScreen
import app.tinyui.PageHost
import app.tinyui.TinyUIHost
import app.tinyui.TinyUIPage
import kotlinx.coroutines.CancellationException

/**
 * [TinyUIPage] for a page of one of [updates]' packages: [name] is the module name, the part before the first
 * `/` names the package. A page from an installed package that fails with E2 / E6 is remounted from the
 * embedded package, which the whole package falls back to (docs/updates.md §4.5).
 */
@Composable
fun UpdatesPage(
    updates: Updates,
    name: String,
    host: TinyUIHost,
    services: HostServices = HostServices.Default,
    propsJson: String = "{}",
    modifier: Modifier = Modifier,
    error: @Composable (PageFailure) -> Unit = { PageFailureScreen(it) },
    onHost: (PageHost) -> Unit = {},
) {
    val pkg = name.substringBefore('/')
    var bundle by remember(updates, name) { mutableStateOf(updates.current(pkg)) }
    val loaded by produceState<Result<LoadedPage>?>(null, bundle, name) {
        value = try {
            Result.success(bundle.page(name))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
    val result = loaded ?: return
    // a page the package cannot load (e.g. not in it) is not the package failing to run: no fallback
    val page = result.getOrElse { t ->
        val failure = remember(t) {
            PageFailure(PageError("E6", name, "", t.message ?: t.toString())).also { host.sink.error(it.error) }
        }
        error(failure)
        return
    }
    TinyUIPage(
        page.module, host, services, propsJson, modifier, page.sourceMaps,
        error = { failure ->
            // only the failures that mean the package cannot run here (docs/updates.md §4.5)
            if ((failure.kind == "E2" || failure.kind == "E6") && page.bundle !== updates.embedded(pkg)) {
                LaunchedEffect(failure) {
                    updates.rollBack(pkg, page.bundle, page.module.name, failure)
                    bundle = updates.embedded(pkg)
                }
            } else {
                error(failure)
            }
        },
        onHost = onHost,
    )
}
