package app.tinyui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The `invisible` layout prop (docs/components.md §2): CSS `visibility: hidden`, Android `View.INVISIBLE`. The node keeps
 * its room but is not drawn, takes no touch (its subtree included: every pointer event is consumed in the initial pass,
 * before any child's gesture sees it) and is not read (the subtree's semantics are cleared). Compose has no such
 * modifier of its own; this is the three pieces it is made of elsewhere.
 */
internal fun Modifier.invisible(): Modifier = this
    .alpha(0f)
    .clearAndSetSemantics {}
    .pointerInput(Unit) {
        // one gesture per pass through the block: consume until every pointer is up, then wait for the next
        awaitEachGesture {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
