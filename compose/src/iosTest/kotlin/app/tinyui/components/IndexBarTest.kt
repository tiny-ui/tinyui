package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `IndexBar` reports each label the finger reaches and shows the page's `value` when no finger is down (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class IndexBarTest {
    private val problems = ArrayList<PatchProblem>()
    private val dispatched = ArrayList<Pair<String, String>>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private fun scopeOf(node: UINode): NodeScope = object : NodeScope {
        override val node = node
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String) = node.events[event] == true
        override fun dispatch(event: String, payload: String) { dispatched += event to payload }
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

    /** A, B, C with `value` B, in a box of [maxHeight]; [props] are extra `p` ops on the bar. */
    private fun bar(props: String = "", maxHeight: Int = 400, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val options = listOf("A", "B", "C").mapIndexed { i, l -> """["c",${i + 2},"Option"],["p",${i + 2},"value","$l"],["p",${i + 2},"text","$l"],["i",1,${i + 2},$i],""" }.joinToString("")
        tree.apply("""[["c",1,"IndexBar"],["p",1,"onSelect",true],["p",1,"value","B"],$props$options["i",0,1,0]]""")
        setContent { MaterialTheme { Box(Modifier.height(maxHeight.dp)) { registry.Render("IndexBar", scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        block()
    }

    private fun ComposeUiTest.centerOf(label: String): Offset = onNodeWithText(label).getUnclippedBoundsInRoot().let {
        with(density) { Offset(((it.left + it.right) / 2).toPx(), ((it.top + it.bottom) / 2).toPx()) }
    }

    @Test
    fun valueHighlightsItsLabel() = bar {
        onNodeWithText("B").assertIsSelected()
        onNodeWithText("A").assertIsNotSelected()
        onNodeWithText("C").assertIsNotSelected()
    }

    /** The tap is reported on touch down; with the finger up the highlight is the page's `value` again. */
    @Test
    fun tapReportsTheLabelOnce() = bar {
        onNodeWithText("C").performClick()
        waitForIdle()
        assertEquals(listOf("onSelect" to """{"value":"C"}"""), dispatched)
        onNodeWithText("B").assertIsSelected()
    }

    /** Sliding reports the label under the finger each time it changes, and that label is the highlighted one. */
    @Test
    fun slideReportsTheLabelUnderTheFinger() = bar {
        val (a, b, c) = listOf("A", "B", "C").map { centerOf(it) }
        onRoot().performTouchInput { down(a); moveTo(Offset(a.x, a.y + 1f)); moveTo(b) }
        waitForIdle()
        assertEquals(listOf("A", "B"), dispatched.map { it.second.substringAfter(":\"").substringBefore("\"") })
        onNodeWithText("B").assertIsSelected()
        onRoot().performTouchInput { moveTo(c); moveTo(Offset(c.x, c.y + 1f)) }
        waitForIdle()
        onNodeWithText("C").assertIsSelected()
        onNodeWithText("B").assertIsNotSelected()
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf("A", "B", "C"), dispatched.map { it.second.substringAfter(":\"").substringBefore("\"") })
        onNodeWithText("B").assertIsSelected()
    }

    /** One move from A straight onto C reports C, not B on the way: the report is where the finger is. */
    @Test
    fun aMoveAcrossSeveralLabelsReportsWhereItLands() = bar {
        val a = centerOf("A")
        val c = centerOf("C")
        onRoot().performTouchInput { down(a); moveTo(c); up() }
        waitForIdle()
        assertEquals(listOf("A", "C"), dispatched.map { it.second.substringAfter(":\"").substringBefore("\"") })
    }

    private fun ComposeUiTest.middleOf(label: String) = onNodeWithText(label).getUnclippedBoundsInRoot().let { (it.top + it.bottom) / 2 }

    /** With room to spare each label sits in a 20dp cell. */
    @Test
    fun aLabelTakesA20dpCell() = bar {
        assertEquals(40.dp, middleOf("C") - middleOf("A"))
    }

    /** With less room than that the cells shrink evenly (the 16dp labels overlap) and each still answers for its own. */
    @Test
    fun cellsShrinkWhenThereIsLessRoom() = bar(maxHeight = 30) {
        assertEquals(20.dp, middleOf("C") - middleOf("A"))
        onRoot().performTouchInput { down(Offset(centerOf("C").x, with(density) { 28.dp.toPx() })); up() }
        waitForIdle()
        assertEquals(listOf("onSelect" to """{"value":"C"}"""), dispatched)
    }
}
