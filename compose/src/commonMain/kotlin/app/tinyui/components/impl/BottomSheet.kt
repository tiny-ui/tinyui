package app.tinyui.components.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt
import app.tinyui.components.LocalToastLayer
import app.tinyui.components.NodeScope
import app.tinyui.components.bottomSheetStyle

/**
 * `open` is the page's; going false animates the sheet out before it leaves composition. A user dismissal (swipe, scrim,
 * back) has already hidden it when `onDismiss` fires: it stays down until `open` goes false and true again. Its colours
 * are the host's where set (`TinyUIDefaults.bottomSheet`), else M3's, and so is a cap on its height
 * ([sheetContentCap]). While it is up the page's toasts float above it.
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
        // the page's window and its status bar, read out here: inside the sheet the window is the sheet's own
        val fraction = style.maxHeightFraction
        val window = LocalWindowInfo.current.containerSize.height
        val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
        ModalBottomSheet(
            onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
            sheetState = state,
            containerColor = style.containerColor,
            contentColor = style.contentColor,
            // with a cap the sheet never reaches the status bar ([sheetContentCap]), so the content takes no top inset:
            // M3 pads it by as much of the status bar as the sheet overlaps, which would move with the sheet
            contentWindowInsets = if (fraction == null) { { BottomSheetDefaults.windowInsets } } else { { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) } },
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
        ) {
            if (fraction == null) {
                scope.Children()
            } else {
                // M3 sizes a sheet by its content and takes the constraints it is given as the whole screen, so the cap
                // goes on the content: see [sheetContentCap]
                Column(
                    Modifier.layout { measurable, constraints ->
                        val cap = if (constraints.hasBoundedHeight) sheetContentCap(constraints.maxHeight, window, statusBar, fraction) else constraints.maxHeight
                        val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, cap), maxHeight = cap))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                ) { scope.Children() }
            }
        }
    }
}

private val ToastGap = 8.dp

/**
 * The most the content may take so the sheet's top stays [fraction] of a [window] pixels tall below the window's top.
 * M3 lets the sheet reach the top of the window and gives its content [contentMax] of that, the rest going to the drag
 * handle and the bottom inset; keeping (1 - fraction) of the window free above the sheet leaves that much less for the
 * content. The sheet never goes under the [statusBar] either, so a fraction near 1 stops at it. Never more than
 * [contentMax], so with less room (the keyboard up) the content gets what M3 gives it.
 */
internal fun sheetContentCap(contentMax: Int, window: Int, statusBar: Int, fraction: Float): Int =
    (contentMax - maxOf(((1f - fraction) * window).roundToInt(), statusBar)).coerceIn(0, contentMax)

/** Centred, its bottom [gap] above the anchor; kept under [top] (the status bar) when the anchor is that high. */
private class AboveAnchor(private val gap: Int, private val top: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(top))
}
