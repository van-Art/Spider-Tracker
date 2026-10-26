# Phase 3.1.7 — portrait orientation

Completed 25 October 2026.

## Change

The existing `.MainActivity` declaration in `app/src/main/AndroidManifest.xml` now has `android:screenOrientation="portrait"`. `MainActivity.kt` needs no changes. No new Activity, runtime orientation request, dependency, SDK change, game category, resizability opt-out or configuration-change override was added. The existing `adjustNothing` keyboard behavior remains.

Production changes consist of that manifest attribute. `TrackerInteractionTest.kt` adds checks for the installed manifest and Activity recreation retaining the search label, destination, native camera center/zoom and layer state. This report, evidence and the phase APK are the other outputs.

## UI preservation

Compared the APK with `dist/spider-tracker-phase3-1-6-debug.apk`: all 149 entries outside `META-INF` were compared after decompression, and only `AndroidManifest.xml` changed. Compiled application code, resource tables, assets and native libraries are identical; all 66 PNG entries are byte-identical. No dimensions or colors changed.

The full-screen MapLibre map, cyan frame, header/logos, Main Attractions and Live Alerts controls, search field/button, animated character, radar/attached buttons, information panels and keyboard-aware HUD passed the focused interaction checks. Phone and large-screen screenshots were also inspected.

## Validation

Emulator: `Medium_Phone_API_37.0`, Android 17/API 37. App: min SDK 26, target SDK 36, compile SDK 37.

| Check | Result |
| --- | --- |
| Fresh JVM test run | 40 passed |
| Phone configuration | 780 × 1386 px, 320 dpi, smallest width 390dp |
| Portrait launch | Passed |
| Landscape sensor input with auto-rotation enabled | Sensor proposed rotation 90°, app remained at 0° in portrait |
| Disable/re-enable auto-rotation with landscape sensor input | App remained portrait |
| Rotation control | Android Settings rotated to landscape under the same sensor input; returning to Spider Tracker restored portrait |
| Phone instrumentation | 13 focused checks passed; corrected manifest-only assertion also passed |
| Large-screen configuration | 1600 × 2560 px, 320 dpi, smallest width 800dp; app ran at 2560 × 1600 px in landscape |
| Large-screen interactions | All 12 interaction checks passed, including keyboard, camera, search, radar, markers, panels, animation and recreation |
| Large-screen manifest assertion | Passed after removing an invalid runtime-getter expectation |
| Debug APK build | Passed, offline |
| Emulator restoration | Original screen size, density, sensor, auto-rotation, user rotation and keyboard setting restored |

The initial large-screen configuration test assumed `Activity.requestedOrientation` would still return portrait. Android returned `SCREEN_ORIENTATION_UNSPECIFIED` because the restriction was ignored. The test now checks the installed manifest's portrait declaration, and passed on both screen configurations. No production workaround was needed. Original test logs retain this failed assertion and the separate successful reruns.

Recreation coverage uses `ActivityScenario.recreate()` and retained ViewModels. It verifies search-driven camera state, query/destination and layer state. Gesture-panned native camera restoration after recreation, process death and physical devices were not tested; this phase makes no changes to native map state restoration.

Build and fresh JVM test commands:

```text
gradlew.bat -g .gradle-user-home --offline --console=plain :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
gradlew.bat -g .gradle-user-home --offline --console=plain :app:testDebugUnitTest --rerun
```

Only the instrumentation APK was rebuilt after correcting its assertion. The production APK was built once successfully and remained unchanged afterward.

## Android compatibility

Portrait locking applies to supported standard phone configurations, including the tested API 37 phone display with device auto-rotation enabled. This is not a universal orientation guarantee: Android 16 ignores these restrictions for apps targeting SDK 36 on large displays (smallest width at least 600dp). Android 17 removes the developer opt-out for apps targeting SDK 37. The existing adaptive layout remains available when Android ignores the restriction; the API 37 800dp landscape configuration was exercised successfully.

Sources: [Android 16 orientation and resizability changes](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html), [Android 17 orientation and resizability changes](https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html).

Evidence: [phase-3-1-7-evidence](phase-3-1-7-evidence/).

## Debug APK

`dist/spider-tracker-phase3-1-7-debug.apk` — 60,889,743 bytes.

Main distribution APK: `dist/spider-tracker-debug.apk`, a byte-identical copy of this verified build, linked as the primary download in README.

SHA-256: `a3b59a0fe6716a15253b522bf6124cac4bea92067a11b7c4f5d9675858d9f24b`.

Stopped after Phase 3.1.7.
