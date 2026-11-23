package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.pf4j.Extension
import org.slf4j.LoggerFactory

class StoreExtrasPlugin(context: PluginContext) : GameyfinPlugin(context) {

    /**
     * Supplies user rating, critic score, banner images and trailers only. Enable it next to a full metadata
     * provider (IGDB, Steam) and order it so that it wins for those fields.
     */
    @Suppress("Unused")
    @Extension(ordinal = 5)
    class StoreExtrasMetadataProvider : GameMetadataProvider {
        private val log = LoggerFactory.getLogger(javaClass)
        private val http = JdkHttpFetcher()
        private val extras = StoreExtras(SteamClient(http), GogClient(http))

        override val supportedPlatforms: Set<Platform> =
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.LINUX, Platform.MAC)

        override fun fetchByTitle(gameTitle: String, platformFilter: Set<Platform>, maxResults: Int): List<GameMetadata> =
            try {
                extras.fetchByTitle(gameTitle, platformFilter, maxResults)
            } catch (e: Exception) {
                log.warn("Store Extras lookup for '{}' failed: {}", gameTitle, e.toString())
                emptyList()
            }

        override fun fetchById(id: String): GameMetadata? =
            try {
                extras.fetchById(id)
            } catch (e: Exception) {
                log.warn("Store Extras lookup for id '{}' failed: {}", id, e.toString())
                null
            }
    }
}
