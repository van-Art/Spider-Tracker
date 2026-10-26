# Phase 2 — Native Android visual reconstruction

Delivered 23 October 2026 (Asia/Yerevan). Kotlin/Compose app, package `com.spidertracker.app`, version `0.2.0` (1). This is an independent reconstruction of the dated Phase 1 observations. It is not a browser wrapper.

## Evidence read before implementation

Read the Phase 1 README, architecture/map, measured visual specification, interaction specification, reconstruction contract, validation limits, screenshot index and machine-readable measurements. The source snapshot, runtime data, REA action states and image files were inventoried before implementation. All 232 retained reference/original PNGs were visually reviewed through 12 contact sheets; canonical evidence remains the 223 REA captures, not the failed-attempt images. The 810-file hash inventory is [here](evidence/phase1-review/inventory.json); contact sheets are in the same directory. The implementation does not alter the Phase 1 archive.

Primary contracts: [measurements](../analysis/02-visual-specification.md), [interactions](../analysis/03-interactions.md), [Android translation](../analysis/04-android-reconstruction.md), [evidence limits](../analysis/05-validation-and-limits.md).

## Implemented behavior

| Surface | Native implementation and functional behavior |
|---|---|
| Shell | Canvas stepped frame, independent corner/edge/bevel bands, map clipping, header badge, menu/message controls, ticker shelf, sound toggle, footer and trailer tab. No Compose Material widgets. |
| Map | Embedded MapLibre Native texture view, original navy vector style, OpenFreeMap OSM tiles, country/place/street hierarchy and public-domain world fallback. Pan and pinch are native gestures; rotation/tilt/stock compass are disabled. Visible linked provider attribution replaces Google attribution. |
| HUD | Canvas rulers, grid, polar radar, rotating sweep and distance/bearing dots. Radar uses all fixtures independently of the two map filters. Global control resets the camera; target control centers the fixed demo neighborhood. |
| Markers | 23 confirmed, 29 rumored, 8 events and 1 trailer fixture. Original category glyphs, separate GeoJSON symbol layers, overlap enabled, explicit draw order. 25.6dp mobile / 32dp desktop-style envelopes. |
| Selection | Marker tap or Activity Log row selects a fixture, marks it seen, flies the camera and projects a clamped 150×100dp card. Camera motion hides the card until idle; a newer request supersedes the previous native animation. Map background/gesture clears it. |
| Filters | Two toggles, matching green/red/cream roles. Confirmed/rumored changes update both the map source and Activity Log. Events and trailer stay visible. A replacement toast restarts on rapid changes. |
| Navigation | Six observed entries in the original order: Activity Log, Report Sightings, Web Watch, Videos, Events, Help. Full map-width menu on phones, 320dp panel otherwise. Outside tap and Android Back dismiss overlays. |
| Activity Log | Charcoal scanlines, date-descending rows, category icons, single-line titles, dates, seen-state opacity, scrolling. Badges disappear at ≤600dp. Selecting a row returns to the map. |
| Message detail | Rumored-card action populates the amber message control, green mechanical base and cream content. Thumbnail opens original media. Closing clears the detail and returns idle; stale messages cannot be reopened. |
| Help | Measured category/control labels and yellow intro over an original low-contrast map texture; tap anywhere closes it. |
| Web Watch | Six original illustrated profiles. Phone: vertical articles, 200×300dp portrait above text. Tablet: vertical articles with side-by-side content. Width >768dp: horizontal carousel, arrows and six position controls. |
| Events / Report | Right-edge 350dp viewport-height scroll panels, independent of map clipping. Events use white text/near-black backing and original media. Report uses cyan/navy and mock posts; its CTA reports demo availability in the existing button. |
| Media / Videos | Viewport lightbox, original vector city/drone images, play/pause demo animation, five-item expandable gallery and current-item metadata. No remote film/video player. |
| Startup / guidance | Boot, explicit sound choice, tap/7-second tutorial, 1.5-second loading, live map. Delayed section explainers remember dismissal. Original short synthesized interaction sound respects the sound setting. |
| Motion | Menu 240ms, staggered links 90+36ms, panel fades/slides, log 560ms slide, message 600ms arrival, radar 1.08rad/s, 12fps stepped mascot, ticker 45dp/s, native camera 1.5s and expanding video strip. |

## Measured layout and Android adaptations

One CSS reference pixel maps to one logical dp at font scale 1.0. `TrackerLayout` implements the measured ≤768dp breakpoint, page minimum 600dp, footer 170dp, mobile frame 27dp and desktop-style frame 36dp. At 390×844 the map is exactly `(29,29,332,586)` and footer starts at y674. At 768×1024 the map is `(29,29,710,766)`. Short landscape keeps the reference's taller composition and allows the whole page to scroll.

Typography uses licensed Pixelify Sans; commercial PF Videotext was not copied. Cyan `#96e0f7`, pale cyan `#a8e1fe`, charcoal `#282828`, cream `#eaeadc`, orange `#f89d51`, water `#0e172b` and land `#043956` follow Phase 1. Additional uninterrupted frame samples yielded face `#3997bb`, side bevel `#2976a0`, horizontal recess `#154f80` and edge `#1e1e14`.

The world camera was calibrated from Android screenshots: requested MapLibre zoom 1.0 is closer to the captured Google view than the initial 0.5 assumption. Numeric Google zoom 1.5 was not treated as a cross-engine contract. Detail request is 12.597 at the selected mock city. Geography, tile labels and fixture positions remain different; see the [comparison report](visual-comparison.md).

Android adaptations include transient immersive system bars, platform Back handling, checkbox/button semantics and expanded control hit areas. Small radar hit areas meet with deterministic later-control precedence. Marker stories are also reachable through the labeled Activity Log. Larger font scale is supported by scrollable navigation and bounded text, but TalkBack/hardware accessibility certification has not been performed.

## Source structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/spidertracker/app/MainActivity.kt` | Native host, immersive window, ViewModel and debug-only screenshot-state intents. |
| `TrackerModel.kt` | Independent fixtures, filter/section/media state, saved settings/seen state, camera requests and Back behavior. |
| `MapRenderer.kt` | `TrackerMapRenderer` boundary and MapLibre implementation; data/style/camera/projection/lifecycle bridge. No location or routing dependency. |
| `TrackerScreen.kt` | Layout, persistent shell, HUD, startup, custom text/buttons and footer. |
| `TrackerPanels.kt` | Menu, log, explainers, Web Watch, side feeds, message and media panels. |
| `TrackerArt.kt` | Original Canvas/vector/bitmap graphics and palette. |
| `app/src/main/assets/tracker-style.json` | Original OpenMapTiles-schema style; regenerate with `node scripts/prepare-map.cjs`. |
| `scripts/capture-android.ps1` | Reproducible emulator state captures at matched logical sizes. |
| `scripts/compare-android.py` | Density normalization, paired boards, overlays, masks, hashes and pixel diagnostics. |

The renderer interface accepts scene state, camera request, pin/background callbacks, projected card anchor and camera feedback. Only this implementation imports MapLibre classes. Compose panels and fixture/filter logic do not depend on the engine. The native MapView receives activity lifecycle events and is destroyed on disposal. SavedStateHandle retains filters, sound, startup/section and seen/explainer state; transient media/selection/camera are not persisted across process death.

## Independent assets and services

All scanner, mascot, marker, frame and city/drone art is generated from original vector/Canvas geometry. Stories, profiles and event descriptions are mock content. No first-party site image, logo, font, sprite, film still, Google tile or source archive is packaged.

Pixelify Sans is bundled under SIL OFL; Natural Earth country geometry is public domain. MapLibre's BSD license and font license are in packaged assets and [docs/licenses](licenses). OpenStreetMap/OpenMapTiles/OpenFreeMap attribution is visible and linked. Credits include Natural Earth and the original artwork. See packaged `NOTICE.txt` for sources.

Only map tile/glyph requests use the network. The application contains no GPS permission, location reader, route engine, reporting backend, remote sighting service, analytics or push registration. The target button deliberately uses a fixed demo location. Offline, bundled world geometry, fixtures and panels remain available; uncached street detail and glyphs need network access.

## Build and delivery

AGP 9.1.1, Gradle 9.3.1, built-in Kotlin/Compose plugin 2.2.10, Compose BOM 2026.03.00, MapLibre Native 13.6.1. Minimum Android 26, compile API 37.0, target API 36. The tested host used Android Studio JBR 25.0.3 and Android SDK build-tools 36.0.0. Runtime dependencies are pinned; lint's newer-version notices are documented rather than causing unrelated upgrades.

Set `local.properties` to your Android SDK, then run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# With an emulator/device connected:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Installable delivery: [dist/spider-tracker-phase2-debug.apk](../dist/spider-tracker-phase2-debug.apk). Gradle output: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed universal APK; it is not a production store release.

Actual validation and remaining limitations: [test results](phase-2-test-results.md), [visual comparison](visual-comparison.md). [Phase 3 navigation plan](phase-3-navigation-plan.md) is documentation only.
