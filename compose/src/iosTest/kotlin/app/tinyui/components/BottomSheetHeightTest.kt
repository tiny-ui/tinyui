package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host's `maxHeightFraction` on a real M3 sheet (docs/components.md §6): a sheet whose content fills stops that far
 * down the window, one sized by its content stays where it was. Measured against the same sheet without the cap, so
 * the drag handle's height does not come into it.
 */
@OptIn(ExperimentalTestApi::class)
class BottomSheetHeightTest {
    private class Measured(val contentTop: Int, val contentHeight: Int, val window: Int)

    /** An open sheet holding a Column of [height] with a text at its top; where that Column ends up in the window. */
    private fun measure(height: String, defaults: TinyUIDefaults?): Measured {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        var measured: Measured? = null
        runComposeUiTest {
            tree.apply(
                """[["c",1,"BottomSheet"],["p",1,"open",true],["c",2,"Column"],["p",2,"width","fill"],["p",2,"height",$height],""" +
                    """["c",3,"Text"],["p",3,"text","Sheet body"],["i",0,1,0],["i",1,2,0],["i",2,3,0]]""",
            )
            var window = 0
            setContent {
                window = LocalWindowInfo.current.containerSize.height
                MaterialTheme {
                    if (defaults == null) renderer.Render(tree.node(1)!!)
                    else CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { renderer.Render(tree.node(1)!!) }
                }
            }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val text = onNodeWithText("Sheet body").fetchSemanticsNode()
            // the text is the Column's first child, so its top is the Column's; the Column's bottom is the window's less the bottom inset
            val column = text.layoutInfo.parentInfo!!
            measured = Measured(text.boundsInWindow.top.roundToInt(), column.height, window)
        }
        return measured!!
    }

    private val capped = TinyUIDefaults(bottomSheet = BottomSheetStyle(maxHeightFraction = 0.9f))

    @Test
    fun aFillingSheetStopsAtTheHostsFraction() {
        val free = measure(height = "\"fill\"", defaults = null)
        val limited = measure(height = "\"fill\"", defaults = capped)
        val gap = (0.1f * limited.window).roundToInt()
        assertTrue(limited.window > 0, "a window to measure against")
        // without the cap the sheet reaches the top of the window; with it, a tenth of the window is left above
        assertTrue(abs(limited.contentTop - free.contentTop - gap) <= 1, "moved down by $gap: ${free.contentTop} -> ${limited.contentTop} in ${limited.window}")
        assertTrue(abs(free.contentHeight - limited.contentHeight - gap) <= 1, "shorter by $gap: ${free.contentHeight} -> ${limited.contentHeight}")
    }

    @Test
    fun aContentSizedSheetStaysWhereItWas() {
        val free = measure(height = "120", defaults = null)
        val limited = measure(height = "120", defaults = capped)
        assertEquals(free.contentTop, limited.contentTop)
        assertEquals(free.contentHeight, limited.contentHeight)
    }
}
