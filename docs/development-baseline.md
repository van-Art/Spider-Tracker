# Incremental development baseline

Verified 27 October 2026 (Asia/Yerevan).

## Project and Git

- Single native Android `app` module: Kotlin/Compose, MapLibre Native 13.6.1, Photon search, pixel-art HUD, geographic radar, attractions and explicit demo alerts.
- Navigation is already implemented through `NavigationController`, `RoutingProvider`, Valhalla, a location foreground service and Android TTS. The build-time Valhalla URL defaults to empty; routing requires a configured service. README's Phase 3.1.7 description predates this source.
- Gradle wrapper 9.3.1, Android Gradle Plugin 9.1.1, Compose compiler plugin 2.2.10; compile API 37.0, target 36, minimum 26. APK metadata still reports version `0.3.1.2-radar` (code 6).
- `git status --short` returns `fatal: not a git repository (or any of the parent directories): .git`. No `.git` exists here; branch, HEAD and tracked user changes cannot be established. No repository was initialized and no baseline commit was attempted.
- Existing root `AGENTS.md` was empty (0 bytes); it now contains concise development guidance. No nested `AGENTS.md` was found under application sources, scripts or docs.

## Verification

Commands run from the project root, with process-local `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` for successful Gradle invocations:

```powershell
./gradlew.bat -g .gradle-user-home --offline --no-daemon --console=plain --continue :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat -g .gradle-user-home --offline --no-daemon --console=plain --continue :app:testDebugUnitTest --rerun :app:connectedDebugAndroidTest
py -3 scripts/update-attractions.py --self-test
```

| Check | Result |
| --- | --- |
| Fresh JVM tests | 59 passed across 8 suites, 0 failures/errors |
| Debug APK | Successful incremental build; existing outputs were up to date |
| Debug lint | 0 errors, 15 warnings, 1 hint |
| Offline catalog exporter self-test | Passed |
| Android instrumentation | 78 run: 67 passed, 11 failed, 0 skipped/errors; emulator `Medium_Phone_API_37.0`, Android 17/API 37 |

APK: `app/build/outputs/apk/debug/app-debug.apk`, 61,220,590 bytes, SHA-256 `908f54a8d45016d250e846743d078a62ada2dd74577697765807b71ad6e79faf`.

The existing `dist/spider-tracker-debug.apk` has a different SHA-256 (`a3b59a0fe6716a15253b522bf6124cac4bea92067a11b7c4f5d9675858d9f24b`); it remains the historical Phase 3.1.7 delivery and was not replaced.

## Failures, warnings and limits

- Two sandboxed Gradle attempts failed before task execution with `Could not connect to the Gradle daemon`, including `--no-daemon` (which still forks a single-use daemon). The same offline build succeeded outside the sandbox. This is an execution-environment limitation; no application or build configuration workaround was applied.
- SDK tooling warns that it understands XML schema versions up to 3 but encountered version 4. It did not block the build.
- Lint findings: target API age (1), newer Gradle (1), newer dependencies (3), orientation restrictions (2), backup rules (1), KTX suggestions (7), primitive Compose state hint (1). These are existing findings; no upgrade, suppression or behavior change was made.
- Historical Phase 4 documentation reports intermittent aggregate instrumentation failures in pixel-asset Activity teardown and autocomplete invocation counts. Historical results are not evidence of a fresh pass.
- Current Android run: `GeographicRadarTest.physicalRadarDotOpensMatchingAttractionDetails` fails its camera-equality assertion at line 132: expected zoom 14.0, observed approximately 13.8777 after the tap (with a small center shift). Cause is unproven; investigate camera updates/animation and test synchronization before changing behavior.
- Routing integration tests require the existing Armenia Valhalla service at emulator URL `http://10.0.2.2:8002`. Host `/status` at `http://127.0.0.1:8002` refused the connection, and Docker Desktop's Linux engine pipe was unavailable. Routing-unavailable errors and the recalculation timeout are recorded with this unmet prerequisite; Docker and the service were not started or reconfigured.
- Physical devices, driving, production routing deployment and release builds are outside this baseline check.

All 11 Android failures from this run (test names are scoped to the named class):

| Class / test | Observed failure |
| --- | --- |
| `GeographicRadarTest.physicalRadarDotOpensMatchingAttractionDetails` | Camera mismatch, line 132, described above |
| `NavigationIntegrationTest.androidSpeechEngineCanAcceptRealManeuverText` | Routing unavailable |
| `NavigationIntegrationTest.realRoutesAllModesAlternativesAndStrictParsing` | Routing unavailable |
| `NavigationIntegrationTest.actualBackendRecalculationAndSimulatedProgress` | 30-second timeout with the routing service unavailable |
| `NavigationIntegrationTest.foregroundGpsHudRecenterStopAndActivityRecreation` | Routing unavailable, line 140 |
| `Phase41IntegrationTest.searchDetailsManualOriginRealRouteAlternativesAndBack` | Routing unavailable, line 112 |
| `Phase312MapTest.phase314UpdatesKeyboardAndExplicitCameraActionsKeepOneMapView` | GPS recenter camera condition timed out after 6 seconds, line 234 |
| `Phase41IntegrationTest.locationButtonRequestsPermissionAndDenialIsUseful` | Expected permission result -1 (denied), got 0 (granted), line 150 |
| `Phase41IntegrationTest.approximatePermissionUsesFreshNetworkLocation` | Expected permission result -1 (denied), got 0 (granted), line 164 |
| `PixelAssetsTest.placeDetailsDoNotInventAddressAndNavigateSetsOnlyDestination` | `TEST CAFE` detail was not displayed, line 83 |
| `TrackerInteractionTest.landscapeAccessoryImeKeepsNativeMapAndSearchStable` | Keyboard visibility condition timed out after 5 seconds in helper at line 209 |

The six failures outside routing prerequisites have not been diagnosed or rerun in isolation. Their presence in this baseline does not establish when they were introduced. Historical pixel-teardown/autocomplete failures did not recur in this run.

Raw logs and the original source hash manifest are in `.tools/incremental-baseline-20261027/` (local ignored evidence). Fresh JVM XML reports are in `app/build/test-results/testDebugUnitTest/`; lint reports are in `app/build/reports/`. The completed Android HTML report is `app/build/reports/androidTests/connected/debug/index.html`, with XML under `app/build/outputs/androidTest-results/connected/debug/`.

## Change boundary and next task

Only `AGENTS.md` and this report are intentional source-tree edits. All 95 original application source/resource, test, script, Gradle and root documentation/configuration files in the before/after hash check remain byte-identical. Generated caches, test reports and build outputs may change during validation.

Next task: restore Docker Desktop and the existing `spider-phase4-valhalla` service, verify `/status`, then rerun the failed Android tests. Investigate any remaining radar camera mismatch separately. Establish/recover the intended Git repository and review ignore rules and file selection before making a baseline commit.
