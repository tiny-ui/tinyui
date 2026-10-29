package app.tinyui

import kotlin.test.Test
import kotlin.test.assertEquals

class PageBackTest {
    private val log = ArrayList<String>()
    private val back = PageBack { log += "close" }

    @Test
    fun closesThePageWithoutHandlers() {
        back.back()
        assertEquals(listOf("close"), log)
    }

    @Test
    fun theLastEnabledHandlerTakesIt() {
        val first = PageBack.Handler(onBack = { log += "first" })
        val second = PageBack.Handler(onBack = { log += "second" })
        back.add(first)
        back.add(second)
        back.back()
        second.enabled = false
        back.back()
        first.enabled = false
        back.back()
        assertEquals(listOf("second", "first", "close"), log)
    }

    @Test
    fun aRemovedHandlerNoLongerTakesIt() {
        val handler = PageBack.Handler(onBack = { log += "handler" })
        back.add(handler)
        back.remove(handler)
        back.back()
        assertEquals(listOf("close"), log)
    }
}
