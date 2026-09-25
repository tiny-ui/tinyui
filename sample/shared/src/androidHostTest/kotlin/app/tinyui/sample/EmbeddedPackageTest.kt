package app.tinyui.sample

import app.tinyui.BuildManifest
import app.tinyui.PackageCheck
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every package this App embeds can run on its host (docs/updates.md §4.1). */
class EmbeddedPackageTest {
    @Test
    fun embeddedPackagesRunOnThisHost() {
        val root = File(System.getProperty("tinyui.embedded"))
        for (pkg in listOf("sample", "sample-extra")) {
            val manifest = BuildManifest.parse(root.resolve("$pkg/manifest.json").readText())
            assertEquals(emptyList(), PackageCheck.problems(manifest, sampleHost()), "embedded package $pkg")
        }
    }
}
