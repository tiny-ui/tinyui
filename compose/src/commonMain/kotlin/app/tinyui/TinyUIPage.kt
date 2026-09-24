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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Renders one TinyUI page; the host owns navigation and passes the page's bytecode (docs/app-model.md).
 * To deliver a navigation result to this page, keep the [PageHost] (see [onHost]) and call [PageHost.emit].
 */
@Composable
fun TinyUIPage(
    page: PageModule,
    host: TinyUIHost,
    services: HostServices = HostServices.Default,
    propsJson: String = "{}",
    modifier: Modifier = Modifier,
    sourceMaps: SourceMaps = SourceMaps.EMPTY,
    error: @Composable (PageFailure) -> Unit = { PageFailureScreen(it) },
    onHost: (PageHost) -> Unit = {},
) {
    val pageHost = remember(page, host, services, propsJson, sourceMaps) {
        PageHost(page, host, services, propsJson, sourceMaps = sourceMaps)
    }
    DisposableEffect(pageHost) {
        onHost(pageHost)
        pageHost.start()
        onDispose { pageHost.close() }
    }
    Box(modifier) {
        val failure = pageHost.failure
        if (failure != null) {
            error(failure)
        } else {
            for (child in pageHost.tree.root.children) key(child.id) { pageHost.Render(child) }
        }
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
