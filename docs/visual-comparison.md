# Phase 2 — Visual comparison

Reference: the retained 22 October 2026 Phase 1 REA captures. Android: the final debug APK documented in [test results](phase-2-test-results.md). These are real emulator screenshots of native Compose/MapLibre rendering, not design mockups.

## Capture and normalization

26 final captures cover 16 phone states, 5 tablet states and 5 short-landscape states. [The comparison manifest](comparisons/comparison-metrics.json) records each raw/normalized path, SHA-256, timestamp, requested camera, fixture, density, viewport, decorative clock, actual AVD/API/serial and selected reference/hash. Each raw PNG has a `.png.json` device sidecar. Thirteen pairs have matching logical dimensions.

| Profile | Android physical pixels | Density | Logical / reference size | Behavior reviewed |
|---|---:|---:|---:|---|
| Phone | 780×1688 | 320dpi | 390×844 | Map, menu, two filter states, popup, message, image, feeds, log, Web Watch, Help, sound/tutorial, videos/gallery |
| Tablet | 1536×2048 | 320dpi | 768×1024 | Mobile frame; 320dp menu; log badges; vertical Web Watch with side-by-side articles; Help |
| Short landscape | 1688×780 | 320dpi | 844×390 | Desktop-style frame; page scroll; menu; horizontal Web Watch; video/gallery fitting available height |

Font scale1.0, English mock text, API37 x86_64, SwiftShader. All physical images are normalized with exact 2:1 BOX downsampling into RGB; no crop or geometric warp. The source REA screenshots were DPR1. Capture intents are debug-only, use ordinary app panels, disable the delayed explanatory overlays/count banner, and hold the decorative clock at0.7s. Startup flow is also exercised by the separate instrumentation test.

Map images use an independent OSM style and original replacement artwork. Mock content has different titles, dates, locations and count from the website. Those differences are retained, not disguised by copying protected media. Requested camera metadata is not a measurement of the original Google map's effective fractional zoom.

## Measured geometry

| Region | Phase 1 contract | Android result |
|---|---|---|
| Phone map | x29,y29,w332,h586 | Same layout rectangle; screenshot edges reviewed |
| Phone footer | y674, height170 | Same reserved layout |
| Tablet map | x29,y29,w710,h766 | Same layout rectangle |
| Short-landscape map | x51,y41,w742,h313 with 600px page minimum | Same logical rectangle; page scroll exposes the lower content |
| Phone menu/message envelopes | menu50 at10,11; message48 at330,14 | Same anchors/envelopes; original scanner symbols replace protected art |
| Phone badge | centered, width180, top16 | Same envelope; original text/glyph shapes |
| Phone filters | 38×30 at y140/y174 | Same visual envelopes; expanded invisible hit regions |
| Phone ticker | centered, width≈281,height33 | Same envelope; linked sound control sits to its right |
| Marker symbols | 25.6 mobile /32 desktop | Corrected native image density; same logical sizes |
| Web Watch portrait | 200×300 stacked at ≤550 | Same image dimensions; original skyline/drone content |
| Popup / message thumbnail | 150×100 /88×88 mobile | Same envelope sizes; independent media |
| Phone video strip | two visible thumbnails, height56 each | Corrected from four thumbnails in a full-width appended strip to a centered floating strip with two visible items |

Pure layout assertions are in the JVM suite. Screenshot comparisons additionally check the rasterized edges, palette, panel composition and text fitting; the geometric assertions alone are not presented as pixel fidelity proof.

## Iterative frame correction

Pixel threshold: a pixel changes when **any RGB channel differs by more than16/255**. This is the same threshold used for the retained REA comparison.

| Android iteration | Uninterrupted frame bands (14,823 pixels) | Frame chrome (48,354 pixels) |
|---|---:|---:|
| Initial native rendering | 39.466% changed | 30.783% changed |
| Density, scale and bevel correction | 0.000% changed | 4.783% changed |
| Final one-pixel bands and clipped badge edge | 0.000% changed | **1.344% changed** (650 pixels) |

The 1.344% result meets Phase 1's proposed ≤2% target **for this explicitly defined frame-chrome region only**. It is not a whole-app similarity score. Rounded/stepped corners and some edge pixels still differ. The zero band result means sampled flat bands match exactly; it does not establish that the frame art or entire screen matches exactly.

Chrome mask: base `(0,0)-(390,674)`, excluding map `(29,29)-(361,615)` and the replacement/menu/badge/message/filter/mascot/ticker/sound envelopes listed in the [metrics JSON](comparisons/comparison-metrics.json). Rectangles use exclusive right/bottom coordinates. See [chrome mask](comparisons/phone-frame-chrome-mask.png) and [uninterrupted-band mask](comparisons/phone-frame-bands-mask.png), where white pixels are evaluated. Paint outside the declared replacement envelopes is still counted. Footer creative artwork and map content are evaluated separately by layout/visual inspection.

The initial map camera was too wide at MapLibre0.5 and the markers were half-size because bitmap density was interpreted as2×. Explicit bitmap density160 and global zoom1.0 bring the visible world scale and marker envelopes closer to the reference. Country-boundary clutter at world zoom was reduced. Water/land composite colors are `#0e172b`/`#043956` without a second tint. The radar's mobile circle is70dp; its position was moved4dp left and8dp up to match the measured footprint.

Original frame samples added during implementation: face `#3997bb`, side bevel `#2976a0`, horizontal recess `#154f80`, outer edge `#1e1e14`, plus the one-pixel transition bands. Original stepped Canvas geometry is used; no frame image was extracted into the APK.

## Paired evidence

Each paired state has a side-by-side board, 50% overlay, unmodified absolute difference image and full-screen diagnostic in the manifest.

- [Phone map](comparisons/phone-map-side-by-side.png), [menu](comparisons/phone-menu-side-by-side.png), [Activity Log](comparisons/phone-log-side-by-side.png)
- [Message detail](comparisons/phone-detail-side-by-side.png), [sighting image](comparisons/phone-media-side-by-side.png)
- [Events](comparisons/phone-events-side-by-side.png), [Report](comparisons/phone-report-side-by-side.png)
- [Web Watch](comparisons/phone-webwatch-side-by-side.png), [Help](comparisons/phone-help-side-by-side.png)
- [Video](comparisons/phone-videos-side-by-side.png), [expanded gallery](comparisons/phone-gallery-side-by-side.png)
- [Tablet Help](comparisons/tablet-help-side-by-side.png), [short-landscape map](comparisons/landscape-map-side-by-side.png)

Iteration map PNGs and the earlier four-thumbnail gallery are preserved in `docs/screenshots`. The final app gallery has the source's floating position, narrow phone envelope, arrows and two-item window; original demo media replaces film playback.

REA was used again for the actual normalized Android PNG against the original phone reference. [Raw result](comparisons/rea-phone-comparison.json), [reference artifact](comparisons/rea-phone-reference.artifact.json), [Android artifact](comparisons/rea-phone-android.artifact.json). These self-verifying artifacts contain exact PNG bytes/hashes. The full-screen difference includes original artwork/font, OSM geography/labels, different fixtures and the reference's transient count banner; it must not be inverted into a claimed “accuracy” percentage.

## Remaining visual differences

| Area | Intentional replacement or remaining gap |
|---|---|
| Protected art | Original scanner/mascot, wordmark, category glyphs and procedural city/drone art replace site characters, film images and sponsor logos. Layout envelopes/color roles are preserved, creative imagery is deliberately distinct. |
| Typeface | Pixelify Sans is licensed; PF Videotext's glyph width, cap height, weight and text rhythm are not identical. Map labels use Noto Sans from the tile/glyph provider. |
| Basemap | OSM/Natural Earth coastlines, country labels, road hierarchy and city geography differ from Google. No Google logo/terms or extracted tiles remain. Mock Lisbon detail is compared with the source's Columbus composition, not treated as a geographic pixel match. |
| Fine layout | Some frame corners, control glyph alignment, panel-header/list spacing, media/header edges and tiny text advances differ. The radar globe glyph is a few pixels lower than its source-art center. The frame-only target does not certify all controls within2dp. |
| Mechanical detail | Cream/green message layout and clearing behavior exist, but the wire, lamps, hinge and guarded three-phase mechanism are simplified into a600ms slide/fade. Popup tail and complex ornamentation are simplified. |
| Motion | Menu/log/Web Watch/side-feed timing families, radar and tutorial timers are implemented. Source blur/scale refinements, staged Help callouts, event/trailer glow and exact ticker/radar loop phase are not fully matched. Native camera duration is1.5s, including log selection, rather than the source's longer2.5s log flight. No frame-by-frame timing parity or performance trace is claimed. |
| Media/services | Original image and animated demo playback replace protected media/YouTube integration. Reports/events/Web Watch are independent fixtures. No Street View, Google3D, conditional Samsung downloads, backend or GPS/routing was implemented. |

Final captures show usable phone/tablet/landscape layouts and functioning state transitions. These stated differences remain reviewable follow-up work; the project does not claim pixel-identical reproduction or complete source-feature parity.

## Reproduce comparisons

Install the final debug APK first; the connected-test task may uninstall its test applications afterward. Use `scripts/capture-android.ps1 -Serial emulator-5556` with the documented viewport/state arguments (verify the current serial first), then:

```powershell
python scripts/compare-android.py
rea compare-web-screenshots docs/comparisons/rea-phone-reference.artifact.json docs/comparisons/rea-phone-android.artifact.json --channel-threshold 16 --format json
```

Keep font scale1.0, density320, the dated references and the fixture version constant. Preserve each correction's before/after PNGs. Reassess map and creative-media regions separately rather than loosening the chrome mask to hide a defect.
