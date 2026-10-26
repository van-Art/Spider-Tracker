# Android reconstruction contract — implementation has not started

This document translates the completed analysis into engineering requirements. Component names here are proposed code organization, not additional UI. Use Kotlin and Jetpack Compose for layout/text/semantics, Canvas/vector drawing for custom chrome and HUD, Compose animation primitives for state transitions, MapLibre Native for map rendering, OSM-derived vector data, and foreground location/GPS.

## Smallest complete architecture

One Activity, one screen-level state holder/ViewModel, one map renderer, one repository for independent sighting/event content. A top-level Compose `Box` owns the persistent frame, map viewport, content panels, over-frame controls, footer and viewport modals. The existing website event bus is evidence of behavior, not a reason to recreate a global event bus in Kotlin.

State to retain: boot phase, sound choice, visible section, first-open explanation state per section, confirmed/rumored booleans, selected pin/card, active rumored detail, camera, active media, temporary toast/count banner, seen IDs and last visit. Distinguish a closed detail from a collapsed animation frame. Use one source of truth for marker/log filters. Radar deliberately reads its own complete pin input to match the observed behavior.

Use a lifecycle-aware MapLibre Native MapView hosted by Compose's AndroidView if the selected project has no existing map wrapper. Standard native style sources/layers provide markers and a custom basemap; avoid an extra map abstraction dependency for this screen. Verify the actual SDK release/API at implementation time; this analysis does not pin a guessed version. [Official MapLibre Android examples](https://maplibre.org/maplibre-native/android/examples/) provide the relevant GeoJSON, style, camera and location surfaces.

## Pixel and density contract

Treat the website phone's CSS viewport as logical layout space. Android physical pixels must be divided by density before applying that reference. A390CSS-px phone reference should be compared to a390dp app content viewport, or both normalized to a single declared scale. Do not compare a1170px Android screenshot to390px reference without accounting for3× density.

Apply status/navigation bar insets once outside the measured content viewport; screenshot comparison crops system chrome, not app content. Keep the170px-equivalent footer and frame breakpoint rules rather than using Material adaptive navigation. `BoxWithConstraints` can select the existing≤768/≤550/≤500/≤420 behaviors in logical dp. Do not map Android orientation directly to the website's width breakpoint.

Use a small explicit layout calculation or custom Layout for the15-slice frame and shelf rows. Canvas paths draw step corners/bevels in integer device pixels. Compose text supplies labels, semantics and wrapping rules. Clip the map and overlays to the frame-content bounds; place viewport-side feeds and lightboxes outside that clip.

For font replacement: verify license first, then compare uppercase alphabet, digits, punctuation, long titles, dates, and the ticker. Tune actual glyph advances/baselines against references; Compose `sp` respects font scaling, so add accessibility checks for enlarged text rather than hard-locking user font scale. Any exact comparison runs must declare fontScale1.0, then separately test larger text without clipping essential controls.

## MapLibre + OpenStreetMap

OSM is data; MapLibre is the renderer. Pick an OSM-derived **vector** tile source with appropriate service terms, or self-host the chosen schema. A stock raster OSM tile image cannot be recolored feature-by-feature into this navy/teal basemap. Preserve visible OSM/provider attribution; do not carry Google's logo/terms into a MapLibre view. [OSM attribution requirements](https://www.openstreetmap.org/copyright) apply to the resulting map. The standard OSM raster server also has separate [usage constraints](https://operations.osmfoundation.org/policies/tiles/); do not use it as an unrestricted offline/prefetch service.

Build an original style using the chosen vector schema: dark water/background, teal land, low-contrast administrative boundaries, muted labels; introduce thin blue streets and muted highways at city zoom. Feature/source-layer names depend on that schema and are not known from the Google cloud map ID. Hide unwanted POI clutter through the original style rather than painting over map screenshots. Use [MapLibre style layers](https://maplibre.org/maplibre-style-spec/layers/) for fills, lines, symbols and zoom-dependent styling.

Start with center/zoom from01, but calibrate **visible geographic span**, not just the numeric zoom. Google and MapLibre camera zooms/tile conventions may require an offset. Match recognizable coastlines at the same map rectangle, then city street scale. Keep both engines' captured coordinate/span measurements in comparison records. Preserve horizontal world behavior and latitude bounds as supported.

Markers: independent GeoJSON features keyed by stable ID with category and display override. Separate style layers or explicit filters control confirmed/rumored; trailer/events remain. Use original icon images generated from vector shapes or Canvas at required density, allowing overlap/placement rather than automatic cluster aggregation. Control draw ordering explicitly. If MapLibre collision handling suppresses features, enable overlap/ignore placement as appropriate and verify parity with the reference.

Marker tap: query rendered hit candidates in the map viewport, select according to visual order, detect overlap at low zoom, animate to the observed focus span, then anchor the150×100-equivalent Compose popup to a projected screen coordinate. Popup uses clamped bounds and pixel pointer. Programmatic camera animation and popup completion must share a cancellation token/job so rapid selections cannot reveal stale content.

Rulers, grid and radar are Compose Canvas overlays. Do not bake HUD into map tiles. Clip map tint below chrome and above basemap/markers in the same order as the source, taking composite sampled palette into account. Keep attribution readable above the overlay or reserve an unobstructed attribution strip.

## GPS substitution

The website uses IP-derived centering; the requested independent app uses foreground GPS/location. Trigger a foreground request from the existing radar target action. Handle approximate grant, precise grant, denial, permanent denial, disabled location provider, unavailable fix and timeout. Keep global map usable in all cases; use the observed transient toast region for feedback where possible. Do not add an unobserved always-visible location puck or background tracking feature.

For precise location on Android12+, fine and coarse permissions are requested together; approximate location remains a valid grant. See [Android runtime location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime). Do not request background location for a center-map action. Cancel outstanding location updates when the screen/activity stops; distinguish a stale cached fix from a current GPS result. A standard LocationManager foreground one-shot GPS/network approach can meet this scope without adding a new service dependency, but actual hardware calibration and provider behavior still require device verification.

Centering should preserve current zoom, consistent with the source target button. Location remains local app state; do not send it to the original website or its telemetry. A failed fix should leave the current camera intact. If permission prompting introduces OS UI, that is a platform-required state and is excluded from website pixel matching, not silently omitted.

## Original replacement assets

Preserve geometry, purpose, color role and frame cadence while using a distinct independent identity. No protected logo/character tracing, film stills, Sony/Samsung/Marvel branding, original site sprites, commercial font redistribution, Google tile extraction, or media hotlinking in the app. Captured material under `analysis/source`, `analysis/rea`, `analysis/evidence`, and `analysis/references` is an inspection archive and must be excluded from Android packaged assets/resources.

| Reference asset family | Independent replacement requirement |
|---|---|
| Central tracker logo | Original wordmark in280/180px envelope, same center/top anchor; avoid copied emblem/lettering |
| Menu head and close animation | Original mascot/control silhouette in62/50px envelope; equivalent idle/close progression12/18fps |
| Lower-left character | Original small field-agent/mascot art, same59×95/height60 envelope and12fps cadence |
| Spider marker/filter glyphs | Original recognizable category pictograms with same green/red/white/cyan semantics and pixel contour scale |
| Event star badge | Independently drawn event icon; preserve footprint/color distinction |
| Frame slices, buttons, pin-card border | Original Canvas/vector stepped paths with measured bevel bands and row/column dimensions |
| Radar web image | Original geometric polar-grid artwork,100px square with matching controls/sweep |
| Map-grid and ruler art | Original Canvas lines/ticks at measured screen-space locations |
| Web Watch background | Original vector linework/map texture, same low contrast and orientation motion |
| Sighting/event/villain/trailer media | Original/licensed content, comparable aspect ratios and luminance; preserve layout and navigation |
| Film/sponsor/footer artwork | Independent app identity/credits in the same reserved envelopes; no false sponsorship |
| Wallpapers/stickers/emoji | Original independent collection; preserve conditional presentation only if the app has a defined eligibility requirement |
| Pixel typeface | Independently licensed pixel font or original typeface, baseline/advance calibration required |
| Sounds | Original/cleared effects, corresponding categories, explicit sound choice |

No replacement artwork is generated during the analysis deliverable. Content counts and title lengths used for visual fixtures should mimic measured density without copying creative descriptions. An original app name and content identity remain product inputs; do not invent a final brand during reconstruction.

## Behavior and accessibility

Build custom styled controls with Compose semantics, selected/toggle states, meaningful labels and keyboard/back handling. Draw icons with Canvas/ImageVector but keep their actions reachable to TalkBack. At minimum48dp touch targets can be expanded invisibly around visually smaller filters/radar controls without moving artwork; resolve neighboring hit-region overlap deterministically. Use spatially separate hit zones where enlarged targets meet.

Android Back: dismiss media, section explanation, active section/menu, then return to map; let Activity exit only after overlays are closed. This is an Android platform adaptation of Escape/outside-dismiss behavior, not an invented visible UI. Pause decorative loops/media in background. Respect reduced-motion settings in animation execution. The website's global focus-outline suppression is not an accessibility requirement to reproduce.

## Content boundaries and known gaps

The independent app needs independent sighting/event fixtures or a defined content service; it must not imply GPS can discover fictional sightings. The public JSON schema is reference for data shape only. No sign-in, submissions backend, moderation, push subscription, analytics recreation, tickets integration or scraping scheduler is authorized by visual reconstruction alone.

MapLibre/OSM does not replace Street View or photorealistic Google3D. Original panorama assets can be displayed in an appropriate licensed renderer later, but no empty panorama/model controls should be shown just to resemble dormant source branches. Treat those modes as explicit content/platform gaps in acceptance, not as implemented parity.

Implement only after this analysis deliverable. Subsequent work should first establish the shell and basemap, then existing interactions, then original media/art, and run the comparison protocol in05 after each material visual change.
