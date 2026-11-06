package app.tinyui.components.internal

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue

/**
 * What a user did to a component that a retained page must come back with (docs/app-model.md §5): typed text, code
 * digits and scroll positions are `retain`ed, so they live as long as the page; focus and an open menu are
 * `remember`ed and start over. Under the default store `retain` is `remember`.
 */
@Composable
internal fun retainScrollState(): ScrollState = retain { ScrollState(0) }

// a scrolling axis is unbounded, so there is no remaining space to share: weight is ignored there rather than collapsing the child to 0
internal fun weightOf(child: UINode): Float? = (child.props["weight"] as? Double)?.toFloat()?.takeIf { it > 0f }

/**
 * How `align="stretch"` plays out (docs/components.md §3), as in CSS: every child that takes room and sets no size of
 * its own on the cross axis fills the container's. With a fixed or `fill` cross size that is just a fill; otherwise the
 * container first sizes itself from its children (an intrinsic measurement: a Row as tall as its tallest child, a
 * Column as wide as its widest), which asks the whole subtree. A subtree with a component that cannot answer
 * ([ComponentSchema.intrinsics] false, e.g. LazyColumn) would crash the page, so the stretch is dropped and reported.
 */
internal enum class Stretch { None, Fill, Intrinsic }

@Composable
internal fun ComponentRegistry.stretchOf(scope: NodeScope, cross: String): Stretch {
    if (scope.get<String>("align") != "stretch") return Stretch.None
    val own = scope.get<SizeValue>(cross)
    if (own is SizeValue.Fixed || own == SizeValue.Fill) return Stretch.Fill
    // walked in composition, not remembered: reading every child list subscribes this container to changes at any depth
    val blocker = firstWithoutIntrinsics(scope.node) ?: return Stretch.Intrinsic
    LaunchedEffect(blocker.id) {
        scope.reportProblem("align=\"stretch\" sizes this ${scope.node.type} from its children, which ${blocker.type} (node ${blocker.id}) cannot answer; give the ${scope.node.type} a fixed $cross or take the ${blocker.type} out. Not stretched.")
    }
    return Stretch.None
}

/**
 * Only nodes that take room are measured with their parent; a popup or a sheet lives in a window of its own. A node
 * with both width and height fixed answers from those (Compose's size modifier never asks the content), so nothing
 * under it is ever asked: a lazy list in a fixed box is fine. One fixed axis is not enough — a nested Row asks its
 * children's intrinsic width to share out the space even when the Row is only being asked for its height.
 */
private fun ComponentRegistry.firstWithoutIntrinsics(node: UINode): UINode? {
    for (child in node.children) {
        val schema = schema(child.type) ?: continue
        if (!schema.layout) continue
        if (child.props["width"] is SizeValue.Fixed && child.props["height"] is SizeValue.Fixed) continue
        if (!schema.intrinsics) return child
        firstWithoutIntrinsics(child)?.let { return it }
    }
    return null
}

/** A child that takes room and has no size of its own on [cross]; one that set it, `wrap` included, keeps it. */
internal fun ComponentRegistry.stretches(child: UINode, cross: String): Boolean =
    schema(child.type)?.layout != false && child.props[cross] == null

/**
 * `gap` as a leading inset on every child that takes room, except the first of them. A child whose schema has no layout
 * (a popup, a sheet, an `Option`, a `BackHandler`) takes no room — a popup still sits in the parent's composition,
 * Compose's `Popup` anchors through a zero-size node there — so it gets no inset and does not count as first;
 * `Arrangement.spacedBy` would space that node too, and the parent would grow by one gap while the popup is open and
 * shrink back when it closes. Null when there is no gap or the child takes no room.
 */
internal fun ComponentRegistry.gapBefore(scope: NodeScope): (UINode) -> Dp? {
    val gap = scope.get<Dp>("gap")?.takeIf { it > 0.dp } ?: return { null }
    val takesRoom = { child: UINode -> schema(child.type)?.layout != false }
    val first = scope.node.children.firstOrNull(takesRoom)?.id
    return { child -> gap.takeIf { takesRoom(child) && child.id != first } }
}
