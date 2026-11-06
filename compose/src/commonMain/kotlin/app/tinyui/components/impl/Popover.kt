package app.tinyui.components.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.tinyui.schema.NodeScope

/**
 * A card next to the parent node, which is the anchor: Compose's `Popup` anchors to the layout it is called in, and
 * built-ins render their children without wrappers. Visibility follows `open` (faded); a tap outside is only reported,
 * since the field the user just tapped is "outside" too. Never focusable: a hint under a focused field must not take
 * the keyboard away.
 */
@Composable
internal fun PopoverComponent(scope: NodeScope) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = scope.get<Boolean>("open") == true
    // stays composed while fading out
    if (!visible.currentState && !visible.targetState) return
    val matchWidth = scope.get<Boolean>("matchAnchorWidth") == true
    var anchorWidth by remember { mutableStateOf<Int?>(null) }
    if (matchWidth) {
        // the same zero-size probe Popup itself uses to find its parent
        Layout(content = {}, modifier = Modifier.onGloballyPositioned { anchorWidth = it.parentLayoutCoordinates?.size?.width }) { _, _ -> layout(0, 0) {} }
        // not drawn until the width is known, so it never shows at the wrong width first
        if (anchorWidth == null) return
    }
    val gap = with(LocalDensity.current) { POPOVER_GAP.roundToPx() }
    val above = scope.get<String>("placement") == "above"
    val position = remember(above, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                popoverOffset(anchorBounds, windowSize, popupContentSize, above, gap, layoutDirection == LayoutDirection.Rtl)
        }
    }
    val width = anchorWidth?.takeIf { matchWidth }?.let { with(LocalDensity.current) { it.toDp() } }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(visibleState = visible, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = if (width != null) Modifier.width(width) else Modifier,
                shape = MaterialTheme.shapes.medium,
                // explicit: contentColorFor would guess from the container and can land on a muted colour
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) { scope.Children() }
        }
    }
}

private val POPOVER_GAP = 8.dp

/**
 * Where a popover of [content] size goes: on the [above] / below side of [anchor] with [gap] px between, start edges
 * aligned. The preferred side wins when it fits, else the other side when that fits, else the roomier side; the result
 * is kept inside [window] on both axes.
 */
internal fun popoverOffset(anchor: IntRect, window: IntSize, content: IntSize, above: Boolean, gap: Int, rtl: Boolean): IntOffset {
    val topY = anchor.top - gap - content.height
    val bottomY = anchor.bottom + gap
    val fitsTop = topY >= 0
    val fitsBottom = bottomY + content.height <= window.height
    val roomierTop = anchor.top > window.height - anchor.bottom
    val onTop = if (above) fitsTop || !fitsBottom && roomierTop else !fitsBottom && (fitsTop || roomierTop)
    val x = if (rtl) anchor.right - content.width else anchor.left
    return IntOffset(
        x.coerceIn(0, (window.width - content.width).coerceAtLeast(0)),
        (if (onTop) topY else bottomY).coerceIn(0, (window.height - content.height).coerceAtLeast(0)),
    )
}
