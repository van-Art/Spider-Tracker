# Phase 3.1.1 — UI polish and map usability

This is a focused update to the existing Kotlin / Compose / MapLibre application. No new dependency, theme, font, asset, routing or responsive redesign was introduced.

## Changes

- Search frame and arrow artwork share a centered 40dp visual height. The arrow has a 54×48dp touch region, allowing its original 54×41 aspect ratio to fit without the previous narrow-box height reduction. A 2dp gap separates the artwork containers. The overall shelf bounds and animated character remain unchanged.
- Search content uses 12dp horizontal insets and an 8dp icon-to-text gap. Placeholder, editable text and cursor share one explicit line style, centered in the frame; Android font padding is disabled for this field only. The existing pixel font, 14sp size, cyan color, single-line scrolling, selection/editing, button submission, keyboard Search, results and destination behavior remain.
- The original search PNG uses the existing protected 7px end caps. Only its straight middle resizes. Original PNG bytes/colors and nearest-neighbor drawing are preserved; no corners are redrawn or stretched horizontally.
- Removed the permanent OSM caption below the green control and the permanent map status banner. Required map attribution remains, along with source/verification metadata in details and filters.
- A brief notification explains: “ZOOM CLOSER FOR PLACES · POI DATA APPEARS AT STREET ZOOM”. It uses the existing five-second feedback timer and polite accessibility announcement. It runs when the places layer is enabled at overview zoom or the camera enters zoom below12, without repeating on every camera/tile update. Existing active feedback takes priority. The actual POI query gate, loaded-tile availability and geographic coordinates are unchanged; no world-zoom places are invented.
- The cream DEMO caption remains while enabled demo alert records exist, and disappears when the alert layer is off or filters exclude all reports. DEMO also remains in alert details/filter panels. The green/red/OFF PNGs, independent toggles and long-press categories are preserved.

## Radar and graphics preservation

TrackerLayout and the complete radar drawing/control block are byte-for-byte equivalent after newline normalization to the saved Phase3.1 source. Dots still use existing geographic distance/bearing calculations and enabled records/acquired device fixes. There are no decorative data points. The center and sweep remain the existing radar geometry.

The existing inner lines have nominal sRGB contrast4.63:1 over the base navy water color and3.72:1 over the base land color, excluding shading/overlapping content. They remain legible in the emulator capture; no line/color or radar geometry change was needed. This is not a minimum contrast guarantee over every possible map feature. Original14 PNG hashes are unchanged; separate marker glows and normal MapLibre map rendering retain their existing behavior.

## Changed files

- app/src/main/java/com/spidertracker/app/AddressSearchUi.kt
- app/src/main/java/com/spidertracker/app/MapLayerUi.kt
- app/src/main/java/com/spidertracker/app/TrackerScreen.kt
- app/src/androidTest/java/com/spidertracker/app/TrackerInteractionTest.kt
- app/src/androidTest/java/com/spidertracker/app/MapLayerInteractionTest.kt
- app/build.gradle.kts — version0.3.1.1 / code4
- README.md, this report, phase-3-1-1-evidence/, phase-3-1-1-screenshots/, dist/spider-tracker-phase3-1-1-debug.apk

The pre-polish modified production/configuration files are saved under .tools/phase311-baseline/. Earlier phase APKs and documentation remain separate.

## Validation

All requested gates passed using the existing toolchain and task emulator Medium_Phone_API_37.0 / emulator-5554 / API37, at780×1386 pixels /320dpi:

- `:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` — BUILD SUCCESSFUL in1m32s.
- 18 JVM tests and27 Android instrumentation tests passed;0 failures/errors/skips.
- Lint:0 errors,12 existing advisory warnings (dependency/target updates, backup extraction guidance and KTX suggestions). Existing Espresso click-constructor deprecations remain in test compilation.
- Added `searchArtworkAndEditableLineRemainAligned`: artwork top/bottom and input/icon center alignment within1px, inner padding/bounds, cursor insertion at the beginning and48dp minimum arrow target.
- Added `poiHintExpiresAndDemoCaptionFollowsEnabledReports`: expiring hint, no permanent OSM/zoom caption, no invented overview POIs, DEMO status on/off, required attribution retained.
- Retained regressions pass for search submission/results/selection, empty/error/unconfigured states, keyboard resize, independent layer switches, long-press categories, actual OSM tile ingestion, native marker projection/clustering/details, GPS provider dispatch, global reset, radar calculations, animation/lifecycle/reduced motion and original PNG pixel colors.
- The first test compile exposed an incorrect chain after the Unit-returning selection API. It was split into separate calls; the subsequent complete gate passed. No production behavior was changed for that test fix.

Actual final screenshots are in `phase-3-1-1-screenshots/`: `contextual-hint.png`, `map.png` after expiration, `keyboard.png` and `layers-off.png`. Inspection confirms the40dp frame/arrow visual alignment, centered input/cursor, unclipped shelf with keyboard, no permanent OSM banner, clear DEMO label while enabled and no demo marker/caption with both switches off. The original art's gray/navy shading remains intentionally unchanged. Radar contrast/dots/buttons remain as before. No universal pixel-perfect or device-matrix claim is made.

The test XML, lint report, successful build log, APK signature/badging, normalized radar-source comparison,14 original asset hashes and screenshot/package receipt are under `phase-3-1-1-evidence/`. Every packaged PNG was decoded and compared in RGBA with its original. Core map renderer, data/filter repository, geocoder, device-location and nearest-neighbor drawing sources match the Phase3.1 hashes. Earlier artifacts were preserved.

## Debug APK

`dist/spider-tracker-phase3-1-1-debug.apk` — version0.3.1.1 / code4, 60,421,170 bytes. Debug signature verified with APK Signature Scheme v2. Installation and cold startup also passed after Gradle's test cleanup.

SHA256: `fc4d4e0680172685b950834f2656d89f264ba4f564771d00c0905f3c52bf082e`.

## Limits

Live Alerts remains explicit demonstration data; OSM coverage depends on loaded street-level tiles and is not independent verification. GPS is foreground/on-demand. No physical-device GPS, sustained performance/battery or full orientation/device matrix is certified by this polish pass. No routing or Phase3.2 work was started.
