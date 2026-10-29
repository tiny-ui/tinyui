package app.tinyui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.retain.RetainObserver
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Renders one TinyUI page; the host owns navigation and passes the page's bytecode (docs/app-model.md).
 * To deliver a navigation result to this page, keep the [PageHost] (see [onHost]) and call [PageHost.emit].
 * The page is visible (`pageVisible()`) while it is composed and the lifecycle is at least STARTED; its toasts and
 * dialogs draw over it.
 *
 * The page is [retain]ed: by default it closes when it leaves composition, as if remembered. Under a
 * [androidx.compose.runtime.retain.LocalRetainedValuesStore] that keeps exited values (one per navigation entry), it
 * lives on off screen, engine and all, and comes back as the user left it; it closes when that store lets it go
 * (docs/app-model.md §5).
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
    val retained = retain(page, host, propsJson, sourceMaps) {
        RetainedPage(PageHost(page, host, propsJson, locals, sourceMaps = sourceMaps))
    }
    val pageHost = retained.host
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    // a new list of the same objects each recomposition must not remount the page: the context just follows it
    SideEffect {
        pageHost.context.uriHandler = uriHandler
        pageHost.context.clipboard = clipboard
        pageHost.context.locals = locals.associate { it.local to it.value }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(retained) {
        onHost(pageHost)
        retained.startOnce()
        onDispose {
            // off screen but maybe kept: hidden, and nothing to open links or copy with until it is back
            pageHost.visible(false)
            pageHost.context.uriHandler = null
            pageHost.context.clipboard = null
        }
    }
    DisposableEffect(pageHost, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> pageHost.visible(true)
                Lifecycle.Event.ON_STOP -> pageHost.visible(false)
                else -> {}
            }
        }
        // addObserver replays ON_START only when already started; below that the page starts hidden
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pageHost.visible(false)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier) {
        val failure = pageHost.failure
        if (failure != null) {
            error(failure)
        } else {
            CompositionLocalProvider(LocalPageBack provides pageHost.back) {
                for (child in pageHost.tree.root.children) key(child.id) { pageHost.Render(child) }
            }
        }
        pageHost.ui.Overlay(Modifier.align(Alignment.BottomCenter))
    }
}


/** Closes the page when the retaining store lets it go, not when it merely leaves composition. */
private class RetainedPage(val host: PageHost) : RetainObserver {
    private var started = false

    fun startOnce() {
        if (started) return
        started = true
        host.start()
    }

    override fun onRetained() {}
    override fun onEnteredComposition() {}
    override fun onExitedComposition() {}
    override fun onRetired() = host.close()
    override fun onUnused() = host.close()
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
