# Phase 3 supplied assets

All six images came from the user's existing `app/src/main/res/drawable-nodpi/` directory. No replacement images were generated. PNG bytes and transparency are preserved. SHA-256 values, dimensions and every sprite source rectangle are recorded in `phase-3-evidence/asset-inventory.json`.

| Resource | Native pixels | Placement |
|---|---:|---|
| left_up_logo.png | 62×64 | Top-left, decorative, aspect-fit 40dp |
| right_up_logo.png | 64×64 | Top-right, aspect-fit 40dp |
| tracker_logo.png | 280×47 | Centered full graphic, width proportional to available space |
| zoom_to_global.png | 24×24 | Radar right boundary, visible 28dp / hit 48dp |
| center.png | 24×24 | Radar lower-right boundary, visible 28dp / hit 48dp |
| spider_sprite_sheet.png | 2048×43 | Bottom-left, 32×50.96dp |

`center.png.png` was the filename found on disk. It was renamed to `center.png` so Android generates the intended resource identifier. No image editing or recoloring was performed.

## Sprite extraction

The sheet is not a regular 32px or 64px tile atlas. Alpha-column inspection finds 76 visible runs, each 24 or 25px wide and separated by 2–3 transparent columns. A common source rectangle of 27×43px gives every silhouette one transparent padding column on either side where available. Each x origin is runStart−1, clamped to sheet bounds. `SuppliedArt.kt` discovers these runs from actual bitmap alpha at decode time. Frame count and source bounds are asserted in instrumentation tests.

The full sheet decodes once with inScaled=false. Animation draws a source rectangle of the same ImageBitmap, rather than allocating a cropped bitmap each tick. Source rectangles and pixel buffers are created once. FilterQuality.None is used for both sprite and other PNGs. Aspect ratios are maintained. Fractional dp scaling can repeat some pixel columns by different integer widths, but introduces no filtered intermediate bitmap colors.

`phase-3-evidence/sprite-inspection.png` shows all 76 extracted frames at 3× nearest-neighbor scale. The early 32px-grid diagnostic was replaced after transparent-gap measurements disproved it.

## Relationship to the approved image

The screenshot contains a cyan lettered header, cream/green corner badge and orange/blue robot. The supplied files contain the complete white/red header, an orange left head, a white right spider icon, pale blue radar icons and a red character. The written requirement explicitly says to use these PNGs unchanged, so those asset differences remain intentional. The screenshot has no left head, while the written requirement explicitly requires it. Existing independent Phase 2 marker glyphs also differ from the screenshot's spider glyphs and are preserved as requested. These are not claims of pixel-perfect matching.

Asset provenance is user-supplied; no new license or ownership claim is made. Existing independently drawn fixture artwork, map-source credits and the licensed Pixelify Sans font remain in place.
