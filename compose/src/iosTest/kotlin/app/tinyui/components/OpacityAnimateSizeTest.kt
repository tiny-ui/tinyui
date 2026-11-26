package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `opacity` and `animateSize` (docs/components.md §2), rendered through the node tree like a page would. */
@OptIn(ExperimentalTestApi::class)
class OpacityAnimateSizeTest {
    private val registry = ComponentRegistry().registerBuiltins()

    /** The red channel of the centre pixel of a black box over white, at [opacity] (unset when null), and the taps it got. */
    private fun centre(opacity: Double?): Pair<Float, Int> {
        val problems = ArrayList<PatchProblem>()
        val tree = NodeTree(registry) { problems += it }
        var taps = 0
        val renderer = NodeRenderer(registry, onEvent = { _, event, _ -> if (event == "onClick") taps++ })
        var red = 0f
        runComposeUiTest {
            val prop = opacity?.let { """,["p",2,"opacity",$it]""" } ?: ""
            tree.apply(
                """[["c",1,"Box"],["p",1,"width",40],["p",1,"height",40],["p",1,"background","#FFFFFF"],""" +
                    """["c",2,"Box"],["p",2,"width",40],["p",2,"height",40],["p",2,"background","#000000"],["p",2,"onClick",true]$prop,""" +
                    """["i",0,1,0],["i",1,2,0]]""",
            )
            setContent { MaterialTheme { renderer.Render(tree.node(1)!!) } }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val node = onNode(hasClickAction())
            val pixels = node.captureToImage().toPixelMap()
            red = pixels[pixels.width / 2, pixels.height / 2].red
            node.performClick()
            waitForIdle()
        }
        return red to taps
    }

    @Test
    fun opacityFadesTheNodeAndKeepsItsTaps() {
        val (opaque, _) = centre(null)
        val (half, taps) = centre(0.5)
        val (gone, _) = centre(0.0)
        assertEquals(0f, opaque, 0.02f)
        assertEquals(0.5f, half, 0.06f)
        assertEquals(1f, gone, 0.02f)
        assertEquals(1, taps, "a faded node still takes the tap")
    }

    @Test
    fun outOfRangeIsClamped() {
        assertEquals(0f, centre(2.0).first, 0.02f)
        assertEquals(1f, centre(-1.0).first, 0.02f)
    }

    /** A column that gains a child grows to it over frames with `animateSize`, at once without it. */
    private fun heightsAfterGrowing(animate: Boolean): Pair<Int, Int> {
        val tree = NodeTree(registry) {}
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        var mid = 0
        var end = 0
        runComposeUiTest {
            val prop = if (animate) """["p",1,"animateSize",true],""" else ""
            tree.apply("""[["c",1,"Column"],$prop["c",2,"Box"],["p",2,"width",40],["p",2,"height",40],["i",0,1,0],["i",1,2,0]]""")
            setContent { MaterialTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("wrap")) { renderer.Render(tree.node(1)!!) } } }
            waitForIdle()
            mainClock.autoAdvance = false
            tree.apply("""[["c",3,"Box"],["p",3,"width",40],["p",3,"height",200],["i",1,3,1]]""")
            mainClock.advanceTimeBy(100)
            mid = onNode(androidx.compose.ui.test.hasTestTag("wrap")).fetchSemanticsNode().size.height
            mainClock.advanceTimeBy(2000)
            end = onNode(androidx.compose.ui.test.hasTestTag("wrap")).fetchSemanticsNode().size.height
            mainClock.autoAdvance = true
        }
        return mid to end
    }

    @Test
    fun animateSizeGrowsOverFrames() {
        val (mid, end) = heightsAfterGrowing(animate = true)
        val (instantMid, instantEnd) = heightsAfterGrowing(animate = false)
        assertEquals(instantEnd, end, "ends at the same size")
        assertEquals(instantEnd, instantMid, "without it the size jumps")
        assertTrue(mid < end, "with it the size is still on its way: $mid < $end")
    }
}
