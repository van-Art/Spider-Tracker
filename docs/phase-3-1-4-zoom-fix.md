# Phase 3.1.4 — Map zoom, gestures and camera

Date: 2026-10-25. MapLibre Native Android 13.6.1; no dependency, asset, layout or map-style changes.

## Root cause

The renderer applied the initial camera in `getMapAsync`, then reconsidered the same request when `loaded` changed. A native camera change at style readiness could therefore be overwritten by a 1500 ms flight back to the initial zoom. A deterministic instrumentation regression reproduced **zoom 5 → 1** with the request-consumption guard disabled.

Separately, sighting selection checked the last requested `camera.zoom`, rather than observed native zoom. After pinch zoom, selection could unexpectedly refocus. Marker source queries and radar projections also ran redundantly during camera movement. Radar's rectangular touch region covered transparent corners of its polygon.

The original zoom regression also had injection timing failures: a requested 40 ms press took 134–148 ms, and a two-finger tap took 367 ms against the native 150 ms limit. Double taps no longer wait for rendering between events; two-finger taps use a defined 30 ms event sequence. Native thresholds are unchanged.

## Changes

- `MapRenderer.kt`: consume each camera request once, initialize from the latest request, clamp explicit zoom to native limits, cancel superseded explicit transitions, publish final observed zoom at idle, and remove the hard-coded cluster zoom ceiling of 17. Reproject existing sprites while moving; refresh cluster membership after movement, rather than query sources on each camera frame.
- `TrackerModel.kt`: use observed zoom when deciding whether sighting selection needs focus.
- `RadarUi.kt`: clip the radar hit region to its existing twelve-sided outline; transparent corners pass touches to the native map.
- `Phase312MapTest.kt`: fix double-tap injection timing; add style-readiness, rapid-command, bounds, pan, keyboard, explicit-action, marker and radar-corner regressions.
- `Phase312StateTest.kt`: regress observed zoom versus explicit sighting focus.
- This report.

## Gestures and camera behavior

Native pinch, double tap, quick zoom, two-finger tap and pan remain enabled. Double tap uses MapLibre's tapped focal point; two-finger tap is supported by this installed version ([native gesture implementation](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapGestureDetector.java)). Rotation and tilt retain their previously disabled configuration. No permanent zoom buttons or Compose map gesture engine were added.

Global Map, Current Location, address selection and explicit marker actions retain their camera behavior. Marker/filter changes, pulse/radar animation, search editing, keyboard insets and ordinary recomposition do not replay consumed camera requests. The remembered MapView and native tile cache remain in use.

## Validation

Emulator: `Medium_Phone_API_37.0`, Android API 37, density 320 dpi; portrait and landscape native gesture tests.

- Relevant unit suites: **37 passed**.
- **12 unique targeted instrumentation cases verified across the final runs**: native pinch in/out, double tap, two-finger tap (portrait/landscape), pan, rapid commands, zoom bounds, startup reset regression, keyboard/recomposition stability, GPS/search/global actions, GPS/attraction/alert anchoring, clustering, marker/radar interaction, tapped focal-point retention and circular glow alignment.
- The 12-case batch passed 11 cases; its remaining failure was the measured 367 ms injected tap. After correcting test timestamps, both affected gesture orientation cases passed (**2/2**); no application thresholds were relaxed. GPS/search camera checks use deterministic provider fixtures.
- Final `:app:assembleDebug`: **BUILD SUCCESSFUL**. Logs and unit/gesture JUnit XML are saved in `docs/phase-3-1-4-evidence/`.

APK: `app/build/outputs/apk/debug/app-debug.apk` (60,853,821 bytes).

## Limitations

Physical-device feel and frame-rate profiling are not measured. Cluster membership refreshes when the camera settles; existing sprites remain geographically projected during movement. The intentional global minimum zoom remains 1.0. Rotation remains disabled as before. Phase 3.2 was not started.
