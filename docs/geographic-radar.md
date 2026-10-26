# Functional geographic radar

## Data and origins

`RadarGeography.kt` replaces the old zoom-dependent decorative model. `geographicRadar` consumes the already enabled/filtered `MapFiltersViewModel.visibleMarkers`: grounded records loaded once from GlobalAttractionsRepository, plus LiveAlertsRepository records. No random points, destination substitutes or whole-world radius guesses are used. Current catalog:40 Wikidata/CC0 attractions across29 country labels, incomplete. Six alert records are bundled demonstrations, never a live traffic feed.

Mode A uses the latest accepted DeviceFix as origin. The user dot is cyan and exactly(0,0); accepted emulator/mock fixes are labeled TEST GPS. Mode B uses the actual MapLibre camera center for exploration, with no cyan user-location dot. Camera panning only changes this mode's relative positions. The visible MAP/GPS/TEST GPS caption and full accessible/long-press description distinguish modes; the latter explicitly states GPS UNAVAILABLE in map mode. Invalid camera coordinates produce an unavailable origin and no invented coordinates.

GPS remains the existing foreground, permission-gated one-shot provider. Explicit location requests obtain fresh provider-validated coordinates (age<=120s, finite position/accuracy); new accepted fixes refresh radar. A failed/timed-out refresh or permission denial clears a previous fix so radar returns to map mode instead of representing stale success. No continuous/background GPS listener or automatic recentering is introduced by radar. Ordinary geographic refreshes and range/filter changes send no camera command. The explicit globe/GPS actions retain their existing camera behavior.

## Geodesic projection

For origin latitude φ1/longitude λ1 and feature φ2/λ2 (radians):

`a = sin²((φ2−φ1)/2) + cosφ1 cosφ2 sin²((λ2−λ1)/2)`

`distanceKm = 6371 × 2 × atan2(sqrt(a), sqrt(1−a))`, clamping a to[0,1].

`bearing = atan2(sin(Δλ) cosφ2, cosφ1 sinφ2 − sinφ1 cosφ2 cosΔλ)`.

With selected range R, omit distances greater than R; retain the boundary within floating-point tolerance. Normalize `r=distance/R`, `x=r sin(bearing)`, `y=−r cos(bearing)`. This is linear distance and north up, independent of map bearing/pitch/zoom. Dateline and near-pole tests ensure finite results. For the polygonal web, UI uses a uniform inscribed-radius inset `(halfSize−3dp)×cos(15°)` to keep full2dp dots inside the boundary. Touch selection uses the identical scale, not a separate projection.

## Range, interaction and transparency

Supported radii:1/5/20/100km, default20km. Tap empty web to cycle ranges; named accessibility actions select each radius directly. Range persists in the existing private SharedPreferences (`radar-range-km`), with invalid stored values falling back to20. The caption always states the actual local radius, even at global map zoom; this is a local exploration radar, not a claim to cover the world.

Tap a nearby radar dot to select its corresponding stable map-feature ID and open the existing detail panel. Coincident/dense dots use nearest-hit selection; per-feature accessibility actions provide alternatives for the first100 entries. Selecting a dot does not automatically recenter the map. Selecting another feature updates details; disabling a layer removes dots and stale selection through the existing shared state.

A visible DEMO suffix appears whenever demo alert dots are in range and enabled. Accessible radar status, long-press details and selected alert details also identify DEMO, bundled source and unverified/not-real-incident status. Hiding a layer removes only its dots. In no-GPS mode, requesting location preserves the existing explicit denial/services-off/unavailable messages; it never substitutes a destination or camera center for GPS.

## Performance and lifecycle

The pure snapshot is remembered by marker list, accepted fix, effective origin and range. Camera movement in GPS mode does not invalidate its geography; zoom alone does not change range or recalculate positions. The sweep reads the existing foreground animation clock only in Canvas draw. No per-dot Android View, per-frame bitmap allocation, network fetch or geodesic calculation is introduced. Background/reduced motion freezes the existing clock. The bounded catalog/alert collections reuse existing repository limits and native map clustering; radar does not fetch a worldwide dataset on camera movement.

## Files

Added: RadarGeography.kt, RadarUi.kt, RadarGeographyTest.kt, GeographicRadarTest.kt. Updated: TrackerScreen.kt, AddressSearchUi.kt, MapLayers.kt, TrackerModel.kt, AddressSearch.kt, app/build.gradle.kts, MapLayersTest.kt, Phase3StateTest.kt, TrackerInteractionTest.kt, README and follow-up reports/evidence. The extra search correction spaces attempts from actual completion (including first-use URL/HTTP initialization); the previous timestamp preceded URL construction and could let observed request starts fall below the test's interval.

The equivalent source snapshot is `.tools/radar-keyboard-baseline/source.zip` with a SHA256 manifest; this checkout has no Git metadata. The earlier `.tools/phase312-baseline` snapshot also remains available. No assets or dependencies were replaced.
