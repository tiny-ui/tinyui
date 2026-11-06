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
        // started one by one, the first worker could finish before the next begins: each says when it is at the line,
        // and they are let go once all of them are there
        val atTheLine = List(8) { CompletableDeferred<Unit>() }
        val go = CompletableDeferred<Unit>()
        val firsts = withContext(Dispatchers.Default) {
            val workers = atTheLine.map { here -> async { here.complete(Unit); go.await(); names.count { keys.firstTime(it) } } }
            atTheLine.awaitAll()
            go.complete(Unit)
            workers.awaitAll().sum()
        }
        assertEquals(names.size, firsts)
    }
}
