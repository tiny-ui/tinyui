package app.tinyui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BundleTest {
    /** In-memory package files that count every read. */
    private class Files(private val content: Map<String, String>) : BundleFiles {
        val reads = mutableListOf<String>()
        override suspend fun read(path: String): ByteArray? {
            reads += path
            return content[path]?.encodeToByteArray()
        }
    }

    private val manifest = """
        {
          "runtime": ["tinyui-core", "tinyui-native"],
          "pages": ["shop/home", "shop/cart"],
          "files": { "tinyui-core": "runtime/core", "tinyui-native": "runtime/native", "shop/home": "pages/home", "shop/cart": "pages/cart" },
          "buildIds": { "tinyui-core": "aaaaaaaa", "tinyui-native": "bbbbbbbb", "shop/home": "cccccccc", "shop/cart": "dddddddd" },
          "name": "shop",
          "publicKey": "BMxP",
          "version": "20260922T090000Z-3f2a1c",
          "createdAt": "2026-09-22T09:00:00Z",
          "engine": "04be246001599f5995fa2f2d8c91a0f198d3f34c",
          "protocol": 1,
          "hashes": { "tinyui-core": "00", "tinyui-native": "01", "shop/home": "02", "shop/cart": "03" }
        }
    """.trimIndent()

    private fun files(vararg extra: Pair<String, String>) = Files(
        mapOf(
            "manifest.json" to manifest,
            "runtime/core.bin" to "CORE",
            "runtime/native.bin" to "NATIVE",
            "pages/home.bin" to "HOME",
            "pages/cart.bin" to "CART",
            *extra,
        ),
    )

    @Test
    fun loadParsesTheManifestAndPagesCarryTheirModule() = runTest {
        val bundle = Bundle.load(files())
        assertEquals("shop", bundle.name)
        assertEquals("20260922T090000Z-3f2a1c", bundle.manifest.version)
        assertEquals(1, bundle.manifest.protocol)
        assertNull(bundle.manifest.hostVersion, "an embedded manifest has no hostVersion")
        val page = bundle.page("shop/home")
        assertEquals("shop/home", page.module.name)
        assertEquals("HOME", page.module.bytecode.decodeToString())
        assertEquals("cccccccc", page.module.buildId)
        assertEquals("CORE", page.runtime.core.decodeToString())
        assertSame(bundle, page.bundle)
    }

    @Test
    fun runtimeBytecodeIsReadOnceForEveryPage() = runTest {
        val files = files()
        val bundle = Bundle.load(files)
        val home = bundle.page("shop/home")
        val cart = bundle.page("shop/cart")
        assertSame(home.runtime, cart.runtime)
        assertEquals(1, files.reads.count { it == "runtime/core.bin" })
        assertEquals(1, files.reads.count { it == "runtime/native.bin" })
        assertEquals(1, files.reads.count { it == "runtime/core.js.map" }, "a missing map is asked for once, not per page")
    }

    @Test
    fun sourceMapsComeAlongWhenTheFilesHaveThem() = runTest {
        val map = """{"version":3,"sources":["src/pages/home.tsx"],"mappings":"AAAA"}"""
        val bundle = Bundle.load(files("pages/home.js.map" to map))
        assertTrue(!bundle.page("shop/home").sourceMaps.isEmpty)
        assertTrue(bundle.page("shop/cart").sourceMaps.isEmpty, "no map for cart nor the runtime")
        assertSame(SourceMaps.EMPTY, bundle.page("shop/cart").sourceMaps)
    }

    @Test
    fun refusesUnknownPagesAndMissingFiles() = runTest {
        val bundle = Bundle.load(files())
        assertFailsWith<IllegalArgumentException> { bundle.page("shop/nowhere") }
        val broken = Bundle.load(Files(mapOf("manifest.json" to manifest, "runtime/core.bin" to "CORE", "runtime/native.bin" to "NATIVE")))
        assertFailsWith<IllegalStateException> { broken.page("shop/home") }
        assertFailsWith<IllegalStateException> { Bundle.load(Files(emptyMap())) }
    }

    @Test
    fun manifestWithoutPackageIdentityIsRejected() {
        assertFailsWith<IllegalArgumentException> { BuildManifest.parse("""{"runtime":[],"pages":[],"files":{},"buildIds":{}}""") }
        val bundled = BuildManifest.parse(manifest.removeSuffix("}") + ""","hostVersion":"1"}""")
        assertEquals("1", bundled.hostVersion)
        assertFailsWith<IllegalArgumentException>("a JSON null is not an identity") {
            BuildManifest.parse("""{"runtime":[],"pages":[],"files":{},"buildIds":{},"name":null,"publicKey":null}""")
        }
        val nulls = BuildManifest.parse(manifest.removeSuffix("}") + ""","hostVersion":null}""")
        assertNull(nulls.hostVersion)
    }
}
