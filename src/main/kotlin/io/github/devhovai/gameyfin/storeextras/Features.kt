package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.GameFeature.*

/** Steam store categories (`appdetails.categories[].id`) mapped onto Gameyfin features and mode badges. */
object SteamCategories {
    private val features: Map<Int, Set<GameFeature>> = mapOf(
        2 to setOf(SINGLEPLAYER),
        1 to setOf(MULTIPLAYER),
        49 to setOf(MULTIPLAYER), // PvP
        36 to setOf(ONLINE_PVP, ONLINE_MULTIPLAYER, MULTIPLAYER),
        37 to setOf(LOCAL_PVP, LOCAL_MULTIPLAYER, SPLITSCREEN, MULTIPLAYER),
        47 to setOf(MULTIPLAYER), // LAN PvP
        9 to setOf(CO_OP, MULTIPLAYER),
        38 to setOf(ONLINE_CO_OP, CO_OP, ONLINE_MULTIPLAYER, MULTIPLAYER),
        39 to setOf(LOCAL_CO_OP, CO_OP, LOCAL_MULTIPLAYER, SPLITSCREEN, MULTIPLAYER),
        48 to setOf(CO_OP, MULTIPLAYER), // LAN Co-op
        24 to setOf(SPLITSCREEN, LOCAL_MULTIPLAYER, MULTIPLAYER),
        20 to setOf(MULTIPLAYER, ONLINE_MULTIPLAYER), // MMO
        27 to setOf(CROSS_PLATFORM, CROSSPLAY, MULTIPLAYER),
        28 to setOf(CONTROLLER_SUPPORT),
        18 to setOf(CONTROLLER_SUPPORT),
        22 to setOf(ACHIEVEMENTS),
        25 to setOf(LEADERBOARDS),
        23 to setOf(CLOUD_SAVES),
        30 to setOf(WORKSHOP),
        41 to setOf(REMOTE_PLAY), 42 to setOf(REMOTE_PLAY), 43 to setOf(REMOTE_PLAY), 44 to setOf(REMOTE_PLAY),
        31 to setOf(VR), 53 to setOf(VR), 54 to setOf(VR),
    )

    fun features(categoryIds: Set<Int>): Set<GameFeature> = categoryIds.flatMapTo(LinkedHashSet()) { features[it].orEmpty() }

    /** Mode badge labels in display order; each label is only listed when it adds something over a more specific one. */
    fun modeLabels(ids: Set<Int>): List<String> = buildList {
        fun has(vararg id: Int) = id.any { it in ids }
        if (has(2)) add("Single-player")
        if (has(38)) add("Online Co-op")
        if (has(39)) add("Local Co-op") else if (has(24) && !has(37)) add("Split Screen")
        if (has(9, 48) && !has(38, 39)) add("Co-op")
        if (has(36)) add("Online PvP")
        if (has(37)) add("Local PvP")
        if (has(49) && !has(36, 37)) add("PvP")
        if (has(20)) add("MMO")
        if (has(28)) add("Controller support") else if (has(18)) add("Partial controller support")
    }
}

/** GOG catalog `features[].slug` / name mapped onto Gameyfin features. */
object GogFeatures {
    fun features(names: Set<String>): Set<GameFeature> = names.flatMapTo(LinkedHashSet()) { name ->
        when (name.lowercase().replace(Regex("""[^a-z]+"""), "")) {
            "singleplayer", "single" -> setOf(SINGLEPLAYER)
            "multiplayer", "multi" -> setOf(MULTIPLAYER)
            "coop", "cooperative" -> setOf(CO_OP, MULTIPLAYER)
            "onlinecoop" -> setOf(ONLINE_CO_OP, CO_OP, ONLINE_MULTIPLAYER, MULTIPLAYER)
            "splitscreen", "sharedsplitscreen" -> setOf(SPLITSCREEN, LOCAL_MULTIPLAYER, MULTIPLAYER)
            "crossplatformmultiplayer", "crossplatform" -> setOf(CROSS_PLATFORM, CROSSPLAY, MULTIPLAYER)
            "controllersupport", "controller" -> setOf(CONTROLLER_SUPPORT)
            "achievements" -> setOf(ACHIEVEMENTS)
            "leaderboards" -> setOf(LEADERBOARDS)
            "cloudsaves" -> setOf(CLOUD_SAVES)
            else -> emptySet()
        }
    }

    /** Mode badge labels for the features GOG lists. */
    fun modeLabels(features: Set<GameFeature>): List<String> = buildList {
        if (SINGLEPLAYER in features) add("Single-player")
        if (ONLINE_CO_OP in features) add("Online Co-op")
        if (CO_OP in features && ONLINE_CO_OP !in features) add("Co-op")
        if (MULTIPLAYER in features && CO_OP !in features) add("Multiplayer")
        if (CONTROLLER_SUPPORT in features) add("Controller support")
    }
}
