package com.spidertracker.app

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class GeographicRadarTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var previousRange=20
    @Before fun enabled() {compose.runOnIdle {
        val f=compose.activity.mapFilters;previousRange=f.radarRangeKm;f.setRadarRange(20)
        if(!f.state.placesEnabled)f.toggle(MarkerKind.Place)
        if(!f.state.alertsEnabled)f.toggle(MarkerKind.Alert)
        PlaceCategory.entries.filter {it !in f.state.placeCategories}.forEach(f::toggleCategory)
        AlertCategory.entries.filter {it !in f.state.alertCategories}.forEach(f::toggleCategory)
    }}
    @After fun restore(){compose.runOnIdle {compose.activity.mapFilters.setRadarRange(previousRange)}}
    private fun snapshot()=compose.onNodeWithTag("radar").fetchSemanticsNode().config[RadarSnapshotKey]
    private fun nativeMap():Pair<MapView,MapLibreMap> {
        fun find(v:View):MapView? {if(v is MapView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        val result=AtomicReference<MapLibreMap>();var view:MapView?=null
        compose.runOnIdle {view=find(compose.activity.window.decorView);view!!.getMapAsync {map->map.getStyle {result.set(map)}}}
        compose.waitUntil(20000){result.get()!=null};android.os.SystemClock.sleep(1800)
        return view!! to result.get()
    }
    private fun move(map:MapLibreMap,lat:Double,lon:Double) {
        compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(lat,lon),14.0))}
        compose.waitUntil(5000){snapshot().originLat?.let {abs(it-lat)<.001}==true}
    }
    @Test fun londonLayersUseGeographicRecordsAndDoNotMoveCamera() {
        val (_,map)=nativeMap();move(map,51.5074,-.1278)
        compose.waitUntil(5000){snapshot().dots.any {it.id=="wikidata:Q5933"}}
        val original=map.cameraPosition
        val initial=snapshot();assertEquals(RadarMode.Map,initial.mode)
        assertFalse(initial.dots.any {it.kind==MarkerKind.User})
        assertTrue(initial.dots.any {it.kind==MarkerKind.Place});assertTrue(initial.demo)
        assertTrue(initial.dots.filter {it.kind==MarkerKind.Alert}.all {it.demo})
        compose.onNodeWithTag("radar-status").assertTextContains("DEMO",substring=true)
        compose.onNodeWithTag("layer-alerts").performClick()
        assertFalse(snapshot().demo);assertFalse(snapshot().dots.any {it.kind==MarkerKind.Alert})
        compose.onNodeWithTag("layer-places").performClick();assertTrue(snapshot().dots.isEmpty())
        compose.runOnIdle {assertEquals(original,map.cameraPosition)}
        compose.onNodeWithTag("layer-places").performClick();compose.onNodeWithTag("layer-alerts").performClick()
        assertEquals(initial.dots,snapshot().dots)
        val image=compose.onNodeWithTag("radar").captureToImage().asAndroidBitmap()
        val pixels=IntArray(image.width*image.height)
        image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
        assertTrue("Green attraction dot not rendered",pixels.any {android.graphics.Color.green(it)>android.graphics.Color.red(it)*1.2 && android.graphics.Color.green(it)>android.graphics.Color.blue(it)})
        assertTrue("Red alert dot not rendered",pixels.any {android.graphics.Color.red(it)>100 && android.graphics.Color.red(it)>android.graphics.Color.green(it)*1.5})
        export("radar-map-london.png")
    }
    @Test fun gpsControlUsesFreshAndroidDeviceFixAndGlobeReturnsToViewport() {
        nativeMap()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=compose.activity.getSystemService(android.location.LocationManager::class.java)
        compose.runOnIdle {compose.activity.locationProvider=AndroidLocationProvider(compose.activity)}
        compose.onNodeWithTag("map-center").performClick()
        compose.waitUntil(25000){!compose.activity.tracker.locating}
        org.junit.Assume.assumeTrue("Requires a fresh device or emulator GPS fix",compose.activity.tracker.userFix!=null)
        val device=manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        val user=snapshot().dots.single {it.kind==MarkerKind.User}
        assertEquals(device!!.latitude,user.lat,.00001);assertEquals(device.longitude,user.lon,.00001)
        assertEquals(0f,user.x,0f);assertEquals(0f,user.y,0f)
        export("radar-device-gps.png")
        compose.onNodeWithTag("map-global").performClick()
        compose.waitUntil(6000){snapshot().mode==RadarMode.Map && abs(compose.activity.tracker.camera.zoom-1.0)<.01}
        assertNotNull(compose.activity.tracker.userFix)
        compose.runOnIdle {compose.activity.tracker.locationDenied()}
        compose.waitUntil(5000){snapshot().dots.none {it.kind==MarkerKind.User}}
    }
    @Test fun acceptedGpsFixChangesOriginAndFailureReturnsToMapMode() {
        val (_,map)=nativeMap();move(map,51.5074,-.1278)
        fun fix(lat:Double,lon:Double) {compose.runOnIdle {compose.activity.tracker.locate(object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(lat,lon,8f,true)})};compose.waitUntil(5000){snapshot().mode==RadarMode.Gps && snapshot().originLat==lat}}
        fix(51.5074,-.1278)
        var user=snapshot().dots.single {it.kind==MarkerKind.User}
        assertEquals(0f,user.x,0f);assertEquals(0f,user.y,0f);assertTrue(snapshot().testLocation)
        compose.waitUntil(5000){abs(map.cameraPosition.target!!.latitude-51.5074)<.001};android.os.SystemClock.sleep(1600)
        val first=snapshot().dots.first {it.kind==MarkerKind.Alert}.distanceKm
        fix(51.51,-.12);assertNotEquals(first,snapshot().dots.first {it.kind==MarkerKind.Alert}.distanceKm)
        user=snapshot().dots.single {it.kind==MarkerKind.User};assertEquals(51.51,user.lat,0.0)
        export("radar-test-gps.png")
        android.os.SystemClock.sleep(1600)
        val before=map.cameraPosition
        compose.runOnIdle {compose.activity.tracker.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix=throw LocationUnavailable("TEST GPS UNAVAILABLE")})}
        compose.waitUntil(5000){snapshot().mode==RadarMode.Map}
        assertFalse(snapshot().dots.any {it.kind==MarkerKind.User})
        compose.runOnIdle {assertEquals(before,map.cameraPosition);assertEquals("TEST GPS UNAVAILABLE",compose.activity.tracker.toast)}
    }
    @Test fun rangeCyclesAndPersistsWithoutChangingMapAndStatusUsesLatestMode() {
        val (_,map)=nativeMap();move(map,51.5074,-.1278)
        compose.waitUntil(5000){snapshot().dots.any {it.id=="wikidata:Q5933"}}
        val before=map.cameraPosition
        val initialDots=snapshot().dots
        compose.onNodeWithTag("radar").performTouchInput {click(Offset(width*.15f,height*.5f))}
        assertEquals(100,snapshot().rangeKm)
        assertEquals(initialDots,snapshot().dots)
        val action=compose.onNodeWithTag("radar").fetchSemanticsNode().config[SemanticsActions.CustomActions].first {it.label=="Radar range 5 km"}
        compose.runOnIdle {assertTrue(action.action())}
        assertEquals(5,snapshot().rangeKm)
        assertEquals(initialDots,snapshot().dots)
        compose.onNodeWithTag("radar").performTouchInput {longClick(Offset(width*.15f,height*.5f))}
        compose.runOnIdle {assertTrue(compose.activity.tracker.toast.contains("MAP VIEWPORT"));assertEquals(before,map.cameraPosition)}
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {assertEquals(5,compose.activity.mapFilters.radarRangeKm)}
    }
    @Test fun physicalRadarDotOpensMatchingAttractionDetails() {
        val (_,map)=nativeMap();move(map,48.85826,2.2945)
        val dot=snapshot().dots.minBy {it.distanceKm};assertEquals(MarkerKind.Place,dot.kind)
        val before=map.cameraPosition
        compose.onNodeWithTag("radar").performTouchInput {
            val r=min(width,height)/2f
            val radius=(r-3*compose.activity.resources.displayMetrics.density)*cos(PI/12).toFloat()
            click(Offset(r+radius*dot.x,r+radius*dot.y))
        }
        compose.waitUntil(5000){compose.activity.mapFilters.selected?.id==dot.id}
        compose.runOnIdle {assertEquals(before,map.cameraPosition)}
        compose.onNodeWithTag("layer-marker-detail").assertIsDisplayed()
        export("radar-selected-attraction.png")
    }
    @Test fun worldViewportZoomBearingAndDatelineUseNativeGeography() {
        val (view,map)=nativeMap()
        compose.onNodeWithTag("map-global").performClick()
        compose.waitUntil(6000){abs(map.cameraPosition.zoom-1.0)<.01 && snapshot().dots.any {it.kind==MarkerKind.Place}}
        assertTrue(snapshot().dots.any {it.distanceKm>100})
        assertFalse(snapshot().label.contains("20 KM"))
        export("radar-world-viewport.png")
        fun camera(lat:Double,lon:Double,zoom:Double,bearing:Double) {
            compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder().target(org.maplibre.android.geometry.LatLng(lat,lon)).zoom(zoom).bearing(bearing).build()))}
            compose.waitUntil(5000){snapshot().originLat?.let {abs(it-lat)<.001}==true}
        }
        camera(51.5074,-.1278,12.0,0.0)
        val before=snapshot().dots.first {it.kind==MarkerKind.Alert}
        camera(51.5074,-.1278,14.0,90.0)
        compose.waitUntil(5000){snapshot().dots.firstOrNull {it.id==before.id}?.let {it.x!=before.x || it.y!=before.y}==true}
        val rotated=snapshot().dots.first {it.id==before.id}
        compose.runOnIdle {
            val p=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(rotated.lat,rotated.lon))
            val x=2*p.x/view.width-1;val y=2*p.y/view.height-1
            assertEquals(x*sqrt(1-y*y/2),rotated.x,.0001f)
            assertEquals(y*sqrt(1-x*x/2),rotated.y,.0001f)
        }
        camera(-25.0,-179.99,2.0,0.0)
        val dateline=snapshot()
        assertTrue("Visible Australian records across dateline missing",dateline.dots.any {it.lon>140})
        assertTrue(snapshot().dots.all {hypot(it.x,it.y)<=1.000001f})
        camera(0.0,0.0,16.0,0.0)
        assertTrue(snapshot().dots.isEmpty())
    }

    @Test fun markerSpriteAndNativeGlowDirectlyBelowRadarAreCompletelyConcealed() {
        compose.runOnIdle {compose.activity.setContent {
            TrackerApp(compose.activity.tracker,MapLibreRenderer(),true,{}, {},compose.activity.mapFilters)
        }}
        val (view,map)=nativeMap()
        val record=compose.activity.mapFilters.visibleMarkers.first {it.id=="wikidata:Q6373"}
        move(map,record.lat,record.lon)
        val radarBounds=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
        val mapBounds=compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val under=radarBounds.center-Offset(radarBounds.width*.15f,-radarBounds.height*.1f)-mapBounds.topLeft
        compose.runOnIdle {
            val p=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(record.lat,record.lon))
            val target=map.projection.fromScreenLocation(android.graphics.PointF(view.width/2f+p.x-under.x,view.height/2f+p.y-under.y))
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(target))
        }
        compose.waitUntil(8000){compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey]
            .any {it.id==record.id && it.count==1 && hypot(it.x-under.x,it.y-under.y)<3f}}
        compose.runOnIdle {
            val style=map.style!!
            style.addSource(org.maplibre.android.style.sources.GeoJsonSource("radar-test-underlay",
                org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(record.lon,record.lat))))
            style.addLayer(org.maplibre.android.style.layers.CircleLayer("radar-test-glow","radar-test-underlay").withProperties(
                org.maplibre.android.style.layers.PropertyFactory.circleRadius(90f),
                org.maplibre.android.style.layers.PropertyFactory.circleColor("#ff00ff"),
                org.maplibre.android.style.layers.PropertyFactory.circleBlur(.5f)))
        }
        android.os.SystemClock.sleep(700)
        val dots=snapshot().dots
        val withMarker=compose.onNodeWithTag("radar").captureToImage().asAndroidBitmap()
        export("radar-marker-under-polygon.png")
        compose.runOnIdle {
            map.style!!.removeLayer("radar-test-glow");map.style!!.removeSource("radar-test-underlay")
            compose.activity.mapFilters.toggle(MarkerKind.Place);compose.activity.mapFilters.toggle(MarkerKind.Alert)
        }
        compose.waitUntil(5000){compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey].isEmpty()}
        val without=compose.onNodeWithTag("radar").captureToImage().asAndroidBitmap()
        val r=withMarker.width/2f
        val radius=(r-3*compose.activity.resources.displayMetrics.density)*cos(PI/12).toFloat()
        var checked=0
        for(y in 0 until withMarker.height)for(x in 0 until withMarker.width) {
            val angle=((atan2((y-r).toDouble(),(x-r).toDouble())+2*PI)%(PI/6))-PI/12
            val edge=r*cos(PI/12)/cos(angle)
            if(hypot(x-r,y-r)>edge-2 || dots.any {hypot(x-r-radius*it.x,y-r-radius*it.y)<10f})continue
            assertEquals("Sprite or glow leaked at $x,$y",without.getPixel(x,y),withMarker.getPixel(x,y));checked++
        }
        assertTrue(checked>1000)
    }
    @Test fun tallDockedInsetsKeepRadarSearchAndSuggestionsSeparateInLandscape() {
        val previous=compose.activity.requestedOrientation
        var original:androidx.core.view.WindowInsetsCompat?=null
        try {
            compose.runOnUiThread {compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(10000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            val (view,map)=nativeMap()
            move(map,51.5074,-.1278)
            compose.runOnIdle {
                compose.activity.tracker.editSearch("TEST")
                compose.activity.tracker.search(object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=List(5){AddressResult("test-$it","TEST PLACE $it",0.0,0.0)}})
            }
            compose.waitUntil(5000){compose.activity.tracker.searchResults.size==5}
            val viewport=compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
            val fixedChrome=listOf("tracker-logo","left-logo","right-logo","layer-places","layer-alerts")
                .associateWith {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
            val camera=map.cameraPosition;val w=view.width;val h=view.height
            val density=compose.activity.resources.displayMetrics.density
            val rootHeight=compose.onNodeWithTag("tracker-root").fetchSemanticsNode().boundsInRoot.height
            original=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            // Synthetic system insets exercise a tall docked IME; this is not a live keyboard capture.
            val testInsets=androidx.core.view.WindowInsetsCompat.Builder(original)
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,(rootHeight-180*density).toInt()))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            compose.runOnUiThread {androidx.core.view.ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView,testInsets)}
            compose.waitUntil(5000){compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top<130*density}
            val radar=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
            val search=compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot
            val suggestions=compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
            assertTrue(radar.top>=0);assertEquals(120*density,radar.width,1f)
            assertTrue(radar.bottom<=180*density);assertTrue(search.bottom<=180*density)
            assertTrue(search.right<radar.left);assertTrue(suggestions.right<radar.left)
            assertTrue(suggestions.bottom<search.top)
            compose.onNodeWithTag("search-result-0").assertIsDisplayed()
            listOf("map-global","map-center","spider-sprite","search-submit").forEach {tag->
                val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag clipped above",bounds.top>=0);assertTrue("$tag behind IME",bounds.bottom<=180*density)
            }
            assertEquals(viewport,compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle {assertEquals(camera,map.cameraPosition);assertEquals(w,view.width);assertEquals(h,view.height)}
            fixedChrome.forEach {(tag,bounds)->assertEquals(tag,bounds,compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)}
            val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
            automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
            compose.runOnIdle {compose.activity.locationProvider=object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix=throw LocationUnavailable("TEST RADAR GPS ACTION")}}
            export("radar-tall-ime-synthetic.png")
            compose.onNodeWithTag("map-center").performTouchInput {click()}
            compose.waitUntil(5000){compose.activity.tracker.toast=="TEST RADAR GPS ACTION"}
            compose.runOnIdle {assertEquals(camera,map.cameraPosition)}
            compose.onNodeWithTag("map-global").performTouchInput {click()}
            compose.waitUntil(6000){abs(map.cameraPosition.zoom-1.0)<.01}
            compose.runOnIdle {assertEquals(w,view.width);assertEquals(h,view.height)}
        } finally {
            compose.runOnUiThread {
                original?.let {androidx.core.view.ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView,it)}
                compose.activity.requestedOrientation=previous
            }
        }
    }
    private fun export(name:String) {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val file=java.io.File(compose.activity.getExternalFilesDir(null),name)
        file.outputStream().use {automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }
}
