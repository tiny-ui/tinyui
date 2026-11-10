package app.tinyui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** `markup` as the rendered `Text` reads it: marks are read only when the prop is true (docs/components.md §3). */
@OptIn(ExperimentalTestApi::class)
class TextMarkupTest {
    private val registry = ComponentRegistry().registerBuiltins()
    private val renderer = NodeRenderer(registry, onEvent = { _, _, _ -> })
    private val marked = "a **b** c [x](y)"

    /** What a `Text` showing [marked] puts on screen, with [markupProp] as an extra patch op (or none). */
    private fun shows(markupProp: String, expected: String) = runComposeUiTest {
        val problems = ArrayList<PatchProblem>()
        val tree = NodeTree(registry) { problems += it }
        tree.apply("""[["c",1,"Text"],["p",1,"text","$marked"]$markupProp,["i",0,1,0]]""")
        setContent { MaterialTheme { renderer.Render(tree.node(1)!!) } }
        waitForIdle()
        assertTrue(problems.isEmpty(), problems.toString())
        onNodeWithText(expected).assertExists()
    }

    @Test
    fun absentShowsEveryCharacter() = shows("", marked)

    @Test
    fun falseShowsEveryCharacter() = shows(""",["p",1,"markup",false]""", marked)

    /** A handler alone does not turn the marks on. */
    @Test
    fun aLinkHandlerAloneShowsEveryCharacter() = shows(""",["p",1,"onLinkClick",true]""", marked)

    /** No handler bound: the link is still rendered, brackets gone. */
    @Test
    fun trueReadsTheMarks() = shows(""",["p",1,"markup",true]""", "a b c x")
}
