# Architecture, DOM, data, and maps

## Observed structure and source boundaries

The HTML uses `data-astro-cid-*` scoped attributes and component-named `_astro/*.astro_astro_type_script_index_0_lang.*.js` modules. Styles include CSS nesting and minified component bundles. Public modules use direct DOM APIs, classes, attributes, and `CustomEvent` dispatch. This is sufficient evidence for an Astro-generated page with imperative browser controllers; it does not reveal the original repository or backend implementation.

The principal DOM hierarchy is:

```text
page-layout: grid(header 0, tracker 1fr, footer 170)
  viewport overlays: XFeed, EventsPanel
  tracker-container
    fifteen-slice-frame: 3 columns × 5 rows
      frame-content: clipped, contains layout/paint
        MainMenu
        Preloader / SoundOptIn / intro video / PreMap
        map-view
          map-vector-layer
          map-pano-layer
          map-3d-layer
          map overlay tint / HUD / grid / radar
          Google Maps panorama embed
        ActivityLog / conditional Downloads / WebWatch
        NotificationCenter / HelpScreen / Toast
      bottom frame row: MainUIPanel / ticker / sound
    over-frame-ui: menu, badge, character, message center, filters
    WatchTrailer CTA
  site-footer / collapsible credits
  viewport Lightbox: backdrop, header, media or download options
```

Positioning contexts matter: menu/log/help/Web Watch occupy **frame content**; Events and X feed are right-side **viewport** overlays; the Lightbox covers the viewport. Numeric `z-index` values from different contexts cannot simply be globally sorted. `contain: layout paint`, transforms, opacity, and map `isolation:isolate` create boundaries.

## Module responsibilities

| Public module prefix | Responsibility and relationships |
|---|---|
| `Layout` | Boot phase controller, sound preferences, event adapters, global `controller` |
| `runtimeData` | One cached promise for `data/main.json`; does not periodically poll in this module |
| `spideyPinsData` | Base pin load, hidden-pin exclusion, fallback thumbnail, event normalization and region lookup |
| `FrameUI`, `spritesheet` | Menu head state animation, character loop, resize reconstruction |
| `MainMenu` | Configuration ordering/visibility; closes other panels before dispatching navigation |
| `Preloader`, `SoundOptIn`, `PreMap` | Startup gates, progress theater, sound choice, tutorial/map-readiness handoff |
| `Map2D` | Google map, AdvancedMarkerElement instances, category visibility, popup manager, pin selection |
| `flyToLocation` | Mercator camera interpolation, easing, cancellation generation counters |
| `Radar` | Canvas sweep and pin plotting; global view and cached neighborhood actions |
| `Map3D`, `Map2DPano`, `GoogleMapsPanoEmbed` | Alternative map/media modes; source-confirmed, see limitations below |
| `ActivityLog`, `pinCookies`, `seenCookie` | Date-sorted list, shared filters, seen state gated by functional consent |
| `MsgCenter` | Rumored sighting detail content and staged mechanical panel animation |
| `NotificationCenter` | Temporary count banner; distinct from MsgCenter |
| `XFeed`, `EventsPanel`, `WebWatch` | Content views and media navigation |
| `SectionExplainerModal` | Reusable first-open information panel with delayed opening |
| `Lightbox` | YouTube/video, image, galleries, ticketing, download choice modes |
| `soundManager`, `Toast` | Categorized sound, ticker/UI feedback |
| `pushNotificationManager`, `SamsungExclusives` | Device eligibility, optional download section, notification subscription machinery |

`page.json` retains loaded-script sources and DOM. `bundle.json` parsed **67 scripts, zero parse failures, 192,208 visited AST nodes**. Its own completeness applies to admitted scripts, not all possible website behavior. Endpoint-candidate heuristics such as WebGL extension names or `minZoom` are not HTTP APIs and must not be copied into an API contract.

## Data flow

The inline boot object sets `window.siteInit` before feature controllers. `runtimeData` fetches `./data/main.json` with `cache:no-store` and shares the promise. Observed snapshot: **53 base pins: 23 confirmed, 29 rumored, 1 trailer**, six Web Watch articles, five gallery video entries. Event pins arrive separately and explain why the initial combined sighting count can exceed 53. The capture displayed 127; that is snapshot data, not a constant.

Source-confirmed fetch surfaces:

| Surface | Input/output contract observed in client |
|---|---|
| `data/main.json` | `init`, `xFeed`, `spideyPins`, `videoGallery`, `webwatch`, sound declarations |
| `data/events.php?region=XX` | `{events: array}` or grouped arrays; fallback region US after lookup |
| `x-feed.php?max=20` | `{posts: [...]}`; client always prepends its own introduction |
| CloudFront regional lookup, fallback `ipwho.is` | Neighborhood coordinates and coarse city/region cache |
| Google Maps JavaScript API | Vector map, markers, 3D and panorama facilities |
| `push/subscribe.php` | Subscription POST, source-only; **not executed** |

No authenticated/private APIs, form submissions, X posts, tickets, or push subscriptions were needed. Public data URLs establish client behavior, not permission to depend on Sony's backend in the independent app.

Base pin fields include `id`, `pinType`, `lat`, `lng`, `title`, `createdAt`, `hidden`, optional thumbnail/image, URL, trailer source, panorama URL(s), custom panorama configuration, or 3D model reference. Rumored entries can carry handle, display location, message, image/video arrays. Event normalization maps location coordinates, name, attachments and `pinTypeOverride` into the same pin shape.

## Map rendering and camera

**Observed:** the basemap is Google Maps, not OSM/MapLibre. Default UI is disabled, greedy gestures are configured, tilt is zero in 2D, and map details/labels vary with zoom. The map has blue-black water, teal land, subtle blue boundary lines, muted labels, and an additional CSS tint `#0d1a4d26` above it. The world presentation can repeat horizontally; it is not a static world image.

**Source constants:**

| Property | Value |
|---|---|
| Initial/global center | latitude `15.973899714245507`, longitude `-49.9007427311529` |
| Initial/minimum zoom | `1.5` |
| Bounds restriction | north 81°, south −78°, west −180°, east 180°, strict |
| Alternate configured start | New York `(40.740974041558644, -73.95684992135047)`, zoom `11.83948942625231`; inactive in captured configuration |
| Pin focus zoom | `13.597060635860297` |
| Marker overlap focus | 1500 ms; show popup about 80 ms after flight |
| Activity-row focus | 2500 ms; popup around 2600 ms |
| Global/normal helper flight | normally 1200 ms; variable caller overrides |

`flyToLocation` interpolates projected Mercator coordinates (latitude clamp ±85.05112878). For sufficiently distant destinations it may first zoom out and then move; `singleRoute` bypasses this. Camera generations cancel old animation frames. A second user selection must not allow the first flight's delayed popup to become the final visible selection in the Android implementation.

There is **no observed numbered marker clustering**. Each admitted point creates a DOM-backed AdvancedMarkerElement. A selected marker whose screen rectangle overlaps another marker, while below focus zoom, triggers the zoom flight before showing a card. Implement this behavior without inventing cluster bubbles.

Draw order: trailer `999999`, events `500`, confirmed `100`, rumored `10`. Base icon sizes: 32 CSS px desktop, 25.6 at width ≤768. Events may use type overrides for display/filter grouping. Trailer is white; events use a light-blue star-shaped badge; confirmed green and rumored red. Exact artwork must be replaced.

The popup is a **150×100 CSS px** cream thumbnail card, black 3 px stepped border, small bottom pointer, underlined action strip. Its position is clamped to an 8 px map inset; source places it above the marker when space allows. It is dismissed by map/camera changes or outside pointer-down. Pop-in uses opacity and 3 px vertical translation over 160 ms. A 220 ms scheduled hide path exists in the manager, but the inspected marker handlers bind clicks rather than proving a hover-only popup behavior.

CTA priority: trailer → event → 3D model → Google panorama → custom panorama → image lightbox → external URL → rumored message detail. Action labels reflect this priority. It is not an always-present generic details sheet.

## Radar and map HUD

The radar is a **100×100 logical px Canvas**, backing resolution capped at DPR 2. A separate web-like bitmap is the background. Canvas draws a rotating sector, sweep line, center dot and category-colored points. The sector is `0.55π` radians; leading angle increments `0.018` per frame, so the website speed depends on frame rate (about 5.82 seconds per revolution at 60 Hz). A time-based equivalent is the Android translation, not a measured fixed web period.

For each point: Haversine distance, geographic bearing minus π/2, radial compression `(distance/range)^0.82`, radius up to 50 px. The source range expands with zoom out: `50 * 2^(s*i*0.58)`, where `i=max(1,13-minZoom)` and `s=min(1,max(0,13-zoom)/i)`, lower bounded by 25 km. Dot radius 2 px. Radar reads the pin arrays, **not the filter visibility flags**; hiding confirmed markers does not itself remove their radar dots. Event pins are appended. In 3D, base dots narrow to confirmed pins with model IDs.

HUD rulers and grid are screen-space decorations, not measured geographic grid lines. Desktop ruler anchors: horizontal `(100,25)`, height18; vertical `(25,100)`, width18, relative to map. At ≤550 these become `(56,16)` and `(16,56)`. Grid is offset 5% right / 8% down, full-size, opacity0.5 and clipped by map content.

## 3D and panorama scope

Source includes lazy 3D initialization, range/tilt/heading controls, source thresholds (enter constant11, exit range12000, hard zoom-out18500), source-specific model focus, a Photo Sphere Viewer-style custom panorama, and Google panorama iframe navigation. The captured runtime base pins contain 12 Google panorama destinations and no activated model-ID/custom-pano pins. These branches are **source-confirmed**, not verified equivalents in MapLibre. A configured constant alone does not prove that crossing ordinary 2D zoom automatically enters 3D; the inspected code enters from a model action.

OSM/MapLibre will not supply Google Street View or Google's photorealistic 3D. The Android contract therefore keeps the measured shell/camera behavior and uses original/licensed panorama/media content only when supplied. Do not replace missing content by inventing a new UI or pretending an OSM map is Street View.
