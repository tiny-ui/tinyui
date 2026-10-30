package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import app.tinyui.node.NodeTree
import app.tinyui.node.PatchProblem
import app.tinyui.node.UINode
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope
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
        @Composable override fun modifier(clickable: Boolean, height: Boolean) = Modifier
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
