package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.ComponentSchema
import app.tinyui.schema.LocalChildModifier
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A LazyColumn with groups: sticky `SectionHeader` rows, and an `IndexBar` child that is not a row but a strip bound
 * to the list (docs/components.md §3).
 */
@OptIn(ExperimentalTestApi::class)
class LazyColumnSectionsTest {
    private val problems = ArrayList<PatchProblem>()
    private val reported = ArrayList<String>()
    private var probesCreated = 0
    private val registry = ComponentRegistry().registerBuiltins().apply {
        // a row that counts how many times it was built from scratch
        register(ComponentSchema("test.Probe", emptyMap(), emptyMap(), emptyMap(), children = false, layout = true)) { scope ->
            remember { ++probesCreated }
            Box(scope.modifier())
        }
    }
    private val tree = NodeTree(registry) { problems += it }

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

    private val BAR = 900

    /** The IndexBar node with these labels as Options (none: the bar takes its labels from the headers). */
    private fun bar(vararg labels: String) = """["c",$BAR,"IndexBar"],["p",$BAR,"label","Index"],""" +
        labels.mapIndexed { i, l -> """["c",${901 + i},"Option"],["p",${901 + i},"value","$l"],["p",${901 + i},"text","$l"],["i",$BAR,${901 + i},$i],""" }.joinToString("")

    /**
     * A 360 × [height] list of three groups, A, B and D: a 30dp header "Group X" and six 40dp rows "x1"…"x6" each, so
     * rows 0–6 are A, 7–13 B, 14–20 D. [bar] is the ops of an IndexBar, inserted as the list's first child.
     */
    private fun list(bar: String = "", height: Int = 200, check: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val ops = StringBuilder("""[["c",1,"LazyColumn"],["p",1,"width",360],["p",1,"height",$height],""")
        var at = 0
        if (bar.isNotEmpty()) ops.append(bar).append("""["i",1,$BAR,${at++}],""")
        listOf("A", "B", "D").forEachIndexed { g, name ->
            val header = 100 * (g + 1)
            ops.append("""["c",$header,"SectionHeader"],["p",$header,"index","$name"],["p",$header,"height",30],""")
            ops.append("""["c",${header + 1},"Text"],["p",${header + 1},"text","Group $name"],["i",$header,${header + 1},0],["i",1,$header,${at++}],""")
            for (r in 1..6) {
                val row = header + 10 + r
                ops.append("""["c",$row,"Text"],["p",$row,"text","${name.lowercase()}$r"],["p",$row,"height",40],["i",1,$row,${at++}],""")
            }
        }
        ops.append("""["i",0,1,0]]""")
        tree.apply(ops.toString())
        setContent { MaterialTheme { Box { registry.Render("LazyColumn", scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        check()
        assertTrue(problems.isEmpty(), problems.toString())
    }

    private fun ComposeUiTest.jumpTo(index: Int) {
        tree.apply("""[["x",1,"jumpTo",{"index":$index}]]""")
        waitForIdle()
    }

    private fun ComposeUiTest.top(text: String) = onNodeWithText(text).getUnclippedBoundsInRoot().top
    private fun ComposeUiTest.bottom(text: String) = onNodeWithText(text).getUnclippedBoundsInRoot().bottom
    private fun ComposeUiTest.strip() = onNode(hasContentDescription("Index"))
    private fun at(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)

    /** Touches the strip in the cell of label [index] of [count]. */
    private fun ComposeUiTest.touch(index: Int, count: Int) {
        strip().performTouchInput { down(Offset(width / 2f, height * (index + 0.5f) / count)); up() }
        waitForIdle()
    }

    /** A header stays at the top while its rows scroll under it, and a row the page jumps to is not left under it. */
    @Test
    fun aHeaderIsPinnedAndDoesNotCoverTheRowJumpedTo() = list {
        jumpTo(4)
        assertEquals(0.dp, top("Group A"))
        onNodeWithText("a4").assertIsDisplayed()
        assertTrue(top("a4") >= bottom("Group A"), "a4 at ${top("a4")} under the header ending at ${bottom("Group A")}")
        // the next group's header takes over
        jumpTo(9)
        assertEquals(0.dp, top("Group B"))
        onNodeWithText("Group A").assertDoesNotExist()
        assertTrue(top("b2") >= bottom("Group B"))
    }

    /** `sticky` off: the header scrolls away like any row. */
    @Test
    fun aHeaderThatIsNotStickyScrollsAway() = list {
        tree.apply("""[["p",100,"sticky",false]]""")
        waitForIdle()
        jumpTo(4)
        onNodeWithText("Group A").assertDoesNotExist()
        assertEquals(0.dp, top("a4"))
    }

    /** The bar is the list's child but not one of its rows: indexes count rows, and it is there wherever the list is. */
    @Test
    fun theIndexBarIsNotARow() = list(bar("A", "B", "C", "D")) {
        strip().assertIsDisplayed()
        jumpTo(7)
        assertEquals(0.dp, top("Group B"))
        jumpTo(20)
        onNodeWithText("d6").assertIsDisplayed()
        strip().assertIsDisplayed()
    }

    /** A touch on a label takes the list to the header with that `index`, and the strip then says that group. */
    @Test
    fun aTouchOnALabelTakesTheListToItsGroup() = list(bar("A", "B", "C", "D")) {
        strip().assert(at("A"))
        touch(3, 4)
        assertEquals(0.dp, top("Group D"))
        strip().assert(at("D"))
        touch(0, 4)
        assertEquals(0.dp, top("Group A"))
    }

    /** A label without a group of its own goes to the next label down that has one. */
    @Test
    fun aLabelWithoutAGroupGoesToTheNextOne() = list(bar("A", "B", "C", "D")) {
        touch(2, 4)
        assertEquals(0.dp, top("Group D"))
    }

    /** The highlight is the group the list is at, however it got there. */
    @Test
    fun theHighlightFollowsTheList() = list(bar("A", "B", "C", "D")) {
        jumpTo(9)
        strip().assert(at("B"))
        jumpTo(2)
        strip().assert(at("A"))
    }

    /** Without Options the labels are the headers' own, in order. */
    @Test
    fun withoutOptionsTheLabelsAreTheHeaders() = list(bar()) {
        strip().assert(at("A"))
        touch(2, 3)
        assertEquals(0.dp, top("Group D"))
        strip().assert(at("D"))
    }

    /** Labels that would be squeezed into less than their own height are not shown at all. */
    @Test
    fun noStripWhenTheLabelsDoNotFit() = list(bar("A", "B", "C", "D"), height = 50) {
        val bounds = strip().getUnclippedBoundsInRoot()
        assertEquals(0.dp, bounds.bottom - bounds.top)
    }

    /** Hiding and showing the bar (a search field in use) does not rebuild the rows. */
    @Test
    fun hidingAndShowingTheBarKeepsTheRows() = list(bar("A", "B", "C", "D")) {
        // under the first header, where it is on screen (a row put above the first visible one would not be composed)
        tree.apply("""[["c",500,"test.Probe"],["p",500,"height",40],["i",1,500,2]]""")
        waitForIdle()
        assertEquals(1, probesCreated)
        tree.apply("""[["r",$BAR]]""")
        waitForIdle()
        strip().assertDoesNotExist()
        // a new bar: node ids are not reused
        tree.apply("""[["c",950,"IndexBar"],["p",950,"label","Index"],["i",1,950,0]]""")
        waitForIdle()
        strip().assertIsDisplayed()
        assertEquals(1, probesCreated, "the row was built again")
    }

    /** Anywhere but in a LazyColumn it has no list to index: nothing drawn, one problem. */
    @Test
    fun anIndexBarOutsideAListIsReported() = runComposeUiTest {
        tree.apply("""[["c",1,"Column"],${bar("A", "B")}["i",1,$BAR,0],["i",0,1,0]]""")
        setContent { MaterialTheme { Box { registry.Render("Column", scopeOf(tree.node(1)!!)) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        strip().assertDoesNotExist()
        assertEquals(1, reported.size, reported.toString())
        assertTrue("IndexBar" in reported[0], reported[0])
    }
}
