package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sizes a design system fixes once, set by the host through [TinyUIDefaults] and left out of the pages
 * (docs/components.md §6): a button's minimum height, the top bar's height and back icon, and a field's minimum
 * height as a stretched row sees it. Plus `Select` handing its tap to the page.
 */
@OptIn(ExperimentalTestApi::class)
class HostSizesTest {
    private val problems = ArrayList<PatchProblem>()
    private val events = ArrayList<String>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }
    private val renderer = NodeRenderer(registry, onEvent = { id, event, _ -> events += "$id:$event" })

    private fun near(a: Dp, b: Dp) = abs(a.value - b.value) < 0.5f
    private fun ComposeUiTest.height(text: String): Dp = onNodeWithText(text).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun render(defaults: TinyUIDefaults, ops: String, check: ComposeUiTest.() -> Unit) = runComposeUiTest {
        tree.apply(ops)
        setContent { MaterialTheme { CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { Box { renderer.Render(tree.node(1)!!) } } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        check()
    }

    private val compact = TinyUIDefaults(
        textField = TextFieldStyle(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), minHeight = 48.dp),
        filledButton = ButtonStyle(minHeight = 48.dp),
        topAppBar = TopAppBarStyle(height = 48.dp, backIconSize = 32.dp),
    )

    @Test
    fun aButtonIsAtLeastTheHostsMinimumAndThePagesHeightWins() {
        render(
            compact,
            """[["c",1,"Column"],["c",2,"Button"],["p",2,"text","Host"],["c",3,"Button"],["p",3,"text","Page"],["p",3,"height",40],""" +
                """["c",4,"Button"],["p",4,"text","Other"],["p",4,"variant","outlined"],["i",0,1,0],["i",1,2,0],["i",1,3,1],["i",1,4,2]]""",
        ) {
            assertTrue(near(height("Host"), 48.dp), "host minimum: ${height("Host")}")
            assertTrue(near(height("Page"), 40.dp), "the page's own height: ${height("Page")}")
            assertTrue(near(height("Other"), 40.dp), "a variant the host left alone keeps M3's: ${height("Other")}")
        }
    }

    @Test
    fun theTopBarTakesTheHostsSizesAndThePagesWin() {
        val bar = """["c",1,"TopAppBar"],["p",1,"backIcon","0 0 24 24|M0 0h24v24H0z"],["p",1,"backLabel","Back"]"""
        render(compact, """[$bar,["i",0,1,0]]""") {
            onNodeWithContentDescription("Back", useUnmergedTree = true).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
            // the back button is centred in the bar: half of 48dp down, where M3's 64dp bar would put it at 32dp
            val centre = onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().let { (it.top + it.bottom) / 2 }
            assertTrue(near(centre, 24.dp), "centre of the back button $centre")
        }
    }

    @Test
    fun thePagesBackIconSizeWinsOverTheHosts() {
        render(compact, """[["c",1,"TopAppBar"],["p",1,"backIcon","0 0 24 24|M0 0h24v24H0z"],["p",1,"backLabel","Back"],["p",1,"backIconSize",20],["i",0,1,0]]""") {
            onNodeWithContentDescription("Back", useUnmergedTree = true).assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
        }
    }

    /** M3 answers an intrinsic measurement with its own 56dp whatever the host set; the row must come out at the field's real height. */
    @Test
    fun aStretchedRowIsAsTallAsTheHostsField() {
        render(
            compact,
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],["p",1,"gap",8],""" +
                """["c",2,"TextField"],["p",2,"weight",1],["c",3,"Button"],["p",3,"text","Resend"],["p",3,"variant","outlined"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) {
            val field = onNode(hasSetTextAction()).getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue(near(field, 48.dp), "field $field")
            assertTrue(near(height("Resend"), 48.dp), "button ${height("Resend")}")
        }
    }

    /** The line under the field is not part of the box: it adds to the row as it adds to the field. */
    @Test
    fun supportingTextStillCountsInAStretchedRow() {
        render(
            compact,
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],""" +
                """["c",2,"TextField"],["p",2,"weight",1],["p",2,"supportingText","Hint"],["c",3,"Button"],["p",3,"text","Resend"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) {
            val field = onNode(hasSetTextAction()).getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue(field > 48.dp, "field with its hint $field")
            assertTrue(near(height("Resend"), field), "button ${height("Resend")}, field $field")
        }
    }

    /** A floating label adds to the field's height: the stretched row follows the field, it is not cut down to the bare box. */
    @Test
    fun aLabelledSelectKeepsItsHeightInAStretchedRow() {
        render(
            compact,
            """[["c",1,"Row"],["p",1,"width",360],["p",1,"align","stretch"],""" +
                """["c",2,"Select"],["p",2,"weight",1],["p",2,"label","Method"],["p",2,"value","sms"],["c",4,"Option"],["p",4,"value","sms"],["p",4,"text","SMS"],""" +
                """["c",3,"Button"],["p",3,"text","Resend"],["p",3,"variant","outlined"],["i",0,1,0],["i",1,2,0],["i",2,4,0],["i",1,3,1]]""",
        ) {
            // the bare box would be the host's 48dp; with the label on its border the field is taller, and so is the row
            assertTrue(height("Resend") > 50.dp, "button ${height("Resend")}")
        }
    }

    /** The page starts listening for the tap while the menu is open: the menu goes, the choices are the page's to show now. */
    @Test
    fun anOpenMenuClosesWhenThePageTakesTheTapOver() {
        render(
            compact,
            """[["c",1,"Select"],["p",1,"width",240],["p",1,"value","sms"],""" +
                """["c",2,"Option"],["p",2,"value","sms"],["p",2,"text","SMS"],["c",3,"Option"],["p",3,"value","email"],["p",3,"text","Email"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) {
            onNodeWithText("SMS").performClick()
            waitForIdle()
            assertTrue(onAllNodes(androidx.compose.ui.test.hasText("Email")).fetchSemanticsNodes().isNotEmpty(), "the menu is open")
            runOnUiThread { tree.apply("""[["p",1,"onClick",true]]""") }
            waitForIdle()
            assertTrue(onAllNodes(androidx.compose.ui.test.hasText("Email")).fetchSemanticsNodes().isEmpty(), "taken over: the menu is gone")
        }
    }

    @Test
    fun aSelectWithOnClickHandsTheTapToThePage() {
        render(
            compact,
            """[["c",1,"Select"],["p",1,"width",240],["p",1,"value","sms"],["p",1,"onClick",true],""" +
                """["c",2,"Option"],["p",2,"value","sms"],["p",2,"text","SMS"],["c",3,"Option"],["p",3,"value","email"],["p",3,"text","Email"],["i",0,1,0],["i",1,2,0],["i",1,3,1]]""",
        ) {
            onNodeWithText("SMS").performClick()
            waitForIdle()
            assertEquals(listOf("1:onClick"), events)
            assertTrue(onAllNodes(androidx.compose.ui.test.hasText("Email")).fetchSemanticsNodes().isEmpty(), "the menu stayed shut")
        }
    }
}
