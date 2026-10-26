# Phase 3 visual validation

The approved image is the layout reference; the supplied PNG files are used unchanged as explicitly required. No pixel-perfect claim is made.

## Evidence and method

- Approved original: `phase-3-evidence/reference/approved.png` (941×1672, SHA-256 cc6c0bfc776e305f23f4d7c6325ac379f87bf0f443f05c946cead0df667b4d5d).
- Final Android reference capture: `phase-3-screenshots/reference-map.png` (780×1386 at 320dpi / 390×693dp).
- Side-by-side: `phase-3-evidence/comparison.png`; unmasked RGB difference amplified 3×: `phase-3-evidence/difference-3x.png`.
- Reproduce with `scripts/capture-phase3.ps1` and `scripts/compare-phase3.py`.
- Reference rectangles were manually read from source pixels, approximately ±2px. Android rectangles use native UI bounds and the matched capture geometry. Native bounds are preserved in `phase-3-evidence/reference-layout.xml`.
- The reference is uniformly normalized to the Android screenshot dimensions (aspect-ratio difference below 0.1%). The 390dp reference width is a comparison convention, not a claim about the original screenshot density.

## Measured final layout

| Element | Reference normalized dp (L,T,R,B) | Android dp | Edge delta dp |
|---|---|---|---|
| map | 26.11, 27.77, 363.06, 630.41 | 27.0, 28.0, 363.0, 635.0 | 0.89, 0.23, -0.06, 4.59 |
| header | 100.3, 16.16, 291.36, 46.01 | 99.5, 16.0, 290.5, 48.0 | -0.8, -0.16, -0.86, 1.99 |
| radar | 244.11, 523.48, 346.07, 626.27 | 245.5, 520.5, 349.0, 624.0 | 1.39, -2.98, 2.93, -2.27 |
| search visible outline | 45.59, 636.22, 335.71, 674.76 | 46.0, 639.0, 334.0, 679.0 | 0.41, 2.78, -1.71, 4.24 |
| global visible icon | 336.12, 557.88, 363.89, 585.24 | 335.0, 558.5, 363.0, 586.5 | -1.12, 0.62, -0.89, 1.26 |
| location visible icon | 314.16, 603.06, 342.75, 632.07 | 317.0, 600.0, 345.0, 628.0 | 2.84, -3.06, 2.25, -4.07 |
| character | 12.85, 632.9, 42.27, 683.05 | 10.0, 632.0, 42.0, 683.0 | -2.85, -0.9, -0.27, -0.05 |

The map edges differ by at most 4.59dp, the header rectangle by about 2dp and the radar/globe/target edges by about 4.1dp. The final search shelf is about 2.8–4.2dp lower than the reference; its right edge differs by about 1.7dp. The character rectangle is within about 3dp after the final adjustment. Artwork silhouettes have their own transparent margins, so visible pixels do not equal image-box bounds.

## Color and pixel rendering

| Sample | Reference RGB | Android RGB |
|---|---|---|
| Cyan frame | [1, 190, 253] | [0, 190, 252] |
| Navy water | [11, 23, 38] | [14, 23, 43] |
| Input interior | [16, 38, 50] | [17, 38, 50] |

The frame is #00BEFC and input #112632; measured reference samples are #01BEFD and #102632. PNGs use no filtering, retain their aspect ratios and transparency, and sprite frames share the original bitmap. Canvas strokes and font rasterization are native Android rendering and are not expected to match a raster mockup pixel for pixel.

## Iteration

1. The first pass retained an unnecessary 48dp blank region because mandatory gesture insets reserved hidden three-button navigation. It is preserved as `phone-first-pass.png`; safe-drawing/IME insets corrected the viewport.
2. The search outline was changed from Phase 2 pale cyan to the measured bright cyan, keeping the navy field and green arrow.
3. Measurement found the search shelf 9–10dp too low. It moved up 6dp; the sprite moved right/up and became 32×50.96dp. Radar touch targets adapt above the shelf while visible icons stay anchored to its boundary.

## Responsive and functional captures

| Capture | Logical size / state | Result |
|---|---|---|
| reference-map.png | 390×693dp | Main approved aspect; full map frame |
| phone-map.png | 390×844dp | Taller viewport; bottom anchors preserved |
| compact-map.png | 320×640dp | Header fits, input and both radar controls visible |
| tablet-map.png | 768×1024dp | Proportional map, centered header, radar capped at120dp |
| landscape-map.png | 844×390dp | Full width, visible input/character, no scrolling/footer |
| keyboard-input.png | Actual IME visible | Map shrinks; field, arrow and character remain above keyboard |
| live-search.png / live-destination.png | Actual Photon London query | Real returned choices and centered destination |
| location-permission.png / location-denied.png | OS dialog / denial | Foreground request and explicit denial feedback |
| location-device-fix.png | Android provider with emulator GPS | TEST LOCATION label and actual platform camera action |
| location-disabled.png | Device services off | Explicit app error; no mock fallback |
| location-approximate.png | Coarse granted, fine denied | No permission loop; emulator has no coarse network provider |

Main layouts, keyboard, live search and destination were recaptured on the final APK. Manual permission/GPS captures were made before the last shelf/character refinement; the location adapter is unchanged and final instrumentation rechecks permission dispatch and radar actions.

## Remaining differences

- Supplied header/corners/sprite differ in artwork from the approved mockup, including the required extra left head. These are intentional asset instructions, not generated substitutes.
- Independent Phase 2 marker glyphs, fixture positions and world geometry differ from the screenshot. The existing MapLibre navy style is preserved. Reference coastlines/textures, country-label placement, grid cadence and font rasterization are not identical.
- The reference radar appears octagonal; the existing 12-spoke/12-sided radar is retained and enlarged as requested. Real fixture positions determine its dots; the mockup dots are not copied.
- Attribution remains in the map. It is absent from the mockup and remains necessary for the live OSM stack. Search result UI is a required functional extension with no approved results-state screenshot.
- Search and radar touch regions reserve 48dp even though artwork is smaller. On very short IME windows (map height below150dp) radar controls temporarily hide to preserve editing space.
- Captures use the emulator’s density and default font scale. No physical cutout device, TalkBack, large-font or minimum-API visual certification was performed.

Whole-image mean absolute RGB error is 19.439 /255. It includes all pixels, map differences, supplied artwork, text and textures, without masks. This diagnostic must not be presented as a visual-fidelity percentage. Geometry and the explicit differences above are the useful acceptance evidence.
