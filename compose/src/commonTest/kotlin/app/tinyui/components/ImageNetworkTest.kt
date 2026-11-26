package app.tinyui.components

import app.tinyui.PageAssets
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ImageNetworkTest {
    /**
     * `Image` loads https only, and the client it shares with the `default` channel is a plain `HttpClient(engine)`:
     * Ktor follows redirects itself (OkHttp and Darwin are set not to) and stops at one from https down to http.
     */
    @Test
    fun aRedirectFromHttpsToHttpIsNotFollowed() = runTest {
        val asked = ArrayList<String>()
        val client = HttpClient(MockEngine { request ->
            asked += request.url.toString()
            if (request.url.protocol.name == "https") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://cdn.example.com/flag.png"))
            } else {
                respond("PNG", HttpStatusCode.OK)
            }
        })
        val response = client.get("https://cdn.example.com/flag.png")
        assertEquals(HttpStatusCode.Found, response.status, "the redirect comes back as is, no picture")
        assertEquals(listOf("https://cdn.example.com/flag.png"), asked, "the http address is never asked")
    }

    @Test
    fun anAssetImageIsTheSameRequestWhateverReaderItCameWith() {
        val a = AssetImage("shop", "assets/0123456789abcdef.png", PageAssets { null })
        val b = AssetImage("shop", "assets/0123456789abcdef.png", PageAssets { null })
        assertEquals(a, b, "a recomposition that rebuilds it must not start the request again")
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, AssetImage("cart", "assets/0123456789abcdef.png", PageAssets { null }), "another package")
    }

    @Test
    fun aLongSrcIsCutInReports() {
        val long = "https://cdn.example.com/" + "x".repeat(200)
        assertEquals(81, srcForReport(long).length)
        assertEquals("https://a.b/c.png", srcForReport("https://a.b/c.png"))
    }
}
