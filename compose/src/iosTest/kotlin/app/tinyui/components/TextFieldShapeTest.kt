package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `cornerRadius` on a `TextField` is the outline's own shape, not a clip (docs/components.md §2): the field's bottom
 * corners are rounded even with supporting text below it, which a clip over the whole node would leave square.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldShapeTest {
    private val scheme = lightColorScheme(outline = Color.Black, onSurfaceVariant = Color.White)

    /** How far in from the left the outline is, [above] the field's bottom border. */
    private fun outlineInset(props: String, above: Dp): Dp {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        var inset: Dp? = null
        runComposeUiTest {
            tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"supportingText","Hint"]$props,["i",0,1,0]]""")
            setContent { MaterialTheme(colorScheme = scheme) { renderer.Render(tree.node(1)!!) } }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val image = onRoot().captureToImage()
            val pixels = image.toPixelMap()
            fun dark(x: Int, y: Int) = pixels[x, y].let { it.alpha > 0.5f && it.red < 0.5f && it.green < 0.5f && it.blue < 0.5f }
            with(density) {
                // down the middle: the top border, then the bottom border (the supporting text is white)
                val mid = image.width / 2
                val borders = (0 until image.height).filter { dark(mid, it) }
                val bottom = borders.first { it > borders.first() + 8.dp.roundToPx() }
                val row = bottom - above.roundToPx()
                val left = (0 until mid).first { dark(it, row) }
                inset = left.toDp()
            }
        }
        return inset!!
    }

    @Test
    fun theRadiusRoundsTheFieldNotTheNode() {
        val m3 = outlineInset("", above = 8.dp)
        val round = outlineInset(""",["p",1,"cornerRadius",24]""", above = 8.dp)
        // M3's 4dp corner is done 8dp above the bottom: the outline is at the edge
        assertTrue(m3 < 2.dp, "M3's corner: $m3")
        // a 24dp corner, 8dp above the bottom border is 24 - sqrt(24² - 16²) ≈ 6dp in
        assertTrue(round > 4.dp && round < 8.dp, "a 24dp corner at the field's own bottom: $round")
    }

    @Test
    fun aBackgroundFollowsTheRadius() {
        val problems = ArrayList<PatchProblem>()
        val registry = ComponentRegistry().registerBuiltins()
        val tree = NodeTree(registry) { problems += it }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
        runComposeUiTest {
            tree.apply("""[["c",1,"TextField"],["p",1,"width",300],["p",1,"cornerRadius",24],["p",1,"background","#FF0000"],["i",0,1,0]]""")
            setContent { MaterialTheme(colorScheme = scheme) { renderer.Render(tree.node(1)!!) } }
            waitForIdle()
            assertTrue(problems.isEmpty(), problems.toString())
            val image = onRoot().captureToImage()
            val pixels = image.toPixelMap()
            // the very corner is outside a 24dp rounding; the middle is inside it
            assertTrue(pixels[1, 1].alpha < 0.1f, "no background in the rounded-off corner: ${pixels[1, 1]}")
            assertTrue(pixels[image.width / 2, image.height / 2].red > 0.9f, "background inside: ${pixels[image.width / 2, image.height / 2]}")
        }
    }
}
