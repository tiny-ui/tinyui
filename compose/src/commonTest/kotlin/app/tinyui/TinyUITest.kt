package app.tinyui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TinyUITest {
    @Test
    fun linksAgainstQuickJs() {
        assertTrue(TinyUI.engineVersion.isNotEmpty())
    }

    @Test
    fun aHostRunsPackagesOfItsMajorBuiltAgainstItsOwnOrAnOlderTinyui() {
        assertTrue(TinyUI.isCompatible("0.7.0", "0.7.0"))
        assertTrue(TinyUI.isCompatible("0.9.2", "0.7.0"))
        assertTrue(TinyUI.isCompatible("2.3.0", "2.0.1"))
        assertFalse(TinyUI.isCompatible("0.7.0", "0.7.1"))
        assertFalse(TinyUI.isCompatible("2.0.0", "1.9.0"), "a new major may have removed what the page uses")
        assertFalse(TinyUI.isCompatible("1.0.0", "0.9.0"))
    }

    @Test
    fun preReleasesSortBelowTheirReleaseAndGarbageIsNeverCompatible() {
        assertTrue(TinyUI.isCompatible("0.7.0", "0.7.0-rc.1"))
        assertFalse(TinyUI.isCompatible("0.7.0-rc.1", "0.7.0"))
        assertTrue(TinyUI.isCompatible("0.7.0-rc.10", "0.7.0-rc.2"))
        for (bad in listOf("", "0.7", "v0.7.0", "01.0.0", "0.7.0-rc.01")) assertFalse(TinyUI.isCompatible("0.7.0", bad), bad)
    }

    @Test
    fun numbersOfAnySizeCompareExactly() {
        assertTrue(TinyUI.isCompatible("0.99999999999999999999.0", "0.7.0"), "beyond Int and Long")
        assertFalse(TinyUI.isCompatible("0.7.0", "0.99999999999999999999.0"))
        assertFalse(TinyUI.isCompatible("0.7.0-rc.9007199254740992", "0.7.0-rc.9007199254740993"), "beyond a double's precision")
    }
}
