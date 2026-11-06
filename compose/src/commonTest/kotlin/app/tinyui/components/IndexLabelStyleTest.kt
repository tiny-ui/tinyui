package app.tinyui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The index strip's labels keep the size they have at the default system font size (docs/components.md §3). */
class IndexLabelStyleTest {
    private fun px(style: TextStyle, fontScale: Float) = Density(density = 3f, fontScale = fontScale).run {
        val unscaled = style.unscaled(this)
        unscaled.fontSize.toPx() to unscaled.lineHeight.toPx()
    }

    @Test
    fun theSizeIsTheSameAtAnyFontScale() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
        // 12sp and 16sp at density 3 and the default scale; sp ↔ dp goes through a division, hence the tolerance
        for (scale in listOf(1f, 2f, 0.85f)) {
            val (size, lineHeight) = px(style, scale)
            assertEquals(36f, size, 0.01f, "font size at scale $scale")
            assertEquals(48f, lineHeight, 0.01f, "line height at scale $scale")
        }
    }

    @Test
    fun aStyleWithoutASizeInSpFallsBackToTwelve() {
        val size = Density(density = 3f, fontScale = 2f).run { TextStyle(fontSize = TextUnit.Unspecified).unscaled(this).fontSize.toPx() }
        assertEquals(36f, size, 0.01f)
    }

    /** A line height relative to the font size follows it as it is. */
    @Test
    fun aRelativeLineHeightIsKept() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 1.5.em)
        assertEquals(1.5.em, style.unscaled(Density(density = 3f, fontScale = 2f)).lineHeight)
    }
}
