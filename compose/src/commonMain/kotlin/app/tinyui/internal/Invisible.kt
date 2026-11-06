package app.tinyui.internal

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The `invisible` layout prop (docs/components.md §2): CSS `visibility: hidden`, Android `View.INVISIBLE`. The node keeps
 * its room but is not drawn, not read, and is transparent to touch: neither it nor anything under it is hit, the touch
 * goes on to whatever is around or behind it (the button a placeholder label sits in, say). Compose has no such modifier;
 * clipping to an empty shape is what makes hit testing skip the whole subtree, since a point is only hit inside the clip.
 */
internal fun Modifier.invisible(): Modifier = this
    .alpha(0f)
    .clip(Nothing)
    .clearAndSetSemantics {}

private val Nothing = GenericShape { _, _ -> }
