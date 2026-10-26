# Phase 3.1.2 — marker effects and sharp graphics

The supplied34×34 user/green/red PNGs remain unchanged in drawable-nodpi. Native MapLibre projects and clusters stable geographic records; one existing Compose Canvas draws their sprites with FilterQuality.None, preserved aspect ratios and rounded physical-pixel destinations. There is no Android View per marker or per-frame bitmap decode. The original header/logos, search/button art, radar controls and76-frame character atlas retain their rendering rules.

## Circular rings

A native stroke prototype with circle-pitch-alignment/scale viewport still measured108×76px at45° tilt on the installed renderer. It was replaced with screen-space Canvas rings beneath all sprite artwork. This avoids map pitch/bearing distortion while using the native geographic projection for the center. Search destinations also get a cyan ring without being mislabeled as a GPS fix or replacing the existing destination symbol.

User/destination rings are cyan#00D7FA /1.8s; attractions green#45D26A /2.4s; alerts red#FF4D50 /1.6s. Radius expands20→38dp, stroke2dp, opacity0.7→0 and repeats. The actual PNG does not move or blur. Controlled stroke anti-aliasing/transparency is confined to the light effect. All rings draw first, followed by the unchanged sprite PNGs, so another marker's ring cannot paint over sprite artwork.

Clusters use a restrained static native21-pixel highlight, not individual animated rings. Demo alert clusters/individuals carry an integrated navy-backed DEMO label; details also show the full demonstration warning/source/status. No permanent DEMO caption remains below the left red control.

## Work and lifecycle

The existing foreground clock is shared with radar/character and read in the Canvas draw phase. The map root does not recompose for each pulse. Only on-screen nonclustered points request pulse time; samples are computed once per visible kind, source images and sorted drawing order are remembered, and bounds are shared for the same-sized sprites. A frame does not allocate bitmaps or create marker Views. The source projection refresh remains coalesced and guarded by native renderer readiness/lifecycle.

Animation runs only while RESUMED. System animator scale0 produces a static25dp/0.45-opacity ring and stops the clock work. Disabled layers remove their records/sprites/rings and radar dots. Off-screen kinds do not request pulse frames. Position updates follow native camera events, source updates and completed rendering frames.

## Measured proof

The actual screen-space ring at45° bearing/tilt measured104×104 physical pixels at320dpi, with0.5px center error on each axis. The test compares real Android captures with/without the layer, outside the sprite bounds. It also checks the static cluster highlight, requested periods, frame changes, layer stopping, reduced motion and background pause/resume. See ring.json, ring-tilt.png and the final test report. This is measured local evidence, not a universal pixel-perfect or sustained-FPS certification.
