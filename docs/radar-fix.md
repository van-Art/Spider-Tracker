Radar fix — verified 25 October 2026 (Asia/Yerevan).

**Root causes and rendering findings**

The radar applied the selected physical radius (20 km by default) to both GPS and map exploration. At world zoom, this discarded almost every available attraction. Only camera latitude/longitude reached the radar; visible bounds, zoom and bearing did not participate in its projection. Any retained GPS fix also forced GPS mode after returning to global map exploration.

The checked source already had an opaque navy fill and MapLibre TextureView rendering. Its original opacity test passed before editing, so a native rendering-order inversion or the reported sprite bleed was not reproduced. The sweep was not clipped to the polygon. The fix uses the existing non-antialiased pixel fill, explicit radar overlay stacking and polygon clipping; native/Compose occlusion is now covered by a pixel comparison and screenshot.

**Result**

- Rendering order is map/native effects → Compose marker sprites/pulses → opaque navy polygon → web → geographic dots → clipped sweep → attached controls. Existing radar dimensions, placement, web geometry, artwork and controls are retained.
- Exploration projects the same enabled repository records as the map. MapLibre's visible geographic bounds filter candidates; its native screen projection supplies Mercator scale, bearing and longitude wrapping. The viewport is mapped into the web's inscribed disk, including viewport corners. Exploration coverage is independent of the stored GPS range.
- GPS-centered mode uses the accepted Android device fix, spherical distance/bearing and selected physical range. Only a device fix produces a cyan dot. Globe, map gestures and destination navigation return radar coverage to the viewport without discarding the actual fix.
- Camera movement/idle/layout, layer/record/fix changes and rendering/lifecycle refreshes update the viewport snapshot. The pipeline uses Compose observable state, not StateFlow; no lost flow collection was found.
- Overlapping dots are compacted into cells independently by kind, capped at 256 attractions and 256 alerts, plus the user. Dots retain original repository IDs and coordinates. Animation does not recompute geographic projection.

**Files changed**

- [RadarGeography.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/main/java/com/spidertracker/app/RadarGeography.kt): viewport mapping, bounded dots and mode labels.
- [RadarUi.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/main/java/com/spidertracker/app/RadarUi.kt): mode selection, opaque pixel polygon, clipping and draw order.
- [MapRenderer.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/main/java/com/spidertracker/app/MapRenderer.kt): native visible-bounds/projection callback and refresh triggers.
- [TrackerScreen.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/main/java/com/spidertracker/app/TrackerScreen.kt): observable viewport snapshot wiring.
- [TrackerModel.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/main/java/com/spidertracker/app/TrackerModel.kt): explicit GPS-centered radar state and exploration transitions.
- [RadarGeographyTest.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/test/java/com/spidertracker/app/RadarGeographyTest.kt): world records, corners, invalid projection, density and physical geography.
- [Phase3StateTest.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/test/java/com/spidertracker/app/Phase3StateTest.kt): Globe exits GPS-centered radar while retaining the fix.
- [RadarRenderingTest.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/androidTest/java/com/spidertracker/app/RadarRenderingTest.kt): updated viewport input; opacity and crisp colors.
- [GeographicRadarTest.kt](C:/Users/Vahram/Desktop/Spider-Tracker/app/src/androidTest/java/com/spidertracker/app/GeographicRadarTest.kt): camera/bearing/dateline, visible colors, native/Compose occlusion and Android GPS-provider checks.

**Verification**

36 JVM unit tests and 11 targeted instrumentation tests passed, with zero failures or skips, on Medium_Phone_API_37.0 (API 37), 780 × 1386 pixels, density 320. Tests cover layer toggles, panning, zoom, bearing, dateline wrapping, GPS/no-fix behavior, range independence, dot selection, attached controls, landscape inset regression, clustering and reduced motion/background animation behavior.

The occlusion test places the real British Museum map sprite directly beneath the radar and adds an exaggerated magenta native glow at the same coordinates. It compares polygon-interior pixels against a second state with the markers/glow removed, excluding radar dots and a two-pixel boundary. The pixels match. A separate test verifies rendered green and red dot pixels.

The actual AndroidLocationProvider accepted fresh emulator GNSS coordinates; its coordinates matched the centered radar user dot. The emulator source is visibly labeled TEST GPS. The existing 1,000-report native clustering check settled to one cluster in 304 ms. A unit test also covers radar compaction of 20,000 records; no physical-device frame-time profile was performed.

- [Occlusion screenshot](C:/Users/Vahram/Desktop/Spider-Tracker/docs/screenshots/radar-fix/marker-under-polygon.png)
- [Attraction and alert dots](C:/Users/Vahram/Desktop/Spider-Tracker/docs/screenshots/radar-fix/geographic-dots-london.png)
- [World viewport](C:/Users/Vahram/Desktop/Spider-Tracker/docs/screenshots/radar-fix/world-viewport.png)
- [Android-provider GPS](C:/Users/Vahram/Desktop/Spider-Tracker/docs/screenshots/radar-fix/device-gps.png)
- Test/build logs: build/radar-verification.log and build/radar-apk.log.

**Debug APK**

[app-debug.apk](C:/Users/Vahram/Desktop/Spider-Tracker/app/build/outputs/apk/debug/app-debug.apk), 60,853,024 bytes. `:app:assembleDebug` succeeded after verification.

SHA-256: `167EC1B7CEE597398ACDF019050C568CC5FA1E6CCDE3466C01128BC34CFF22A6`.

**Remaining limitations**

The existing alerts repository supplies explicitly labeled demonstration alerts, not a live incident feed. Attraction coverage remains limited to the existing catalog. Dense cells retain representative records rather than displaying every overlapping record. GPS was verified through the emulator's Android provider; physical hardware was not checked. The reported original bleed was not reproducible in the checked source/emulator, though the completed mask passes direct occlusion verification. No dependencies or assets were added; unrelated UI was not changed.
