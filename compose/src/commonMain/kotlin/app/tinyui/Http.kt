package app.tinyui

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse as KtorResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent

/** Wraps a host's ktor client as a channel: its plugins (auth, refresh, common headers) apply to the page's requests. */
class KtorChannel(private val client: HttpClient) : HttpChannel {
    override suspend fun request(request: HttpRequest): HttpResponse {
        val response = try {
            client.request(request.url) {
                method = HttpMethod.parse(request.method.uppercase())
                val contentType = request.headers.entries.firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }?.value
                headers { request.headers.filterKeys { !it.equals(HttpHeaders.ContentType, ignoreCase = true) }.forEach { (k, v) -> append(k, v) } }
                // ktor owns Content-Type: it travels on the body, not as a header
                request.body?.let { setBody(TextContent(it, contentType?.let(ContentType::parse) ?: ContentType.Text.Plain)) }
            }
        } catch (e: ResponseException) {
            e.response
        }
        return response.toTinyUI()
    }

    private suspend fun KtorResponse.toTinyUI() = HttpResponse(
        status = status.value,
        headers = headers.entries().associate { (k, v) -> k.lowercase() to v.joinToString(", ") },
        body = bodyAsText(),
    )
}

/** The engine each platform ships; the `default` channel adds nothing to it. */
internal expect fun defaultHttpClient(): HttpClient
