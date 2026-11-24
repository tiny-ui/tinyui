package app.tinyui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The host's `contentPadding` on a real M3 sheet (docs/components.md §6): without a drag handle the content starts on
 * the sheet's top edge; the padding moves it down by that much and nothing else moves — the content's bottom stays,
 * with or without a height cap.
 */
@OptIn(ExperimentalTestApi::class)
class BottomSheetContentPaddingTest {
    private class Measured(val top: Int, val bottom: Int, val density: Float)

    private fun measure(style: BottomSheetStyle): Measured {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        var measured: Measured? = null
        runComposeUiTest {
            tree.apply(
                """[["c",1,"BottomSheet"],["p",1,"open",true],["c",2,"Column"],["p",2,"width","fill"],["p",2,"height","fill"],""" +
                    """["c",3,"Text"],["p",3,"text","Sheet body"],["i",0,1,0],["i",1,2,0],["i",2,3,0]]""",
            )
            setContent {
                MaterialTheme { CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(bottomSheet = style)) { renderer.Render(tree.node(1)!!) } }
            }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val text = onNodeWithText("Sheet body").fetchSemanticsNode()
            val top = text.boundsInWindow.top.roundToInt()
            // the text is the filling Column's first child: its top is the Column's
            measured = Measured(top, top + text.layoutInfo.parentInfo!!.height, density.density)
        }
        return measured!!
    }

    private fun movesTheContentDownOnly(cap: Float?) {
        val bare = measure(BottomSheetStyle(dragHandle = false, maxHeightFraction = cap))
        val padded = measure(BottomSheetStyle(dragHandle = false, maxHeightFraction = cap, contentPadding = PaddingValues(top = 16.dp)))
        val gap = (16 * padded.density).roundToInt()
        assertTrue(abs(padded.top - bare.top - gap) <= 1, "cap $cap: down by 16dp ($gap px): ${bare.top} -> ${padded.top}")
        assertTrue(abs(padded.bottom - bare.bottom) <= 1, "cap $cap: the bottom stays: ${bare.bottom} -> ${padded.bottom}")
    }

    @Test
    fun theHostsPaddingMovesTheContentDownOnly() = movesTheContentDownOnly(cap = null)

    @Test
    fun andTheSameUnderAHeightCap() = movesTheContentDownOnly(cap = 0.9f)
}
