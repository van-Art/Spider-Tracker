# Evidence, reproduction, limits, and fidelity protocol

## What this deliverable establishes

This is a completed, bounded analysis of the publicly accessible UI captured on 22 October 2026. The core source, visual, interaction, responsive and map behavior findings are documented in 01–04. Canonical scenario completion and artifact hashes are checked by `build-inventory.cjs`. Counts and exact evidence IDs are machine-readable in `evidence/inventory.json` and `evidence/reference-manifest.json`.

The current implementation gate is **analysis complete / Android implementation not started**. No APK, emulator/device screenshot, GPS test or Android-vs-website fidelity score exists yet. The next phase can implement from the documents; it must not label this evidence-validation pass as Android verification.

## Capture environment

- Windows, installed `rea-agents`6.5.0, Chrome156.0.8078.48.
- REA-owned temporary headless Chrome profiles for canonical scenarios; no personal browser profile/cookies copied.
- Viewports1440×1000 desktop,390×844 phone,768×1024 tablet,844×390 landscape; deviceScaleFactor1, localeen-US, timezoneAsia/Yerevan, light color scheme, **reduced_motion:no-preference**. REA defaults to reduced motion, so this explicit override is essential for observing animations.
- Cookie rejection for normal scenarios. Conditional downloads uses `?push_test` and is labeled separately. Videos scenarios use fresh consent-accepted profiles to distinguish the gallery from the rejected-consent path.
- A separate passive CDP profile provided source/DOM/bundle evidence. A browser measurement at approximately887×871CSSpx/DPR1.5 supplied computed styles; it is not mixed into DPR1 screenshot comparison.
- Original supplied desktop screenshots are2559×1396 physical pixels, with unknown exact browser CSS viewport/DPR. Do not assume those physical dimensions are the CSS measurements.

Canonical scenarios are request/result pairs:

| Scenario | Coverage |
|---|---|
| `desktop` | Cookie/sound/tutorial/map, both filters, menu, log/explainer, report feed, events/explainer, Web Watch/explainer/next, Help, trailer marker popup, credits |
| `phone` | Same principal panels with observed phone reading/menu layout; excludes hidden desktop Web Watch arrows |
| `tablet` | Startup, map, menu and Help at768px breakpoint |
| `landscape` | Startup/map at844×390; visible vertical overflow constraints |
| `detail-desktop`, `detail-phone` | Rumored marker/card, message detail, image lightbox, clear-on-close, global camera, trailer consent-blocked state |
| `downloads-conditional` | Source-declared QA switch exposes device-gated downloads; does not subscribe/download |
| `videos-desktop`, `videos-phone` | Consent-accepted Videos gallery presentation; playback success is not inferred from screenshots |

The full manifest is the source of truth for successful retained captures. Intermediate screenshots are intentional: after-each-action capture includes both transition and settled steps. A frame tagged `*-settled` is a bounded wait, not proof that every external resource or long animation finished. In particular, section explainers start800ms after open and their1s panel transition extends beyond the first1s wait.

## How to reproduce

From the workspace root in PowerShell:

```powershell
rea --version
node analysis/make-scenarios.cjs
rea capture-browser-scenario analysis/rea/desktop.request.json --format json > analysis/rea/desktop.json
rea capture-browser-scenario analysis/rea/phone.request.json --format json > analysis/rea/phone.json
# Repeat for the other request names above.
node analysis/extract-evidence.cjs
node analysis/build-inventory.cjs
```

Run all canonical request names before extraction/inventory on a fresh folder. The scripts write analysis artifacts only; they are not Android application code. REA's launch mode owns and cleans its temporary browser. It does not require permanent MCP registration or a Ghidra/Hopper installation for this workflow. Source capture uses the supplied `fetch-source.ps1` against known same-origin script/CSS links; retain `index.html` from the same site revision.

Passive commands used with an isolated Chrome loopback debugging endpoint:

```powershell
rea list-browser-targets http://127.0.0.1:9225 --format json
rea inspect-web-page ENDPOINT TARGET --include-accessibility-text --include-script-sources --include-console-text --format json
rea analyze-web-bundle ENDPOINT TARGET --format json
rea capture-web-screenshot ENDPOINT TARGET --format json
```

`ENDPOINT`/`TARGET` are placeholders; the saved target ID is capture-local. Passive capture defaults to the selected origin. It cannot establish first-load network causality after attaching. REA's documentation: [browser scenario contract](https://github.com/morluto/rea/blob/main/docs/browser-scenario-contract.md), [CDP observation](https://github.com/morluto/rea/blob/main/docs/browser-observation.md).

The download helper checks same-origin URLs, records SHA-256 and refuses an implausibly small resource capture. The extraction helper checks SHA-256 of each decoded PNG/DOM/AX value. Inventory checks every canonical action completed, PNG signature/size/hash, and51 public JS/CSS file hashes. These are evidence integrity checks, not implementation-mirroring unit tests.

## REA failures and limits retained honestly

The initial attached-browser scenario returned a generic execution failure; passive capture was unaffected. Switching to provider-owned launch mode succeeded. `rea/doctor.json` reports missing native-analysis engines and missing agent registrations; those are not requirements for the successful browser/static-JavaScript commands used here. No global REA setup/configuration changes were made.

Earlier declarative runs tried three controls that exist in DOM but are not actionable in their current state:

1. Help's hidden close control: corrected to its actual tap-anywhere dismissal.
2. Idle message alert: corrected to populate it through the rumored-pin CTA first. Closing later clears it, so a second tap cannot reopen stale content.
3. Desktop Web Watch Next at phone width: corrected to the phone vertical layout.

Failed outputs have explicit `*-attempt.json` names, plus the initial `desktop-start.json` attachment failure. They are not silently treated as completed interaction evidence. Final canonical scenario results contain no failed/cancelled action; inventory asserts this. **Whole-scenario Evidence still reports incomplete network events / equality ineligible**, because some ordinary background requests remained unfinished when capture ended. This does not make the retained PNG or completed click missing, but it prevents claims of whole-session equivalence. REA's broader completeness flags and limitations remain in the raw envelopes and summary rather than being replaced by a blanket claim of complete network or third-party coverage.

Static bundle results cover admitted sources; the first-party passive capture policy excludes some other-origin metadata/scripts/frames and observes network only after attachment. Scenario requests capture ordinary browser network events, but **not response bodies/header values**. No claims of private backend recovery, original server source, source-map recovery, WebMCP behavior or exhaustive execution coverage are made.

## What remains unverified

| Area | Known from evidence | Remaining limit |
|---|---|---|
| Map/markers/filtering | Live and source confirmed | Google cloud-style definition and tile schema not recovered |
| Basemap replacement | MapLibre/OSM contract specified | Provider/schema and camera zoom calibration require implementation |
| GPS | Existing target action and source IP behavior known | No precise user GPS requested, no Android device tested |
| Video | Consent-blocked path and consent-accepted gallery captured | Complete playback/audio/codec/device support not certified |
| 3D/custom panoramas | Dormant source branches/controls documented | No activated model/custom-pano fixture; MapLibre does not provide Google3D/StreetView |
| Google panorama destinations | Twelve in runtime base-pin data | Their complete interactive views not captured in this pass |
| Conditional downloads | Panel captured via shipped QA switch | Real Samsung eligibility/device behavior, file download, chooser branches not all exercised |
| Events/X feed | Populated current responses and renderer states | Every remote attachment/error/empty combination not triggered |
| Sound | Source categories and volume control | Audio quality, output devices and exact sound matching not measured |
| Persistent seen state | Consent gates and cookie code inspected | Repeated visits under every consent/device combination not tested |
| Touch behavior | Narrow-viewport layout captured | REA sets viewport/DPR, not a full mobile UA/touch/hardware emulator; actual pinch, fling and edge gestures require device tests |
| Responsive | Four fixed viewports + computed887px measurement | No exhaustive width sweep/browser-engine parity; landscape overflow needs Android-specific decision |
| Assets/font | Original-replacement inventory specified | No redistribution rights established, no replacement art/font selected yet |

These limits are bounded follow-up items, not permission to invent missing UI. Device-gated and dormant source elements must remain classified separately from the normal site.

## Screenshot comparison and iterative improvement protocol

### Baselines

Use390×844 as the primary Android phone reference. Also compare a768px tablet content viewport and a short landscape viewport;1440×1000 is useful for desktop-style geometry/reference only. Match logical viewport, locale, font scale, camera geographic extent, filters, selected pin, panel, scroll position, data fixture and seen-state before comparing. Preserve the dated reference rather than recapturing a moving live site on every iteration.

For each implemented state save: source reference path/hash; Android PNG path/hash; emulator/device/model/API; physical pixels/density/content crop; font scale; fixture version; camera; state and animation time. A screenshot without this metadata is visual evidence, not a reproducible baseline.

### Regions and metrics

Measure stable **chrome**, text/layout, basemap and creative-media regions separately. Original replacement art and OSM cartography are expected differences, so a whole-screen similarity percentage is misleading. Declare exact mask rectangles/polygons, reason and area in the comparison record. Never mask misplaced controls, clipped text, an incorrect panel, attribution, or other actual defects.

Proposed acceptance targets, **not achieved results**:

- Frame/map rectangle edges within1 logical px; control anchors and sizes within2 logical px at baseline density.
- No essential text clipped, date/category overlap, incorrect menu order, missing state, or inaccessible control.
- Stable unmasked chrome: compare channel difference with threshold16/255, target≤2% changed pixels after density normalization. Replacement glyphs/wordmark require their own layout envelope check rather than forcing pixel identity.
- Basemap: compare coastline/landmark positions, visible geographic extent, label hierarchy and sampled palette. Provider-specific labels/coastlines are not expected to be pixel identical.
- Animation: capture specified start/mid/end milestones; verify source timing windows and interruption behavior. Do not score continuous radar/ticker/sprite phase as random static failures.
- Interaction parity: filters change marker/log state, actual phone Web Watch is vertical, closed message detail returns idle, modal/back dismissal is correct, and location failure preserves usable map.

REA accepts self-verifying PNG artifacts for numeric pixel comparisons:

```powershell
rea compare-web-screenshots before.artifact.json after.artifact.json --channel-threshold 16 --format json
```

The input contains `sha256`, `bytes`, `media_type:image/png`, `data_base64`. Wrap an Android PNG using its exact bytes/hash only after it exists; normalize or crop first and record that transformation. REA comparison supports the documented non-interlaced8-bitRGB/RGBA formats and does not perform OCR/perceptual correction.

This analysis validates that path with `comparison-self-check.json` (same reference → zero changed pixels) and `filter-comparison.json` (world map vs confirmed-off). The latter reports42,359 changed pixels/1,440,000 (about2.94%) at threshold16, including animation/ticker/notification timing differences. **It is not an Android fidelity score and not an isolated measurement of confirmed markers.**

### Iteration order

1. Match content viewport,15-slice frame and footer geometry.
2. Match anchors, icon envelopes, ticker and text metrics.
3. Match basemap palette and geographic span.
4. Match markers, popup and shared filter behavior.
5. Match all captured panels and responsive variants.
6. Add original media/art and calibrated animation.
7. Test GPS and device/permission/error/lifecycle behavior; repeat affected visual baselines.

After every material correction, keep a before/after Android PNG pair, numeric/geometry comparison and short explanation of improvement. Do not repeatedly broaden unrelated tests after the relevant gates pass. Record any intentional deviation such as brand replacement or an unavoidable OSM/Google content difference.

Implementation can begin from this package in a subsequent phase. It must retain these references and report actual Android verification honestly.
