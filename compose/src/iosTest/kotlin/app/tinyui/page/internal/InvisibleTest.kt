package app.tinyui.page.internal

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

/** `invisible`: room kept, nothing drawn, transparent to touch (the subtree is not hit, what is around it is), nothing read (docs/components.md §2). */
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

    /**
     * A tap over an invisible node is not taken by it or anything under it, a clickable child included: it goes through
     * to what is around — here the clickable box the invisible one sits in. Visible, the child takes it instead. This is
     * what a placeholder label inside a button relies on: the button must still get the tap.
     */
    @Test
    fun isTransparentToTouch() {
        assertEquals(0 to 1, clicks(invisible = true))
        assertEquals(1 to 0, clicks(invisible = false))
    }

    /** (clicks the inner child got, clicks the outer box got) */
    private fun clicks(invisible: Boolean): Pair<Int, Int> {
        var inner = 0
        var outer = 0
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("wrap").size(100.dp).clickable { outer++ }) {
                    Box(Modifier.size(100.dp).then(if (invisible) Modifier.invisible() else Modifier)) {
                        Box(Modifier.size(100.dp).clickable { inner++ })
                    }
                }
            }
            onNodeWithTag("wrap").performTouchInput { click() }
            waitForIdle()
        }
        return inner to outer
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
