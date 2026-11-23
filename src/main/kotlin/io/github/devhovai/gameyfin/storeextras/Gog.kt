package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.Platform

data class GogHit(
    val id: String,
    val title: String,
    val productType: String?,
    /** GOG scores reviews 0..50 (4.6 stars -> 46). */
    val reviewsRating: Int?,
    val reviewsCount: Int?,
    val coverHorizontal: String?,
    val background: String?,
    val developers: Set<String>,
    val publishers: Set<String>,
    val platforms: Set<Platform>,
) {
    /** 0..100, or null when GOG has no rating or too few reviews. */
    fun userRating(): Int? =
        reviewsRating?.takeIf { it > 0 && (reviewsCount ?: 0) >= ReviewCounts.MIN_VOTES }?.let { (it * 2).coerceIn(0, 100) }
}

class GogClient(private val http: HttpFetcher) {

    fun search(title: String): List<GogHit> {
        val url = "https://catalog.gog.com/v1/catalog?query=like:${enc(title)}&limit=10&locale=en-US&countryCode=US&productType=in:game,pack"
        val res = http.get(url)?.takeIf { it.ok } ?: return emptyList()
        val products = parseObject(res.body)?.get("products").arr() ?: return emptyList()
        return products.mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            GogHit(
                id = o["id"].str() ?: return@mapNotNull null,
                title = o["title"].str() ?: return@mapNotNull null,
                productType = o["productType"].str(),
                reviewsRating = o["reviewsRating"].int(),
                reviewsCount = o["reviewsCount"].int(),
                coverHorizontal = o["coverHorizontal"].str(),
                background = o["galaxyBackgroundImage"].str(),
                developers = o["developers"].strings(),
                publishers = o["publishers"].strings(),
                platforms = o["operatingSystems"].strings().mapNotNull {
                    when (it.lowercase()) {
                        "windows" -> Platform.PC_MICROSOFT_WINDOWS
                        "linux" -> Platform.LINUX
                        "osx", "mac" -> Platform.MAC
                        else -> null
                    }
                }.toSet(),
            )
        }
    }

    /** Trailer links GOG lists for a product. They are YouTube links, which Gameyfin's player supports. */
    fun videos(id: String): List<String> {
        val res = http.get("https://api.gog.com/products/${enc(id)}?expand=videos")?.takeIf { it.ok } ?: return emptyList()
        val videos = parseObject(res.body)?.get("videos").arr() ?: return emptyList()
        return videos.mapNotNull { v -> v.obj()?.get("video_url").str()?.let(::normalizeVideoUrl) }
    }

    /** Title of a product, used to resolve a stored GOG id back to a catalog entry. */
    fun title(id: String): String? =
        http.get("https://api.gog.com/products/${enc(id)}")?.takeIf { it.ok }?.let { parseObject(it.body) }?.get("title").str()

    companion object {
        private val embed = Regex("^https?://(?:www\\.)?youtube(?:-nocookie)?\\.com/embed/([\\w-]{11}).*$")

        fun normalizeVideoUrl(url: String): String =
            embed.matchEntire(url)?.let { "https://www.youtube.com/watch?v=${it.groupValues[1]}" } ?: url
    }
}
