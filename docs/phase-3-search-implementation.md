# Phase 3 address search and foreground location

## Search contract

`GeocodingProvider` returns validated AddressResult values (id, label, latitude, longitude). `PhotonGeocoder` calls the free, self-hostable Photon API. The default endpoint is https://photon.komoot.io. Override it at build time with `-PgeocoderUrl=https://your-photon-host`; an empty property (`-PgeocoderUrl=`) produces an explicit no-provider state. Only HTTPS hosts without embedded credentials, queries or fragments are accepted. Android cleartext traffic remains disabled.

Typing is local only. Tap the green arrow or the keyboard Search action to request suggestions for an address, city or place. The keyboard then closes and up to five selectable real matches appear above the field. This intentionally uses submitted search, not per-keystroke autocomplete. Choosing a current result sets a cyan destination circle and requests a zoom-13 camera flight. No hardcoded successful search exists in production. Test doubles are confined to test code.

The ViewModel cancels previous queries and ignores stale generations. Editing invalidates old suggestions. Result selection accepts only an item in the current returned list. Dismissal and Back close the results panel. Global reset preserves the selected destination while returning the default camera (15.9738997,−49.9007427, zoom1, bearing0, tilt0).

## Provider policy, validation and failures

The [Photon project](https://github.com/komoot/photon) permits reasonable project use of its public demo, may throttle extensive use and offers no availability guarantee. The app sends explicit requests only, with a descriptive User-Agent, a process-wide mutex, at least 1100ms between network request starts and a bounded persistent cache of 20 endpoint-scoped normalized queries for seven days. Cached empty results also avoid repeat traffic. There are no retries, background query loops or batch downloads. SharedPreferences cache is private app storage; queries/returned labels can contain addresses. Cloud backup is disabled with allowBackup=false and clearing application storage clears this cache. Vendor device-transfer behavior is not certified; the existing Android12 data-extraction lint warning is documented. Requests go to the configured Photon server, not the reference website.

This local throttle is not a server-wide quota across all installations. A broad distribution should configure a dedicated/self-hosted Photon instance and assess its capacity. The [Photon API contract](https://github.com/komoot/photon/blob/master/docs/api-v1.md) documents `/api/?q=...&limit=5&lang=en` and GeoJSON [longitude,latitude] coordinates. OSM attribution is shown in map/results. Photon uses OSM data; no paid token is required.

The public [Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/) prohibits client-side autocomplete and imposes aggregate application limits and additional generated-app conditions. This app does not use the public Nominatim endpoint.

Queries must contain 2–160 characters. Network timeout is 15s, response size at most 262144 characters. Parsing examines at most 100 features, accepts Point geometries and finite coordinates in geographic bounds, normalizes control/whitespace in labels, deduplicates IDs and returns at most five items. Invalid feature rows are skipped; malformed top-level data becomes unavailable. HTTP429 gives a busy state; HTTP errors, offline conditions and missing configuration never become a fabricated destination. HTTPS timeouts, canceled calls and dialogs leave the existing map usable. Shared cached data is subject to the same parser validation.

## Location

`DeviceLocationProvider` returns a plain DeviceFix. `AndroidLocationProvider` uses platform LocationManager; no paid/fused SDK or background service is required. The radar target requests coarse/fine together only when tapped and accepts approximate access. See [Android permission guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).

Location must be enabled. Enabled network and (with precise grant) GPS providers are considered. A last-known fix is accepted only when at most 120s old with finite, valid coordinates and nonnegative accuracy. Otherwise a cancellable update request waits for a valid fix, bounded by 20s in the ViewModel. Completion, timeout, stop, cancellation, disabled providers or errors remove listeners. User panning/global reset/new pin/new destination cancels old requests so late fixes cannot overwrite newer actions. A second target tap cancels a pending request.

Successful centering keeps zoom at least13 and preserves a closer current zoom. It reports measured accuracy. Emulator or marked mock fixes explicitly say TEST LOCATION; no demo neighborhood is substituted after failure. Denial and permanent denial report the permission/settings state; disabled services and timeout are explicit. The app does not request background permission or transmit device coordinates to a routing/backend service.

## Future routing boundary

The independent destination model, location provider and renderer camera contract are sufficient inputs for a separately authorized route-preview feature. No speculative navigation framework, route provider, guidance UI, location puck or turn-by-turn service was added. Current sightings/events/media remain Phase 2 fixtures.
