# Phase 3.1.2 — UI polish

The existing Kotlin / Compose / MapLibre project and supplied pixel-art resources remain the design source. There is no new theme, generated artwork, Material dialog, footer, menu, dependency or routing system. Version0.3.1.2 / code5.

## Baseline and skills

The complete pre-change app source and build/configuration files were saved in `.tools/phase312-baseline/source.zip` with a SHA256 manifest before implementation. This workspace has no Git metadata, so this is the requested equivalent baseline snapshot.

Installed definitions were read before implementation: `professional-ui-ux-architect`, `bug-hunt-swarm`, `cavecrew`, `brainstorming`. The first supplied component/state/accessibility review, the second a four-role read-only diagnosis before edits, and cavecrew supplied bounded two-file changes and findings-only review through the available Codex subagent API. The installed brainstorming skill applies to Laravel and was not applied to Android. No additional skill/plugin, paid service, Claude Design Bridge or other design provider was installed or used.

## Component hierarchy and affected layout

Atoms: supplied PNGs, original pixel font, editable field,48dp touch targets, cyan rule and light text. Molecules: unified search field/arrow, suggestion row, layer toggle, metadata field. Organisms: autocomplete dropdown, TrackerPlaceDetailPanel, replacement notification. The map screen retains its original frame, centered header/top logos, sprite and radar/attached controls.

Normal map: header → full underlying map with left layer controls and geographic sprites → unchanged lower-right radar → centered unified search shelf with character at the left. Search: the map remains in place; only the shelf, dropdown and character lift over the IME. Details: a bounded centered overlay above the shelf, with the map visible around it. No navigation tabs or sidebar were added to the approved single-map workflow.

Search width is `min(600dp, width−96dp)` with a160dp small-window floor constrained to available width. This reduces the previous screen-wide component and centers its parent. The54×48dp arrow overlaps the field's right cap by6dp; both artwork regions remain40dp high. Existing7px source end caps resize uniformly, with only the straight center segment changing width. Character clearance remains separate from the input. In shorter keyboard windows, the dropdown prioritizes selectable rows over its optional header/footer; this is a small content-fit rule, not a full responsive redesign.

## Tokens and contrast

Existing colors/font are reused. Surface: solid#0E1C3B; background#0E172B; primary/text/border#96E0F7; body#EAEADC; input text/cursor#00D7FA; selected row#18415A. Green#69AF7F and red artwork retain their existing semantics; original PNG colors are unchanged. Button text#1E1E14 appears on cream/green. Base spacing8/16/24dp is retained with the original12dp field/panel inset. Borders are1dp, square and pixel aligned; PNGs use FilterQuality.None.

| Text / component background | WCAG sRGB contrast |
|---|---:|
| Cyan / navy surface |11.46:1|
| Cream / navy surface |13.86:1|
| Cyan / selected row |7.36:1|
| Cream / selected row |8.91:1|
| Search cyan / brightest input body shade#3B3B3B |6.45:1|
| Dark action text / cream |13.83:1|
| Dark action text / green |6.43:1|

These are measured opaque component pairs, not certification of every basemap, asset pixel or OS surface. The original input shading is preserved. Formula: [W3C G18](https://www.w3.org/WAI/WCAG22/Techniques/general/G18). Touch areas retain at least48dp per [Android accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Full TalkBack/large-font certification is not claimed.

## Interaction and states

| Component | Empty / loading | Ideal / success | Error / recovery |
|---|---|---|---|
| Search | Placeholder; below3 characters no fetch; SEARCHING during debounce/network | Up to5 grounded results; choose → close dropdown/IME → native camera + destination | No matches or provider/network error keeps query; edit/retry/clear |
| Attractions | Incomplete-catalog/no-area-data explanation in filters | World/regional clusters; street individual records; detail panel | Corrupt/old private cache falls back to bundled catalog; invalid updates are rejected atomically |
| Alerts | Empty filtered layer has no marker badge | Visible demo markers carry DEMO; details identify demonstration/source/status | No actual live feed is implied |
| Notifications | No startup event; loading not applicable | Toggle creates one1800ms replacement banner | Next event cancels the previous expiry |
| Details | Missing metadata is omitted or explicitly NOT PROVIDED | Clear hierarchy, scrollable body, fixed close/action; Back closes | Disabling layer clears stale selection; Navigate sets destination and explains routing unavailable |

Text entry retains cursor/selection/copy/paste; clear is a named48dp action. Hardware arrow keys select suggestions; Enter chooses. Rows expose selected state, toggles expose independent checked state, and marker accessibility selection uses the same dismiss-search/IME path as a physical marker tap. Controlled rings respect reduced motion/background lifecycle; source PNGs remain sharp.

## Files

See the delivery receipt for hashes. Modified production: AddressSearch.kt, TrackerModel.kt, AddressSearchUi.kt, TrackerScreen.kt, MapLayerUi.kt, MapLayers.kt, MapRenderer.kt, MainActivity.kt, AndroidManifest.xml, app/build.gradle.kts. Added: GlobalAttractions.kt, TrackerPlaceDetailPanel.kt, assets/global-attractions.json and scripts/update-attractions.py. Tests: Phase312StateTest, GlobalAttractionsTest, Phase312MapTest plus updated MapLayersTest, GeocoderTest, TrackerInteractionTest and MapLayerInteractionTest. README, the seven Phase3.1.2 documents, evidence/screenshots and the separate APK complete the delivery.

No physical-device matrix, full responsive redesign, background navigation or routing work is part of this phase.
