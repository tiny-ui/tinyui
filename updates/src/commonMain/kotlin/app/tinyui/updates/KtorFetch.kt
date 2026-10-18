package app.tinyui.updates

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import okio.IOException

/**
 * A `fetch` for [Updates] over the host's ktor [client]: GET `<base>/<path>`. [base] is read on every call, so a
 * host switches channel or server without rebuilding [Updates]. The client stays the host's: timeouts, proxy,
 * engine and `expectSuccess` are its business.
 *
 * What this adds is the failure message, since that is all [UpdateEvent.Failed] can carry out of the transport and
 * hosts report it within a short telemetry value (docs/updates.md §4.1):
 * - a non-2xx response → `HTTP <status> cf-ray=<id or -> server=<name or -> body=<first 60 chars>`. Every response
 *   from Cloudflare carries `cf-ray`; a 5xx without it was forged by something between the device and the server
 * - a timeout → ktor's sentence without its trailing `[url=…]`
 * - anything else (DNS, connect, TLS) → the engine's own words, unchanged
 */
fun ktorFetch(client: HttpClient, base: () -> String): suspend (path: String) -> ByteArray = { path ->
    try {
        val response = client.get("${base().trimEnd('/')}/$path")
        if (!response.status.isSuccess()) throw FetchFailure(response.describe())
        response.readRawBytes()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FetchFailure) {
        throw e
    } catch (e: ResponseException) {
        throw FetchFailure(e.response.describe(), e)
    } catch (e: Exception) {
        throw FetchFailure(e.message?.substringBefore(" [url=") ?: e.toString(), e)
    }
}

private class FetchFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

private suspend fun HttpResponse.describe(): String {
    val body = runCatching { bodyAsText() }.getOrDefault("").replace(Regex("\\s+"), " ").trim().take(60)
    return "HTTP ${status.value} cf-ray=${headers["cf-ray"] ?: "-"} server=${headers["server"] ?: "-"} body=$body"
}
