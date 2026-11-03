package app.tinyui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.LocalChildModifier
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** `trailingIconSize` sizes the glyph only: the tappable slot stays M3's 48dp (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TextFieldTrailingIconTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()

    /** The page's scope without a page: props from the node, children rendered the way PageHost renders them. */
    private fun scopeOf(node: UINode): NodeScope = object : NodeScope {
        override val node = node
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String) = node.events[event] == true
        override fun dispatch(event: String, payload: String) {}
        @Composable override fun modifier(clickable: Boolean): Modifier {
            var m = LocalChildModifier.current
            (get<SizeValue>("width") as? SizeValue.Fixed)?.let { m = m.width(it.dp) }
            (get<SizeValue>("height") as? SizeValue.Fixed)?.let { m = m.height(it.dp) }
            return m
        }
        @Composable override fun Children() = Children { Modifier }
        @Composable override fun Children(childModifier: (UINode) -> Modifier) {
            for (child in node.children) CompositionLocalProvider(LocalChildModifier provides childModifier(child)) { registry.Render(child.type, scopeOf(child)) }
        }
        @Composable override fun RenderChild(child: UINode) = CompositionLocalProvider(LocalChildModifier provides Modifier) { registry.Render(child.type, scopeOf(child)) }
    }

    private fun near(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp) = abs(a.value - b.value) < 0.5f

    private fun buttonBounds(sizeProp: String) = runComposeUiTest {
        // a tree per render: the two renders are two pages
        val tree = NodeTree(registry) { problems += it }
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"trailingIcon","0 0 24 24|M0 0h24v24H0z"]$sizeProp,["p",1,"onTrailingClick",true],["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        // the IconButton: the one clickable node that is not the field itself
        bounds = onNode(hasClickAction() and !hasSetTextAction()).getUnclippedBoundsInRoot()
    }

    private var bounds: DpRect? = null

    @Test
    fun sizingTheGlyphLeavesTheButtonAlone() {
        buttonBounds("")
        val m3 = bounds!!
        buttonBounds(""",["p",1,"trailingIconSize",20]""")
        val sized = bounds!!
        assertTrue(near(m3.right - m3.left, sized.right - sized.left) && near(m3.bottom - m3.top, sized.bottom - sized.top), "same button with a 20dp glyph: $sized vs $m3")
        assertTrue(m3.right - m3.left >= 40.dp, "M3's button, not the glyph: $m3")
    }
}
