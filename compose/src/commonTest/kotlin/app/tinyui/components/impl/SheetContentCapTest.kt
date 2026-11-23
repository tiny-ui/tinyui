package app.tinyui.components.impl

import kotlin.test.Test
import kotlin.test.assertEquals

/** The content's cap keeps the host's share of the window free above the sheet (docs/components.md §6). */
class SheetContentCapTest {
    // a 2424 px window with a 142 px status bar; M3 gives the content 2235 px, the drag handle and the bottom inset
    // taking the other 189 px

    @Test
    fun theSheetTopIsTheShareOfTheWindowDown() {
        val cap = sheetContentCap(contentMax = 2235, window = 2424, statusBar = 142, fraction = 0.9f)
        assertEquals(1993, cap)
        // the sheet is the content plus 189 px, so 242 px (10% of the window) stay above it
        assertEquals(242, 2424 - (cap + 189))
    }

    @Test
    fun aShareNearTheWholeWindowStopsAtTheStatusBar() {
        assertEquals(2235 - 142, sheetContentCap(contentMax = 2235, window = 2424, statusBar = 142, fraction = 1f))
    }

    @Test
    fun withLessRoomTheContentGetsLess() {
        // the keyboard takes 800 px of what M3 gives the content
        assertEquals(1993 - 800, sheetContentCap(contentMax = 2235 - 800, window = 2424, statusBar = 142, fraction = 0.9f))
    }

    @Test
    fun neverBelowZero() {
        assertEquals(0, sheetContentCap(contentMax = 100, window = 2424, statusBar = 0, fraction = 0.5f))
    }
}
