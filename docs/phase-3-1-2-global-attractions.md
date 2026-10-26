# Phase 3.1.2 — global attractions

The user-facing green layer is Main Attractions. It no longer depends on loaded street POI tiles, viewport callbacks or London. `GlobalAttractionsRepository` supplies an offline catalog independently of MapLibre; the old tile repository remains internal for compatibility tests but is not the main-layer feed.

## Current grounded coverage

The initial curated catalog contains40 attractions across29 distinct constituent P17 country labels and six inhabited continents. Countries on a shared geographic record are listed together, not invented as one country. It is explicitly incomplete, including gaps within represented countries and countries with no catalog records. Categories are editorial:15 historic,9 natural,5 buildings,3 cultural,3 monuments,3 museums,2 parks. No UNESCO certification or importance ranking is inferred.

Names, nondeprecated Earth P625 coordinates, country/locality labels, stable Wikidata IDs, entity revisions and source links were retrieved from Wikidata on2026-10-24 UTC. English/French/Spanish/Japanese labels exist for all40 records, Armenian for39. The repository uses requested-language labels when available and falls back to the grounded default name. Country/locality reflect source statements and may represent an administrative region rather than a city. Addresses/descriptions/ratings are not invented.

Wikidata structured data is [CC0](https://www.wikidata.org/wiki/Wikidata:Licensing). The asset carries schema/version/license/source/incomplete/retrieval metadata; each record has its entity source URL. App details identify Wikidata/CC0 and incomplete curation. This is data attribution, not independent verification. No proprietary artwork or photos were downloaded.

## Storage and rendering

`CachedGlobalAttractionsRepository` reads the bundled asset once, prefers a valid newer private cache and falls back on corrupt/older cache. The source reader captures application assets, avoiding Activity retention after rotation. Payloads require schema1, CC0/Wikidata metadata, valid stable IDs/source links/categories, finite coordinates and unique IDs; memory is bounded by4MB payload and20,000 records. A delta may not both delete and upsert the same ID. Newer deltas merge upserts and explicit deletions into a validated complete snapshot and use AtomicFile; invalid input cannot partially overwrite the last good cache.

A native GeoJSON source holds the enabled catalog. MapLibre's spatial index/clustering uses radius42 logical map pixels and maximum cluster zoom15. World zoom aggregates rather than clears data; regional views progressively separate nearby records; street views show supplied green PNGs at unchanged coordinates. Stable IDs survive filtering and zoom. Camera movement updates projection/cluster visibility without downloading the catalog. Drawing caps visible projected sprites at1000 and favors the acquired user fix. An unrepresented area remains empty and filters explain that no catalog attractions are in that view; no fallback attractions are generated.

Legacy nonempty POI-category preferences migrate to available attraction categories; deliberately empty selections remain empty. Independent green/red flags and new category selections persist in the existing private SharedPreferences.

## Incremental update workflow

`scripts/update-attractions.py --self-test` validates the exporter offline. Normal maintenance fetches batches of at most50 entity IDs, at least five seconds apart, with bounded Retry-After behavior. Exact English identity guards rejected incorrect QIDs during curation. Network/missing-entity failures never imply deletion or replace the good asset. `--delta PATH` exports changed/new records; `--remove QID` makes deletion explicit. Output is atomically written after complete validation. Add curated IDs/identity/category guards to grow coverage in any country through the same pipeline.

Runtime `applyUpdates` is a validated repository ingestion boundary; update scheduling/distribution is not configured and there is no per-pan request or background update service. The delivered APK includes the stated snapshot. A future update caller can apply a delta and reload the repository/application layer; no current UI promises continuously updated worldwide data.

Catalog tests validate metadata/offline/localization, atomic updates/rollback, malformed/conflicting deltas and preference migration. Native tests cover world/country/street clusters, France and Japan through the same feed, geographic projection and an honest empty Pacific view. The country list, category counts and catalog hash are in phase-3-1-2-evidence/ui-and-catalog-measurements.json.
