# Phase 3.1 pixel rendering

No new raster artwork or replacement icons were generated. Fourteen supplied PNGs remain in drawable-nodpi; the eight new files are inventoried with SHA256/dimensions in phase-3-1-evidence/asset-inventory.json. Existing six header/radar/sprite resources are also preserved. No PNG is edited, recolored or rewritten.

| Added integration | Source pixels | Rendering |
|---|---:|---|
| search_bar.png | 527×40 | 7px caps protected; only middle changes width |
| search_button.png | 54×41 | Original graphic, aspect fit, 48dp touch area |
| confirmed_sightings_button.png | 57×43 | Places ON |
| rumored_sightings_button.png | 57×43 | Alerts ON, visible DEMO caption |
| off_button.png | 57×43 | Independent OFF visual for either switch |
| main_point_location.png | 34×34 | Last actual device-provider fix, cyan glow behind |
| confirmed_sightings_location.png | 34×34 | OSM place / place cluster |
| rumored_sightings_location.png | 34×34 | Alert / alert cluster, demo truth preserved |

## Drawing rules

SuppliedImage uses a direct Canvas bitmap draw with FilterQuality.None and integer destination bounds. PixelBounds maintains aspect ratio. If an integer scale uses at least90% of the available fit it is preferred; otherwise nearest-neighbor fitting keeps the existing component size without interpolated bitmap colors. At320dpi, 34dp markers are exactly2× their34px source. Source transparency and opaque pixel colors are retained.

Search background end caps scale by height, so their corner geometry does not stretch horizontally when field width changes. The middle's straight rows expand to fill the available width. The original field colors and edges come from the PNG, not a generated Canvas imitation. Its original gray/navy appearance is intentionally preserved even though the Phase3 interim Canvas frame used brighter cyan.

Marker sprites bypass native symbol-texture filtering: MapLibre owns the clustered coordinates and glow circles, while one overlay Canvas draws only visible PNGs at rounded geographic projections. There is no per-marker bitmap allocation, crop, filtered resize or Android View. The character still uses source rectangles of a single decoded76-frame atlas. Existing header/logos/radar controls use the same bitmap helper.

Custom frame, button and ruler paths/lines use non-antialiased native Paint with physical-pixel stroke rounding. Pixel HUD circles use the same crisp Paint. The existing pixel font is retained; text is laid out directly by Android/Compose without scaling a raster text image. Native font rasterization and supplied assets' existing shading/partially transparent edge pixels are not rewritten. MapLibre roads, geographic geometry and map labels retain normal antialiasing. Only the separate pulse CircleLayers apply blur.

## Evidence and limits

Android screenshot tests compare opaque pixels of the rendered search caps and the green marker against the original PNGs at the emulator's integer scale. Transparent pixels composite over the underlying surface, so they are not incorrectly compared with opaque surface colors. Package verification separately compares full RGBA source/packaged images. Source dimensions/hashes, local emulator captures and test outcomes are retained in this phase's evidence.

Pixel-perfect filtering at the tested density does not certify every device, font rasterizer, GPU, font scale or orientation. Fractional nearest-neighbor scaling can repeat columns unevenly, though it does not blur them. Full device-size/orientation validation is explicitly deferred to Phase3.2; no additional reference screenshots are required for this phase.
