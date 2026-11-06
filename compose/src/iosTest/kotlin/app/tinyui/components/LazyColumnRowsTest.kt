package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A lazy list takes its row count once per measure pass and then asks for each row by index. Patches are applied from
 * the engine thread, so one can land in the middle of that pass: the list must keep measuring the rows it started with.
 */
@OptIn(ExperimentalTestApi::class)
class LazyColumnRowsTest {
    private val problems = ArrayList<PatchProblem>()
    private val dispatched = ArrayList<String>()
    private var onProbeMeasured: () -> Unit = {}
    private val registry = ComponentRegistry().registerBuiltins().apply {
        // a row that tells the test when the list is measuring it
        register(ComponentSchema("test.Probe", emptyMap(), emptyMap(), emptyMap(), children = false, layout = true)) {
            Box(Modifier.fillMaxWidth().height(20.dp).layout { measurable, constraints ->
                onProbeMeasured()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            })
        }
    }
    private val tree = NodeTree(registry) { problems += it }

    private fun scopeOf(node: UINode): NodeScope = object : NodeScope {
        override val node = node
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String) = node.events[event] == true
        override fun dispatch(event: String, payload: String) { dispatched += event }
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

    /** Ten rows, the first one a probe; while the list measures it, a patch removes all but the first two. */
    @Test
    fun rowsRemovedWhileMeasuringDoNotCrashTheList() = runComposeUiTest {
        val rows = (3..11).joinToString("") { id -> """["c",$id,"Text"],["p",$id,"text","row $id"],["i",1,$id,${id - 2}],""" }
        tree.apply("""[["c",1,"LazyColumn"],["p",1,"width",360],["p",1,"height",400],["c",2,"test.Probe"],["i",1,2,0],$rows["i",0,1,0]]""")
        var removed = false
        onProbeMeasured = {
            if (!removed) {
                removed = true
                tree.apply((4..11).joinToString(",", "[", "]") { id -> """["r",$id]""" })
            }
        }
        setContent { MaterialTheme { Box { registry.Render(tree.node(1)!!.type, scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(removed, "the probe row was never measured")
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithText("row 3").assertExists()
        onNodeWithText("row 4").assertDoesNotExist()
        onNodeWithText("row 11").assertDoesNotExist()
    }

    /**
     * A row appended and scrolled to in the same flush is reached: the command runs once the flush is applied, before
     * the recomposition it causes, and the list must already know the new row by then (docs/components.md §4).
     */
    @Test
    fun aRowAppendedAndScrolledToInOneFlushIsReached() = runComposeUiTest {
        val rows = (2..31).joinToString("") { id -> """["c",$id,"Text"],["p",$id,"text","row $id"],["i",1,$id,${id - 2}],""" }
        tree.apply("""[["c",1,"LazyColumn"],["p",1,"width",360],["p",1,"height",200],$rows["i",0,1,0]]""")
        setContent { MaterialTheme { Box { registry.Render(tree.node(1)!!.type, scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        tree.apply("""[["c",99,"Text"],["p",99,"text","row 99"],["i",1,99,30],["x",1,"scrollTo",{"index":30}]]""")
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithText("row 99").assertIsDisplayed()
    }

    private fun sixtyRows() = (2..61).joinToString("") { id -> """["c",$id,"Text"],["p",$id,"text","row $id"],["i",1,$id,${id - 2}],""" }

    /**
     * A command does not wait for the animation of the one before it: the later one takes over where the list is, the
     * earlier target is never reached, and commands keep coming. Frame by frame: a handler that queued the commands
     * would get to the first target before turning back.
     */
    @Test
    fun aLaterCommandTakesOverFromARunningScroll() = runComposeUiTest {
        tree.apply("""[["c",1,"LazyColumn"],["p",1,"width",360],["p",1,"height",200],["p",1,"onScrollEnd",true],${sixtyRows()}["i",0,1,0]]""")
        setContent { MaterialTheme { Box { registry.Render(tree.node(1)!!.type, scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        mainClock.autoAdvance = false
        tree.apply("""[["x",1,"scrollTo",{"index":55}]]""")
        repeat(2) { mainClock.advanceTimeByFrame() }
        tree.apply("""[["x",1,"scrollTo",{"index":0}]]""")
        // long enough for the first animation to have arrived, had it been left to finish
        repeat(180) {
            mainClock.advanceTimeByFrame()
            onNodeWithText("row 57").assertDoesNotExist()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithText("row 2").assertIsDisplayed()
        // one scroll as far as the page can tell: the hand-over in the middle is not an end
        assertEquals(listOf("onScrollEnd"), dispatched)
        // the interrupted scroll ended with a cancellation: that was its own, the list still takes commands
        tree.apply("""[["x",1,"scrollTo",{"index":40}]]""")
        waitForIdle()
        onNodeWithText("row 42").assertIsDisplayed()
    }
}
