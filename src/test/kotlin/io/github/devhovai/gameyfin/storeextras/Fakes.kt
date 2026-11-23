package io.github.devhovai.gameyfin.storeextras

/** Serves recorded fixtures instead of touching the network. First matching route wins. */
class FakeHttp(
    private val routes: List<Pair<String, HttpResult?>>,
    private val existing: Set<String> = emptySet(),
) : HttpFetcher {
    val requested = mutableListOf<String>()

    override fun get(url: String): HttpResult? {
        requested += url
        return routes.firstOrNull { url.contains(it.first) }?.second ?: HttpResult(404, "")
    }

    override fun exists(url: String): Boolean = existing.any { url.contains(it) }
}

fun fixture(name: String): HttpResult =
    HttpResult(200, object {}.javaClass.getResource("/fixtures/$name")!!.readText())

val steamReviewsRedirect = HttpResult(302, "")
