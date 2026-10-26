# Phase 3.1.2 — zoom investigation

## Diagnosis before implementation

Four read-only bug-hunt roles investigated reproduction/scope, code-path failure seams, regression contracts and proof/observability. The baseline and prior reports were read before edits.

1. High confidence: world-zoom place disappearance was a data contract, not proof of broken zoom. The renderer returned empty records below12, queried only loaded POI tiles and the VM replaced its whole places list. A separate global catalog now retains records independently of zoom.
2. High confidence: shared root IME padding and adjustResize shrank native dimensions and visible geography. IME now lifts only the search overlays; native dimensions/camera are measured directly.
3. No evidence that ordinary Canvas overlays consumed gestures or that camera commands restarted on every recomposition. The Native MapView is remembered, overlays have no full-map pointer interceptor, and camera animation is keyed to requested camera revisions. Those ownership rules are retained.
4. Minimum zoom1 is intentional; zoom-out at the global minimum cannot decrease further. Zoom tests begin at5 rather than interpreting that constraint as failure.

## Native interaction path

Actual MotionEvents traverse the Compose AndroidView into MapLibre. Zoom, double-tap, quick-zoom and scroll settings are explicit. Rotation/tilt gesture policy is unchanged. Programmatic zoomIn/zoomOut tests use MapLibre Native camera APIs; globe/GPS retain their original actions. There is one native camera, no competing custom zoom implementation, no added +/− widget and no lowered SDK gesture threshold.

Tests assert actual native zoom and geographic center, not marker presence. They cover programmatic6/5, pinch spread/pinch inward, double tap, two-finger tap, global reset and absence of a delayed reset in portrait and landscape. Projection/clustering/radar use the same native observed camera.

## Gesture harness findings

Early injected pinch moved too slowly for the installed SDK's scale-begin speed threshold. Cached MapLibre13.6.1 bytecode/resource data uses0.6dp minimum speed; synchronous injection overhead made the initial20ms/12.5px increments fall below it. A faster, realistic multi-touch displacement reached the native scale path without a production threshold change.

The two-finger tap detector has a150ms timeout and a6mm minimum span (99.2126px on this emulator). A70px test tap was too narrow. Even a wider completely stationary MOVE was coalesced before reaching MapView, leaving the detector's previous70px distance cache intact. The final harness supplies180px separation and a2×1px movement below touch slop, refreshing real detector input. Native touch traces distinguish delivered events from attempted injection and record duration/thresholds. This fixes the test's invalid event sequence, not a fabricated camera response. Earlier failed traces remain as investigation evidence.

Final measured zoom results and pass/fail status are in phase-3-1-2-test-results.md and the zoom receipts. No universal hardware gesture guarantee is claimed. The full Phase3.2 responsive/orientation program was not started.
