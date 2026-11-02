package app.tinyui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.LocalChildModifier
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue
import kotlin.test.Test
import kotlin.test.assertTrue

/** `backIconSize` sizes the back icon only; unset, the icon keeps its own 24dp; the button stays a 48dp target (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TopAppBarBackIconTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

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

    private fun assertBackIcon(sizeProp: String, expected: Int) = runComposeUiTest {
        tree.apply("""[["c",1,"TopAppBar"],["p",1,"height",48],["p",1,"backIcon","0 0 24 24|M0 0h24v24H0z"],["p",1,"backLabel","Back"]$sizeProp,["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TopAppBar", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        // the icon's description merges into the button: unmerged it is the icon, merged it is the button
        onNodeWithContentDescription("Back", useUnmergedTree = true).assertWidthIsEqualTo(expected.dp).assertHeightIsEqualTo(expected.dp)
        onNodeWithContentDescription("Back").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun backIconSizeSizesTheIcon() = assertBackIcon(""",["p",1,"backIconSize",32]""", 32)

    @Test
    fun unsetKeepsTheIconsOwnSize() = assertBackIcon("", 24)
}
