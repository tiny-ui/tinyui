package app.tinyui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageSrcTest {
    @Test
    fun anImportedImageIsReadFromThePackage() {
        assertEquals(ImageSrc.Packaged("assets/0123456789abcdef.png"), ImageSrc.parse("asset:assets/0123456789abcdef.png"))
    }

    @Test
    fun anHttpsUrlIsLoadedOverTheNetwork() {
        assertEquals(ImageSrc.Remote("https://cdn.example.com/flags/us.png"), ImageSrc.parse("https://cdn.example.com/flags/us.png"))
    }

    @Test
    fun anythingElseDrawsNothing() {
        assertIs<ImageSrc.Invalid>(ImageSrc.parse("http://cdn.example.com/a.png"), "plain http")
        assertIs<ImageSrc.Invalid>(ImageSrc.parse("asset:pages/home.jsb"), "outside assets/")
        assertIs<ImageSrc.Invalid>(ImageSrc.parse("asset:assets/../pages/home.jsb"), "climbs out")
        assertIs<ImageSrc.Invalid>(ImageSrc.parse("./hero.png"), "a path the build did not turn into a reference")
        assertIs<ImageSrc.Invalid>(ImageSrc.parse(""), "empty")
    }
}
