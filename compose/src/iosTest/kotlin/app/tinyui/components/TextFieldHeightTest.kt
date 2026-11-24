package app.tinyui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A text input is M3's 56dp by default; the host makes it denser through `TinyUIDefaults.textField` (`contentPadding` +
 * `minHeight`), and the prefix block follows the field (docs/components.md §3, §6). Pages never set the height.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldHeightTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()
    // a single line of text measures a pixel over its line height; M3's own OutlinedTextField does the same
    private fun near(a: Dp, b: Dp) = abs(a.value - b.value) <= 1f

    private val compact = TinyUIDefaults(textField = TextFieldStyle(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), minHeight = 48.dp))

    @Test
    fun m3MinimumByDefault() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["i",0,1,0]]""")
        setContent { MaterialTheme { Box(Modifier.testTag("field")) { renderer.Render(tree.node(1)!!) } } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        assertTrue(near(field.bottom - field.top, 56.dp), "M3's 56dp: $field")
    }

    @Test
    fun theHostMakesItDenser() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["i",0,1,0]]""")
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { renderer.Render(tree.node(1)!!) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        assertTrue(near(field.bottom - field.top, 48.dp), "the host's 48dp: $field")
    }

    @Test
    fun supportingTextHangsBelowTheDenserField() = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["p",1,"supportingText","Required"],["i",0,1,0]]""")
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { renderer.Render(tree.node(1)!!) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        val supporting = onNodeWithText("Required", useUnmergedTree = true).bounds()
        // the box above the supporting text is the 48dp; the node is taller by the text and its gap
        assertTrue(field.bottom - field.top > 48.dp, "taller than the box with supporting text: $field")
        assertTrue(supporting.top - field.top >= 48.dp, "supporting text below the 48dp box: $supporting in $field")
    }

    @Test
    fun thePrefixBlockFollowsTheField() = runComposeUiTest {
        tree.apply(
            """[["c",1,"TextField"],["p",1,"width",300],["p",1,"placeholder","Phone"],["p",1,"prefixText","+86"],""" +
                """["p",1,"prefixBackground","surfaceVariant"],["p",1,"onPrefixClick",true],["i",0,1,0]]""",
        )
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTinyUIDefaults provides compact) { Box(Modifier.testTag("field")) { renderer.Render(tree.node(1)!!) } }
            }
        }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        val field = onNodeWithTag("field").bounds()
        // the block is the tappable node, so the prefix text merges into it
        val block = onNodeWithText("+86").bounds()
        // 40 = the host's 48dp less 4dp above and below
        assertTrue(near(block.bottom - block.top, 40.dp), "block 40dp tall in a 48dp field: $block in $field")
        assertTrue(abs((block.top - field.top).value - (field.bottom - block.bottom).value) <= 1f, "block centred: $block in $field")
    }

    private val withSmall = TinyUIDefaults(
        textField = TextFieldStyle(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), minHeight = 48.dp),
        smallTextField = TextFieldStyle(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), minHeight = 40.dp),
    )

    /** The field's box, and its placeholder as drawn: M3 clips the text to the room the padding leaves. */
    private fun small(type: String, defaults: TinyUIDefaults, extra: String = "", fontScale: Float = 1f): Pair<DpRect, DpRect> {
        // a tree per field: each is its own page
        val problems = ArrayList<PatchProblem>()
        val tree = NodeTree(registry) { problems += it }
        var out: Pair<DpRect, DpRect>? = null
        runComposeUiTest {
            tree.apply("""[["c",1,"$type"],["p",1,"width",300],["p",1,"size","small"],["p",1,"placeholder","Phone"]$extra,["i",0,1,0]]""")
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    MaterialTheme {
                        CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { Box(Modifier.testTag("field")) { renderer.Render(tree.node(1)!!) } }
                    }
                }
            }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            out = onNodeWithTag("field").bounds() to onNodeWithText("Phone").getBoundsInRoot()
        }
        return out!!
    }

    @Test
    fun aSmallFieldIsTheHostsSmallOneWithAWholeLine() {
        // the search field: a trailing icon, whose slot M3 makes at least 48dp
        val cases = listOf("TextField" to "", "TextField" to """,["p",1,"trailingIcon","0 0 24 24|M0 0h24v24H0z"]""", "Select" to "")
        for ((type, extra) in cases) {
            val (field, text) = small(type, withSmall, extra)
            assertTrue(near(field.bottom - field.top, 40.dp), "$type: the host's small 40dp: $field")
            // 8dp above and below leave the 24dp line whole; the regular 12dp would leave 16dp of it drawn
            assertTrue(text.bottom - text.top >= 23.dp, "$type: a whole line drawn: $text")
            assertTrue(near(text.top - field.top, field.bottom - text.bottom), "$type: centred: $text in $field")
        }
    }

    @Test
    fun withoutAHostSmallFieldItIsTheRegularOne() {
        val (field, _) = small("TextField", compact)
        assertTrue(near(field.bottom - field.top, 48.dp), "the host's regular 48dp: $field")
    }

    @Test
    fun aLargerFontMakesTheSmallFieldTallerRatherThanCutTheText() {
        val (field, text) = small("TextField", withSmall, """,["p",1,"trailingIcon","0 0 24 24|M0 0h24v24H0z"]""", fontScale = 1.5f)
        // a 36dp line and 8dp above and below: 52dp, over the 40dp cap a fixed one would keep
        assertTrue(field.bottom - field.top > 48.dp, "taller with the text: $field")
        assertTrue(text.bottom - text.top >= 35.dp, "a whole line drawn: $text")
    }
}
