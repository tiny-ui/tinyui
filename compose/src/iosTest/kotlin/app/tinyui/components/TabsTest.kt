package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `Tabs` shows the page's `value` and only reports a tap; an unmatched value selects nothing (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TabsTest {
    private val problems = ArrayList<PatchProblem>()
    private val dispatched = ArrayList<Pair<String, String>>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private val renderer = NodeRenderer(registry, onEvent = { _, event, payload -> dispatched += event to payload })

    /** Two tabs, Email and Phone, with `value` set to [value] (or unset when null) and an `onSelect` handler. */
    private fun tabs(value: String?, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
        val valueProp = if (value == null) "" else """,["p",1,"value","$value"]"""
        tree.apply(
            """[["c",1,"Tabs"],["p",1,"onSelect",true]$valueProp,["c",2,"Option"],["p",2,"value","email"],["p",2,"text","Email"],""" +
                """["c",3,"Option"],["p",3,"value","phone"],["p",3,"text","Phone"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        )
        setContent { MaterialTheme { Box(Modifier.width(320.dp)) { renderer.Render(tree.node(1)!!) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        block()
    }

    @Test
    fun valueSelectsItsTab() = tabs("phone") {
        onNodeWithText("Phone").assertIsSelected()
        onNodeWithText("Email").assertIsNotSelected()
    }

    @Test
    fun unmatchedValueSelectsNoTab() = tabs("code") {
        onNodeWithText("Phone").assertIsNotSelected()
        onNodeWithText("Email").assertIsNotSelected()
    }

    @Test
    fun unsetValueSelectsNoTab() = tabs(null) {
        onNodeWithText("Phone").assertIsNotSelected()
        onNodeWithText("Email").assertIsNotSelected()
    }

    /** The tap is reported, not applied: the selection stays where `value` says until the page writes it back. */
    @Test
    fun tapReportsTheValueAndLeavesTheSelection() = tabs("phone") {
        onNodeWithText("Email").performClick()
        waitForIdle()
        assertEquals(listOf("onSelect" to """{"value":"email"}"""), dispatched)
        onNodeWithText("Phone").assertIsSelected()
        onNodeWithText("Email").assertIsNotSelected()
    }
}
