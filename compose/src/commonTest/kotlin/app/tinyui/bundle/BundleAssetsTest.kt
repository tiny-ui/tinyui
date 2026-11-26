package app.tinyui.bundle

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundleAssetsTest {
    private val manifest = """
        {
          "pages": ["shop/home"],
          "files": { "shop/home": "pages/home" },
          "buildIds": { "shop/home": "cccccccc" },
          "name": "shop",
          "publicKey": "BMxP",
          "version": "20261126T090000Z-3f2a1c",
          "createdAt": "2026-11-26T09:00:00Z",
          "engine": "04be246001599f5995fa2f2d8c91a0f198d3f34c",
          "tinyui": "0.14.1",
          "hashes": { "shop/home": "02", "assets/0123456789abcdef.png": "05" },
          "assets": ["assets/0123456789abcdef.png"]
        }
    """.trimIndent()

    private val files = mapOf(
        "manifest.json" to manifest.encodeToByteArray(),
        "pages/home.jsb" to "HOME".encodeToByteArray(),
        "assets/0123456789abcdef.png" to byteArrayOf(1, 2, 3),
    )

    @Test
    fun theAssetsAreDownloadedAndVerifiedWithThePages() = runTest {
        val bundle = Bundle.load { files[it] }
        assertEquals(listOf("assets/0123456789abcdef.png"), bundle.manifest.assets)
        assertEquals("assets/0123456789abcdef.png" to "assets/0123456789abcdef.png", bundle.manifest.payload.last(), "its hash key is its path")
    }

    @Test
    fun aPageReadsOnlyTheImagesTheManifestLists() = runTest {
        val page = Bundle.load { files[it] }.page("shop/home")
        assertContentEquals(byteArrayOf(1, 2, 3), page.module.assets.read("assets/0123456789abcdef.png"))
        assertNull(page.module.assets.read("pages/home.jsb"), "not an asset: a src cannot reach the bytecode")
        assertNull(page.module.assets.read("assets/ffffffffffffffff.png"), "not in this package")
    }

    @Test
    fun aPackageWithoutImagesListsNone() = runTest {
        val plain = manifest.replace(Regex(""",\s*"assets": \[[^]]*]"""), "")
        assertEquals(emptyList(), BuildManifest.parse(plain).assets)
    }
}
