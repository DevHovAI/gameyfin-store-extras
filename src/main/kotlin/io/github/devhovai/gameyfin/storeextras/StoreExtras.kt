package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.net.URI

/**
 * Builds [GameMetadata] from the Steam and GOG stores: description (Markdown, with a badge line on top), features,
 * user rating, critic score, banner images and trailers. Title, platforms and release date are always set as well:
 * Gameyfin groups results of all providers by normalized title and release year, and without them this provider's
 * result never merges. Steam is the primary source, GOG fills gaps and covers titles Steam does not sell.
 */
class StoreExtras(private val steam: SteamClient, private val gog: GogClient) {

    fun fetchByTitle(title: String, platformFilter: Set<Platform>, maxResults: Int): List<GameMetadata> {
        val limit = maxResults.coerceAtLeast(1)
        val gogHits by lazy { gog.search(title) }
        val fromSteam = TitleMatcher.best(title, steam.search(title), limit) { it.name }
            .mapNotNull { hit -> fromSteam(hit.id, platformFilter, TitleMatcher.score(title, hit.name)) { _ -> gogHits } }
        if (fromSteam.isNotEmpty()) return fromSteam
        return TitleMatcher.best(title, gogHits, limit) { it.title }
            .filter { platformFilter.isEmpty() || it.platforms.isEmpty() || it.platforms.any(platformFilter::contains) }
            .map { fromGog(it, platformFilter, TitleMatcher.score(title, it.title)) }
    }

    fun fetchById(id: String): GameMetadata? {
        if (id.startsWith(GOG_PREFIX)) {
            val gogId = id.removePrefix(GOG_PREFIX)
            val title = gog.title(gogId) ?: return null
            val hit = gog.search(title).firstOrNull { it.id == gogId } ?: return null
            return fromGog(hit, emptySet(), 100)
        }
        val appId = id.toIntOrNull() ?: return null
        return fromSteam(appId, emptySet(), 100, gog::search)
    }

    /** [match] is how well Steam's title fits the query; badges for a store are only added when it is [CONFIDENT]. */
    private fun fromSteam(appId: Int, platformFilter: Set<Platform>, match: Int, gogHits: (String) -> List<GogHit>): GameMetadata? {
        val app = steam.details(appId) ?: return null
        if (app.type != "game") return null
        val platforms = if (platformFilter.isEmpty()) app.platforms else app.platforms.intersect(platformFilter)
        if (app.platforms.isNotEmpty() && platforms.isEmpty()) return null

        val headers = LinkedHashSet<URI>()
        steam.heroUrl(appId)?.let { headers += URI(it) }
        app.headerImage?.let { headers += URI(it) }
        val videos = app.movies.mapNotNull { it.bestUrl }.take(MAX_VIDEOS).map(::URI).toCollection(LinkedHashSet())
        val title = app.name.filterNot { it in "™©®" }.trim()
        val reviews = steam.reviews(appId)
        val confident = match >= CONFIDENT

        val other = TitleMatcher.best(title, gogHits(title), 1) { it.title }.firstOrNull()
            ?.takeIf { TitleMatcher.score(title, it.title) >= CONFIDENT }
        val gogDetails = other?.takeIf { app.descriptionHtml == null || videos.isEmpty() }?.let { gog.details(it.id) }

        val badges = buildList {
            if (confident) reviews?.percent()?.let { add(Badges.steam(it, reviews.sentiment(), appId)) }
            other?.storeLink?.let { add(Badges.gog(other.badgeRating, it)) }
            app.metacritic?.takeIf { it in 1..100 }?.let { add(Badges.metacritic(it, app.metacriticUrl)) }
            if (steam.deckVerified(appId)) add(Badges.steamDeckVerified())
            SteamCategories.modeLabels(app.categories).forEach { add(Badges.mode(it)) }
        }
        val body = (app.descriptionHtml ?: gogDetails?.descriptionHtml)?.let(HtmlMarkdown::convert)
        val features = SteamCategories.features(app.categories).ifEmpty { GogFeatures.features(other?.features.orEmpty()) }

        return GameMetadata(
            originalId = appId.toString(),
            title = title,
            platforms = platforms.ifEmpty { null },
            description = Badges.description(badges, body),
            headerUrls = headers.ifEmpty { null }
                ?: other?.let { gogHeaders(it) },
            release = app.release ?: other?.release,
            userRating = reviews?.percent() ?: other?.userRating(),
            criticRating = app.metacritic?.takeIf { it in 1..100 },
            developedBy = app.developers.ifEmpty { null },
            publishedBy = app.publishers.ifEmpty { null },
            videoUrls = videos.ifEmpty { null }
                ?: gogDetails?.videos?.take(MAX_VIDEOS)?.map(::URI)?.toCollection(LinkedHashSet())?.ifEmpty { null },
            features = features.ifEmpty { null },
        )
    }

    private fun fromGog(hit: GogHit, platformFilter: Set<Platform>, match: Int): GameMetadata {
        val platforms = if (platformFilter.isEmpty()) hit.platforms else hit.platforms.intersect(platformFilter)
        val details = gog.details(hit.id)
        val features = GogFeatures.features(hit.features)
        val badges = buildList {
            if (match >= CONFIDENT) hit.storeLink?.let { add(Badges.gog(hit.badgeRating, it)) }
            addAll(GogFeatures.modeLabels(features).map(Badges::mode))
        }
        val body = details.descriptionHtml?.let(HtmlMarkdown::convert)
        return GameMetadata(
            originalId = GOG_PREFIX + hit.id,
            title = hit.title,
            platforms = platforms.ifEmpty { null },
            description = Badges.description(badges, body),
            headerUrls = gogHeaders(hit),
            release = hit.release,
            userRating = hit.userRating(),
            developedBy = hit.developers.ifEmpty { null },
            publishedBy = hit.publishers.ifEmpty { null },
            videoUrls = details.videos.take(MAX_VIDEOS).map(::URI).toCollection(LinkedHashSet()).ifEmpty { null },
            features = features.ifEmpty { null },
        )
    }

    private fun gogHeaders(hit: GogHit): Set<URI>? =
        listOfNotNull(hit.background, hit.coverHorizontal).map(::URI).toCollection(LinkedHashSet()).ifEmpty { null }

    companion object {
        const val GOG_PREFIX = "gog-"
        const val MAX_VIDEOS = 4

        /** Minimum [TitleMatcher.score] for a store to get a badge: "Anno 1503" -> "Anno 1503 History Edition" (84) passes, sequels (<= 70) do not. */
        const val CONFIDENT = 84
    }
}
