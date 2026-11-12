package app.tinyui.components.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.tinyui.components.LocalToastLayer
import app.tinyui.components.NodeScope
import app.tinyui.components.bottomSheetStyle

/**
 * `open` is the page's; going false animates the sheet out before it leaves composition. A user dismissal (swipe, scrim,
 * back) has already hidden it when `onDismiss` fires: it stays down until `open` goes false and true again. Its colours
 * are the host's where set (`TinyUIDefaults.bottomSheet`), else M3's. While it is up the page's toasts float above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetComponent(scope: NodeScope) {
    val open = scope.get<Boolean>("open") == true
    var showing by remember { mutableStateOf(false) }
    // content-sized, never parked half way
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    LaunchedEffect(open) {
        when {
            // reopened while the hide was still animating: still composed, so bring it back up
            open && showing -> state.show()
            open -> showing = true
            showing -> { state.hide(); showing = false }
        }
    }
    if (showing) {
        val style = bottomSheetStyle()
        ModalBottomSheet(
            onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
            sheetState = state,
            containerColor = style.containerColor,
            contentColor = style.contentColor,
            dragHandle = {
                Box {
                    BottomSheetDefaults.DragHandle()
                    // a toast of the page would be under this window. It floats just above the sheet instead: the
                    // bottom of a sheet is where its buttons are, and the keyboard may be right under it
                    LocalToastLayer.current?.let { toasts ->
                        val gap = with(LocalDensity.current) { ToastGap.roundToPx() }
                        val top = WindowInsets.statusBars.getTop(LocalDensity.current)
                        Popup(popupPositionProvider = remember(gap, top) { AboveAnchor(gap, top) }) { toasts.Host(Modifier) }
                    }
                }
            },
        ) { scope.Children() }
    }
}

private val ToastGap = 8.dp

/** Centred, its bottom [gap] above the anchor; kept under [top] (the status bar) when the anchor is that high. */
private class AboveAnchor(private val gap: Int, private val top: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(top))
}
