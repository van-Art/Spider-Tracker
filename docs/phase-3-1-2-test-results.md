# Phase 3.1.2 — test results

Completed before the separately requested keyboard/radar follow-up. The preserved APK is `dist/spider-tracker-phase3-1-2-debug.apk` (version0.3.1.2/code5). SHA256 and size: [delivery receipt](phase-3-1-2-evidence/delivery.json).

| Gate | Result |
|---|---|
| JVM tests |24 passed,0 failures/errors |
| Android instrumentation, API37 AVD |44 passed,0 failures/errors/skips |
| Lint |0 errors,12 warnings |
| Debug APK |Assembled, v2 signature verified |
| Additional gesture-navigation IME checks |Portrait + landscape passed separately |

The consolidated `assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug` run completed successfully in3m13s. [Full log](phase-3-1-2-evidence/validation.log), [Android XML](phase-3-1-2-evidence/android-test-results.xml), four JVM XML suites and [lint report](phase-3-1-2-evidence/lint-results.txt) are preserved. Investigation failures remain as diagnostic evidence and are not counted as passing runs.

Coverage includes autocomplete debounce/minimum length/deduplication/stale cancellation/cache/throttle/errors/result selection, editable query/copy/paste/hardware keyboard, fixed native viewport/camera with IME, independent layers/categories/persistence, world/country clustering, supplied art, marker selection/details/Back, real-location provider integration with labeled fixtures, native zoom/pinch/double/two-finger tap, circular screen-space marker rings under map tilt, reduced motion and background clock pause. Catalog exporter offline self-test passed. Dense1000-marker fixture tested bounded rendering; no sustained battery/FPS benchmark is claimed.

Measured portrait map viewport672×1214px remained unchanged with the keyboard; search lifted over actual IME insets and restored. Gesture navigation also passed; landscape Gboard used physical-keyboard accessory mode rather than an occluding software body. The old phase intentionally retained radar bounds during IME; the newly requested follow-up changes that behavior. The circular pulse measured104×104px at45° bearing/pitch with0.5px center deviation. See the individual evidence JSON and captured detail/ring screenshots.

Live public Photon availability, physical GPS accuracy, every IME/API/device/orientation and complete accessibility certification were not established by these deterministic tests. Forty grounded catalog records across29 country labels are incomplete. Alerts remain explicitly DEMO. Automated update scheduling and routing are not implemented. No Phase3.2 work was started.
