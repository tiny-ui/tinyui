package app.tinyui.components.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** `**bold**` and `[label](id)` in a `Text` with `markup` (docs/components.md §3): flat, stray marks literal; without `markup` nothing is read. */
class MarkedTextTest {
    private val blue = TextLinkStyles(SpanStyle(color = Color.Blue))
    private fun links(text: String) = markedText(text, blue) {}.let { s ->
        s.getLinkAnnotations(0, s.length).map { s.text.substring(it.start, it.end) to (it.item as LinkAnnotation.Clickable).tag }
    }
    private fun bolds(text: String) = markedText(text, blue) {}.let { s ->
        s.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { s.text.substring(it.start, it.end) }
    }

    @Test
    fun spansBecomeLinksAndBracketsGo() {
        val s = markedText("我同意 [隐私政策](privacy) 及 [服务协议](terms)", blue) {}
        assertEquals("我同意 隐私政策 及 服务协议", s.text)
        assertEquals(listOf("隐私政策" to "privacy", "服务协议" to "terms"), links("我同意 [隐私政策](privacy) 及 [服务协议](terms)"))
    }

    @Test
    fun linksTakeTheGivenStyles() {
        val semi = TextLinkStyles(SpanStyle(color = Color.Red, fontWeight = FontWeight.SemiBold))
        val s = markedText("[a](x)", semi) {}
        assertEquals(semi, (s.getLinkAnnotations(0, s.length).single().item as LinkAnnotation.Clickable).styles)
    }

    @Test
    fun strayBracketsStayLiteral() {
        assertEquals("a [b] (c) [d]( e)", markedText("a [b] (c) [d]( e)", blue) {}.text)
        assertEquals(emptyList(), links("a [b] (c) [d]( e)"))
    }

    @Test
    fun doubleStarsBecomeBoldAndGo() {
        val s = markedText("code sent to **me@x.io**, valid **30** minutes", blue) {}
        assertEquals("code sent to me@x.io, valid 30 minutes", s.text)
        assertEquals(listOf("me@x.io", "30"), bolds("code sent to **me@x.io**, valid **30** minutes"))
    }

    @Test
    fun strayStarsStayLiteral() {
        assertEquals("a * b ** c", markedText("a * b ** c", blue) {}.text)
        assertEquals(emptyList(), bolds("a * b ** c"))
    }

    @Test
    fun boldAndLinksMixFlat() {
        val s = markedText("**x** [y](z)", blue) {}
        assertEquals("x y", s.text)
        assertEquals(listOf("x"), s.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { s.text.substring(it.start, it.end) })
        assertEquals(listOf("y" to "z"), links("**x** [y](z)"))
    }

    @Test
    fun tapReportsTheId() {
        var tapped: String? = null
        val s = markedText("[重新输入](edit)", blue) { tapped = it }
        (s.getLinkAnnotations(0, s.length).single().item as LinkAnnotation.Clickable).linkInteractionListener!!.onClick(s.getLinkAnnotations(0, s.length).single().item)
        assertEquals("edit", tapped)
    }

    /** A run of stars is content, not a pair of marks around one of its own stars. */
    @Test
    fun runsOfStarsStayLiteral() {
        for (masked in listOf("ab***cd", "ab****cd", "ab*****cd", "ab********cd@x.io", "135 **** 4263", "***a***")) {
            assertEquals(masked, markedText(masked, blue) {}.text)
            assertEquals(emptyList(), bolds(masked))
        }
    }

    /** Copy with a masked value in it: the marks are the two-star ones, the run inside is part of what is bold. */
    @Test
    fun boldHoldsARunOfStars() {
        assertEquals("sent to 135 **** 4263 now", markedText("sent to **135 **** 4263** now", blue) {}.text)
        assertEquals(listOf("135 **** 4263"), bolds("sent to **135 **** 4263** now"))
        assertEquals(listOf("ab********cd@x.io", "30"), bolds("**ab********cd@x.io** valid **30** minutes"))
    }

    /** Text is data unless the page says it is copy. */
    @Test
    fun withoutMarkupEveryCharacterStays() {
        val masked = "ab********cd@x.io [x](y) **z**"
        val s = shownText(masked, markup = false, blue) {}
        assertEquals(masked, s.text)
        assertEquals(emptyList(), s.spanStyles)
        assertEquals(emptyList(), s.getLinkAnnotations(0, s.length))
    }

    @Test
    fun withMarkupMarksAreRead() {
        val s = shownText("**b** [a](x)", markup = true, blue) {}
        assertEquals("b a", s.text)
        assertEquals(1, s.getLinkAnnotations(0, s.length).size)
    }
}
