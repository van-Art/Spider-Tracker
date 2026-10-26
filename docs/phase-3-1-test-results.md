# Phase 3.1 test results

The final source passed `:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` (final-validation.log,3m34s). Existing toolchain/architecture retained: Gradle9.3.1, AGP9.1.1, Kotlin/Compose, MapLibre13.6.1, compile37, target36, min26. Tests ran on the identified task AVD Medium_Phone_API_37.0 / emulator-5554 / API37, at320dpi. No additional user images were requested.

## Final results

- 18 JVM tests: pass;0 failures/errors/skips.
- 25 Android instrumentation tests: pass;0 failures/errors/skips.
- Lint:0 errors,12 warnings. Warnings are dependency/target upgrade notices, data-extraction guidance and KTX suggestions. The native Espresso click constructor also emits a test-code deprecation warning. No dependency migration was added to this phase.
- Debug APK signature verifies (v2). No background-location permission or routing/backend service was added.

The final XML, lint, build, package and signature evidence is preserved in phase-3-1-evidence/. Historical Phase2/Phase3 APKs are separate. The delivery verifier checks their hashes, final APK equality, packaged PNG pixel values and expected test counts.

## Acceptance coverage

| Requirement | Executed evidence |
|---|---|
| Startup/search/keyboard | Existing Android tests verify launch, editable field, zero requests on typing, submitted search, suggestions/selection/destination, unavailable state and IME resize/back |
| Green/red/independent OFF | State tests cover all four combinations; native UI tests tap both controls, preserve radar/search/GPS and check absence of relevant sprites |
| Category persistence | Long press opens each filter panel; a fresh VM reads matching private SharedPreferences; empty sets and independent flags restore |
| Real OSM places | Native test moves toLondon atzoom15, ingests actual loaded POI tiles, asserts non-demo/source metadata and visible green sprites;30 real records exported to real-osm-places.json |
| Marker geography/clustering | Native projected coordinates match MapLibre within1px after the camera settles; six reports form a cluster; cluster/sprite data deduplicates world copies |
| Details/navigation boundary | Tapping a native alert opens DEMO/source/verification details; place-panel test verifies missing-address and unavailable-distance labels, and Navigate sets only a destination with explicit unavailable-routing feedback |
| GPS | Granted platform dispatch updates userFix and an anchored user PNG, including with both data layers disabled; stale/invalid/timeout/denied/global-cancel logic remains covered |
| Pulses/motion/lifecycle | Unit periods/expansion/fade/repeat checks; actual native radius changes, layer-off stopping, system duration-scale0 static glow and pause/resume verified; character frame/lifecycle tests retained |
| Radar | Coordinate/distance/bearing tests, independent-layer filtering and acquired-fix dot; geometry/attached control positions remain the existing TrackerLayout |
| PNG sharpness | Android captures compare original opaque source pixels with search end caps and nearest-neighbor marker pixels; original14 PNG dimensions/RGBA are package-verified |
| Multiple markers | 1000 nearby test reports form one visible cluster, settling in478ms on the final emulator run (density-test.json) |

The density timing is source-update-to-observed-cluster latency for one test workload, not a sustained FPS, worst-case latency, energy or hardware performance certification. Synthetic report data is confined to the test workload and the explicitly named demo repository; it is not presented as real traffic.

## JVM cases

- `MapLayersTest.fabricatedSelectionsCannotOpenDetails`
- `MapLayersTest.placesValidateCategoriesAndNeverInventMetadata`
- `MapLayersTest.independentLayersAndCategoriesOnlyHideTheirOwnMarkers`
- `MapLayersTest.pulsesUseRequestedPeriodsAndReducedMotionIsStatic`
- `MapLayersTest.pixelBoundsKeepAspectRatioAndIntegerPixelsAcrossDensities`
- `MapLayersTest.radarUsesCoordinatesAndDisabledLayersRemoveDots`
- `Phase3StateTest.gpsTimeoutDisabledServicesAndDenialAreExplicit`
- `Phase3StateTest.spriteExtractionUsesTransparentGapsAndCommonPadding`
- `Phase3StateTest.editsCancelStaleSearchAndUnavailableNeverSucceeds`
- `Phase3StateTest.gpsFixAndGlobalCancellationCannotJumpToAnOldRequest`
- `Phase3StateTest.searchRequiresSubmissionAndSelectionMovesCamera`
- `Phase3StateTest.coordinateAndFreshnessValidationRejectInvalidDeviceData`
- `TrackerStateTest.detailCloseClearsSelectionAndBackUnwindsMediaFirst`
- `TrackerStateTest.explanationIsOncePerSectionAndToastReplacementIsSafe`
- `TrackerStateTest.newCameraRequestSupersedesPreviousAndFixturesAreValid`
- `TrackerStateTest.filtersKeepEventsAndTrailerAndLogUsesSameData`
- `TrackerStateTest.savedStateRestoresFiltersPhaseAndSection`
- `TrackerStateTest.layoutsFillAvailableSpaceAndRadarTargetsStaySeparate`

## Android cases

- `GeocoderTest.realProviderCodeCachesNormalizesAndThrottlesRequests`
- `GeocoderTest.parserValidatesGeometryCoordinatesAndLabels`
- `MapLayerInteractionTest.oneThousandNearbyReportsClusterWithoutOneViewPerMarker`
- `MapLayerInteractionTest.bothLayerTogglesKeepSearchRadarAndGpsMarkerAvailable`
- `MapLayerInteractionTest.nativeGlowAnimatesAndStopsWithItsLayer`
- `MapLayerInteractionTest.longPressFiltersPersistAndOffStateUsesIndependentCategories`
- `MapLayerInteractionTest.systemReducedMotionAndBackgroundStopNativePulsing`
- `MapLayerInteractionTest.filterCloseRespondsToPhysicalPointerInputAboveNativeMap`
- `MapLayerInteractionTest.tappingAnAlertOpensHonestCompactDetails`
- `MapLayerInteractionTest.nativeAlertClustersProduceGeographicallyAnchoredSprites`
- `MapLayerInteractionTest.actualOsmTilesSupplyPlacesAtStreetZoom`
- `PixelAssetsTest.searchCapsRetainOriginalPixelsAcrossDifferentWidths`
- `PixelAssetsTest.placeDetailsDoNotInventAddressAndNavigateSetsOnlyDestination`
- `PixelAssetsTest.sharedPreferencesKeepIndependentFlagsAndAnEmptyCategorySet`
- `PixelAssetsTest.markerSpritePreservesColorsWithNearestNeighborDrawing`
- `TrackerInteractionTest.mapLibreStyleLoadsAndNativeMapRespondsToPan`
- `TrackerInteractionTest.assetsDecodeAtNativeResolutionAndSpriteAdvances`
- `TrackerInteractionTest.retainedFiltersLogAndMediaStillWork`
- `TrackerInteractionTest.rumorMediaAndCloseClearTheMessage`
- `TrackerInteractionTest.submittedSearchResultsMoveCameraAndDrawDestination`
- `TrackerInteractionTest.keyboardKeepsInputVisibleAndResizesTheMap`
- `TrackerInteractionTest.foregroundAnimationClockPausesAndResumes`
- `TrackerInteractionTest.radarUsesGrantedDeviceProviderAndGlobalResetsCamera`
- `TrackerInteractionTest.unavailableSearchPreservesEditableFieldAndDismissal`
- `TrackerInteractionTest.startupUsesSuppliedArtAndRemovedControlsStayAbsent`

## Investigation and fixes

The first native pass exposed a missing initial projection refresh and a query-source call before MapLibre's renderer existed. The final renderer uses completed-frame readiness, cached-coordinate movement and pause/disposal guards; the complete25-test run finishes without the earlier native crash. Tests also needed to wait for the existing1500ms startup camera flight before measuring/mutating native data. Place ingestion initially retained an empty pre-tile scan; a one-time fully-loaded-viewport refresh now obtains actual data. The search-cap assertion now compares opaque PNG colors instead of incorrectly expecting transparent PNG pixels to equal opaque window pixels. A physical-touch regression test confirms panel dismissal; the early capture script had read before dismissal settled. An asynchronous cluster-source race is covered by a bounded0.9s coalesced refresh plus idle refresh, and system motion observes the authoritative Settings value rather than a delayed ValueAnimator cache. Earlier failure logs are retained as investigation evidence, not final success evidence.

## Limits / not executed

- No physical-device GPS accuracy, first-fix, battery or sustained thermal/FPS profiling. Location represents the last successful foreground fix; continuous tracking is not added.
- No real live-traffic provider; six fixed reports are explicit DEMO data. No user-report publishing backend.
- OSM tiles provide a bounded, zoom-dependent subset and often omit addresses. OSM data is not independent verification or guaranteed current business information.
- The real-tile integration test requires network or valid map cache. Local state/pixel/provider-boundary tests remain deterministic; public service availability is external.
- No min-API26 hardware run, TalkBack/large-font certification, or full orientation/device-size matrix. Basic local density checks are documented; Phase3.2 was not started.
- Native font rasterization and original asset shading remain; no universal pixel-perfect claim is made.
- Existing Android12 vendor device-transfer behavior is not certified; cloud backup remains disabled.
- No full routing, turn guidance, background location or account/backend integration.

## Debug APK

`dist/spider-tracker-phase3-1-debug.apk`, package com.spidertracker.app, version0.3.1 / code3.

Bytes: 60,737,485. SHA256: `ba26296bcbd84d583653cfd47211148a1c2d21c2c76679654eadb79fc1438ddc`.

## Final local captures

Final320dpi43-test evidence includes real OSM place detail, explicit demo alert detail, startup and actual Android LocationManager GPS with both layers disabled. `both-layers-off-gps.png` shows the acquired user PNG and cyan radar dot while both data switches use the supplied OFF asset. The injected emulator fix at51.5074,−0.1278 is explicitly labeled TEST LOCATION. This is a platform-provider integration check, not physical satellite accuracy proof.

`basic-420dpi.png` uses1080×1920 physical pixels at420dpi; controls/header/search/radar remain visible and proportionate. The task emulator is restored to780×1386 at320dpi. This basic density check does not start the full Phase3.2 orientation/device-size program. Earlier `*-pass`/selection-step captures are investigation snapshots; current detail/GPS/basic captures are the delivery evidence.
