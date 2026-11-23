package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.net.URI

/**
 * Builds [GameMetadata] that only carries what other providers are missing: user rating, critic score,
 * banner images and trailers. Title, platforms and release date are always set as well: Gameyfin groups results of
 * all providers by normalized title and release year, and without them this provider's result never merges. Steam is the primary source, GOG fills gaps and covers titles Steam does not sell.
 */
class StoreExtras(private val steam: SteamClient, private val gog: GogClient) {

    fun fetchByTitle(title: String, platformFilter: Set<Platform>, maxResults: Int): List<GameMetadata> {
        val limit = maxResults.coerceAtLeast(1)
        val fromSteam = TitleMatcher.best(title, steam.search(title), limit) { it.name }
            .mapNotNull { fromSteam(it.id, platformFilter) }
        if (fromSteam.isEmpty()) {
            return TitleMatcher.best(title, gog.search(title), limit) { it.title }
                .filter { platformFilter.isEmpty() || it.platforms.isEmpty() || it.platforms.any(platformFilter::contains) }
                .map { fromGog(it, platformFilter) }
        }
        return fromSteam.map { fillGaps(it, title) }
    }

    fun fetchById(id: String): GameMetadata? {
        if (id.startsWith(GOG_PREFIX)) {
            val gogId = id.removePrefix(GOG_PREFIX)
            val title = gog.title(gogId) ?: return null
            val hit = gog.search(title).firstOrNull { it.id == gogId } ?: return null
            return fromGog(hit, emptySet())
        }
        val appId = id.toIntOrNull() ?: return null
        val base = fromSteam(appId, emptySet()) ?: return null
        return fillGaps(base, base.title)
    }

    private fun fromSteam(appId: Int, platformFilter: Set<Platform>): GameMetadata? {
        val app = steam.details(appId) ?: return null
        if (app.type != "game") return null
        val platforms = if (platformFilter.isEmpty()) app.platforms else app.platforms.intersect(platformFilter)
        if (app.platforms.isNotEmpty() && platforms.isEmpty()) return null

        val headers = LinkedHashSet<URI>()
        steam.heroUrl(appId)?.let { headers += URI(it) }
        app.headerImage?.let { headers += URI(it) }
        val videos = app.movies.mapNotNull { it.bestUrl }.take(MAX_VIDEOS).map(::URI).toCollection(LinkedHashSet())

        return GameMetadata(
            originalId = appId.toString(),
            title = app.name.filterNot { it in "™©®" }.trim(),
            platforms = platforms.ifEmpty { null },
            headerUrls = headers.ifEmpty { null },
            release = app.release,
            userRating = steam.reviews(appId)?.percent(),
            criticRating = app.metacritic?.takeIf { it in 1..100 },
            developedBy = app.developers.ifEmpty { null },
            publishedBy = app.publishers.ifEmpty { null },
            videoUrls = videos.ifEmpty { null },
        )
    }

    private fun fromGog(hit: GogHit, platformFilter: Set<Platform>): GameMetadata {
        val platforms = if (platformFilter.isEmpty()) hit.platforms else hit.platforms.intersect(platformFilter)
        val headers = listOfNotNull(hit.background, hit.coverHorizontal).map(::URI).toCollection(LinkedHashSet())
        val videos = gog.videos(hit.id).take(MAX_VIDEOS).map(::URI).toCollection(LinkedHashSet())
        return GameMetadata(
            originalId = GOG_PREFIX + hit.id,
            title = hit.title,
            platforms = platforms.ifEmpty { null },
            headerUrls = headers.ifEmpty { null },
            release = hit.release,
            userRating = hit.userRating(),
            developedBy = hit.developers.ifEmpty { null },
            publishedBy = hit.publishers.ifEmpty { null },
            videoUrls = videos.ifEmpty { null },
        )
    }

    /** Fills rating, banners and trailers that Steam did not deliver from a matching GOG listing. */
    private fun fillGaps(base: GameMetadata, query: String): GameMetadata {
        if (base.userRating != null && !base.videoUrls.isNullOrEmpty() && !base.headerUrls.isNullOrEmpty()) return base
        val hit = TitleMatcher.best(base.title, gog.search(query), 1) { it.title }.firstOrNull() ?: return base
        val other = fromGog(hit, emptySet())
        return base.copy(
            userRating = base.userRating ?: other.userRating,
            release = base.release ?: other.release,
            headerUrls = base.headerUrls ?: other.headerUrls,
            videoUrls = base.videoUrls ?: other.videoUrls,
        )
    }

    companion object {
        const val GOG_PREFIX = "gog-"
        const val MAX_VIDEOS = 4
    }
}
