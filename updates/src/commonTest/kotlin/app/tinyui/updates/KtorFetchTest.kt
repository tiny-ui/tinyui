package app.tinyui.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorFetchTest {
    private fun client(expectSuccess: Boolean = false, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) { this.expectSuccess = expectSuccess }

    private suspend fun failure(client: HttpClient, base: String = "https://u.example/app/production"): String =
        assertFailsWith<IOException> { ktorFetch(client) { base }("shop/1/current.json") }.message!!

    @Test
    fun getsTheBytesAtBaseSlashPathReadingBaseOnEveryCall() = runTest {
        var channel = "production"
        val urls = ArrayList<String>()
        val client = client { request -> urls += request.url.toString(); respond(byteArrayOf(1, 2, 3)) }
        val fetch = ktorFetch(client) { "https://u.example/app/$channel/" }
        assertContentEquals(byteArrayOf(1, 2, 3), fetch("shop/1/current.json"))
        channel = "staging"
        fetch("shop/1/current.json")
        assertEquals(listOf("https://u.example/app/production/shop/1/current.json", "https://u.example/app/staging/shop/1/current.json"), urls)
    }

    @Test
    fun aNonSuccessResponseIsStatusRayServerAndTheStartOfTheBody() = runTest {
        val html = "<html>\n  <body>\n    error code: 1033 " + "x".repeat(100)
        val headers = headersOf("cf-ray" to listOf("a4a58647cdcde4b1-LAX"), "Server" to listOf("cloudflare"))
        val expected = "HTTP 504 cf-ray=a4a58647cdcde4b1-LAX server=cloudflare body=<html> <body> error code: 1033 " + "x".repeat(60 - "<html> <body> error code: 1033 ".length)
        assertEquals(expected, failure(client { respond(html, HttpStatusCode.GatewayTimeout, headers) }))
        assertEquals(expected, failure(client(expectSuccess = true) { respond(html, HttpStatusCode.GatewayTimeout, headers) }), "the same words when the host's client throws on non-2xx")
    }

    @Test
    fun aResponseWithoutCloudflareHeadersShowsDashes() = runTest {
        assertEquals("HTTP 502 cf-ray=- server=- body=", failure(client { respond("", HttpStatusCode.BadGateway) }))
    }

    @Test
    fun aTimeoutLosesTheUrlSuffix() = runTest {
        assertEquals("Request timeout has expired", failure(client { throw HttpRequestTimeoutException("https://u.example/app/production/shop/1/current.json", 15_000) }))
    }

    @Test
    fun otherFailuresKeepTheEngineWords() = runTest {
        assertEquals("Unable to resolve host \"u.example\": No address associated with hostname", failure(client { throw IllegalStateException("Unable to resolve host \"u.example\": No address associated with hostname") }))
    }
}
