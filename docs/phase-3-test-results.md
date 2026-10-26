# Phase 3 build and test results

Validated 24 October 2026 (Asia/Yerevan). Final production source is the source used for `final-build-tests.log`. Manual live-provider and OS location receipts supplement deterministic tests; sightings/media remain mock data.

## Final build

`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` completed successfully in 34s on the final pass. JAVA_HOME was Android Studio JBR25; Gradle9.3.1, AGP9.1.1, Compose BOM2026.03.00 and MapLibre13.6.1 were retained. SDK compile37 / target36 / minimum26. Device tests ran on the identified task AVD Medium_Phone_API_37.0, emulator-5554, API37 (reported Android17). No unrelated AVD was resized or stopped.

- 12 JVM tests passed, 0 failures/errors/skips.
- 12 Android instrumentation tests passed, 0 failures/errors/skips.
- Lint: 0 errors, 11 warnings. Warnings concern newer dependency/target versions, KTX convenience calls and Android12 data-extraction guidance. No forced dependency upgrades were made during this UI reconstruction.
- Debug APK signature verifies with APK Signature Scheme v2.
- Package com.spidertracker.app, version0.3.0 / code2. Foreground coarse/fine permissions present; background-location permission absent.

Reports are preserved in `phase-3-evidence/TEST-*.xml`, `android-test-results.xml`, `lint-results.txt`, `apk-signature.txt`, `apk-badging.txt` and `final-build-tests.log`. `delivery.json` records the delivered APK/source hashes. `scripts/verify-phase3.py` asserts test counts, manifest/signature, all six packaged image pixel values, responsive capture sizes, live-provider evidence and preservation of the historical Phase2 APK.

## Coverage

| Area | Evidence and outcome |
|---|---|
| Startup and removed UI | Native map opens directly; supplied art/sprite/radar/input exist; footer, hamburger, filters and trailer launcher absent |
| Asset loading | All six native dimensions decode; 76 source frames are27×43 and within bounds; package verification compares RGBA pixels |
| Map rendering and gestures | Native MapLibre style and OSM source load; actual Android MapView responds to Espresso pan outside controls |
| Address input / submit | Editing makes zero provider calls; IME submits; explicit submit button handles unavailable state |
| Result selection / camera | Returned fixture is selectable, camera reaches coordinates/zoom13, GeoJSON destination layer contains the selected point (wrapped source duplicates deduplicated in assertion) |
| Real geocoding | Final APK submitted London to Photon, returned five real matches, selected Westminster at51.5074456,−0.1277653; real street map and cyan destination captured |
| Provider parser / policy | Point/coordinate/label validation, malformed/oversized responses, invalid hosts, HTTPS-only configuration, normalized persistent cache and request spacing verified |
| Search errors / races | Missing provider, empty results, unavailable, short input and query-change cancellation covered; fabricated result membership rejected |
| GPS permissions | Instrumented granted dispatch; actual OS precise/approximate permission dialog and actual denial recorded manually |
| Device location | Platform GPS received an emulator-injected40.1,44.5 fix, camera moved, TEST LOCATION appeared; listener removed afterward |
| Location errors / cancellation | Disabled service and no coarse provider recorded manually; timeout, denial/permanent denial, stale/invalid fixes and global-reset cancellation covered by state tests |
| Radar controls | 48dp distinct regions, compact/landscape/tablet bounds, granted provider action and global reset covered |
| Animation / lifecycle | All sprite frames decode and frame advances; production ForegroundAnimationClock is exercised through RESUMED→CREATED→RESUMED and pauses without accumulating background time |
| Responsive / keyboard | Geometry tests across six constraint sets; final captures at five sizes; actual IME shrinks the map while input/arrow remain displayed and returns after Back |
| Retained interactions | Activity Log/filter model and rumored detail→media→close paths remain covered without restoring removed controls |

## Executed JVM cases

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

## Executed Android cases

- `GeocoderTest.realProviderCodeCachesNormalizesAndThrottlesRequests`
- `GeocoderTest.parserValidatesGeometryCoordinatesAndLabels`
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

## Iteration and limitations

The first Android run passed8/11. Three failures exposed an invalid source-encoding byte in a UI string/test, an assertion that counted MapLibre world-wrap copies, and an attempted semantics lookup after the Activity stopped. Source text was normalized to UTF8; the spatial assertion now checks one unique destination and a direct lifecycle-clock test replaces the invalid stopped-Activity lookup. The next12/12 run passed. A later unit geometry check caught a compact-screen radar target crossing the raised search shelf; the target constraint was corrected and all final12+12 tests pass. Earlier logs are retained as iteration evidence, not final success evidence.

The final five layout captures, keyboard, live query and selected destination use the delivered APK. Manual OS permission/GPS/service captures precede the last shelf/character-only alignment adjustment; the location adapter did not change, and final tests cover its UI dispatch. The approximate-only emulator has no enabled network location provider: it reports NO LOCATION PROVIDER AVAILABLE and does not invent a fix. A real approximate first fix was not demonstrated.

Not executed: physical-device GPS first-fix/accuracy/battery calibration, min-API26 device execution, physical notch/cutout hardware, TalkBack/large-font certification, network-wide/multi-install provider load, full routing, navigation guidance or backend services. Unit/fixture tests prove deterministic state handling; they do not claim physical GPS accuracy. Lint's data-extraction warning remains: allowBackup=false disables cloud backup, while vendor device-transfer behavior was not certified.

## APK

`dist/spider-tracker-phase3-debug.apk` — 60,586,062 bytes, SHA-256 `08ad408efa2acbe363231e51d3003b2daa898b8c60f570fccd4400f18fd0a638`.

The normal Gradle output is `app/build/outputs/apk/debug/app-debug.apk`; delivery verification requires byte equality. Phase2 APK/evidence remain preserved separately. This is a debug-signed development APK, not a release/store package.
