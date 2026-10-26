package com.spidertracker.app

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MapLayerInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun resetFilters() {compose.runOnIdle {
        val f=compose.activity.mapFilters
        if(!f.state.placesEnabled)f.toggle(MarkerKind.Place)
        if(!f.state.alertsEnabled)f.toggle(MarkerKind.Alert)
        PlaceCategory.entries.filter {it !in f.state.placeCategories}.forEach(f::toggleCategory)
        AlertCategory.entries.filter {it !in f.state.alertCategories}.forEach(f::toggleCategory)
    }}
    private fun nativeMap():MapLibreMap {
        val result=AtomicReference<MapLibreMap>()
        fun find(view:View):MapView? {
            if(view is MapView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let {return it}
            return null
        }
        compose.runOnIdle {find(compose.activity.window.decorView)!!.getMapAsync {map->map.getStyle {result.set(map)}}}
        compose.waitUntil(20000){result.get()!=null}
        // Style readiness precedes the existing 1500ms startup camera flight.
        android.os.SystemClock.sleep(1800)
        return result.get()
    }
    private fun points()=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey]
    private fun tapMarker(marker:ProjectedMarker) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(MapView::class.java))
            .perform(androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,{view->
                val p=IntArray(2);view.getLocationOnScreen(p);floatArrayOf(p[0]+marker.x,p[1]+marker.y)
            },androidx.test.espresso.action.Press.FINGER))
    }
    private fun exportScreenshot(name:String) {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val file=java.io.File(compose.activity.getExternalFilesDir(null),name)
        java.io.FileOutputStream(file).use {automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }

    @Test fun layerNoticesExpireAndDemoStatusBelongsToMarkers() {
        nativeMap()
        compose.onNodeWithTag("map-feedback").assertDoesNotExist()
        compose.onNodeWithTag("layer-places").performClick()
        compose.onNodeWithText("MAIN ATTRACTIONS: OFF").assertIsDisplayed()
        compose.onNodeWithTag("layer-places").performClick()
        compose.onNodeWithText("MAIN ATTRACTIONS: ON").assertIsDisplayed()
        compose.waitUntil(4000){compose.activity.mapFilters.layerNotice==null}
        compose.onNodeWithText("ZOOM IN FOR OSM PLACES").assertDoesNotExist()
        compose.onNodeWithTag("demo-alert-status",useUnmergedTree=true).assertDoesNotExist()
        compose.waitUntil(8000){points().any {it.kind==MarkerKind.Alert}}
        assertTrue(points().filter {it.kind==MarkerKind.Alert}.all {it.demo})
        compose.onNodeWithTag("layer-alerts").performClick()
        compose.onNodeWithText("LIVE ALERTS: OFF").assertIsDisplayed()
        compose.waitUntil(5000){points().none {it.kind==MarkerKind.Alert}}
        compose.onNodeWithTag("layer-alerts").performClick()
        compose.onNodeWithText("LIVE ALERTS: ON").assertIsDisplayed()
        compose.onNodeWithText("© OpenStreetMap").assertIsDisplayed()
    }

    @Test fun nativeAlertClustersProduceGeographicallyAnchoredSprites() {
        val map=nativeMap()
        compose.runOnIdle {if(!compose.activity.mapFilters.state.alertsEnabled)compose.activity.mapFilters.toggle(MarkerKind.Alert)}
        try {compose.waitUntil(10000) {points().any {it.kind==MarkerKind.Alert}}}
        catch(error:Throwable) {
            var data=""
            val sprites=points()
            compose.runOnIdle {
                data="source="+map.style!!.getSourceAs<GeoJsonSource>("alerts")!!.querySourceFeatures(null).joinToString {it.toJson()}+
                    "; rendered="+map.queryRenderedFeatures(android.graphics.RectF(0f,0f,1000f,2000f),"alerts-glow").size+"; scene="+compose.activity.mapFilters.visibleMarkers.size+"; sprites="+sprites
            }
            throw AssertionError(data,error)
        }
        val point=points().first {it.kind==MarkerKind.Alert}
        compose.runOnIdle {
            assertEquals(6,compose.activity.mapFilters.visibleMarkers.count {it.kind==MarkerKind.Alert})
            val screen=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(point.lat,point.lon))
            assertEquals(screen.x,point.x,1f);assertEquals(screen.y,point.y,1f)
            assertEquals(6,point.count)
        }
    }

    @Test fun bothLayerTogglesKeepSearchRadarAndGpsMarkerAvailable() {
        nativeMap()
        compose.runOnIdle {
            val f=compose.activity.mapFilters
            if(!f.state.placesEnabled)f.toggle(MarkerKind.Place)
            if(!f.state.alertsEnabled)f.toggle(MarkerKind.Alert)
        }
        compose.onNodeWithTag("layer-places").performClick().assertIsOff()
        compose.onNodeWithTag("layer-alerts").performClick().assertIsOff()
        compose.runOnIdle {assertTrue(compose.activity.mapFilters.visibleMarkers.isEmpty())}
        compose.waitUntil(5000){points().none {it.kind!=MarkerKind.User}}
        compose.onNodeWithTag("address-input").assertIsDisplayed()
        compose.onNodeWithTag("radar").assertIsDisplayed()
        compose.onNodeWithTag("map-global").performClick()
        compose.runOnIdle {assertEquals(1.0,compose.activity.tracker.camera.zoom,0.0)}
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.runOnIdle {compose.activity.locationProvider=object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(40.7,-74.0,10f,true)}}
        compose.onNodeWithTag("map-center").performClick()
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.User}}
        compose.runOnIdle {assertEquals(40.7,compose.activity.tracker.userFix!!.lat,0.0)}
    }

    @Test fun longPressFiltersPersistAndOffStateUsesIndependentCategories() {
        nativeMap()
        compose.onNodeWithTag("layer-places").performTouchInput {longClick()}
        compose.onNodeWithTag("layer-filters").assertIsDisplayed()
        compose.onNodeWithTag("place-category-Historic").performClick()
        compose.runOnIdle {
            val restored=MapFiltersViewModel(compose.activity.getSharedPreferences("map-layers",0))
            assertEquals(compose.activity.mapFilters.state,restored.state)
        }
        compose.onNodeWithTag("filters-close").performClick()
        compose.onNodeWithTag("layer-alerts").performTouchInput {longClick()}
        compose.onNodeWithTag("alert-category-Accident").assertIsDisplayed().performClick()
        compose.onNodeWithTag("filters-close").performClick()
    }

    @Test fun catalogAttractionsWorkAtWorldCountryAndStreetZoom() {
        val map=nativeMap()
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Place}}
        val catalog=compose.activity.mapFilters.visibleMarkers.filter {it.kind==MarkerKind.Place}
        assertTrue(catalog.size>=94)
        assertTrue(catalog.all {!it.demo && it.source=="Wikidata · CC0" && it.country!=null && it.infoUrl!=null})
        val initialLargest=points().filter {it.kind==MarkerKind.Place}.maxOf {it.count}
        assertTrue(initialLargest>1)
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.858296,2.294479),5.0))}
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Place} && points().filter {it.kind==MarkerKind.Place}.maxOf {it.count}<initialLargest}
        assertTrue(compose.activity.mapFilters.visibleMarkers.count {it.kind==MarkerKind.Place}>=94)
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.858296,2.294479),17.0))}
        compose.waitUntil(10000){points().any {it.id=="wikidata:Q243" && it.count==1}}
        val marker=points().first {it.id=="wikidata:Q243"}
        compose.runOnIdle {
            val p=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(marker.lat,marker.lon))
            assertEquals(p.x,marker.x,1f);assertEquals(p.y,marker.y,1f)
        }
        tapMarker(marker)
        try {compose.waitUntil(5000){compose.activity.mapFilters.selected?.id==marker.id}}
        catch(error:Throwable) {
            exportScreenshot("phase4-attraction-tap-failure.png")
            println("Attraction tap: wanted=${marker.id}, selected=${compose.activity.mapFilters.selected?.id}, coordinatePanel=${compose.onAllNodesWithTag("map-destination").fetchSemanticsNodes().isNotEmpty()}")
            throw error
        }
        compose.onNodeWithTag("layer-marker-detail").assertIsDisplayed()
        compose.onNodeWithText("Eiffel Tower").assertIsDisplayed()
        compose.onNodeWithText("France").assertIsDisplayed()
        exportScreenshot("phase312-attraction-detail.png")
        compose.onNodeWithTag("marker-detail-close").performClick()
        compose.onNodeWithTag("layer-marker-detail").assertDoesNotExist()
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(35.3606,138.7274),10.0))}
        compose.waitUntil(10000){points().any {it.id=="wikidata:Q39231"}}
        assertTrue(compose.activity.mapFilters.visibleMarkers.count {it.kind==MarkerKind.Place}>=94)
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(0.0,-140.0),12.0))}
        compose.waitUntil(10000){points().none {it.kind==MarkerKind.Place}}
        compose.onNodeWithTag("layer-places").performTouchInput {longClick()}
        compose.onNodeWithText("NO CATALOG ATTRACTIONS IN THIS VIEW · COVERAGE INCOMPLETE").assertIsDisplayed()
    }

    @Test fun tappingAnAlertOpensHonestCompactDetails() {
        val map=nativeMap()
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(51.507,-.128),15.0))}
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Alert && it.count==1}}
        val marker=points().filter {it.kind==MarkerKind.Alert && it.count==1}.minBy {(it.x-336)*(it.x-336)+(it.y-607)*(it.y-607)}
        tapMarker(marker)
        compose.waitUntil(5000){compose.activity.mapFilters.selected?.id==marker.id}
        compose.onNodeWithTag("layer-marker-detail").assertIsDisplayed()
        compose.onNodeWithText("DEMO ALERT").assertIsDisplayed()
        compose.onNodeWithText("DEMONSTRATION DATA — NOT A REAL INCIDENT").assertIsDisplayed()
        compose.onNodeWithText("DEMO · UNVERIFIED").performScrollTo().assertIsDisplayed()
        exportScreenshot("phase312-demo-alert-detail.png")
        compose.onNodeWithTag("marker-detail-close").performClick()
    }

    @Test fun detailPanelUpdatesOnAnotherMarkerAndClosesOnBackOrLayerOff() {
        val map=nativeMap()
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.845,2.315),12.0))}
        compose.waitUntil(10000){points().any {it.id=="wikidata:Q243" && it.count==1} && points().any {it.id=="wikidata:Q19675" && it.count==1}}
        tapMarker(points().first {it.id=="wikidata:Q243"})
        compose.waitUntil(5000){compose.activity.mapFilters.selected?.id=="wikidata:Q243"}
        compose.onNodeWithText("Eiffel Tower").assertIsDisplayed()
        // Selection now centers the map; return to the shared viewport before tapping another place.
        compose.runOnIdle {map.cancelTransitions();map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.845,2.315),12.0))}
        compose.waitUntil(5000){points().any {it.id=="wikidata:Q19675" && it.x<650}}
        val next=points().first {it.id=="wikidata:Q19675"}
        tapMarker(next)
        try {compose.waitUntil(3000){compose.activity.mapFilters.selected?.id=="wikidata:Q19675"}}
        catch(error:Throwable){throw AssertionError("second=$next selected=${compose.activity.mapFilters.selected?.id} panel="+compose.onNodeWithTag("layer-marker-detail").fetchSemanticsNode().boundsInRoot,error)}
        compose.onNodeWithText("Louvre Museum").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("layer-marker-detail").assertDoesNotExist()
        compose.runOnIdle {map.cancelTransitions();map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.845,2.315),12.0))}
        compose.waitUntil(5000){points().any {it.id=="wikidata:Q243" && it.x>0}}
        tapMarker(points().first {it.id=="wikidata:Q243"})
        compose.waitUntil(5000){compose.activity.mapFilters.selected?.id=="wikidata:Q243"}
        compose.onNodeWithTag("layer-marker-detail").assertIsDisplayed()
        compose.onNodeWithTag("layer-places").performClick()
        compose.onNodeWithTag("layer-marker-detail").assertDoesNotExist()
        compose.runOnIdle {assertNull(compose.activity.mapFilters.selected)}
    }
    @Test fun accessibleMarkerSelectionDismissesSearchAndKeyboard() {
        val map=nativeMap()
        compose.runOnIdle {
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(48.858296,2.294479),17.0))
            compose.activity.geocoder=object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=listOf(AddressResult("test","TEST PARIS",48.8,2.3))}
        }
        compose.waitUntil(10000){points().any {it.id=="wikidata:Q243"}}
        compose.onNodeWithTag("address-input").performClick().performTextInput("Paris")
        compose.waitUntil(5000){compose.activity.tracker.searchResults.isNotEmpty()}
        compose.onNodeWithTag("search-results").assertIsDisplayed()
        val actions=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {assertTrue(actions.first {it.label.contains("wikidata:Q243")}.action())}
        compose.onNodeWithTag("search-results").assertDoesNotExist()
        compose.onNodeWithTag("layer-marker-detail").assertIsDisplayed()
        compose.onNodeWithTag("marker-detail-close").performClick()
        compose.onNodeWithTag("layer-marker-detail").assertDoesNotExist()
    }

    @Test fun screenRingsAnimateAndStopWithTheirLayer() {
        val map=nativeMap()
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(51.507,-.128),17.0))}
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Alert}}
        var first=0f
        compose.waitUntil(5000){MarkerKind.Alert in compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey].visibleKinds}
        first=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey].pulse(MarkerKind.Alert)!!.radius
        compose.waitUntil(3000) {
            kotlin.math.abs(compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey].pulse(MarkerKind.Alert)!!.radius-first)>1
        }
        compose.onNodeWithTag("layer-alerts").performClick()
        compose.waitUntil(5000){points().none {it.kind==MarkerKind.Alert}}
        var stopped=0f
        stopped=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey].pulse(MarkerKind.Alert)!!.radius
        android.os.SystemClock.sleep(250)
        val effects=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey]
        assertFalse(MarkerKind.Alert in effects.visibleKinds);assertEquals(stopped,effects.pulse(MarkerKind.Alert)!!.radius,0f)
    }
    @Test fun oneThousandNearbyReportsClusterWithoutOneViewPerMarker() {
        val map=nativeMap()
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Alert}}
        val start=android.os.SystemClock.elapsedRealtime()
        compose.runOnIdle {
            val features=(0 until 1000).map {i->org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(-.128+i%10*.0001,51.507+i/10*.0001)).apply {addStringProperty("id","test-dense-$i")}}
            map.style!!.getSourceAs<GeoJsonSource>("alerts")!!.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features))
        }
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Alert && it.count==1000}}
        val elapsed=android.os.SystemClock.elapsedRealtime()-start
        assertEquals(1,points().count {it.kind==MarkerKind.Alert})
        compose.runOnIdle {
            val file=java.io.File(compose.activity.getExternalFilesDir(null),"phase31-density-test.json")
            file.writeText("{\"inputReports\":1000,\"visibleClusters\":1,\"settleMs\":$elapsed}")
            val fd=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phase31-density-test.json")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
        }
    }
    @Test fun systemReducedMotionAndBackgroundStopScreenPulsing() {
        val map=nativeMap()
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(51.507,-.128),17.0))}
        compose.waitUntil(10000){points().any {it.kind==MarkerKind.Alert}}
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val old=android.provider.Settings.Global.getString(compose.activity.contentResolver,"animator_duration_scale") ?: "1"
        fun scale(value:String) {
            val fd=automation.executeShellCommand("settings put global animator_duration_scale $value")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
        }
        val effects=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey]
        fun radius():Float {var value=0f;compose.runOnUiThread {value=effects.pulse(MarkerKind.Alert)?.radius ?: 0f};return value}
        try {
            scale("0")
            compose.waitUntil(5000){radius()==25f}
            android.os.SystemClock.sleep(200);assertEquals(25f,radius(),0f)
            scale("1")
            compose.waitUntil(5000){radius()!=25f}
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            android.os.SystemClock.sleep(100);val stopped=radius()
            android.os.SystemClock.sleep(250);assertEquals(stopped,radius(),0f)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(5000){kotlin.math.abs(radius()-stopped)>1f}
        } finally {scale(old);compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)}
    }
    @Test fun filterCloseRespondsToPhysicalPointerInputAboveNativeMap() {
        nativeMap()
        compose.onNodeWithTag("layer-places").performTouchInput {longClick()}
        compose.onNodeWithTag("layer-filters").assertIsDisplayed()
        val center=compose.onNodeWithTag("filters-close").fetchSemanticsNode().boundsInRoot.center
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .perform(androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,{
                floatArrayOf(center.x,center.y)
            },androidx.test.espresso.action.Press.FINGER))
        compose.waitUntil(5000){compose.onAllNodesWithTag("layer-filters").fetchSemanticsNodes().isEmpty()}
    }
}
