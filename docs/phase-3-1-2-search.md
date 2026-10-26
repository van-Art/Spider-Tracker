# Phase 3.1.2 — search and keyboard

The existing Photon provider and BasicTextField are retained. The PNG field/arrow now share one centered parent,40dp artwork height and an overlapping right seam. The arrow is54×48dp; clear is48×48dp. Original seven-pixel source end caps and nearest-neighbor bitmap drawing remain unchanged. Placeholder, input and cursor use the same existing14sp pixel style and explicit line metrics.

## Autocomplete

`TrackerViewModel.editSearch(value, provider)` is the event boundary. It saves the raw query, normalizes the request key, requires three characters and schedules425ms debounce. New edits cancel the old coroutine/request and increment a revision; even a noncooperative stale response cannot replace current results. Same normalized query/provider shares pending or completed results. Destination selection changes the saved label directly, so it cannot accidentally search that label again. Dismissal/clear cancels pending work; retry after an error remains possible. Explicit arrow/IME Search shares this state machine.

Photon uses one cancellation-aware global mutex and at least1100ms between network starts. A32-entry access-ordered memory cache complements the existing20-entry private persisted cache with seven-day expiry.429 responses create a30–3600s per-provider cooldown honoring numeric Retry-After. No exact public requests-per-second entitlement is claimed: the [Photon operator](https://github.com/komoot/photon#demo-server) permits reasonable usage, may throttle/ban extensive use and gives no availability guarantee. The1100ms gate is this app's conservative choice. A larger deployment should point the existing `-PgeocoderUrl=https://your-host` at a dedicated/self-hosted Photon. An empty configured URL produces an explicit unavailable state.

Responses are bounded/validated Point geometries with finite geographic coordinates and available name/locality/country. The compact navy/cyan dropdown shows up to five results, separated rows and a selected state. Missing fields are not fabricated. The body scrolls when space is short; below160dp of dropdown space, optional header/footer give way to selectable rows. Loading, empty results and errors preserve the editable query. Hardware Up/Down/Enter, touch selection, OS copy/paste and clear remain available. Optional GPS-biased ranking was not added; the device fix is not sent with searches.

## Keyboard overlay

Root cause: the previous `safeDrawing.union(ime)` padding measured the entire map using keyboard-free height; safeDrawing itself includes IME. The previous manifest also used adjustResize and a regression test explicitly required shrinking.

The root now uses display cutouts and visibility-independent horizontal system-bar insets. It does not consume IME or dynamic vertical navigation-bar height. The edge-to-edge Activity uses adjustNothing. Only search/dropdown/character are lifted by the greater of IME and bottom navigation-bar insets, subtracting already reserved cutout space. The underlying MapView is remembered, retains its full normal measurement, and receives no camera command from IME events. Radar geometry/geographic state stay unchanged under the keyboard. Back closes suggestions, clears focus and explicitly hides the keyboard; selection does the same before the existing native camera animation.

This is a tested overlay strategy on the local API37 emulator, not a blind manifest-only change. Native view identity, width/height, actual camera and radar bounds are asserted before/open/closed, with the input/dropdown above the IME. Separate navigation-mode evidence is recorded with final tests. Older API/other vendor IME behavior still requires hardware validation; no complete IME matrix is claimed.

Implementation: AddressSearch.kt, TrackerModel.kt, AddressSearchUi.kt, TrackerScreen.kt, MainActivity.kt and AndroidManifest.xml. Evidence and final counts: phase-3-1-2-test-results.md.
