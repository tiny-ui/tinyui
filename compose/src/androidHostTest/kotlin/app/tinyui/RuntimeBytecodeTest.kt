package app.tinyui

import wang.harlon.quickjs.QuickJs
import kotlin.test.Test
import kotlin.test.assertEquals

/** The embedded runtime is bytecode for the engine this build links; a quickjs-kmp bump without `pnpm runtime` fails here. */
class RuntimeBytecodeTest {
    @Test
    fun embeddedRuntimeIsBoundToTheLinkedEngine() {
        for (bytes in listOf(TinyUI.runtime.core, TinyUI.runtime.native)) {
            assertEquals("QJKB", bytes.copyOfRange(0, 4).decodeToString())
            assertEquals(QuickJs.upstreamCommit, bytes.copyOfRange(12, 52).decodeToString())
        }
    }
}
