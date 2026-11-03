package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.tinyui.LocalTinyUIDefaults
import app.tinyui.TextFieldStyle
import app.tinyui.TinyUIDefaults
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

/**
 * A text input is M3's 56dp by default; the host makes it denser through `TinyUIDefaults.textField` (`contentPadding` +
 * `minHeight`), and the prefix block follows the field (docs/components.md §3, §6). Pages never set the height.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldHeightTest {
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

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()
    // a single line of text measures a pixel over its line height; M3's own OutlinedTextField does the same
    private fun near(a: Dp, b: Dp) = abs(a.value - b.value) <= 1f

    private val compact = TinyUIDefaults(textField = TextFieldStyle(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), minHeight = 48.dp))

    @Test
    fun m3MinimumByDefault() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["i",0,1,0]]""")
        setContent { MaterialTheme { Box(Modifier.testTag("field")) { registry.Render("TextField", scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        assertTrue(near(field.bottom - field.top, 56.dp), "M3's 56dp: $field")
    }

    @Test
    fun theHostMakesItDenser() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["i",0,1,0]]""")
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        assertTrue(near(field.bottom - field.top, 48.dp), "the host's 48dp: $field")
    }

    @Test
    fun supportingTextHangsBelowTheDenserField() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["p",1,"supportingText","Required"],["i",0,1,0]]""")
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        val supporting = onNodeWithText("Required", useUnmergedTree = true).bounds()
        // the box above the supporting text is the 48dp; the node is taller by the text and its gap
        assertTrue(field.bottom - field.top > 48.dp, "taller than the box with supporting text: $field")
        assertTrue(supporting.top - field.top >= 48.dp, "supporting text below the 48dp box: $supporting in $field")
    }

    @Test
    fun thePrefixBlockFollowsTheField() = runComposeUiTest {
        tree.apply(
            """[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["p",1,"prefixText","+86"],""" +
                """["p",1,"prefixBackground","surfaceVariant"],["p",1,"onPrefixClick",true],["i",0,1,0]]""",
        )
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        // the block is the tappable node, so the prefix text merges into it
        val block = onNodeWithText("+86").bounds()
        // 40 = the host's 48dp less 4dp above and below
        assertTrue(near(block.bottom - block.top, 40.dp), "block 40dp tall in a 48dp field: $block in $field")
        assertTrue(abs((block.top - field.top).value - (field.bottom - block.bottom).value) <= 1f, "block centred: $block in $field")
    }
}
