# Phase 3.1 map layers and data honesty

## State and persistence

MapLayerState stores independent places/alerts flags and separate PlaceCategory / AlertCategory sets. MapFiltersViewModel owns visible lists, selections and filter panels. The existing private SharedPreferences mechanism persists all flags/category sets in `map-layers`; empty sets stay empty. Unknown stored category names are ignored. Disabled layers supply no markers to the renderer, clear an invalid selected detail, and contribute no radar dots. GPS, base-map tiles, search destination and radar controls remain independent.

ConfirmedPlacesRepository converts plain OsmPlaceRecord values to validated LayerMarker values. LiveAlertsRepository exposes a report list and an honest status. The existing TrackerViewModel owns address destination and the latest successfully acquired DeviceFix; no Android location object crosses into the map abstraction. The MapScene/renderer seam carries marker coordinates, fix and callbacks. No paid service or new dependency was added.

## Confirmed Places: real OSM tile POIs

The existing map already uses OpenFreeMap vector tiles in OpenMapTiles format. The adapter queries the loaded `osm` source's `poi` layer and restricts data to the visible geographic bounds. This avoids adding a second places service, bulk geocoding or automatically querying the public Overpass backend. At world zoom, the UI asks the user to zoom in. A street-level viewport is needed; tile zoom/detail/provider availability determine coverage.

The [OpenMapTiles POI schema](https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi.yaml) defines name/class/subclass. Classification uses specific OSM subclasses: cafe/restaurant/fast_food/food_court, hospital, pharmacy, parking, fuel, supermarket, attraction/museum/viewpoint/theme_park/zoo. Generalized hospital/fuel/grocery classes are not used to mislabel clinics, charging stations or department stores. Names prefer available English/Latin/name values, with legacy name_en fallback. Addresses are shown only if the tiles actually supply address tags (often absent). Unknown data is not synthesized.

Coordinates are finite and bounded, names/control characters are normalized, duplicate IDs collapse, and at most1000 supported places are retained from at most20000 decoded tile records. This is a bounded viewport subset, not an exhaustive place directory. There is no claim of independent confirmation, verified opening hours, accessibility, business status or current availability. Details explicitly state OSM DATA · NOT INDEPENDENTLY VERIFIED. The UI name Confirmed Places is the requested control name, not a verification assertion.

Map attribution remains visible. Queries use already loaded vector data and the existing map HTTP cache; no new endpoint, token, scraper or request loop is introduced. Map tile/network failures leave the base-map fallback and search UI usable. Data availability is expressed through the zoom/loaded-tile status. The [MapLibre VectorSource API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-vector-source/query-source-features.html) is the native query boundary.

## Live Alerts: explicit demo source

No trustworthy live traffic feed is configured. DemoLiveAlertsRepository provides six fixed London scenarios, one per requested category (accident, closure, roadworks, obstacle, restriction, user-reported hazard). Every report has demo=true, source Bundled demonstration, verification DEMO · UNVERIFIED and a description stating that it is not an actual incident. There is no fabricated report time; the panel says NOT PROVIDED. The red control visibly says DEMO and long press says LIVE ALERTS · DEMO. Disabling the layer hides only those reports. There are no random activity timers or fabricated GPS locations.

A future real/self-hosted alert feed can replace the repository after its data/license/verification contract is defined. No unsupported free traffic feed is silently substituted, and no user-report publishing/backend was added.

## Rendering, clustering and interaction

Separate GeoJSON sources use MapLibre native clustering (radius42, maximum cluster zoom15) for green/red data. A cluster draws the supplied marker PNG plus its count and tapping zooms in two levels until points separate. Geography/hit testing uses the same projected snapshot as the visible PNGs, with24dp tolerance per axis. User location is unclustered. The selected address destination retains its existing distinct cyan circle.

PNG sprites draw in one Compose Canvas over the map at rounded pixel coordinates. Native GeoJSON sources/clusters and CircleLayers provide geography and glows; they do not filter the sprite artwork. Only viewport-intersecting sprites are emitted, capped at1000 with priority for the GPS marker. World-wrap copies deduplicate by kind/id. A marker never creates a separate Android View. Camera movement reprojects cached coordinates between rendered-frame refreshes.

The radar keeps its previous geometry and buttons. Its dots come from the same enabled marker list and acquired device fix, with haversine distance/bearing relative to the current map camera. Distances outside the zoom-dependent range are omitted. Green means OSM places, red means the explicitly labeled demo alerts, cyan means the actual last device-provider fix. Category toggles recalculate data; radar sweep ticks do not repeat geographic calculations.

## Location and navigation boundary

The Phase3 foreground LocationManager adapter is retained. A successful current-location action updates the user PNG at the acquired coordinates and animates the camera. New fixes replace old coordinates; no mock neighborhood is used on failure. Emulator/mock-provider results remain labeled TEST LOCATION. The marker represents the last acquired foreground fix, not continuous background tracking. Permission denial clears it. Physical accuracy and continuous turn guidance are not certified in this phase.

Navigate on an OSM detail sets the existing destination only. It does not draw a fabricated route, start turn-by-turn navigation or launch a background service.
