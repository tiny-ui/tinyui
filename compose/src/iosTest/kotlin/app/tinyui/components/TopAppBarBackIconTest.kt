package app.tinyui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** `backIconSize` sizes the back icon only; unset, the icon keeps its own 24dp; the button stays a 48dp target (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TopAppBarBackIconTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })

    private fun assertBackIcon(sizeProp: String, expected: Int) = runComposeUiTest {
        tree.apply("""[["c",1,"TopAppBar"],["p",1,"height",48],["p",1,"backIcon","0 0 24 24|M0 0h24v24H0z"],["p",1,"backLabel","Back"]$sizeProp,["i",0,1,0]]""")
        setContent { MaterialTheme { renderer.Render(tree.node(1)!!) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        // the icon's description merges into the button: unmerged it is the icon, merged it is the button
        onNodeWithContentDescription("Back", useUnmergedTree = true).assertWidthIsEqualTo(expected.dp).assertHeightIsEqualTo(expected.dp)
        onNodeWithContentDescription("Back").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun backIconSizeSizesTheIcon() = assertBackIcon(""",["p",1,"backIconSize",32]""", 32)

    @Test
    fun unsetKeepsTheIconsOwnSize() = assertBackIcon("", 24)
}
