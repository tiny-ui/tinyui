package app.tinyui.sample

import app.tinyui.HostSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The host-side check of docs/updates.md §4.1; `-Ptinyui.updateHostSnapshot` writes the file instead. */
class HostSnapshotTest {
    @Test
    fun hostMatchesTheSnapshotOfItsHostVersion() {
        val file = File("tinyui-host/$HOST_VERSION.txt")
        val current = HostSnapshot.render(tinyUIHost, HOST_VERSION)
        if (System.getProperty("tinyui.updateHostSnapshot") != null) {
            file.parentFile.mkdirs()
            file.writeText(current)
            return
        }
        val hint = "if HOST_VERSION $HOST_VERSION is not on main yet, rerun with -Ptinyui.updateHostSnapshot; otherwise raise HOST_VERSION and do that"
        assertTrue(file.exists(), "no ${file.path} for HOST_VERSION $HOST_VERSION; $hint")
        assertEquals(file.readText(), current, "the host no longer matches ${file.path}; $hint")
    }
}
