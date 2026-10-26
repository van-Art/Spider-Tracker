# Phase 3.1.2 — detail panels

`TrackerPlaceDetailPanel` is a reusable custom Compose overlay, not a Material dialog. It uses the existing navy/cyan/pixel-font components, supplied green/red icon and sharp square border. Width is bounded to320dp within the map; height is bounded to340dp and available space above the search shelf. The map remains visible. The48dp close control and attraction Navigate action remain outside the scrollable body.

## Attraction hierarchy

Compact marker/type header → grounded attraction name → type → country → city/region → address → approximate straight-line distance → source → catalog status → optional validated Wikidata source-record action. The source's administrative locality may be a region rather than a city. Missing country/locality/link is omitted; missing address and unavailable distance are explicit. There are no fabricated descriptions, ratings, opening hours or independent-verification claims.

Navigate sets the existing destination and native camera, then explicitly says routing is unavailable. It does not launch turn guidance, calculate a route or add background location. Source links are restricted to valid HTTPS Wikidata entity URLs.

## Alert hierarchy

Compact red icon/type header → alert type → “DEMONSTRATION DATA — NOT A REAL INCIDENT” for demo records → supplied severity if any → description → report time → approximate distance if a fix exists → source → verification status. Absent time/verification is labeled NOT PROVIDED, never the current time or a made-up live source. The bundled scenarios remain demonstrations near London.

## Interaction and states

A physical marker tap or its custom accessibility action uses one shared selection path that dismisses autocomplete/focus/IME before opening details. Another marker updates the selected record; the close control and Android Back close it. Disabling/filtering out the selected layer record clears selection by stable ID. Search focus closes details/category overlays so they cannot cover the editing flow. Opening details preserves map camera/viewport; closing restores normal map interactions outside the actual panel surface.

Long body text scrolls; title/category/source/status use distinct existing font sizes and spacing. Distance is calculated only from the last acquired DeviceFix and is labeled TEST LOCATION for an injected/mock fix. The UI never treats a searched destination as acquired GPS data.

Tests cover physical marker taps, country/name metadata, swapping visible Paris landmarks, Back/close/layer-off, missing metadata/destination-only Navigate, demonstration source/verification and accessibility selection while autocomplete is open. Final results/screenshots are in the test report.
