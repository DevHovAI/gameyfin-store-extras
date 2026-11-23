# gameyfin-store-extras

A [Gameyfin](https://gameyfin.org) v2 metadata plugin (`GameMetadataProvider`) that fills the fields the
common providers leave empty:

| Field | Source |
|---|---|
| `userRating` (0-100) | Steam reviews (share of positive votes); SteamSpy if Steam's endpoint is blocked; GOG rating as fallback |
| `criticRating` (0-100) | Metacritic score listed on the Steam store page |
| `headerUrls` | Steam `library_hero.jpg` (1920x620 banner, if it exists), then `header.jpg`; GOG background/cover as fallback |
| `videoUrls` | Steam trailers (mp4 or webm if Steam offers them, otherwise HLS, then DASH); GOG trailers (YouTube) as fallback |
| `developedBy`, `publishedBy`, `platforms` | as delivered by the same store listing |

It does **not** add store links: the official [Steam Store](https://github.com/gameyfin/plugin-steam-store) and
[GOG Store](https://github.com/gameyfin/plugin-gog-store) plugins already provide `GameStoreProvider`s for both.

Requires Gameyfin >= 2.7.0 (built against plugin-api 2.7.1).

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
4. Give it a priority above your other metadata providers, so its rating, banners and trailers win.

## Notes

- No account or API key is needed. Requests carry a generic user agent and nothing else.
- Steam's `appreviews` endpoint answers `302 -> /` from some networks. The plugin then uses SteamSpy and does
  not ask Steam again for an hour.
- Steam no longer publishes mp4/webm trailers through `appdetails`, only HLS (`.m3u8`) and DASH (`.mpd`).
  Gameyfin's player (react-player) plays both.

## Build

JDK 25 is required.

```sh
./gradlew build          # tests (recorded fixtures, no network) + JAR in build/libs/
```

The version lives in `src/main/resources/MANIFEST.MF` (`Plugin-Version`).

## License

AGPL-3.0, see `LICENSE.md`.
