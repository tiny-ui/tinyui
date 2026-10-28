package app.tinyui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipToTest {
    @Test
    fun noLimitOrShortEnoughIsUntouched() {
        assertEquals("abc", clipTo("abc", null))
        assertEquals("abc", clipTo("abc", 3))
    }

    @Test
    fun cutsAtTheLimit() = assertEquals("ab", clipTo("abc", 2))

    @Test
    fun neverSplitsASurrogatePair() {
        // "a😀b": the emoji is two UTF-16 units at 1..2
        assertEquals("a", clipTo("a😀b", 2))
        assertEquals("a😀", clipTo("a😀b", 3))
    }
}
