package app.tinyui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Where a page's toasts are drawn. A surface that covers the page (a bottom sheet is a window of its own, scrim and
 * all) would hide a toast drawn in the page, so while one is up the toast is drawn in the topmost of them instead:
 * the page draws it only when [covered] is false, a surface calls [Host] in its own content.
 */
internal class ToastLayer(private val toasts: @Composable (Modifier) -> Unit) {
    private val surfaces = mutableStateListOf<Any>()

    /** True while some surface over the page draws the toasts. */
    val covered: Boolean get() = surfaces.isNotEmpty()

    /** Draws the page's toasts here for as long as the caller is the topmost surface in composition. */
    @Composable
    fun Host(modifier: Modifier) {
        val surface = remember { Any() }
        DisposableEffect(surface) {
            surfaces.add(surface)
            onDispose { surfaces.remove(surface) }
        }
        if (surfaces.lastOrNull() === surface) toasts(modifier)
    }
}

/** The page's [ToastLayer]; null where nodes are rendered without a page, as in component tests. */
internal val LocalToastLayer = compositionLocalOf<ToastLayer?> { null }
