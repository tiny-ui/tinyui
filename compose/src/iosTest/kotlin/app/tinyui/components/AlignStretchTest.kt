package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `align="stretch"` on Row / Column, as CSS: unsized children take the cross size; a lazy list is reported, not a crash (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class AlignStretchTest {
    private val problems = ArrayList<PatchProblem>()
    private val reported = ArrayList<String>()
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
        override fun reportProblem(reason: String) { reported += reason }
    }

    private fun near(a: Dp, b: Dp) = abs(a.value - b.value) < 0.5f
    private fun ComposeUiTest.height(text: String): Dp = onNodeWithText(text).getUnclippedBoundsInRoot().let { it.bottom - it.top }
    private fun ComposeUiTest.width(text: String): Dp = onNodeWithText(text).getUnclippedBoundsInRoot().let { it.right - it.left }

    private fun render(ops: String, check: ComposeUiTest.() -> Unit) = runComposeUiTest {
        tree.apply(ops)
        setContent { MaterialTheme { Box { registry.Render(tree.node(1)!!.type, scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        check()
    }

    /** A field and a button side by side: the button takes the field's height, the row needs no height of its own. */
    @Test
    fun aButtonTakesTheFieldsHeight() {
        render(
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],["p",1,"gap",8],""" +
                """["c",2,"TextField"],["p",2,"weight",1],["c",3,"Button"],["p",3,"text","Resend"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) {
            val field = onNode(hasSetTextAction()).getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue(near(height("Resend"), field), "button ${height("Resend")}, field $field")
        }
        assertTrue(reported.isEmpty(), reported.toString())
    }

    /** A child that sets its own height keeps it, as in CSS. */
    @Test
    fun aSizedChildKeepsItsHeight() {
        render(
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],""" +
                """["c",2,"TextField"],["p",2,"weight",1],["c",3,"Button"],["p",3,"text","Resend"],["p",3,"height",40],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) { assertTrue(near(height("Resend"), 40.dp), "button ${height("Resend")}") }
    }

    /**
     * A lazy list with no size of its own cannot be asked for intrinsics (asking throws and would take the page down):
     * no stretch, one problem, no crash. This layout does throw without the guard; a lazy list with a fixed height
     * would not, since the size modifier answers for it.
     */
    @Test
    fun aLazyListIsReportedNotACrash() {
        render(
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],""" +
                """["c",2,"LazyColumn"],["p",2,"weight",1],["c",3,"Button"],["p",3,"text","Resend"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) { assertTrue(height("Resend") < 56.dp, "button not stretched: ${height("Resend")}") }
        assertEquals(1, reported.size, reported.toString())
        assertTrue("LazyColumn" in reported[0], reported[0])
    }

    /** Under a box with both sizes fixed nothing is asked, so a lazy list there blocks nothing: stretched, no problem. */
    @Test
    fun aLazyListInAFixedBoxBlocksNothing() {
        render(
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],""" +
                """["c",2,"Box"],["p",2,"width",120],["p",2,"height",200],["c",4,"LazyColumn"],["i",2,4,0],""" +
                """["c",3,"Button"],["p",3,"text","Resend"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) { assertTrue(near(height("Resend"), 200.dp), "button ${height("Resend")}") }
        assertTrue(reported.isEmpty(), reported.toString())
    }

    /** With a fixed height there is nothing to measure: the stretch is a plain fill, lazy list or not. */
    @Test
    fun aFixedHeightNeedsNoIntrinsics() {
        render(
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"height",72],["p",1,"align","stretch"],""" +
                """["c",2,"LazyColumn"],["p",2,"weight",1],["c",3,"Button"],["p",3,"text","Resend"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) { assertTrue(near(height("Resend"), 72.dp), "button ${height("Resend")}") }
        assertTrue(reported.isEmpty(), reported.toString())
    }

    /** In a Column the buttons take the widest one's width. */
    @Test
    fun columnButtonsTakeTheWidestWidth() {
        render(
            """[["c",1,"Column"],["p",1,"align","stretch"],""" +
                """["c",2,"Button"],["p",2,"text","A"],["c",3,"Button"],["p",3,"text","A much longer label"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) { assertTrue(near(width("A"), width("A much longer label")), "${width("A")} vs ${width("A much longer label")}") }
    }
}
