# Phase 3.1.6 — panels, pulses and worldwide attractions

Completed 25 October 2026. No redesign, original PNG changes, radar geometry changes, search/keyboard changes, GPS-provider changes or routing implementation.

1. **Root causes.** Place/alert details were children of the clipped map viewport; the later radar sibling painted and received input above them. Pulse drawing and animation visibility explicitly excluded clusters, leaving cluster-only views with static native highlights. Marker projection used canonical longitude without choosing the camera's nearest world copy. Attraction discovery was already independent of vector tiles, but the overview contained only 40 curated entries across 29 country labels and had no regional loading path.

2. **Panel fix.** `DetailPanelOverlay` is the last root overlay with explicit z-index, above map, radar and fixed HUD controls. Its opaque navy surface consumes panel taps; controls keep their own handlers. Header/Close and Navigate stay outside the scrolling body. Bounds account for the available viewport and existing bottom shelf/insets. Existing selection, layer-off dismissal, destination-only Navigate and Android Back handling remain in use. Small-screen instrumentation and [attraction](screenshots/phase316-attraction-detail.png)/[alert](screenshots/phase316-alert-detail.png) screenshots confirm the radar stays behind the panel.

3. **Pulses.** The existing shared `MarkerAnimationState`/Canvas now includes clusters: green 2.4s, red 1.6s, cyan 1.8s and subtle clusters 2.8s. Rings expand/fade below unchanged, nearest-neighbor PNGs. Native cluster sources, stationary glow and hit layers remain; no second animation loop or marker Views were added. Projection selects the nearest longitude copy during pan and source refresh. Offscreen rings are culled, disabled layers remove their effects, and existing reduced-motion/foreground lifecycle controls stop animation.

4. **Data implementation.** Extended `GlobalAttractionsRepository` with `AttractionDataSource`, `AttractionEntity`, `AttractionSpatialIndex` and atomic `AttractionCache`. Stable Wikidata IDs, real coordinates, country IDs/labels, optional locality, categories, entity URLs, revisions, localized names and source modification timestamps survive imports and updates. Invalid coordinates/identities and conflicting duplicate payloads are rejected; imports, overlapping packs and viewport results deduplicate by stable ID. Existing records survive partial imports unless explicitly deleted.

   The 94-record overview always feeds native MapLibre clustering, including at world zoom. From zoom 4, only intersecting local country packs are read; five-degree spatial buckets select viewport records and a four-pack LRU bounds retained indexes. File reads/parsing run on `Dispatchers.IO`, with camera requests debounced/cancelled. The same enabled marker list feeds the existing map/GPS radar calculations. No Android camera-triggered public-service downloads occur. Regional full snapshots and versioned deltas are supported; adding regions or expanding pack bounds requires publishing a matching newer index and reopening the repository. Automatic delivery/scheduling is not configured.

5. **Coverage and limitations.** **94 overview attractions across 77 sourced country/jurisdiction IDs, plus 12 distinct regional landmarks in France and the UK: 106 records total.** All original 40 IDs remain. The reviewed selection spans six inhabited continents: examples include Eiffel Tower, Garni/Geghard, Borobudur, Hassan II Mosque, Mount Kenya, Statue of Liberty, Tikal, Machu Picchu, Galápagos, Sydney Opera House and Milford Sound. Exact selections are in the two import manifests. Every represented country is marked **incomplete**; none is certified complete. [Coverage audit](phase-3-1-6-coverage.json) lists counts and 57 unmatched Natural Earth basemap area labels, including territories; this is not a census of sovereign states. Unlisted countries are unavailable in this catalog. Public metadata is not independently verified, and alerts remain explicit demo data.

   Data comes from [Wikidata structured data under CC0](https://www.wikidata.org/wiki/Wikidata:Licensing), with per-record source URLs and bundled NOTICE attribution. Imports use bounded reviewed manifests, official entity exports, local snapshots and optional country-pack output. Online runs are limited to 500 selected identities; offline imports support up to 20,000 records/4 million characters per pack. Larger datasets require additional bounded packs rather than a planet file.

6. **Files changed.**

   - UI/rendering: `TrackerScreen.kt`, `TrackerPlaceDetailPanel.kt`, `MapLayerUi.kt`, `MapRenderer.kt`, `MapLayers.kt`.
   - Data: `GlobalAttractions.kt`, new `AttractionSpatialIndex.kt`, `global-attractions.json`, `attraction-regions/{index,Q142,Q145}.json`, `NOTICE.txt`.
   - Import: `scripts/update-attractions.py`, `scripts/attractions-selection.json`, `scripts/attractions-regional-selection.json`.
   - Tests: `Phase312StateTest.kt`, new `Phase316AttractionsTest.kt`, `GlobalAttractionsTest.kt`, `MapLayerInteractionTest.kt`, `GeographicRadarTest.kt`, `PixelAssetsTest.kt`, new `Phase316OverlayTest.kt` and `Phase316MarkerTest.kt`.
   - Delivery: README, this report, coverage JSON, two screenshots and the APK below. Development entity snapshots/logs remain under `.tools/phase316-*`, outside the APK.

7. **Validation.** 40 JVM tests passed. All 35 focused Android instrumentation tests passed on `Medium_Phone_API_37.0`, including physical pointer handling, Close/Navigate/Back, long scrolling, all pulse colors and clusters, unchanged sprite-center pixels, pan/zoom projection, disabled layers, reduced motion/background, world/country/city detail, deduplication, provenance, cache fallback and radar integration. A further 7/7 catalog instrumentation tests passed after the final regional-update hardening. Lint: 0 errors, 10 existing warnings and 1 hint. Exporter self-tests and offline snapshot reproduction passed. Two initial radar test failures were corrected by waiting for regional data and tapping inside the clipped radar. A later optional live Action API smoke check returned `maxlag`; it aborted without changing the catalog. Full physical-device/orientation validation was not run.

8. **Final debug APK.** `dist/spider-tracker-phase3-1-6-debug.apk` (60,889,743 bytes).

   SHA-256: `67dde0caf64117c34cd9ca9c6b996789526cdfedeeea6beacfeafe9591160aea`.

Reproduce data with Python 3.10+ (the development snapshots are already in `.tools/phase316-entities`):

```text
python scripts/update-attractions.py --self-test
python scripts/update-attractions.py --entity-cache .tools/phase316-entities --offline --coverage-output docs/phase-3-1-6-coverage.json
python scripts/update-attractions.py --manifest scripts/attractions-regional-selection.json --entity-cache .tools/phase316-entities --offline --output .tools/phase316-region-full.json --regions-dir app/src/main/assets/attraction-regions
```

Omit `--offline` to refresh source snapshots into an empty/new cache directory; cached snapshots are intentionally reused. `--delta PATH` emits changed rows and explicit removals for the existing repository update APIs. The Windows sandbox run used the bundled Python executable and a workspace-local TEMP/TMP directory. Final Android checks used Gradle offline with the existing local JDK/dependency cache.

Stopped at Phase 3.1.6.
