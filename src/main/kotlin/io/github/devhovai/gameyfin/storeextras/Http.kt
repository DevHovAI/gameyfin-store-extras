package io.github.devhovai.gameyfin.storeextras

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Result of a plain HTTP GET. Redirects are never followed; a 3xx is reported as-is. */
data class HttpResult(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/** Minimal HTTP seam so the providers can be unit-tested without network access. */
interface HttpFetcher {
    /** Returns null on I/O errors or timeouts. */
    fun get(url: String): HttpResult?

    /** True if the URL answers 2xx to a HEAD request. */
    fun exists(url: String): Boolean
}

class JdkHttpFetcher : HttpFetcher {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val lastRequestAt = HashMap<String, Long>()

    override fun get(url: String): HttpResult? = send(url, head = false)?.let { HttpResult(it.statusCode(), it.body() ?: "") }

    override fun exists(url: String): Boolean = send(url, head = true)?.statusCode() in 200..299

    private fun send(url: String, head: Boolean): HttpResponse<String>? {
        return try {
            val uri = URI.create(url)
            throttle(uri.host)
            val builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, */*;q=0.5")
                .header("Accept-Language", "en")
            val request = if (head) builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build() else builder.GET().build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /** Keeps us polite: at most one request per host and interval (SteamSpy asks for 1 request/s). */
    private fun throttle(host: String) {
        val interval = if (host.contains("steamspy")) 1_100L else 250L
        val wait = synchronized(lastRequestAt) {
            val now = System.currentTimeMillis()
            val next = maxOf(now, (lastRequestAt[host] ?: 0L) + interval)
            lastRequestAt[host] = next
            next - now
        }
        if (wait > 0) Thread.sleep(wait)
    }

    companion object {
        // Deliberately generic: no personal data, no instance information.
        const val USER_AGENT = "Mozilla/5.0 (compatible; GameyfinStoreExtras/1.0)"
    }
}
