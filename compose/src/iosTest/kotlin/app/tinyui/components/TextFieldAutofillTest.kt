package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** `autofill="oneTimeCode"` marks the field as an SMS code for the system; unset, no hint at all (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TextFieldAutofillTest {
    private val problems = ArrayList<PatchProblem>()
    private val registry = ComponentRegistry().registerBuiltins()
    private val tree = NodeTree(registry) { problems += it }

    private val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })

    private fun countHinted(autofill: String, matcher: SemanticsMatcher, expected: Int) = runComposeUiTest {
        tree.apply("""[["c",1,"TextField"]$autofill,["i",0,1,0]]""")
        setContent { MaterialTheme { renderer.Render(tree.node(1)!!) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onAllNodes(matcher, useUnmergedTree = true).assertCountEquals(expected)
    }

    @Test
    fun oneTimeCodeMarksAnSmsCode() = countHinted(""",["p",1,"autofill","oneTimeCode"]""", SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.SmsOtpCode), 1)

    @Test
    fun unsetLeavesItToTheSystem() = countHinted("", SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentType), 0)
}
