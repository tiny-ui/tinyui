package app.tinyui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Renders one TinyUI page; the host owns navigation and passes the page's bytecode (docs/app-model.md).
 * To deliver a navigation result to this page, keep the [PageHost] (see [onHost]) and call [PageHost.emit].
 * The page is visible (`pageVisible()`) while the lifecycle is at least STARTED; its toasts and dialogs draw over it.
 *
 * @param locals this mount's objects for `host.call` capabilities (docs/native-api.md §13).
 */
@Composable
fun TinyUIPage(
    page: PageModule,
    host: TinyUIHost,
    propsJson: String = "{}",
    locals: List<PageLocalValue<*>> = emptyList(),
    modifier: Modifier = Modifier,
    sourceMaps: SourceMaps = SourceMaps.EMPTY,
    error: @Composable (PageFailure) -> Unit = { PageFailureScreen(it) },
    onHost: (PageHost) -> Unit = {},
) {
    val pageHost = remember(page, host, propsJson, locals, sourceMaps) {
        PageHost(page, host, propsJson, locals, sourceMaps = sourceMaps)
    }
    val uriHandler = LocalUriHandler.current
    SideEffect { pageHost.context.uriHandler = uriHandler }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(pageHost) {
        onHost(pageHost)
        pageHost.start()
        onDispose { pageHost.close() }
    }
    DisposableEffect(pageHost, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> pageHost.visible(true)
                Lifecycle.Event.ON_STOP -> pageHost.visible(false)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier) {
        val failure = pageHost.failure
        if (failure != null) {
            error(failure)
        } else {
            for (child in pageHost.tree.root.children) key(child.id) { pageHost.Render(child) }
        }
        pageHost.ui.Overlay(Modifier.align(Alignment.BottomCenter))
    }
}


/** The default failure screen: kind and message, plus the stack when [TinyUI.debug] is on. */
@Composable
fun PageFailureScreen(failure: PageFailure) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("TinyUI page failed (${failure.kind})", style = MaterialTheme.typography.titleMedium)
        Text(failure.message, style = MaterialTheme.typography.bodyMedium)
        if (TinyUI.debug) {
            for (frame in failure.error.frames) Text(frame.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
