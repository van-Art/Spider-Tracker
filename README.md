# Spider Tracker Android

Phase 3.1.7: native Kotlin + Jetpack Compose app with MapLibre Native, supplied pixel-art PNGs, topmost place/alert details, circular marker and cluster pulses, an expanded offline attraction overview and regional packs, explicit demo alerts, Photon autocomplete and foreground device location. Portrait orientation is locked on supported phones, with the existing adaptive fallback on large screens. Routing and a backend remain outside this phase.

- [Install the main debug APK — Phase 3.1.7](dist/spider-tracker-debug.apk)
- [Portrait orientation configuration and validation](docs/phase-3-1-7-portrait-orientation.md)
- [Preserved Phase 3.1.6 debug APK](dist/spider-tracker-phase3-1-6-debug.apk)
- [Phase 3.1.6 fixes, coverage, import commands and validation](docs/phase-3-1-6-summary.md)
- [Preserved keyboard/radar debug APK](dist/spider-tracker-radar-keyboard-debug.apk)
- [Keyboard-aware overlays](docs/keyboard-radar-layout.md)
- [Geographic radar calculations and behavior](docs/geographic-radar.md)
- [Follow-up test results](docs/keyboard-radar-test-results.md)
- [Preserved Phase 3.1.2 debug APK](dist/spider-tracker-phase3-1-2-debug.apk)
- [Phase 3.1.2 UI polish and changed files](docs/phase-3-1-2-ui-polish.md)
- [Autocomplete and keyboard overlay](docs/phase-3-1-2-search.md)
- [Global attractions, coverage and updates](docs/phase-3-1-2-global-attractions.md)
- [Zoom investigation](docs/phase-3-1-2-zoom-investigation.md)
- [Marker rings and pixel rendering](docs/phase-3-1-2-marker-effects.md)
- [Detail panels](docs/phase-3-1-2-detail-panels.md)
- [Phase 3.1.2 test results](docs/phase-3-1-2-test-results.md)
- [Preserved Phase 3.1.1 debug APK](dist/spider-tracker-phase3-1-1-debug.apk)
- [Phase 3.1.1 implementation and validation](docs/phase-3-1-1-implementation.md)
- [Preserved Phase 3.1 debug APK](dist/spider-tracker-phase3-1-debug.apk)
- [Phase 3.1 UI refinement and changed files](docs/phase-3-1-ui-refinement.md)
- [Map layers and data sources](docs/phase-3-1-map-layers.md)
- [Marker animation](docs/phase-3-1-marker-animation.md)
- [Pixel rendering](docs/phase-3-1-pixel-rendering.md)
- [Phase 3.1 test results](docs/phase-3-1-test-results.md)
- [Preserved Phase 3 APK](dist/spider-tracker-phase3-debug.apk)
- [Phase 3 UI changes](docs/phase-3-ui-changes.md)
- [Supplied asset integration](docs/phase-3-asset-integration.md)
- [Search and location implementation](docs/phase-3-search-implementation.md)
- [Screenshot validation](docs/phase-3-visual-validation.md)
- [Build and test results](docs/phase-3-test-results.md)
- [Preserved Phase 2 implementation](docs/phase-2-implementation.md)
- [Preserved Phase 1 REA dossier](analysis/README.md)

```powershell
# Set local.properties sdk.dir to your installed Android SDK first.
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Minimum API 26; compile API 37.0; target API 36. Use JDK 17 or newer supported by Gradle 9.3.1. Debug application ID: `com.spidertracker.app`.

The app opens directly to the expanded map. Typing at least three characters starts debounced Photon autocomplete after 425ms; requests are throttled and cached. Select a suggestion, or use the arrow/keyboard Search, to place a destination. The keyboard lifts search, character, radar and attached controls and preserves the underlying map dimensions/camera. The globe resets the camera; the target requests foreground location permission and an actual device fix. Emulator/mock fixes are labeled TEST LOCATION. Errors never substitute a demo position.

The geographic radar uses the accepted GPS fix, or the map center without a user dot when GPS is unavailable. Its green/red/cyan dots reflect enabled in-range records. Tap empty web to cycle 1/5/20/100km; long press for mode/status, or use named accessibility range actions. No whole-world radar coverage is implied.

Tap the green/red PNG controls to toggle Main Attractions / Live Alerts independently; long press for category filters. Main Attractions has 94 sourced Wikidata overview records across 77 country/jurisdiction IDs and six inhabited continents, plus 12 regional landmarks in France and the UK. Country coverage remains incomplete and not independently verified. Native MapLibre clustering works at world/regional zoom; local packs add detail from zoom 4. Alerts remain six explicit demonstration scenarios, with no live traffic feed. Detail panels sit above the radar, scroll, and set only a destination with Navigate. Sharp PNGs draw above separate circular pulses, including slower cluster pulses. Reduced motion and background lifecycle stop animation work. Toggle notifications replace each other and expire after 1.8s, with none on normal startup.

The default free Photon demo has no availability guarantee. Configure a dedicated/self-hosted instance with `-PgeocoderUrl=https://your-photon-host`, or disable live search explicitly with `-PgeocoderUrl=`. Searches are throttled and cached; see the implementation document for policy and privacy details. Street detail and live geocoding require network access; bundled public-domain world geometry remains available offline.

The Phase 3.1.6 summary documents the current fixes, coverage, import pipeline and test evidence. Earlier reports/APKs are preserved separately. `scripts/update-attractions.py --self-test` validates the exporter offline; automatic update scheduling is not configured. Full device/orientation validation belongs to Phase 3.2 and has not started. No route preview, turn-by-turn navigation or background location is implemented.
