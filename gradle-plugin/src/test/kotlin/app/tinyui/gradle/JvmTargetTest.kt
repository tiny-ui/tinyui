package app.tinyui.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmTargetTest {
    /** The plugin runs in the host's Gradle daemon: built on a newer JDK, it must still load on 17 (class file major 61). */
    @Test
    fun classesLoadOnJava17() {
        for (type in listOf(TinyUIPlugin::class.java, Delivery::class.java, FetchTinyUIPackage::class.java)) {
            val bytes = type.getResourceAsStream("${type.simpleName}.class")!!.use { it.readBytes() }
            val major = (bytes[6].toInt() and 0xff shl 8) or (bytes[7].toInt() and 0xff)
            assertEquals(61, major, type.name)
        }
    }
}
