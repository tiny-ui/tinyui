package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The host's `dragHandle = false` on a real M3 sheet (docs/components.md §6): the handle goes, so content that fills the
 * sheet starts higher, where the handle was. (A sheet sized by its content sits on the bottom: its content does not move.)
 */
@OptIn(ExperimentalTestApi::class)
class BottomSheetDragHandleTest {
    private fun contentTop(defaults: TinyUIDefaults?): Int {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        var top = 0
        runComposeUiTest {
            tree.apply(
                """[["c",1,"BottomSheet"],["p",1,"open",true],["c",2,"Column"],["p",2,"width","fill"],["p",2,"height","fill"],""" +
                    """["c",3,"Text"],["p",3,"text","Sheet body"],["i",0,1,0],["i",1,2,0],["i",2,3,0]]""",
            )
            setContent {
                MaterialTheme {
                    if (defaults == null) renderer.Render(tree.node(1)!!)
                    else CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { renderer.Render(tree.node(1)!!) }
                }
            }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            top = onNodeWithText("Sheet body").fetchSemanticsNode().boundsInWindow.top.roundToInt()
        }
        return top
    }

    @Test
    fun withoutTheHandleTheContentStartsHigher() {
        val m3 = contentTop(null)
        val kept = contentTop(TinyUIDefaults(bottomSheet = BottomSheetStyle(dragHandle = true)))
        val none = contentTop(TinyUIDefaults(bottomSheet = BottomSheetStyle(dragHandle = false)))
        assertTrue(kept == m3, "true is M3's: $kept vs $m3")
        // M3's handle is 4dp tall with 22dp above and below it: at least 20 pixels at any density
        assertTrue(m3 - none >= 20, "up by the handle's height: $m3 -> $none")
    }
}
