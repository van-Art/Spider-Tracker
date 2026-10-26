# Keyboard/radar follow-up — final validation

Date:2026-10-25 (Asia/Yerevan). Build0.3.1.2-radar/code6. Phase3.1.2 was completed first; its24 JVM/44 Android test results and APK are retained separately. No Phase3.2 or routing work was started.

| Final gate | Result |
|---|---|
| JVM unit tests |33 passed,0 failures/errors/skips |
| Android instrumentation, API37 emulator |49 passed,0 failures/errors/skips |
| Additional gesture-navigation keyboard cases |2 passed (portrait/landscape) |
| Additional full software Gboard portrait case |1 passed |
| Lint |0 errors,13 warnings,1 hint |
| Debug build |BUILD SUCCESSFUL; signed APK verified with APK Signature Scheme v2 |
| Original PNGs |All14 source files unchanged; all14 packaged RGBA images/dimensions identical |

The final consolidated `assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug` run completed in3m56s. [Log](keyboard-radar-evidence/validation.log), [49-test XML](keyboard-radar-evidence/android-test-results.xml), JVM XML suites, lint report, APK badging/signature and SHA256/source manifest are in [evidence](keyboard-radar-evidence/delivery.json). Subsequent focused tests used the same unchanged production APK. The extra three executions repeat keyboard cases, not three additional unique tests.

## Keyboard and camera measurements

Actual AVD: Medium_Phone_API_37.0,780×1386px at320dpi. Root cutout/system-bar policy, camera and native MapView identity/width/height are asserted before/open/closed. A shared shift is asserted for radar, globe/GPS targets, search and character. Suggestions are strictly to radar's left and above the search component. Camera comparison includes the native CameraPosition object; layer state and typed query survive.

| Mode | Native map px (before/open/closed) | Search top before/open/restored px | Radar top before/open px |
|---|---|---|---|
| Portrait,3-button,Gboard accessory220px |672×1214 |1270 /1050 /1270 |1041 /821 |
| Portrait,gesture,accessory172px |672×1214 |1270 /1098 /1270 |1041 /869 |
| Landscape,3-button,accessory IME0 |1182×608 |664 /664 /664 |402 /402 |
| Landscape,gesture,IME0 plus48px navigation avoidance |1278×608 |664 /616 /664 |402 /354 |
| Portrait,full software Gboard598px |672×1214 |1270 /672 /1270 |1041 /443 |

At full Gboard, radar bounds are491,443→698,650px (207×207px); search begins at672px and suggestions occupy96,472→475,664px. The first result remains selectable and attached controls are displayed. Closing restores the original radar/search bounds. [Full Gboard screenshot](keyboard-radar-screenshots/full-gboard-tested.png) and [receipt](keyboard-radar-evidence/ime-full-gboard-portrait.json). The first accessory captures are not mistaken for full software-keyboard coverage.

A separate180dp-visible landscape case dispatches **synthetic** system IME insets (not a claimed live keyboard). It verifies an unchanged120dp radar, top/bottom bounds for web/search/character/globe/GPS, no horizontal overlap with search/suggestions, a selectable first result, hidden conflicting left layer controls, native map dimensions/camera and actual raised GPS/globe touch callbacks. GPS fixture failure leaves camera untouched; the explicit globe action changes actual native zoom to1. [Synthetic capture](keyboard-radar-screenshots/tall-ime-synthetic.png). Results scroll in short windows. Controls are not shrunk.

## Functional radar coverage

Eight pure geography cases cover cardinal bearings, linear distance/range boundary, omission beyond1/5/20/100km, dateline/poles, GPS origin/user-at-center, camera origin without user dot, layer/demo filtering, coordinate changes, invalid origin/range and stable IDs. Existing state tests cover independent toggles and a new failed-GPS-refresh case clears the old origin without moving the camera.

Five new Android cases cover real bundled London attractions/demo alert coordinates, both layers off, no fake cyan dot, range cycle/accessibility selection/persistence, latest status after range change, test-GPS updates, failure fallback, no camera movement from data/range changes, a physical dot opening the matching attraction details, and constrained IME layout/attached button actions. Existing49-case suite also covers Photon debounce/cancellation/cache/errors/selection, native zoom gestures/clustering, detail close/Back, PNG rendering, foreground/reduced-motion animation and dense1000-map-marker fixture.

## Live smoke and visual checks

The installed production APK sent an actual Photon query for `Paris`, displayed five available results and selected `Paris, Île-de-France, France`. The dropdown/keyboard closed, the native map moved to the destination and search retained the selected label. This was not a hardcoded-success provider. [Search capture](keyboard-radar-screenshots/live-search.png), [selection capture](keyboard-radar-screenshots/live-selected.png), paired XML receipts.

An actual AndroidLocationProvider/LocationManager request in the AVD acquired the emulator-injected51.5074,−0.1278 fix and displayed TEST LOCATION / TEST GPS; radar showed the cyan user dot at its center, green attractions and red DEMO alerts. [Device-provider capture](keyboard-radar-screenshots/device-location.png). This proves the foreground Android API path on an emulator, not satellite accuracy on physical hardware. Manual OS keyboard-menu exploration included a discarded transient capture; ActivityManager recorded task removal/launcher restart, so those exploratory images are not camera-preservation evidence. The separate automated full-Gboard case above passed native identity/camera assertions.

All14 original PNG source SHA256 values match the Phase3.1.2 receipt; decoded packaged pixels match original RGBA bytes/dimensions exactly. TrackerLayout and outer-frame source were compared byte-for-byte with the pre-follow-up snapshot. Radar web drawing, sweep, icon sizes and internal attached-button offsets retain the existing design. PixelFont, nearest-neighbor rendering and reduced-motion/background clock remain in use. No generated artwork or new dependency was added.

## Failures resolved and limits

The initial full regression had one failure in the geocoder's1100ms spacing assertion: the timestamp preceded first-use URL/fetch initialization. Attempt completion now resets the cooldown; subsequent focused and final full runs passed. Compiler errors during development and earlier investigation logs are not counted as acceptance passes. Read-only review also caught stale pointer status, mismatched hit/draw radii and short-height radar clipping; all were corrected and regression-tested.

Lint's13 warnings are existing target/dependency-version/backup-metadata/KTX suggestions plus the new SharedPreferences KTX suggestion; the single hint suggests mutableIntStateOf for the infrequently changed range. No dependency/API upgrades were added to this UI task. These are not zero-warning claims.

Not checked: physical GPS accuracy, other IME vendors/older APIs, complete TalkBack/large-font/device matrix, sustained FPS/battery/thermal behavior or arbitrary split-screen sizes. Windows too small to fit an unscaled radar/control group cannot be guaranteed; constrained headers may be transiently covered by suggestions. GPS remains foreground request-based with the last accepted fix, without continuous/background tracking. Catalog coverage remains incomplete; alerts are DEMO. There is no routing, automatic catalog update schedule or full responsive redesign.

Final APK: `dist/spider-tracker-radar-keyboard-debug.apk` (60,844,019 bytes), SHA256 `37b41bb88ae35e822af82c59804aea300624877e009bf09fab450ad46d4d7c85`. The old Phase3.1.2 APK is preserved. Emulator navigation mode0 and `show_ime_with_hard_keyboard=0` were restored after checks.
