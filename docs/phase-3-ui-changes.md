# Phase 3 UI changes

Phase 3 continues the existing single-activity Kotlin / Jetpack Compose / MapLibre application. The approved 941×1672 image is preserved in `phase-3-evidence/reference/approved.png`. Phase 1 evidence and Phase 2 source were inspected before editing. This phase supersedes the old Phase 3 navigation proposal; it does not implement routing or a backend.

## Main screen

The footer, trailer launcher, sound/ticker shelf, hamburger action, left confirmed/rumored switches and automatic new-sighting banner are removed from the main screen. The old filtering, panel, log and marker action state remains available internally; no replacement navigation controls were invented. Existing confirmed, rumored, event and trailer markers retain Phase 2 graphics and fixture data. Map and geocoder attribution remains visible as required; it is a small map overlay rather than a footer.

`TrackerLayout` derives geometry from the actual safe content constraints in dp. The map begins at (27,28), spans width minus 54 and height minus 86. The search shelf is anchored 58dp above the bottom. At a 390×693dp safe content area the map is 336×607dp. There is no minimum 600dp page and no screen scrolling. System bars, display cutouts and IME use Compose safe-drawing/IME insets. Hidden three-button navigation no longer leaves a 48dp empty strip; visible navigation bars reserve their own insets.

The frame uses stepped Canvas paths and bright #00BEFC cyan. Map geometry, style, zoom gestures and native panning remain in the renderer. The top and left ruler marks and the grid draw without pointer handlers. Full-size decorative boxes do not consume touches. Click handling is confined to the actual search/results, attribution, marker cards and 48dp radar buttons.

The supplied 280×47 header scales proportionally to min(49% of content width,280dp), centered at 16dp. The 62×64 and 64×64 corner assets fit 40dp areas at 12dp from the sides and 13dp from the top. The left asset is decorative and has no hamburger action.

## Radar and search

The radar diameter is clamp(26.5% of content width,92dp,120dp), up from approximately 70dp on Phase 2 phones. At 390dp it is 103.35dp. Its lower-right anchor comes from the map bounds. The supplied 24px globe and target are displayed at 28dp with nearest-neighbor sampling. Their independent 48dp hit rectangles meet without overlapping; the globe artwork is centered on the radar's right edge, the target is at the lower-right boundary. There are no new square backgrounds or connector lines.

The editable search uses BasicTextField and the existing Pixelify Sans font, a #112632 input surface, #00D7FA stepped outline and #59BA61 arrow. It supports IME Search, an explicit submit button, real result selection, loading, empty, unavailable, error and dismissal states. Results occupy only their measured panel. The sprite has a separate 32×50.96dp area to the left of the field. Narrow inputs scroll horizontally rather than covering the character or submit target.

When the IME leaves less than 150dp of map height, the radar hides until space returns; the input and submit remain visible. This is a responsive adaptation for short windows rather than a new screen. Landscape and tablet retain the same hierarchy.

## Motion and behavior

The existing panel transitions and native 1500ms camera animation remain. The radar sweep uses the existing angular cadence. The supplied sprite advances at 12fps from a shared elapsed foreground clock, sampled at approximately 30Hz; the clock pauses outside RESUMED. Capture mode fixes the animation time at 0.7s for repeatable evidence. Normal startup goes directly to the map because the old tutorial describes removed controls.

Destination and location results are plain coordinate models. MapLibre receives only MapScene / CameraTarget and a destination, keeping geocoding and Android location types outside the renderer. Manual map gestures, global reset, pin selection, a new destination or activity stop cancel an outstanding location fix. There is no route engine, route line, turn guidance, background tracking, reporting service or account integration.

## Files

Changed: `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `TrackerModel.kt`, `TrackerScreen.kt`, `MapRenderer.kt`, the two existing test files, README. Added: `AddressSearch.kt`, `AddressSearchUi.kt`, `DeviceLocation.kt`, `SuppliedArt.kt`, `Phase3StateTest.kt`, `GeocoderTest.kt`, Phase 3 evidence/scripts/docs. The malformed asset filename `center.png.png` was renamed to `center.png` without changing its bytes. Phase 2 evidence/APK remains preserved.

## Complete source/configuration change list

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/spidertracker/app/MainActivity.kt`
- `app/src/main/java/com/spidertracker/app/TrackerModel.kt`
- `app/src/main/java/com/spidertracker/app/TrackerScreen.kt`
- `app/src/main/java/com/spidertracker/app/MapRenderer.kt`
- `app/src/main/java/com/spidertracker/app/AddressSearch.kt` (new)
- `app/src/main/java/com/spidertracker/app/AddressSearchUi.kt` (new)
- `app/src/main/java/com/spidertracker/app/DeviceLocation.kt` (new)
- `app/src/main/java/com/spidertracker/app/SuppliedArt.kt` (new)
- `app/src/main/res/drawable-nodpi/center.png.png` → `center.png` (rename only)
- `app/src/test/java/com/spidertracker/app/TrackerStateTest.kt`
- `app/src/test/java/com/spidertracker/app/Phase3StateTest.kt` (new)
- `app/src/androidTest/java/com/spidertracker/app/TrackerInteractionTest.kt`
- `app/src/androidTest/java/com/spidertracker/app/GeocoderTest.kt` (new)
- `README.md`
- `scripts/capture-phase3.ps1`, `scripts/compare-phase3.py`, `scripts/verify-phase3.py` (new)
- The five `docs/phase-3-*.md` deliverables, `docs/phase-3-evidence/`, `docs/phase-3-screenshots/` and `dist/spider-tracker-phase3-debug.apk` (new)

Original source/configuration snapshots are kept in `.tools/phase3-baseline/`. The workspace has no Git metadata, so no commit or PR was created. Existing Phase1/Phase2 documents, independent marker artwork, media/panel implementation and Phase2 delivery APK remain unchanged.
