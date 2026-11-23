# Changelog

## 1.0.1

- Fix: search results of Store Extras were never merged with those of Steam, IGDB and the other providers, so the
  provider's ID was not stored with the game and its ratings, banners and trailers were never refreshed. Results now
  carry the release date (Steam, GOG as fallback), which Gameyfin uses together with the title to group results.
  The date is start of day UTC, the same instant the official Steam Metadata plugin reports.

## 1.0.0

- Metadata provider "Store Extras": Steam user rating, Metacritic score, banner images and trailers,
  with GOG as fallback source.
