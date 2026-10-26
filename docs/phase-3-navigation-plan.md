# Phase 3 — Navigation plan (not implemented)

Phase 2 ends at a working native visual reconstruction. This plan introduces no code, permissions, location requests, routing, network backend or new visible controls.

## Existing navigation contract

Keep the map as the home surface and preserve the six observed menu destinations. Continue using overlay state instead of adding a bottom navigation bar or new full-screen route hierarchy. Back dismisses media, explanation, message/card, credits and active section before exiting the map. Keep the Activity Log → selected map pin → category action path and the idle-message behavior.

Before introducing platform navigation, preserve these paths with the existing instrumentation tests. A navigation library is unnecessary for the current single-activity overlay structure. Add one only if a later product requirement introduces genuinely separate screens or deep links.

## Step 1 — Foreground location through the existing target action

Replace `TrackerViewModel.neighborhood()`'s fixed New York camera request with a cancellable foreground one-shot location result. Keep the existing target control and toast region; do not add an always-visible location puck. Keep the current zoom when applying a fix, matching the Phase 1 target behavior.

Use Android `LocationManager` before adding another location SDK. Implement a small platform adapter returning a fix with coordinates, accuracy, timestamp and provider, plus explicit unavailable/timeout outcomes. Do not expose Android location types to the MapLibre renderer. The renderer continues receiving only a camera request.

Request coarse/fine together when precise access is needed on Android 12+, accept approximate access, and request only when the user activates the target action. Do not request background location. Handle denied, permanently denied, provider disabled, stale fix, timeout and cancellation. Every failure keeps the existing map usable and leaves the current camera intact. Never send a location to the reference website.

Cancel outstanding requests when the activity stops or a newer center request replaces them. Give each request an ID so a late callback cannot overwrite a newer manual/global camera action. Remove any listener on completion, cancellation or timeout. Do not silently substitute the demo location after a location failure.

Acceptance: tests for approximate/precise/denied/disabled/stale/timeout/cancel outcomes; permission-state instrumentation; real-device first-fix and lifecycle checks; no background updates after stop. Keep the Phase 2 screenshot baselines for all unchanged chrome.

## Step 2 — Navigation state and continuity

Persist only the last usable map camera, active filters and selected stable fixture/content ID where restoration is meaningful. Revalidate the ID against current data before restoring a popup. Restore a usable map if content disappears. A process restart must not resume an old location request or route calculation.

Test rapid pin selection, interrupted camera flights, rotation, background/foreground and process death. Ensure old callbacks cannot reveal a stale card or camera target. Preserve the existing Activity Log as the accessible alternative to spatial markers. Recheck large text, TalkBack order, Back and external attribution links on hardware.

## Step 3 — Routing requires a separate scope decision

No route UI was observed in the reference. A route line, destination chooser, mode selector, directions sheet or turn guidance would be a product extension and needs an explicit interaction/design requirement before implementation. Do not hide invented routing UI inside the reconstruction.

Once authorized, start with a route preview to an existing selected destination. Choose walking/driving modes and an OSM-based routing provider according to that requirement, licensing, coverage and service limits. Keep route calculation separate from `TrackerMapRenderer`; pass an independently testable geometry/result to a dedicated map layer. Validate coordinates, route emptiness, provider error and cancellation at the service boundary. Do not claim straight lines or mock geometry are navigable routes.

A real route preview must show attribution, freshness and failure in an approved layout. Active turn-by-turn navigation, rerouting, background location, notification/service behavior, battery use and spoken guidance are separate work requiring real-device testing and further authorization. Do not add them implicitly.

## Step 4 — Content/backend remains separate

Keep Phase 2 fixtures until an independent service contract is supplied. Do not call the reference site's private integrations or reuse its publishing identity. Backend reporting, moderation, remote events, accounts, analytics and push are outside this navigation plan. When independently authorized, define the minimum read schema first and retain loading/error/empty fixtures for deterministic visual tests.

## Completion gates for the next phase

1. Foreground centering works with both approximate and precise grants; denial never blocks global map use.
2. No location updates survive the foreground request lifecycle; stale callbacks cannot move the camera.
3. Existing Phase 2 state and UI tests pass, and changed permission/location states have emulator and hardware evidence.
4. Re-capture the matched 390×844, 768×1024 and short-landscape baselines after any visible change.
5. Routing and backend remain absent unless their separate scope has been approved and implemented with real test evidence.
