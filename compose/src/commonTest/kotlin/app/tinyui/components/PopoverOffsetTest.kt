package app.tinyui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PopoverOffsetTest {
    private val window = IntSize(400, 800)
    // a field across the middle of the screen, 16 px in from each side
    private val field = IntRect(16, 300, 384, 356)
    private val card = IntSize(368, 200)

    @Test
    fun abovePutsTheBottomEdgeAGapOverTheAnchor() =
        assertEquals(IntOffset(16, 92), popoverOffset(field, window, card, above = true, gap = 8, rtl = false))

    @Test
    fun belowPutsTheTopEdgeAGapUnderTheAnchor() =
        assertEquals(IntOffset(16, 364), popoverOffset(field, window, card, above = false, gap = 8, rtl = false))

    @Test
    fun flipsWhenThePreferredSideHasNoRoom() {
        val nearTop = IntRect(16, 100, 384, 156)
        assertEquals(IntOffset(16, 164), popoverOffset(nearTop, window, card, above = true, gap = 8, rtl = false))
        val nearBottom = IntRect(16, 600, 384, 656)
        assertEquals(IntOffset(16, 392), popoverOffset(nearBottom, window, card, above = false, gap = 8, rtl = false))
    }

    @Test
    fun fitsNeitherSideTakesTheRoomierOneAndStaysInTheWindow() {
        val tall = IntSize(368, 500)
        // 300 above vs 444 below: below, pushed up so the bottom edge stays on screen
        assertEquals(IntOffset(16, 300), popoverOffset(field, window, tall, above = true, gap = 8, rtl = false))
    }

    @Test
    fun startEdgesAlignAndNeverLeaveTheWindow() {
        val narrow = IntSize(100, 50)
        assertEquals(IntOffset(284, 242), popoverOffset(field, window, narrow, above = true, gap = 8, rtl = true))
        val pastRightEdge = IntRect(350, 300, 390, 340)
        assertEquals(IntOffset(300, 242), popoverOffset(pastRightEdge, window, narrow, above = true, gap = 8, rtl = false))
    }
}
