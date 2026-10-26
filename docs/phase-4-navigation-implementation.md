# Spider Tracker — Phase 4 navigation implementation

Validation date: 26 October 2026, Asia/Yerevan. Physical driving validation has **not** been performed.

## 1. Working features

- Configurable, cancellable Valhalla HTTP routing using the existing OkHttp dependency. No public routing demo is configured.
- Driving (`auto`), walking (`pedestrian`), and cycling (`bicycle`). Actual accessibility depends on the server's OSM graph.
- Start from current device location or a manually selected coordinate. Search results and Main Attractions become destinations; long-pressing the map offers SET DESTINATION and SET START POINT.
- Decodes Valhalla polyline6 geometry, joins connected legs, validates summary values and maneuver indices, preserves street names, instructions and localized verbal text, and accepts up to two provided alternatives.
- Preview with route distance, travel time, alternative selection, native geographic route rendering, and viewport fitting around the HUD.
- Cyan selected route, dark casing, muted alternative lines, direction glyphs, and a destination marker.
- Explicit Idle, RoutePreview, Navigating, Recalculating, Arrived, Paused and Error states.
- Continuous Android GPS/network location updates in a location foreground service, with precise-location checks and a stop action in its notification. Notification permission is requested on supported Android versions; denial does not imply background-location permission.
- Progress by projection onto route segments, upcoming Valhalla maneuver, distance to maneuver, remaining distance, remaining estimated duration, estimated local arrival time, and a progress bar.
- Android TTS using returned verbal instructions, mute/unmute, approach/near-turn deduplication, and visible errors for unavailable engines/languages.
- Following camera with speed/distance-dependent zoom, reliable movement bearing, driving tilt, and asymmetric padding to leave road visible ahead. Pan/zoom gestures suspend following without stopping navigation; RECENTER restores it.
- Arrival requires several accurate fixes near both the route end and its endpoint. STOP cancels requests and releases service resources.
- Radar uses accepted navigation fixes while following, and returns to viewport mode when exploring. Existing art, marker sprites, search, radar controls and pixel typography remain in use.

The provider boundary is `RoutingProvider` / `RouteRequest` / `NavRoute`. `NavigationController` contains no MapLibre, Android GPS, TTS or Valhalla JSON dependencies. Android integration is in `NavigationService.kt`; map layers are in the existing renderer.

## 2. Map styling

The original navy/cyan vector style now has 50 layers, including 36 road layers:

- Separate motorway/trunk, primary, secondary/tertiary, residential/minor, service and path hierarchies.
- Separate tunnel, surface and bridge passes, with zoom-dependent widths, road casing, rounded joins, muted/dashed tunnels and path dashes.
- Building footprints and outlines from the existing vector source.
- Conditional extrusion only for an explicit numeric `height` field, with explicit `min_height` when present. No fabricated height or floor-count multiplier is used.
- Collision-aware city/district/place, road and POI labels, with halos and rotation/pitch alignment suitable for navigation.
- Existing pan/zoom gestures retain their configuration. Navigation applies bearing and tilt programmatically; manual rotation/tilt gestures remain disabled as in the original application.

**3D limitation:** the active tile provider may expose only `render_height`, which can include inferred defaults. This implementation deliberately does not treat that as proof of an explicit building height. Building footprints work; visible 3D buildings on the active provider are not claimed as verified. A compatible vector source exposing explicit numeric heights can use the existing extrusion layer.

No Yandex/Google tiles, logos, assets or paid APIs were introduced. Existing OSM/OpenMapTiles/OpenFreeMap attribution remains visible. DEMO alerts remain isolated from routing; requests do not consume alert markers or live traffic. Driving requests omit time-dependent departure parameters and exclude `current`/`predicted` speed sources.

## 3. Routing engine configuration

Select a destination, open **ROUTING SERVICE**, enter the base URL of your own Valhalla instance, and SAVE SERVICE. The URL is stored privately on the device. The app appends `/route`.

Alternatively supply the build property:

```powershell
./gradlew.bat :app:assembleDebug '-PvalhallaUrl=https://routing.example.org'
```

The default build URL is empty, so an unconfigured installation reports a configuration error instead of drawing a fake route. HTTPS is required in release builds. Debug builds additionally allow HTTP only for `localhost`, `127.0.0.1` and emulator host `10.0.2.2`; unrestricted cleartext traffic is not enabled.

### Real service provisioned for validation

- Container: `spider-phase4-valhalla`.
- Official image: `ghcr.io/valhalla/valhalla-scripted`.
- Image digest: `sha256:eaad372b8ef31e3e4592c442875aeb1375fbf5aeaa0d259b469e3b46583ab10d`.
- OSM extract: Geofabrik Armenia; files and generated graph are in `.tools/valhalla/` (ignored build/test data).
- Host endpoint: `http://127.0.0.1:8002`, bound to the host loopback interface.
- Android emulator endpoint: `http://10.0.2.2:8002`.
- No traffic archive, elevation download or public routing demo is used.
- This service covers its Armenia extract, **not the whole world**. Destinations outside the built graph must fail honestly.

With Docker Desktop running, restart the existing service using:

```powershell
docker start spider-phase4-valhalla
Invoke-RestMethod http://127.0.0.1:8002/status
```

To reproduce it in a new checkout, run from the project directory:

```powershell
New-Item -ItemType Directory -Force .tools/valhalla | Out-Null
docker run -d --name spider-phase4-valhalla -p 127.0.0.1:8002:8002 `
  --mount "type=bind,source=$PWD/.tools/valhalla,target=/custom_files" `
  -e tile_urls=https://download.geofabrik.de/asia/armenia-latest.osm.pbf `
  -e server_threads=2 -e build_time_zones=False -e build_elevation=False `
  -e use_default_speeds_config=False `
  ghcr.io/valhalla/valhalla-scripted@sha256:eaad372b8ef31e3e4592c442875aeb1375fbf5aeaa0d259b469e3b46583ab10d
```

For a USB-connected physical debug device, use `adb reverse tcp:8002 tcp:8002` and configure `http://127.0.0.1:8002`. Production deployment needs a user-controlled HTTPS service and appropriate regional/world graph provisioning; that deployment is not performed by this Android change.

The service returned a real Yerevan route from `(40.1772,44.5126)` to `(40.1913,44.5155)`: 2.957 km with two alternatives. The captured JSON is `docs/evidence/phase4-live-driving.json`. Duration changes with mode/costing options; no traffic-adjusted ETA is claimed.

Docker Desktop exited unexpectedly during two early runs. The user confirmed they had not closed it. Tests were subsequently run with the launcher held in an attached process; the app correctly reported backend unavailability during the outages. Docker must remain running for this local server to remain available.

## 4. Navigation SDK compatibility assessment

MapLibre Native remains **13.6.1**. No navigation SDK or new application dependency was added.

The upstream main-branch catalog currently lists Native 13.6.1, but the inspected **published `navigation-core-android:5.0.0-pre13`** POM actually references Native 12.3.1 and `geojson-jvm` / `turf-jvm:7.0.0-pre0`. The app currently receives `android-sdk-geojson` / `android-sdk-turf:6.0.1` through Native 13.6.1. The artifact coordinates differ, and inspection found **16 duplicate `org.maplibre.geojson` class names**. Simply adding the SDK to this app would require resolving those class/API conflicts and validating the SDK against the retained Native version.

Evidence is saved in `docs/evidence/navigation-sdk*.pom`, `navigation-sdk.module`, `navigation-sdk-versions.toml`, `phase4-dependencies.txt` and `phase4-sdk-class-conflicts.txt`. Main-branch versions are not treated as published-binary compatibility proof.

The independent-controller fallback was selected. No SDK was integrated, no Native downgrade was performed, and no SDK route-progress integration is claimed as tested. Progress was instead isolated and tested directly against the fallback controller before UI/service integration, then against actual Valhalla responses on Android. Native/class conflicts are avoided because the conflicting artifacts are absent from the app.

Sources: [MapLibre Navigation](https://github.com/maplibre/maplibre-navigation-android), [published Android POM](https://repo.maven.apache.org/maven2/org/maplibre/navigation/navigation-core-android/5.0.0-pre13/navigation-core-android-5.0.0-pre13.pom), [Valhalla API](https://valhalla.github.io/valhalla/api/route/api-reference/), [official Docker instructions](https://github.com/valhalla/valhalla/blob/master/docker/README.md).

## 5. GPS, progress and lifecycle behavior

- Navigation rejects fixes older than 12 seconds, fixes with accuracy worse than 50 m, invalid values, out-of-order fixes and implausible jumps relative to elapsed time and travel mode.
- Bearing is used only while moving at least 1.5 m/s and when reported bearing accuracy is acceptable. Low-speed fixes do not invent a heading.
- Signal-loss checks hold progress and report the loss. Inaccurate fixes reset deviation/arrival confirmation rather than triggering rerouting.
- Progress matching limits advancement between successive fixes; remaining distance scales the engine's route distance against geometry progress. Remaining time uses untraversed maneuver durations. It is an estimate without traffic or calibrated driving-speed prediction.
- Arrival requires remaining distance at most 35 m, endpoint distance at most 25 m, accuracy at most 25 m, and at least three confirming fixes spanning at least three seconds.
- The foreground service continues outside the visible Activity and owns GPS listeners/TTS. Pause, stop and service destruction clean up listeners, coroutines, notification and speech resources.
- Activity recreation reconnects to the process-owned session. After process loss, only destination/mode recovery metadata is retained, and the session recovers as Paused. Route calculation and an explicit user start are required; a stale route or background service is not silently restored.
- `START_NOT_STICKY` is used. No unrestricted background-location permission, boot receiver or automatic navigation restart is requested.

These thresholds need physical-device calibration. Emulator/mock fixes are labelled simulated and are not evidence of real GPS driving quality.

## 6. Automatic rerouting

Deviation uses distance from the real route geometry. Defaults in `NavigationConfig` are 45 m minimum distance, accuracy multiplier 2, at least three fixes spanning eight seconds, and a 30-second request cooldown. These are constructor-configurable for calibration.

While recalculating, the last valid route stays visible. A new request starts from the accepted GPS position and retains the destination/mode. Cancellation plus generation checks prevent stale responses from overwriting newer routes. Duplicate in-flight preview requests are ignored. Failed reroutes retain the previous route and display an explicit error; there is no fabricated success transition or tight retry loop.

Changing the configured service cancels the prior endpoint's in-flight request before calculating against the new service.

## 7. Verification

The focused live tests verified all three modes, alternatives, real HTTP failures, actual backend recalculation, Android TTS playback, foreground GPS progress (including outside the visible Activity), geographic route anchoring, gesture suspension, recentering, stop and Activity recreation. The final regression totals are recorded below.

| Gate | Result |
| --- | --- |
| JVM unit tests | 53 passed, 0 failures; includes 13 navigation tests |
| Android instrumentation, combined run | 73 run: 71 passed, 2 failed; see details below |
| Navigation integration tests within that run | All 6 passed against actual local Valhalla and Android APIs |
| Fresh-process focused recheck | All 6 passed: five pixel-asset tests and autocomplete destination selection |
| Android lint | Passed; 0 errors, 15 warnings, 1 hint |
| Debug APK and instrumentation APK | Built successfully |
| Physical-device navigation | Not run; checklist below |

Regression fixes include preventing automatic origin acquisition from moving the destination camera, allowing an explicit GPS recenter to replace that automatic lookup, and preventing stale failed previews from reopening in a fresh Activity. The pixel-ring shape test now uses a uniform basemap contrast so background road/label changes cannot masquerade as an elliptical ring.

The combined run's two failures were `PixelAssetsTest.searchCapsRetainOriginalPixelsAcrossDifferentWidths` (ActivityScenario teardown stayed PAUSED instead of DESTROYED, after its pixel assertions) and `TrackerInteractionTest.autocompleteResultsMoveCameraAndDrawDestination` (one extra geocoder invocation). Both passed in a fresh-process recheck, together with the remaining pixel tests. The intermittent aggregate-suite behavior is unresolved; an uninterrupted all-green 73-test run is **not claimed**. All route/navigation, map gesture, camera, radar, marker and lifecycle tests in that final combined run passed.

After tests, `dumpsys activity services com.spidertracker.app` reported no running service, confirming navigation shutdown did not leave location tracking active.

Commands used:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.spidertracker.app.test/androidx.test.runner.AndroidJUnitRunner
```

Navigation integration tests require the real local Valhalla server to be ready and reachable at `10.0.2.2:8002`. Their HTTP-error tests use a loopback test server solely to verify error handling. Controller unit tests use controlled providers solely to exercise cancellation/noise/failure cases; production code contains no mock-routing fallback.

Evidence: `phase4-final-build.txt`, `phase4-all-instrumentation.txt`, `phase4-final-focused-recheck.txt`, controller JUnit XML in `app/build/test-results/testDebugUnitTest/`, and `docs/evidence/phase4-navigation.png`. Earlier failed development runs are retained separately; do not interpret them as the final result. The final full Android suite is invoked directly through `adb am instrument`; the older Gradle connected-test HTML/XML belongs to an earlier development run. Lint errors discovered during implementation were fixed rather than baselined away.

## 8. Not completed / limits

- Physical-device walking/driving/cycling calibration and OEM battery-restriction checks remain manual.
- Two intermittent aggregate-suite failures remain recorded above, although their focused recheck passed. A consistently green combined instrumentation run remains a validation gap.
- A production HTTPS Valhalla deployment, worldwide graph, and operational uptime monitoring are not provisioned.
- Explicit-height 3D buildings are supported conditionally but are not visually verified on the current tile source.
- SDK route-progress integration was deliberately skipped due to the inspected dependency/class conflicts; the working fallback is used.
- ETA is based on Valhalla's remaining maneuver durations, without live traffic or calibrated per-segment speed learning.
- No offline navigation phase was started. Traffic, cameras, live incidents, live transit and a large organization directory remain excluded.

## 9. Debug APK

`app/build/outputs/apk/debug/app-debug.apk`

Size: 60,966,275 bytes. SHA-256: `649d66442f0b6e4c567e4e8984ec230121301ad7ffee0a4b984f4fbb62986977`.

## Physical-device checklist (not executed here)

- [ ] Install the APK; configure an HTTPS service, or use USB `adb reverse` for the local debug server.
- [ ] Outdoors, test precise permission allowed/denied/permanently denied and location services disabled/enabled. Confirm actionable messages and no silent start.
- [ ] Preview known local routes in all three modes, compare geometry against actual roads and lawful access, select alternatives, and try an unreachable destination and coordinates outside graph coverage.
- [ ] Walk a short known route and confirm position accuracy, next-turn distance, maneuver transitions, remaining distance, ETA and repeatable arrival.
- [ ] As a passenger or with a dedicated tester operating the phone, test driving at low/high speed, stops and turns; verify bearing/zoom behavior without distracting the driver.
- [ ] Make a sustained legal detour. Confirm one recalculation from the new position, previous-route visibility during calculation, then replacement by a real new route.
- [ ] Try GPS multipath/poor sky view and a tunnel; confirm inaccurate jumps do not advance progress or create a reroute loop, and recovery resumes from reliable fixes.
- [ ] Disconnect routing connectivity during a detour; confirm the last route remains visible with an explicit error and later retry respects cooldown.
- [ ] Test TTS with available/unavailable languages, mute/unmute, screen lock and another audio source. Confirm actual maneuver text and no excessive repetition.
- [ ] Pan and pinch during navigation; confirm following suspends, progress continues and RECENTER restores the camera. Check label readability and geographic anchoring under the navigation camera's bearing and tilt.
- [ ] Open search and the keyboard; test radar, attached controls, attraction details, touch targets and pixel sharpness on small and large portrait screens.
- [ ] Background/foreground the Activity, lock/unlock, recreate it, and explicitly stop using both HUD and notification. Confirm the service/listeners terminate after navigation ends.
- [ ] Kill the process mid-session and relaunch. Confirm safe paused recovery, fresh route calculation and explicit restart.
