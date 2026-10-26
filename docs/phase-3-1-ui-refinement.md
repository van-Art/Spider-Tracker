# Phase 3.1 UI refinement

This phase extends the existing Kotlin / Compose / MapLibre application. The Phase1 dossier, Phase3 implementation and local PNG files are the references. No new user screenshot, generated artwork, footer or navigation menu was required. Phase3.2 is not started.

## Changes

- The bottom search frame now draws the supplied `search_bar.png` (527×40). Seven source pixels at each end form protected caps, scaled with the height; only the straight middle section changes width. The field remains a single-line BasicTextField with the existing pixel font, submitted Photon search, IME Search, suggestions, destination selection and honest empty/network/unconfigured states.
- The arrow now uses `search_button.png` (54×41) with aspect-fit nearest-neighbor drawing and its own48dp hit target. The animated character remains beside the field.
- The two compact left controls use the supplied green/red57×43 buttons, and independently use `off_button.png` when disabled. They represent Confirmed Places and Live Alerts. Tap toggles; long press opens the respective category panel. The OSM/DEMO captions and accessibility labels make their sources clear. This is the explicitly requested pair of layer controls, not the old sightings menu/filter workflow.
- Compact details reuse the navy/cyan/pixel-font components. OSM details show only available names/addresses and calculate distance only after a device fix. Missing fields say NOT PROVIDED. Navigate sets the existing destination and explicitly reports that routing is unavailable. Alert details show type, description, time when provided, source and verification; demo data is labeled in the panel and beside the red button.
- Legacy mock sightings are retained internally for existing Activity Log/media behavior but are no longer drawn as if they were places or traffic. The main map displays the new layers. Base-map gestures, GPS, address destination and attribution are independent of both switches.

## Geometry and adaptation

The Phase3 TrackerLayout, map rectangle, radar diameter, radar anchor and attached globe/target coordinates remain unchanged. The frame remains dynamic and uses safe-drawing/IME insets. Buttons are48dp wide and52dp high, with compact artwork; their vertical position adjusts above the search shelf in short windows. They hide below160dp map height, while existing radar behavior still hides below150dp. This preserves editing space in extremely short IME windows. Full orientation/device certification belongs to Phase3.2.

The supplied images are not modified. All custom bitmap drawing uses FilterQuality.None, aspect-ratio fitting and rounded physical-pixel destinations. Integer scaling is selected where it loses at most10% of the available fit; fractional nearest-neighbor fitting otherwise preserves the existing layout. Header/logo/sprite/radar assets stay in drawable-nodpi. Sharp custom Canvas paths/lines use native non-antialiased Paint; geographic map roads/text retain normal MapLibre rendering.

## Files

Modified: `app/build.gradle.kts`, `MainActivity.kt`, `TrackerModel.kt`, `TrackerScreen.kt`, `AddressSearchUi.kt`, `SuppliedArt.kt`, `MapRenderer.kt`, README. Added production files: `MapLayers.kt`, `MapLayerUi.kt`. Added tests: `MapLayersTest.kt`, `MapLayerInteractionTest.kt`, `PixelAssetsTest.kt`. Added the five Phase3.1 docs, evidence/screenshots and a separate Phase3.1 APK. The eight newly supplied PNGs were integrated without rewriting their bytes; dimensions/hashes are in `phase-3-1-evidence/asset-inventory.json`.

Source/configuration before this phase is preserved in `.tools/phase31-baseline/`. Existing Phase2/Phase3 APKs and evidence are historical and remain separate from this delivery.

## Local visual evidence

Actual final emulator states are under `docs/phase-3-1-screenshots/`: startup, editable keyboard/search/results, category filters, real OSM place detail and explicit demo alert detail. Real place/alert panels are captured after physical map-marker taps in instrumentation, using the actual tile feed and bundled demonstration source respectively. No outside reference image is needed.

The suite also physically taps the filter close button above the native map. The first manual capture read before dismissal had settled; this did not require a hierarchy redesign. Category panels and marker details retain the existing pixel-art component style. Basic density checks are recorded separately from any future full Phase3.2 device/orientation certification.

The delivered source list also includes `scripts/verify-phase31.py`, which checks final test counts, package metadata, original/packaged PNG pixels and historical APK preservation.
