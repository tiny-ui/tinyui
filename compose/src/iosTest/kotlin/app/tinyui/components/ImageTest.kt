package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import app.tinyui.PageAssets
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `Image` (docs/components.md §3), rendered through the node tree like a page would. */
@OptIn(ExperimentalTestApi::class)
class ImageTest {
    private val registry = ComponentRegistry().registerBuiltins()

    /** A 1×1 red PNG. */
    private val red = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC")

    /** Renders one 40×40 Image with [src] over a white box; the reported problems and the centre pixel's red / green once [settled] holds. */
    private fun render(src: String, files: Map<String, ByteArray>, settled: (problems: List<String>, rgb: Pair<Float, Float>) -> Boolean): Pair<List<String>, Pair<Float, Float>> {
        val problems = ArrayList<String>()
        val tree = NodeTree(registry) { problems += it.toString() }
        val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> }, onProblem = { _, reason -> problems += reason })
        var rgb = 0f to 0f
        runComposeUiTest {
            tree.apply("""[["c",1,"Box"],["p",1,"background","#FFFFFF"],["c",2,"Image"],["p",2,"src",${'"'}$src${'"'}],["p",2,"width",40],["p",2,"height",40],["p",2,"contentScale","fill"],["i",0,1,0],["i",1,2,0]]""")
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalPageAssetSource provides PageAssetSource("shop", PageAssets { files[it] })) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("image")) { renderer.Render(tree.node(1)!!) }
                    }
                }
            }
            fun sample(): Pair<Float, Float> {
                val pixels = onNode(hasTestTag("image")).captureToImage().toPixelMap()
                val p = pixels[pixels.width / 2, pixels.height / 2]
                return p.red to p.green
            }
            waitUntil(timeoutMillis = 5_000) { rgb = sample(); settled(problems, rgb) }
        }
        return problems to rgb
    }

    @Test
    fun anImageFromThePackageIsDrawn() {
        val (problems, rgb) = render("asset:assets/aaaa.png", mapOf("assets/aaaa.png" to red)) { _, (r, g) -> r > 0.9f && g < 0.1f }
        assertTrue(problems.isEmpty(), problems.toString())
        assertEquals(1f, rgb.first, 0.05f)
        assertEquals(0f, rgb.second, 0.05f)
    }

    @Test
    fun aSrcThatIsNeitherDrawsNothingAndIsReported() {
        val (problems, rgb) = render("http://example.com/a.png", emptyMap()) { p, _ -> p.isNotEmpty() }
        assertTrue(problems.single().contains("only https"), problems.toString())
        assertEquals(1f to 1f, rgb, "the box stays as it was")
    }

    @Test
    fun anImageThePackageDoesNotHaveIsReported() {
        val (problems, _) = render("asset:assets/bbbb.png", emptyMap()) { p, _ -> p.isNotEmpty() }
        assertTrue(problems.single().contains("failed to load"), problems.toString())
    }
}
