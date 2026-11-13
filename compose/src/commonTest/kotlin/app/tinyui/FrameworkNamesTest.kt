package app.tinyui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FrameworkNamesTest {
    /** A name in two of the enums would reach whichever bridge function the page happened to call it through. */
    @Test
    fun aNameIsReachedOneWayOnly() {
        val all = J2.entries.map { it.wire } + J3.entries.map { it.wire } + J4.entries.map { it.wire }
        assertEquals(all.size, FrameworkNames.size, all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toString())
    }

    @Test
    fun aHostCapabilityMayNotTakeAFrameworkName() {
        for (name in FrameworkNames) assertFailsWith<IllegalArgumentException>(name) { CapabilityRegistry().register(name) { _, _ -> null } }
    }

    /** A host without a session does not offer `session.signIn`; everything else is there on every host. */
    @Test
    fun signInAndSetLocaleAreTheNamesThatDependOnTheHost() {
        assertEquals(setOf("session.signIn", "i18n.setLocale"), FrameworkNames - FrameworkCapabilities)
        assertTrue("http.request" in FrameworkCapabilities && "device.info" in FrameworkCapabilities && "analytics.track" in FrameworkCapabilities)
    }
}
