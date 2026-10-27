# GPS accuracy circle V2

27 October 2026 (Asia/Yerevan). Branch: `fix/gps-accuracy-circle-v2`.

## History and cause

- [PR #1](https://github.com/van-Art/Spider-Tracker/pull/1), commit `ee8708a`, merged as `5f685a7`, moved the supplied user sprite into MapLibre and replaced the accuracy circle with a geographic polygon. It also added two test files.
- [PR #2](https://github.com/van-Art/Spider-Tracker/pull/2), commit `3dde81e`, merged as `3557bff`, exactly reversed those four files. Its description repeats the original fix's summary; the commit message and PR discussion give no rationale. The reason for choosing to revert cannot be established from this history.
- PR #1 still retained the visible native `user-dot` alongside the new native sprite, and removed the accuracy-area animation. These are observable shortcomings of that patch, not evidence of why PR #2 was created.
- After the revert, the map again drew both `user-dot` and the Compose user bitmap. Native layers and the throttled Compose projection callbacks render independently, allowing their positions to differ during camera changes. The old accuracy implementation used a zoom/latitude-to-screen-radius expression with viewport-aligned circle rendering, rather than a geographic boundary that follows map pitch.

## Rendering paths traced

Graphify `query`, `explain`, and `path --undirected` located the paths; source inspection verified them. The starting graph still referenced PR #1's deleted tests, so it was not treated as proof of the current implementation.

1. `AndroidLocationProvider` validates fresh coordinates/accuracy and emits `DeviceFix`. `TrackerViewModel.locate` stores the latest fix. Navigation can also supply a fix through `TrackerApp.effectiveFix` (and the existing `navigationFix` state update).
2. `TrackerApp` passes that effective fix to `MapScene.userFix`. `MapLibreRenderer` replaces the `user` GeoJSON collection on fix changes. This collection now contains one exact GPS point and a closed, 64-segment geodesic polygon whose radius is `accuracy` meters.
3. Native `user-marker` renders the existing `main_point_location` PNG, centered and viewport-facing at the point. Native `user-accuracy` and `user-accuracy-outline` render only the polygon. The obsolete `user-dot` is removed; `user-hit` is transparent and filters to points.
4. `projectSprites` still projects the user point for accessibility and decorative pulse effects, with its existing deduplication, camera updates, source settling and lifecycle guards. `MapMarkerSprites` omits the user bitmap, preventing a second visible location marker. Place/alert sprites and destination behavior retain their existing paths.
5. Radar independently uses `geographicRadar` for GPS mode or native `projectRadar` / `viewportRadar` for viewport mode, then `RadarOverlay` draws its radar dot. This separate radar representation is preserved.

The accuracy fill retains its lifecycle-aware opacity animation, with a static reset for capture/reduced motion. Explicit recenter also clears navigation padding, bearing and tilt even when the requested target/zoom are already equal. Ordinary camera gestures do not change the GPS geometry.

## Intentional source changes

- `app/src/main/java/com/spidertracker/app/MapRenderer.kt`: geographic accuracy area and sole native pixel marker; accuracy pulse and explicit recenter handling.
- `app/src/main/java/com/spidertracker/app/MapLayerUi.kt`: omit the Compose user bitmap while retaining pulse and semantics.
- `app/src/test/java/com/spidertracker/app/GpsAccuracyTest.kt`: exact single point, meter boundary at several latitudes/longitudes including both dateline sides, and missing-fix removal. The native test covers live fix replacement.
- `app/src/androidTest/java/com/spidertracker/app/GpsAccuracyMapTest.kt`: exact rendered marker count, absence of the old dot, fixed-size sprite, geographic inside/outside probes under zoom/rotation/tilt/pan, recenter, GPS movement/accuracy changes and denial cleanup.
- `app/src/androidTest/java/com/spidertracker/app/Phase316MarkerTest.kt`: bitmap comparison detects a duplicate Compose user sprite while checking preserved pulse and semantics.
- `app/src/androidTest/java/com/spidertracker/app/Phase41IntegrationTest.kt`: readiness checks use the replacement `user-marker` layer.
- `docs/gps-accuracy-circle-v2.md`: this evidence and validation report.

## Validation

Final fresh JVM tests: 61 passed across 9 suites, 0 failures/errors. Debug application APK and instrumentation APK assembled successfully. Lint: 0 errors, 15 warnings, 1 hint (same counts as the development baseline).

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat -g .gradle-user-home --offline --console=plain :app:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew.bat -g .gradle-user-home --offline --console=plain :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.spidertracker.app.GpsAccuracyMapTest,com.spidertracker.app.Phase316MarkerTest"
./gradlew.bat -g .gradle-user-home --offline --console=plain :app:connectedDebugAndroidTest
graphify update .
```

The focused emulator run passed all 3 tests on `Medium_Phone_API_37.0`, Android 17/API 37. The first aggregate run passed 19 tests, then crashed in MapLibre's native `queryRenderedFeatures` during the new GPS test. The test now waits for a rendered frame as well as a loaded style, and verifies that its MapView remains attached before querying. This addresses the test's premature-query race without changing application lifecycle handling. The initial run's evidence is retained under ignored `.tools/gps-accuracy-circle-v2/initial-full-run/`.

The aggregate retry completed all 80 tests: **70 passed, 10 failed, 0 skipped/errors**. The GPS regression, duplicate-Compose-sprite regression, existing marker pulse test, real Android-provider GPS integration, and radar masking test all passed. No process crash occurred on the retry.

All ten failures correspond to tests and failure symptoms already recorded in `docs/development-baseline.md`; this does not independently establish their root causes. They were not suppressed or changed:

| Test | Failure |
| --- | --- |
| `GeographicRadarTest.physicalRadarDotOpensMatchingAttractionDetails` | Camera equality at line 132: expected zoom 14, observed approximately 13.5834 with a small center shift. |
| `NavigationIntegrationTest.androidSpeechEngineCanAcceptRealManeuverText` | Routing unavailable. |
| `NavigationIntegrationTest.realRoutesAllModesAlternativesAndStrictParsing` | Routing unavailable. |
| `NavigationIntegrationTest.actualBackendRecalculationAndSimulatedProgress` | 30-second timeout with routing unavailable. |
| `NavigationIntegrationTest.foregroundGpsHudRecenterStopAndActivityRecreation` | Routing unavailable. |
| `Phase312MapTest.phase314UpdatesKeyboardAndExplicitCameraActionsKeepOneMapView` | GPS recenter condition timed out at line 234 after 6 seconds. |
| `Phase41IntegrationTest.searchDetailsManualOriginRealRouteAlternativesAndBack` | Routing unavailable. |
| `Phase41IntegrationTest.locationButtonRequestsPermissionAndDenialIsUseful` | Expected permission result -1, got 0. |
| `Phase41IntegrationTest.approximatePermissionUsesFreshNetworkLocation` | Expected permission result -1, got 0. |
| `PixelAssetsTest.placeDetailsDoNotInventAddressAndNavigateSetsOnlyDestination` | `TEST CAFE` detail not displayed. |

The baseline's landscape keyboard test passed on this run. Current aggregate XML/HTML reports remain under `app/build/outputs/androidTest-results/connected/debug/` and `app/build/reports/androidTests/connected/debug/`. Ignored logs: `.gps-v2-final-checks.log`, `.gps-v2-focused.log`, `.gps-v2-emulator.log`, `.gps-v2-emulator-retry.log`, and `.gps-v2-graphify.log`. JVM XML reports are under `app/build/test-results/testDebugUnitTest/`; the debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Sandboxed Gradle and ADB could not connect to their local daemons; checks ran successfully outside the sandbox. No build settings were changed for this.

Graphify's AST-only rebuild updates ignored local graph outputs without API usage. It reports package/skill version skew (0.9.95 / 0.9.96), partial extraction warnings for 10 Kotlin files, and 5 files with no modeled symbols. These extraction limits do not indicate Kotlin compilation failures.

No revert, staging, commit, push or merge was performed. No catalog data, routing settings, SDK settings, dependency versions or supplied PNGs were changed. Physical-device GPS/driving and release builds were not checked.

Next task: restore the existing Valhalla test service and rerun the documented failures, then investigate the remaining UI synchronization failures separately.
