package app.tinyui

import kotlin.test.Test
import kotlin.test.assertEquals

class RegionCodeTest {
    @Test
    fun aCountryCodeComesOutUpperCase() {
        assertEquals("US", regionCode("US"))
        assertEquals("CN", regionCode("cn"))
    }

    /** A locale's region may be an area, not a country: es-419 is Latin American Spanish. */
    @Test
    fun anythingThatIsNotTwoLettersIsNoRegion() {
        for (raw in listOf("419", "001", "", "U", "USA", "U1", null)) assertEquals("", regionCode(raw), "$raw")
    }
}
