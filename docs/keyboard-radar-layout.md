# Keyboard-aware search and radar follow-up

This implements the additional user request after Phase3.1.2. Existing Phase3.1.2 reports/APK/evidence are preserved. Current build version:0.3.1.2-radar/code6. No routing or Phase3.2 redesign.

## Root cause and layers

The previous implementation already held MapLibre at full height with `adjustNothing`, edge-to-edge and root cutout/horizontal-system-bar insets. It moved only search/character; Radar still used the full-height layout, so the keyboard covered it. The old test explicitly required stationary radar bounds. That expectation is superseded by this request.

The normal `TrackerLayout` still controls native map/frame/header. Neither its parent nor MapLibre consumes `imePadding`. `BottomSearchOverlay` and `RadarOverlay` use a separate overlay layout whose height subtracts `max(WindowInsets.ime.bottom, navigationBars.bottom)` less already consumed cutout padding. Both consume the same actual insets, with no hardcoded keyboard height. The existing MapView instance, size, camera target/zoom/bearing/pitch and layers are independent of this calculation.

Normal: full map → lower-right web with attached supplied globe/GPS → bottom search/arrow and left character. IME: full map remains beneath keyboard → web/globe/GPS rise together → search/arrow/character rise together. Suggestions appear above search and to the left of radar, with bounded height and a scrollable result body. Their right edge leaves8dp clearance from the radar.

Movement follows the platform's animated IME insets at integer-pixel Compose layout positions. No additional delayed tween trails the keyboard and no bitmap transforms/filtering are added. Dismissal returns the overlay geometry to the normal position. Source PNG bytes, font, frame, radar web drawing and control artwork remain unchanged.

## Short visible windows

The normal radar is92–120dp according to the pre-existing width rule. It is never shrunk for the keyboard. If the normal lifted position would put the web/status above the visible area, its entire group is shifted down just enough for a12dp web top. Search then uses the space to its left, retaining the integrated arrow and character, rather than crossing beneath radar controls. The two left layer buttons are temporarily hidden only in this constrained mode to avoid intersecting the raised character; their data/state is retained and they return when space is restored. In narrow compact fields the visible clear glyph gives way to text space; the named Clear input accessibility action and OS selection/editing remain available. Optional suggestion header/footer disappear in short result windows, with48dp scrollable rows retained.

This fallback is tested with180dp of visible landscape height using explicitly synthetic platform IME insets, alongside actual Gboard accessory-mode and598px full-software-keyboard tests. An arbitrarily small window cannot contain an unscaled120dp web and48dp input: no claim is made that every possible split-screen/IME combination fits. The existing header may be partly covered by transient search suggestions in this constrained mode; a full responsive redesign remains outside scope.

## Components and states

Pixel atoms remain existing cyan/navy rules, pixel font, sharp PNGs and48dp attached control targets. The input/arrow/character form the bottom-search organism; the unscaled web, status and attached controls form the radar organism. No new theme, generated assets, dependency or permanent large settings panel.

Search states remain editable empty/loading/results/no matches/network error. The map/radar stay available in each; successful selection hides suggestions/keyboard before the existing destination camera animation. Radar's empty state is an empty web with explicit mode/range; it does not invent data. Long press exposes unavailable/test/demo status. Main Attractions and Live Alerts retain their own independent state.

Measured color contrast and rendering rules from Phase3.1.2 remain applicable; no new color palette was introduced. Normal geographic line/text antialiasing remains permitted. Pixel images use nearest neighbor, and the existing foreground clock/reduced-motion preference controls the sweep without recalculating geography each frame.
