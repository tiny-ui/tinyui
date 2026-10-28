package app.tinyui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkedTextTest {
    private fun links(text: String) = linkedText(text, Color.Blue) {}.let { s ->
        s.getLinkAnnotations(0, s.length).map { s.text.substring(it.start, it.end) to (it.item as LinkAnnotation.Clickable).tag }
    }

    @Test
    fun spansBecomeLinksAndBracketsGo() {
        val s = linkedText("我同意 [隐私政策](privacy) 及 [服务协议](terms)", Color.Blue) {}
        assertEquals("我同意 隐私政策 及 服务协议", s.text)
        assertEquals(listOf("隐私政策" to "privacy", "服务协议" to "terms"), links("我同意 [隐私政策](privacy) 及 [服务协议](terms)"))
    }

    @Test
    fun strayBracketsStayLiteral() {
        assertEquals("a [b] (c) [d]( e)", linkedText("a [b] (c) [d]( e)", Color.Blue) {}.text)
        assertEquals(emptyList(), links("a [b] (c) [d]( e)"))
    }

    @Test
    fun tapReportsTheId() {
        var tapped: String? = null
        val s = linkedText("[重新输入](edit)", Color.Blue) { tapped = it }
        (s.getLinkAnnotations(0, s.length).single().item as LinkAnnotation.Clickable).linkInteractionListener!!.onClick(s.getLinkAnnotations(0, s.length).single().item)
        assertEquals("edit", tapped)
    }
}
