package app.tinyui.schema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.tinyui.node.Command
import app.tinyui.node.UINode

/** What a component sees of its node: typed props, registered events, children (docs/adr-003 §3.3). */
interface NodeScope {
    val node: UINode

    /** A prop declared in the schema, already converted; the schema default when JS never set it. */
    operator fun <T> get(key: String): T?

    /** Whether JS registered a handler for [event]; components attach gestures only when true. */
    fun has(event: String): Boolean

    /** K2: fire and forget, on any thread. [payload] is the flat JSON object the event schema declares. */
    fun dispatch(event: String, payload: String = "{}")

    /** A `color` prop with theme tokens resolved against the current MaterialTheme. */
    @Composable
    fun color(key: String): Color? = get<ColorValue>(key)?.resolve()

    /**
     * Layout modifier from the common props; with [clickable] the click gesture is attached when `onClick` is registered.
     * With [height] false the `height` prop is left out, for a component that sizes a part of itself by it (TextField's box).
     */
    @Composable
    fun modifier(clickable: Boolean = true, height: Boolean = true): Modifier

    @Composable
    fun Children()

    /** Renders the children with a parent-scoped modifier each (`weight` inside Row / Column); it goes outermost on the child. */
    @Composable
    fun Children(childModifier: (UINode) -> Modifier) = Children()

    /** Renders one child; for components that lay children out themselves (LazyColumn). */
    @Composable
    fun RenderChild(child: UINode)
}

/**
 * Consumes this node's commands as they arrive, oldest first; one-shot, after composition (docs/adr-004 §3.2).
 * A command arrives once its flush is applied, but before the recomposition that flush causes: the handler must read
 * the props it depends on through [NodeScope.get] when it runs, not use values captured during composition.
 */
@Composable
fun NodeScope.Commands(handler: suspend (Command) -> Unit) {
    val node = node
    // the effect outlives recompositions: without this it would keep the first handler and the props it closed over
    val current by rememberUpdatedState(handler)
    LaunchedEffect(node) {
        for (c in node.commands) current(c)
    }
}

/** What the parent wants on the child's outermost modifier; Row / Column set it per child, everyone else resets it. */
internal val LocalChildModifier = compositionLocalOf<Modifier> { Modifier }

