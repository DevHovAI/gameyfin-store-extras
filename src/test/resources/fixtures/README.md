# Test fixtures

Recorded from the public endpoints on 2026-11-23 and trimmed (descriptions, screenshots, tags removed):
`steam_search_*`, `steam_details_*`, `steamspy_*`, `gog_catalog_*`, `gog_product_bg3.json`.

Hand-written, because Steam's `appreviews` endpoint answers `302 -> /` from the recording network:
`steam_reviews_synthetic.json` (shape taken from Steam's documented `query_summary`), `steam_search_empty.json`.

`steam_details_anno1503.json` (Anno 1503 History Edition, app 2820110) was recorded the same way on 2026-11-23.

`pcgw_project_zomboid.json`, `pcgw_baldurs_gate_3.json`, `pcgw_disco_elysium.json` (single-player only, no multiplayer
template) and `pcgw_search_zomboid.json` were recorded from PCGamingWiki's MediaWiki API on 2026-11-24 and cut down to
the infobox ids and the player count facts of `Network/Multiplayer` (no notes or other wiki text).
`pcgw_synthetic_ranges.json` is hand-written (ranges, `unknown`, empty values, co-op/versus), because no real page
with a range turned up.
