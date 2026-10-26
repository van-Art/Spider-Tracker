# Measured visual specification

All lengths here are **CSS px**, not physical screenshot pixels or Android dp. Styles are source-confirmed unless explicitly marked sampled/derived. Original PNGs preserve 1 pixel per CSS px in REA scenarios (DPR1). `live-metrics-887.json` is the additional computed-style capture at DPR1.5; fractional geometry is retained.

## Shell layout

Main layout: full-height grid, header row0, flexible tracker, footer170, minimum page height600. The captured configuration shows film/sponsor footer branding, no top corporate header, no language control, no Get Tickets CTA. Do not render configuration-disabled elements merely because their HTML exists.

| Property | Width >768 | Width ≤768 |
|---|---:|---:|
| Tracker padding top / horizontal | 5 / 15 | 2 / 2 |
| Frame columns | 36, flexible, 36 | 27, flexible, 27 |
| Frame rows | 36, map, 19, 50, 7 | 27, map, 14, 40, 5 |
| Ticker height / caps | 41 / 9 | 33 / 7 |
| Ticker maximum width | 527 | 338 |
| Ticker text | 22 | 18; 16 at ≤420 |
| Top menu width | 62 | 50 |
| Top badge image width | 280 | 180 |
| Message button | 64×64 | 48×48 |
| Marker width | 32 | 25.6 |
| Trailer tab height / min-width | 28 / 140 | 22 / 130 |

**Derived stable geometry**, with `W` the CSS viewport width, `H` the viewport height, and page height `P=max(H,600)`:

- Desktop map origin `(51,41)`, width `W−102`, height `P−287`.
- Mobile map origin `(29,29)`, width `W−58`, nominal height `P−258`.
- Source map has a minimum height300. In a short landscape viewport, flexible grid minimums and page minimum-height can overflow; do not assume `H−170` always fits the visible screen.
- At1440×1000, map bounds `(51,41,1338,713)`, footer starts y830.
- At390×844, map bounds `(29,29,332,586)`, footer starts y674.
- At768×1024, map bounds `(29,29,710,766)` before any overflow constraints.
- Computed887.333×871.333: map `(51,41,785.333,584.333)`, matching the formulas.

The frame is **fifteen-slice**, not a stretched screenshot or a conventional rounded border. Top corners are36×36; horizontal and vertical repeated edge strips have independent periods. The bottom ticker shelf and beveled lower lip are distinct rows. Mobile uses explicit alternate sizes rather than scaling the entire frame. Recreate the stepped outline, light-blue face, dark-blue bevel, and near-black outer edge with integer-aligned Canvas paths.

The over-frame layer is inset `(5,5,0)` desktop, zero on mobile, with z400. Desktop menu wrapper offset `(20,12)` inside it; mobile `(10,11)`. Center badge top19 desktop with image shifted −3; mobile top16. Message slot top13/right23 desktop; top14/right12 mobile. Filter slot left4/top140 (relative to over-frame), with an additional −4 left adjustment in the mobile filter panel.

The lower-left character overlaps the frame and footer edge. Desktop sprite is approximately59×95 with a64×62 container decoration; mobile character height60 and39×40 base decoration. These footprints are layout evidence, not permission to redraw the protected character.

The ticker itself is centered horizontally; the sound button sits to its right, so **the combined ticker+sound group is not the centered object**. Ticker anchor width is `min(maxTicker, availableWidth−reservedSoundWidth−gap)`. Reserved sound widths62/44 and gaps5/3 are desktop/mobile. Panel padding5 desktop; `(4,2,2)` mobile. Actual icon button intrinsic width differs from the reserved width. At390, ticker is about281 px wide. Ticker text clips and scrolls; it does not wrap.

## Palette and typography

| Role | Value | Evidence |
|---|---|---|
| Main UI cyan | `#96e0f7` | CSS |
| Menu text / alternate cyan | `#a8e1fe` | CSS |
| Menu/sidebar backing | `#0e1c3b` with alpha`f2` | CSS; Events overrides may differ |
| Menu border | `#2bb8ff` | CSS |
| Body/background gradient | `#1547aa` to `#030b1c`, last15% fade | CSS |
| Frame-content fallback | `#0d1055` | computed/CSS |
| Boot/log surface | `#282828` | CSS |
| Pin/detail cream | `#eaeadc` | CSS |
| Pin border | `#1a1a1a` | JS/CSS |
| Popup divider | `#d5d5bf` | CSS |
| Explainer orange / highlight | `#f89d51` / `#ffd988` | CSS |
| Help intro yellow | `#ffc619` | CSS |
| Radar sweep line | `#09a4c7` | JS |
| Radar point colors | green`#00ff50`, red`#ff4040`, white`#ffffff`, event`#96e0f7` | JS; not marker-art fill colors |
| Rendered world water | `#0e172b` | sampled composite screenshot |
| Rendered world land | `#043956` | sampled composite screenshot |
| Rendered secondary area | `#1b294d` | sampled composite screenshot |

Map samples come from a six-pixel grid in desktop map rectangle x80..1380/y80..740; source cloud-style paint values were not recovered. The PNG includes the map tint and overlays, so sampled values must not receive that same tint twice during reconstruction. The original user screenshots have different label language and apparent palette; use the dated REA set as the consistent baseline.

Font: **PF Videotext Pro Regular**, bundled WOFF2/WOFF, weight400, normal; font-face ascent82.9%, descent17.1%, line-gap0. Secondary declared font: **Bitcount Prop Single**, variable weight100..900, variation settings slnt0/CRSV0.5/ELSH0/ELXP0. Declaration does not mean every element uses it. Most visible interface copy is PF Videotext, uppercase, with explicit tracking.

Use the measured cap height, baseline, text advance, and line-height when selecting an independently licensed substitute. A stock Android sans-serif or monospaced system font will not match the pixel glyph shapes. PF font redistribution rights are **not established** by this capture.

## Components

**Menu:**320 px-wide panel, map-height; full map width at≤500. Navy95%-opaque backing; 1 px right border; open shadow10px0 24px black28%. Nav column220 desktop/190 mobile, vertically centered, margin-top10. Link font17 desktop/14 mobile, line-height1.1, tracking0.03em, padding10, dashed separators20%-cyan. Hover orange. Six base visible entries. Conditional download row is not present on normal desktop/phone Chrome.

**Filters:**55×41 desktop, vertical gap4;45×37 at≤768;38×30 at≤550. Green/red backgrounds when enabled, cream/white when disabled. Hover scale1.03. Decorative shadow is separate. Source controls use aria-pressed, not a visible label or caption below the icon. No third event toggle is visible.

**Radar:**desktop right30/bottom35 within map; final mobile rule right16/bottom26, scale0.7, bottom-right transform origin. An earlier scale0.85 rule is overridden. Global button24×24 at right−14/bottom38; center24×24 at right13/bottom−6, relative to widget before scale.

**Activity log:** fills map content, charcoal scanlines. Desktop list width up to600; header at top50, centered600-wide reference with heading150 px to its left and close100 px to its right. At≤1050 the header becomes normal flow, padded40/30/10. Rows: padding9×16, gap12, 20px pin, 9px badge, 12px maximum title, 11px maximum date; 1px half-cyan divider. At≤600 badges disappear. At≤550 heading12, header padding36/20/8, close12 with8px X. Seen rows opacity0.35. Vertical scrollbar7px, cyan thumb, charcoal track, square ends.

**X feed:** viewport-height,350 px wide from the right, not map-clipped. Navy backing,2px cyan left edge, shadow−8px0 16px black55%. Header20px10px. Post padding8px20px; avatar28, author14, handle/time10, message12 with1.35 line-height. Media can have10px radii; the site is not uniformly square. Scroll region has a bottom fading mask. Sticky introduction precedes remote posts; report CTA is at the bottom. Phone behavior is evidenced by `phone-report-loaded.png`; do not assume the menu's≤500 rule also governs the feed.

**Events:** viewport-right feed with a350px desktop envelope and full-height scroll. Current source visual differs from X feed: content is near-black with predominantly white type, cyan edge, rounded image/video media and play overlays. Use `desktop-events-content.png` and `phone-events-content.png`, and the exact scoped selectors in the captured CSS rather than applying X-feed cyan text wholesale. Dates, location and descriptive copy depend on each event. Cards can show image arrows/dots and video poster/play affordances.

**Section explainer:** delayed800ms after section open, one per section per session (and persistent seen state when functional consent allows). Centered, max-width400, outer inset16, stepped black4px border. Header36px orange with20px lighter top decoration; title14, dark body. Actual references `*-log-settled`, `*-events-settled`, `*-webwatch-settled` are captured about1s after section open, so may show the panel mid-arrival; they are animation references, not settled final-position measurements. Content snapshots follow dismissal.

**Web Watch:** map-sized panel, deep-blue map-line background. Desktop header padding30px75px9px, title14, carousel body gap20 and horizontal padding20. Arrows30×78; article gap50. Each article has a portrait1:1.5 image filling available article height,30px gap,150px-wide text column. Text: name16/tracking0.1em; description11/line-height1.6. Bottom dots12×6, gap10, active scale1.3. At≤768 arrows/dots disappear and articles become a vertical scroll list; at≤550 each article becomes image above text, image width min(200,available), name14, description10/line-height1.45. Phone screenshot verifies this.

**Message center:**white idle64/48px button; amber when populated. Desktop base container350×278, top43/right0 of slot; mobile270×248/top30. Cream open content with120×120 desktop /88×88 mobile thumbnail, handle, location and optional message. Green mechanical base223×60 desktop /167.25×45 mobile. The wire, colored lamps, scrolling green display, hinged opening and expansion are distinct animation pieces. Copy and media are populated only after rumored-pin CTA. Closing clears the detail and returns idle; it is not a persistent collapsible inbox.

**Count notification:**independent orange banner centered over map content, width210, top70 source position; ornament150 wide. Drops from−60px; appears1s after map transition, holds2.5s, dismisses on page click. Text and count are data/seen-state dependent. It has no observed list behind it.

**Toast:**cyan two-step border2, black82% fill, centered near map bottom, text uppercase. Enter300ms, hold1000ms, exit350ms. Phone font11, bottom10, padding4×9. Fast repeated filter changes restart this single toast rather than stacking notifications.

**Lightbox:**viewport backdrop `#081228f2`; black media box with2px stepped border and shadow0 8 48px black90%. Header32 high, top−32, blue`#258cb1` with lower accent`#054574`, cyan close control14. Image uses contain; video/gallery modes change box sizing. Phone gutter10 at≤640. Consent rejection can replace playback with a separate consent dialog; that dialog is a website integration state, not video playback proof.

**Footer:**170px row. Brand group centered, top-margin28 desktop/23 at≤740, gap60 reducing to12 at≤480. Main logo50px high (48 at≤480), date23, sponsor56. Legal text10, uppercase, flex wrap. Trailer tab sits immediately below the tracker at left50 desktop/15 mobile. The footer is still present below phone map content; do not create an Android bottom navigation bar in its place.

**Video gallery strip:** navy floating panel centered below the player, maximum nominal width780 or viewport minus gutters. Now Playing metadata uses12px type desktop,10 at≤640,9 at≤420. Nominal thumbnail height100 desktop,62 at≤640,56 at≤420; width is1.6×height. Visible item count is4 desktop,3 at≤800,2 at≤420. The current video label and collapse control remain when the thumbnail row closes. Source initially auto-collapses after1000ms; mouse entry/movement expands, pointer leave debounces close300ms, and outside clicks/iframe focus can collapse. A pointer click can therefore close a row that already expanded on hover. Keyboard Enter on the toggle is used for the explicit expanded reference. Preserve that lifecycle rather than showing permanently visible thumbnails.

## Pixel drawing constraints

Use hard square steps and crisp edge fills. Default Material corner radii, elevation, cards, FABs, chips, top app bars, and ripple appearances are not observed replacements. Some media corners are rounded; preserve only those observed exceptions. Keep Canvas frame paths aligned to device pixels and use nearest-neighbor rendering for intentionally pixelated original replacement sprites. Treat text as Compose text with semantics; do not rasterize all interface text into a single Canvas.
