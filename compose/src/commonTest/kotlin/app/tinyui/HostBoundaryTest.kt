package app.tinyui

import app.tinyui.schema.parseIcon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The framework-owned pieces of ADR-007 that do not need an engine. */
class HostBoundaryTest {
    private val sink = object : PageSink {
        override fun error(error: PageError) {}
        override fun log(line: String) {}
    }

    @Test
    fun i18nFallsBackBySubtagThenToTheDefault() {
        val i18n = PackageI18n("en", mapOf("en" to mapOf("a" to "A", "b" to "B"), "zh" to mapOf("a" to "甲"), "zh-Hant" to mapOf("a" to "甲T")))
        assertEquals("甲T", i18n.lookup("a", "zh-Hant-TW"))
        assertEquals("甲", i18n.lookup("a", "zh_CN"))
        assertEquals("B", i18n.lookup("b", "zh-Hant-TW"), "a key the language lacks comes from the default")
        assertNull(i18n.lookup("c", "zh"))
    }

    @Test
    fun storageKeepsWritesAcrossALoadAndRefusesPastItsLimit() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/storage/shop.json".toPath()
        val scope = CoroutineScope(Dispatchers.Default)
        val storage = PackageStorage(fs, file, scope, sink)
        assertNull(storage.set("count", "3"))
        assertEquals("3", storage.get("count"))
        assertEquals("E_QUOTA", storage.set("big", "\"${"x".repeat(PackageStorage.LIMIT_CHARS)}\""))
        assertNull(storage.get("big"))
        withContext(Dispatchers.Default) { while (!fs.exists(file)) delay(20) }
        assertEquals("3", PackageStorage(fs, file, scope, sink).get("count"), "a fresh load reads what was written")
    }

    @Test
    fun iconsParseFilledAndOutlinedAndRefuseAnythingElse() {
        assertNotNull(parseIcon("0 -960 960 960|M480-80L80-480Z"))
        assertNotNull(parseIcon("0 0 24 24|M2 12L22 12|2"))
        assertNull(parseIcon("M2 12L22 12"))
        assertNull(parseIcon("0 0 24|M2 12Z"))
        assertNull(parseIcon("0 0 24 24|M2 12Z|thick"))
    }

    @Test
    fun ktorChannelSendsTheBodyWithItsTypeAndReturnsAnyStatus() = runTest {
        var seen: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            seen = request
            respond("""{"error":"dup"}""", HttpStatusCode.Conflict, headersOf("Content-Type", "application/json"))
        })
        val response = KtorChannel(client).request(
            HttpRequest("POST", "https://api/x", mapOf("Content-Type" to "application/json", "X-Trace" to "1"), """{"plan":"annual"}""", null),
        )
        assertEquals(409, response.status)
        assertEquals("""{"error":"dup"}""", response.body)
        assertEquals("application/json", response.headers["content-type"])
        val body = seen!!.body as TextContent
        assertEquals("""{"plan":"annual"}""", body.text)
        assertTrue(body.contentType.toString().startsWith("application/json"))
        assertEquals("1", seen!!.headers["X-Trace"])
    }
}
