package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** A child without layout (`Menu`, `Popover`, `BackHandler`) takes no room in a `Row` / `Column` with a `gap`, open or closed (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class FloatingChildrenTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })

    /** A Row with gap 4 holding two fixed-width boxes and a Menu, like the language trigger of a top bar: 20 + 4 + 30 wide, menu or no menu. */
    private fun assertRowWidth(menuOpen: Boolean) = runComposeUiTest {
        tree.apply(
            """[["c",1,"Row"],["p",1,"gap",4],["c",2,"Box"],["p",2,"width",20],["p",2,"height",20],["c",3,"Box"],["p",3,"width",30],["p",3,"height",20],""" +
                """["c",4,"Menu"],["p",4,"open",$menuOpen],["c",5,"Option"],["p",5,"value","en"],["p",5,"text","English"],""" +
                """["i",0,1,0],["i",1,2,0],["i",1,3,1],["i",1,4,2],["i",4,5,0]]""",
        )
        setContent { MaterialTheme { Box(Modifier.testTag("row")) { renderer.Render(tree.node(1)!!) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithTag("row").assertWidthIsEqualTo(54.dp)
    }

    @Test
    fun gapIgnoresAClosedMenu() = assertRowWidth(menuOpen = false)

    @Test
    fun gapIgnoresAnOpenMenu() = assertRowWidth(menuOpen = true)

    /** A `BackHandler` first in a Column with gap 4 takes no room either: the first box gets no leading gap, height is 20 + 4 + 20. */
    @Test
    fun gapIgnoresABackHandlerInFirstPlace() = runComposeUiTest {
        tree.apply(
            """[["c",1,"Column"],["p",1,"gap",4],["c",2,"BackHandler"],["p",2,"enabled",false],["c",3,"Box"],["p",3,"width",20],["p",3,"height",20],""" +
                """["c",4,"Box"],["p",4,"width",20],["p",4,"height",20],["i",0,1,0],["i",1,2,0],["i",1,3,1],["i",1,4,2]]""",
        )
        setContent { MaterialTheme { Box(Modifier.testTag("column")) { renderer.Render(tree.node(1)!!) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithTag("column").assertHeightIsEqualTo(44.dp)
    }
}
