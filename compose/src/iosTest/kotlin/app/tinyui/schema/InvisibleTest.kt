package app.tinyui.schema

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** `invisible`: room kept, nothing drawn, no touch for the subtree, nothing read (docs/components.md §2). */
@OptIn(ExperimentalTestApi::class)
class InvisibleTest {
    @Test
    fun keepsItsRoom() = runComposeUiTest {
        setContent {
            Row(Modifier.testTag("row")) {
                Box(Modifier.size(50.dp).invisible())
                Box(Modifier.size(30.dp))
            }
        }
        onNodeWithTag("row").assertWidthIsEqualTo(80.dp)
    }

    @Test
    fun isNotRead() = runComposeUiTest {
        setContent { Box(Modifier.invisible()) { Text("Ghost") } }
        onNodeWithText("Ghost").assertDoesNotExist()
    }

    /** A tap on an invisible node reaches nothing under it, a clickable child included; the same tree takes it when visible. */
    @Test
    fun takesNoTouchForItsSubtree() {
        assertEquals(0, clicksThrough(invisible = true))
        assertEquals(1, clicksThrough(invisible = false))
    }

    private fun clicksThrough(invisible: Boolean): Int {
        var clicks = 0
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("wrap").size(100.dp)) {
                    Box(Modifier.size(100.dp).then(if (invisible) Modifier.invisible() else Modifier)) {
                        Box(Modifier.size(100.dp).clickable { clicks++ })
                    }
                }
            }
            onNodeWithTag("wrap").performTouchInput { click() }
            waitForIdle()
        }
        return clicks
    }

    /** The use it was added for: a label that changes keeps the width of its widest form by stacking it, invisible. */
    @Test
    fun reservesTheWidestLabel() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Row {
                    Box(Modifier.testTag("stack")) {
                        Text("Resend", Modifier.invisible())
                        Text("9s")
                    }
                    Text("Resend", Modifier.testTag("ref"))
                }
            }
        }
        val ref = onNodeWithTag("ref").fetchSemanticsNode().size.width
        assertEquals(ref, onNodeWithTag("stack").fetchSemanticsNode().size.width)
    }
}
