package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `pressEffect="none"` on a clickable container draws nothing while pressed (docs/components.md §2); unset, M3's ripple
 * does. Measured by holding a press and comparing the container's pixels with how they were before it.
 */
@OptIn(ExperimentalTestApi::class)
class PressEffectTest {
    /** How many pixels a held press changes, and how many taps the container reported. */
    private fun press(props: String): Pair<Int, Int> {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        var taps = 0
        val renderer = NodeRenderer(registry, onEvent = { _, event, _ -> if (event == "onClick") taps++ })
        var changed = 0
        runComposeUiTest {
            tree.apply(
                """[["c",1,"Column"],["p",1,"width",200],["p",1,"height",80],["p",1,"background","#FFFFFF"],["p",1,"role","tab"],["p",1,"selected",false]$props,""" +
                    """["p",1,"onClick",true],["c",2,"Text"],["p",2,"text","Tab"],["i",0,1,0],["i",1,2,0]]""",
            )
            setContent { MaterialTheme { renderer.Render(tree.node(1)!!) } }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val node = onNode(hasClickAction())
            val before = node.captureToImage().toPixelMap()
            mainClock.autoAdvance = false
            node.performTouchInput { down(center) }
            // let the ripple grow
            mainClock.advanceTimeBy(300)
            val after = node.captureToImage().toPixelMap()
            for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != after[x, y]) changed++
            node.performTouchInput { up() }
            mainClock.autoAdvance = true
            waitForIdle()
        }
        return changed to taps
    }

    @Test
    fun m3sRippleByDefault() {
        val (changed, taps) = press("")
        assertTrue(changed > 100, "a ripple while pressed: $changed pixels")
        assertEquals(1, taps)
    }

    @Test
    fun noneDrawsNothingButStillTaps() {
        val (changed, taps) = press(""",["p",1,"pressEffect","none"]""")
        assertEquals(0, changed, "nothing drawn while pressed")
        assertEquals(1, taps, "the tap still reaches the page")
    }
}
