package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A command arrives after its flush is applied but before the recomposition it causes (docs/components.md §4). */
@OptIn(ExperimentalTestApi::class)
class CommandPropsTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }
    private val events = ArrayList<String>()

    /** The page's scope without a page: props and events from the node, dispatch recorded. */
    private fun scopeOf(node: UINode) = object : NodeScope {
        override val node = node
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String): T? = (node.props[key] ?: registry.schema(node.type)?.prop(key)?.default) as T?
        override fun has(event: String) = node.events[event] == true
        override fun dispatch(event: String, payload: String) { events += "$event $payload" }
        @Composable override fun modifier(clickable: Boolean) = Modifier
        @Composable override fun Children() {}
        @Composable override fun RenderChild(child: UINode) {}
    }

    @Test
    fun textFieldSetTextClipsToTheLimitOfItsOwnFlush() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"maxLength",4],["p",1,"onChange",true],["i",0,1,0]]""")
        val scope = scopeOf(tree.node(1)!!)
        setContent { MaterialTheme { registry.Render("TextField", scope) } }
        waitForIdle()
        // one flush raises the limit and sets text longer than the old one
        tree.apply("""[["p",1,"maxLength",10],["x",1,"setText",{"text":"123456"}]]""")
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        assertEquals(1, events.size, events.toString())
        assertTrue("123456" in events.single(), "clipped with the limit of an earlier composition: ${events.single()}")
    }

    @Test
    fun textFieldReportsEachChangeOfTheTextAndNotTheSameTextAgain() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"onChange",true],["i",0,1,0]]""")
        val scope = scopeOf(tree.node(1)!!)
        setContent { MaterialTheme { registry.Render("TextField", scope) } }
        waitForIdle()
        assertTrue(events.isEmpty(), "mounting is not a change: $events")
        tree.apply("""[["x",1,"setText",{"text":"abc"}]]""")
        waitForIdle()
        tree.apply("""[["x",1,"setText",{"text":"abc"}]]""")
        waitForIdle()
        assertEquals(1, events.size, "the same text again is not a change: $events")
        // a lowered limit cuts what is there, and that is a change like any other
        tree.apply("""[["p",1,"maxLength",2]]""")
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        assertEquals(2, events.size, events.toString())
        assertTrue("\"ab\"" in events.last(), events.last())
    }

    @Test
    fun textFieldReportsAnEditBeforeTheCommitItIsFollowedBy() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"onChange",true],["p",1,"onCommit",true],["x",1,"focus",{}],["i",0,1,0]]""")
        val scope = scopeOf(tree.node(1)!!)
        setContent { MaterialTheme { registry.Render("TextField", scope) } }
        waitForIdle()
        // typed, then the focus goes in the same breath: the page must hear the text before it hears the commit
        onNode(hasSetTextAction()).performTextInput("abc")
        tree.apply("""[["x",1,"blur",{}]]""")
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val change = events.indexOfFirst { it.startsWith("onChange") }
        val commit = events.indexOfFirst { it.startsWith("onCommit") }
        assertTrue(change in 0 until commit, "onChange before onCommit: $events")
        assertEquals(1, events.count { it.startsWith("onChange") }, "reported once: $events")
        assertTrue("abc" in events[commit], events[commit])
    }

    @Test
    fun codeFieldSetTextTakesTheLengthOfItsOwnFlush() = runComposeUiTest {
        tree.apply("""[["c",1,"CodeField"],["p",1,"length",4],["p",1,"onChange",true],["i",0,1,0]]""")
        val scope = scopeOf(tree.node(1)!!)
        setContent { MaterialTheme { registry.Render("CodeField", scope) } }
        waitForIdle()
        tree.apply("""[["p",1,"length",6],["x",1,"setText",{"text":"123456"}]]""")
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        assertEquals(1, events.size, events.toString())
        assertTrue("123456" in events.single(), "took the length of an earlier composition: ${events.single()}")
    }
}
