package app.tinyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders nodes through [registry], and is what gives each component its [NodeScope]. What a component raises leaves through
 * the two functions: [onEvent] takes an event of a node (K2), [onProblem] a page mistake the component worked around.
 */
internal class NodeRenderer(
    private val registry: ComponentRegistry,
    private val onEvent: (nodeId: Int, event: String, payload: String) -> Unit,
    private val onProblem: (node: UINode, reason: String) -> Unit = { _, _ -> },
) {
    @Composable
    fun Render(node: UINode) = registry.Render(node.type, Scope(node))

    private inner class Scope(override val node: UINode) : NodeScope {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String): Boolean = node.events[event] == true
        override fun dispatch(event: String, payload: String) = onEvent(node.id, event, payload)

        /** docs/components.md §2: parent-scoped (weight) → size → clip → background → border → gesture → padding. */
        @Composable
        override fun modifier(clickable: Boolean): Modifier {
            var m: Modifier = LocalChildModifier.current
            when (val w = get<SizeValue>("width")) { is SizeValue.Fixed -> m = m.width(w.dp); SizeValue.Fill -> m = m.fillMaxWidth(); SizeValue.Wrap -> m = m.wrapContentWidth(); null -> {} }
            when (val h = get<SizeValue>("height")) { is SizeValue.Fixed -> m = m.height(h.dp); SizeValue.Fill -> m = m.fillMaxHeight(); SizeValue.Wrap -> m = m.wrapContentHeight(); null -> {} }
            // outside clip / background / border so those vanish too; the gesture below is skipped as well
            val invisible = get<Boolean>("invisible") == true
            if (invisible) m = m.invisible()
            val shape = get<Dp>("cornerRadius")?.let { RoundedCornerShape(it) } ?: RectangleShape
            if (shape != RectangleShape) m = m.clip(shape)
            color("background")?.let { m = m.background(it) }
            // 0.dp is Compose's hairline (one pixel), not "no border"
            get<Dp>("borderWidth")?.takeIf { it > 0.dp }?.let { m = m.border(it, color("borderColor") ?: MaterialTheme.colorScheme.outline, shape) }
            if (clickable && !invisible && has("onClick")) {
                val role = roleOf(get<String>("role"))
                val selected = get<Boolean>("selected")
                m = if (selected != null) m.selectable(selected, role = role) { dispatch("onClick") } else m.clickable(role = role) { dispatch("onClick") }
            }
            val all = get<Dp>("padding")
            val h = get<Dp>("paddingHorizontal") ?: all
            val v = get<Dp>("paddingVertical") ?: all
            if (h != null || v != null) m = m.padding(start = h ?: 0.dp, top = v ?: 0.dp, end = h ?: 0.dp, bottom = v ?: 0.dp)
            return m
        }

        @Composable
        override fun Children() = Children { Modifier }

        @Composable
        override fun Children(childModifier: (UINode) -> Modifier) {
            for (child in node.children) key(child.id) {
                CompositionLocalProvider(LocalChildModifier provides childModifier(child)) { Render(child) }
            }
        }

        @Composable
        override fun RenderChild(child: UINode) = CompositionLocalProvider(LocalChildModifier provides Modifier) { Render(child) }

        override fun reportProblem(reason: String) {
            onProblem(node, reason)
        }
    }
}

private fun roleOf(name: String?): Role? = when (name) {
    "button" -> Role.Button
    "checkbox" -> Role.Checkbox
    "switch" -> Role.Switch
    "radio" -> Role.RadioButton
    "tab" -> Role.Tab
    else -> null
}
