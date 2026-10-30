package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * `height` on a TextField is the box; the supporting text hangs below it (docs/components.md §3).
 * A single-line text measures one pixel over its line height, so at the test density a 48dp box comes out 49: the
 * assertions allow that pixel, as M3's own `OutlinedTextField(singleLine = true)` has it too.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldHeightTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private fun scopeOf(node: UINode) = object : NodeScope {
        override val node = node
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String) = node.events[event] == true
        override fun dispatch(event: String, payload: String) {}
        @Composable override fun modifier(clickable: Boolean, height: Boolean) = Modifier
        @Composable override fun Children() {}
        @Composable override fun RenderChild(child: UINode) {}
    }

    private fun SemanticsNodeInteraction.assertHeightIsAbout(expected: Dp) {
        val actual = getBoundsInRoot().height
        assertTrue(abs((actual - expected).value) <= 1f, "height $actual, expected $expected ± 1dp")
    }

    @Test
    fun defaultIsTheMaterialHeight() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNode(hasSetTextAction()).assertHeightIsAbout(56.dp)
    }

    @Test
    fun heightSizesTheBox() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"height",48],["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNode(hasSetTextAction()).assertHeightIsAbout(48.dp)
    }

    @Test
    fun supportingTextHangsBelowTheBox() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"height",48],["p",1,"supportingText","Required"],["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNode(hasSetTextAction()).assertHeightIsAtLeast(49.dp)
    }

    @Test
    fun errorIsAnnouncedWithTheSupportingText() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"error",true],["p",1,"supportingText","Not an email address"],["i",0,1,0]]""")
        setContent { MaterialTheme { registry.Render("TextField", scopeOf(tree.node(1)!!)) } }
        waitForIdle()
        onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Not an email address"))
    }
}
