# gameyfin-store-extras

A [Gameyfin](https://gameyfin.org) v2 metadata plugin (`GameMetadataProvider`) that turns the Steam and GOG store
pages into rich game metadata: a description with rating badges, features, ratings, banners and trailers.

| Field | Source |
|---|---|
| `description` | Markdown: a line of badges (see below), a blank line, then the store text converted from HTML (Steam `detailed_description`; GOG description for titles Steam does not sell) |
| `features` | Steam categories mapped to Gameyfin features (single-player, multiplayer, co-op, online/local co-op, PvP, split screen, controller support, achievements, cloud saves, ...); GOG's feature list if Steam has none |
| `userRating` (0-100) | Steam reviews (share of positive votes); SteamSpy if Steam's endpoint is blocked; GOG rating as fallback |
| `criticRating` (0-100) | Metacritic score listed on the Steam store page |
| `headerUrls` | Steam `library_hero.jpg` (1920x620 banner, if it exists), then `header.jpg`; GOG background/cover as fallback |
| `videoUrls` | Up to four trailers, YouTube first: GOG's YouTube trailers (and YouTube links in Steam's movie list, if any). Only when there is no YouTube trailer: Steam's own files (mp4 or webm if offered, otherwise HLS, then DASH) |
| `title`, `release`, `platforms`, `developedBy`, `publishedBy` | as delivered by the same store listing |

It does **not** add store links: the official [Steam Store](https://github.com/gameyfin/plugin-steam-store) and
[GOG Store](https://github.com/gameyfin/plugin-gog-store) plugins already provide `GameStoreProvider`s for both.

Requires Gameyfin >= 2.7.0 (built against plugin-api 2.7.1).

## Priority: put Store Extras first

Gameyfin merges metadata field by field: for every field the provider with the highest priority wins, lower ones
only fill what is still empty. Plugins cannot add widgets to Gameyfin's UI, so everything Store Extras shows has to
fit into the fields Gameyfin already has. Hence the description is Markdown (Gameyfin renders it with
`react-markdown`; raw HTML is not rendered):

```
[![Steam](https://img.shields.io/badge/Steam-97%25%20Overwhelmingly%20Positive-1b2838?logo=steam)](https://store.steampowered.com/app/1086940/) [![GOG](https://img.shields.io/badge/GOG-4.6%2F5-86328a?logo=gogdotcom)](https://www.gog.com/en/game/baldurs_gate_iii) ![Single-player](https://img.shields.io/badge/Single--player-4c566a) ...

Gather your party and return to the Forgotten Realms ...
```

Badges are static [shields.io](https://shields.io) images, so the browser fetches them from img.shields.io. They are
only added when they apply and the store match is confident: Steam score with Steam's own label, GOG score,
Metacritic score, "Steam Deck Verified", and the game modes (Single-player, Online Co-op, Local Co-op, Online PvP,
Local PvP, MMO, Controller support) and, from PCGamingWiki, player counts per mode (see below). The text is English, with videos removed; headings, lists, links and images
are kept. If a store has no text for a game, no description is returned at all, so a lower priority provider's
description still shows.

**Set Store Extras to the highest priority** (Administration -> Plugins). Since 1.1.0 it also supplies the
description and the features, and only wins those fields if it is first. If you prefer another provider's
description, keep Store Extras second: it then still fills the rating, banner, trailer and feature gaps.

## Player counts (PCGamingWiki)

Since 1.2.0 the badge line also shows how many people can play together, for each mode PCGamingWiki lists as
available, e.g. for Project Zomboid:

```
![Local 4 players](https://img.shields.io/badge/Local-4%20players-4c566a) ![LAN 100 players](https://img.shields.io/badge/LAN-100%20players-4c566a) ![Online 100 players](https://img.shields.io/badge/Online-100%20players-4c566a)
```

- A mode only gets a badge if the wiki marks it as available. Counts may be numbers or ranges (`2-4`); with no
  count the badge shows just the mode (`Online multiplayer`). `Co-op` / `Versus` are appended where the wiki names them.
- The page is found via the Steam app id (GOG id for GOG-only titles), the id in its infobox is verified, and a
  title search is the fallback. Single-player games and unknown ids simply get no such badge.
- Results are cached in memory (24 hours; failures for one hour) and PCGamingWiki is asked at most once per second.
  If it is down or answers nonsense, everything else is delivered as before.

Attribution: multiplayer data comes from [PCGamingWiki](https://www.pcgamingwiki.com), whose content is available under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). Store Extras reads only facts (yes/no, player
counts, mode names) through the public MediaWiki API; no wiki text is copied or stored. Thanks to the PCGamingWiki
contributors.

## How matching works

The title Gameyfin asks for is searched on the Steam store. A result is accepted when its normalised title is
equal, or the query plus a few harmless words ("History Edition"). Sequels never match their predecessor
("Baldur's Gate" does not match "Baldur's Gate 3"). If Steam has no match the GOG catalog is used; if Steam
lacks a rating, banner or trailer, a matching GOG listing fills the gap. Ratings with fewer than 10 votes
are ignored.

## Install

### From the marketplace (no restart)

Administration -> Plugins -> add `https://github.com/DevHovAI/gameyfin-store-extras` as a marketplace
source (Gameyfin reads `plugins.json` from this repository), then install **Store Extras** and enable it.

### Manually

1. Download `gameyfin-store-extras-<version>.jar` from the release page.
2. Copy it into the host folder that is mounted at `/opt/gameyfin/plugins`, then restart Gameyfin.
3. Administration -> Plugins -> **Store Extras** -> enable it (third-party plugins start disabled).
4. Give it a priority above your other metadata providers, so its description, features, rating, banners and trailers win.
5. Open a game -> edit metadata -> match again (or run a library scan) so the new fields are fetched.

## Notes

- No account or API key is needed. Requests carry a generic user agent and nothing else.
- Steam's `appreviews` endpoint answers `302 -> /` from some networks. The plugin then uses SteamSpy and does
  not ask the reviews endpoint again for an hour. Search, store details, the Steam Deck report and GOG are never
  affected by that pause.
- Steam no longer publishes mp4/webm trailers through `appdetails`, only HLS (`.m3u8`) and DASH (`.mpd`).
  Gameyfin's player (react-player) plays both.
- **Why YouTube trailers come first:** Gameyfin shows videos with react-player in `light` mode, which first shows a
  preview thumbnail and only loads the video on click. react-player can fetch that thumbnail for YouTube (and Vimeo)
  links only. For Steam's HLS/DASH/mp4 files it has none, so the player shows an empty placeholder, and Gameyfin has
  no thumbnail field for videos to fill that gap. Steam trailers are therefore only a fallback for games without a
  YouTube trailer. GOG lists its trailers as YouTube links, so titles that exist on GOG usually get one.

## Build

JDK 25 is required.

```sh
./gradlew build          # tests (recorded fixtures, no network) + JAR in build/libs/
```

The version lives in `src/main/resources/MANIFEST.MF` (`Plugin-Version`).

## License

AGPL-3.0, see `LICENSE.md`.
