package app.tinyui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MissingKeysTest {
    @Test
    fun aKeyIsNewOnce() {
        val keys = MissingKeys()
        assertEquals(listOf(true, false, true, false), listOf(keys.firstTime("a"), keys.firstTime("a"), keys.firstTime("b"), keys.firstTime("a")))
    }

    @Test
    fun aKeyIsNewToOneThreadOnlyWhenSeveralAskAtOnce() = runTest {
        val keys = MissingKeys()
        val names = List(5_000) { "key$it" }
        // the workers wait at one line and leave it together: started one by one, the first could finish before the next begins
        val go = CompletableDeferred<Unit>()
        val firsts = withContext(Dispatchers.Default) {
            List(8) { async { go.await(); names.count { keys.firstTime(it) } } }.also { go.complete(Unit) }.awaitAll().sum()
        }
        assertEquals(names.size, firsts)
    }
}
