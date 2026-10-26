package com.spidertracker.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Phase312MapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun view():MapView {
        fun find(v:View):MapView? {
            if(v is MapView)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it}
            return null
        }
        var result:MapView?=null
        compose.runOnIdle {result=find(compose.activity.window.decorView)}
        return result!!
    }
    private fun map():MapLibreMap {
        val result=AtomicReference<MapLibreMap>()
        val native=view()
        compose.runOnIdle {native.getMapAsync {m->m.getStyle {result.set(m)}}}
        compose.waitUntil(20000){result.get()!=null}
        SystemClock.sleep(1800)
        return result.get()
    }
    private fun position(map:MapLibreMap):CameraPosition {
        val result=AtomicReference<CameraPosition>()
        compose.runOnUiThread {result.set(map.cameraPosition)}
        return result.get()
    }
    private fun event(down:Long,action:Int,points:List<Pair<Float,Float>>,sync:Boolean=true,time:Long=SystemClock.uptimeMillis()) {
        val properties=points.indices.map {i->MotionEvent.PointerProperties().apply {id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}.toTypedArray()
        val coords=points.map {(x,y)->MotionEvent.PointerCoords().apply {this.x=x;this.y=y;pressure=1f;size=.05f}}.toTypedArray()
        val event=MotionEvent.obtain(down,time,action,points.size,properties,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        try {assertTrue(automation.injectInputEvent(event,sync))}finally {event.recycle()}
    }
    private fun center():Pair<Float,Float> {
        val native=view();val p=IntArray(2)
        compose.runOnUiThread {native.getLocationOnScreen(p)}
        return (p[0]+native.width/2f) to (p[1]+native.height/2f)
    }
    private fun pinch(start:Float,end:Float,at:Pair<Float,Float> = center()) {
        val (cx,cy)=at;val down=SystemClock.uptimeMillis()
        fun points(span:Float)=listOf((cx-span/2) to cy,(cx+span/2) to cy)
        event(down,MotionEvent.ACTION_DOWN,points(start).take(1))
        event(down,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points(start))
        repeat(8){i->SystemClock.sleep(10);event(down,MotionEvent.ACTION_MOVE,points(start+(end-start)*(i+1)/8))}
        event(down,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points(end))
        event(down,MotionEvent.ACTION_UP,points(end).take(1))
    }
    private fun doubleTap() {
        val point=center()
        repeat(2) {
            // Synchronous injection waits for rendering and can exceed Android's double-tap timeout.
            val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,listOf(point),false);SystemClock.sleep(40)
            event(down,MotionEvent.ACTION_UP,listOf(point),false);SystemClock.sleep(70)
        }
    }
    private fun twoFingerTap():Long {
        val (cx,cy)=center();val started=SystemClock.uptimeMillis();val down=started-50
        val points=listOf((cx-90) to cy,(cx+90) to cy)
        // Emulator injection can take >150 ms; model a 30 ms tap without changing native thresholds.
        event(down,MotionEvent.ACTION_DOWN,points.take(1),false,down)
        event(down,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points,false,down+5)
        event(down,MotionEvent.ACTION_MOVE,points.map {(x,y)->(x+2) to (y+1)},false,down+15)
        event(down,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points,false,down+25)
        event(down,MotionEvent.ACTION_UP,points.take(1),true,down+30)
        return SystemClock.uptimeMillis()-started
    }
    private fun export(name:String,text:String) {
        val file=java.io.File(compose.activity.getExternalFilesDir(null),name);file.writeText(text)
        val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }
    private fun exerciseZoom(orientation:String) {
        val map=map()
        compose.runOnIdle {
            assertTrue(map.uiSettings.isZoomGesturesEnabled);assertTrue(map.uiSettings.isDoubleTapGesturesEnabled)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0,-140.0),5.0))
        }
        val receipt=org.json.JSONObject().put("orientation",orientation).put("initialZoom",position(map).zoom)
        compose.runOnIdle {map.animateCamera(CameraUpdateFactory.zoomIn(),250)}
        compose.waitUntil(5000){abs(position(map).zoom-6.0)<.02}
        receipt.put("programmaticIn",position(map).zoom)
        compose.runOnIdle {map.animateCamera(CameraUpdateFactory.zoomOut(),250)}
        compose.waitUntil(5000){abs(position(map).zoom-5.0)<.02}
        val c=position(map);assertEquals(0.0,c.target!!.latitude,.001);assertEquals(-140.0,c.target!!.longitude,.001)
        receipt.put("programmaticOut",c.zoom)
        pinch(70f,390f)
        compose.waitUntil(5000){position(map).zoom>5.3}
        SystemClock.sleep(400);val spread=position(map).zoom;receipt.put("pinchIn",spread)
        pinch(390f,70f)
        compose.waitUntil(5000){position(map).zoom<spread-.3}
        SystemClock.sleep(400);receipt.put("pinchOut",position(map).zoom)
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0,-140.0),5.0))}
        doubleTap();compose.waitUntil(5000){position(map).zoom>5.3}
        SystemClock.sleep(500);val doubled=position(map).zoom;receipt.put("doubleTap",doubled)
        val detector=map.gesturesManager.multiFingerTapGestureDetector
        compose.runOnUiThread {receipt.put("tapEnabled",detector.isEnabled).put("tapSpanThresholdPx",detector.spanThreshold).put("tapTimeThresholdMs",detector.multiFingerTapTimeThreshold)}
        val tapDuration=twoFingerTap();receipt.put("twoFingerInputDurationMs",tapDuration).put("twoFingerEventDurationMs",30)
        try {compose.waitUntil(5000){position(map).zoom<doubled-.3}}
        catch(error:Throwable){throw AssertionError("two-finger duration=$tapDuration zoom=${position(map).zoom} receipt=$receipt",error)}
        SystemClock.sleep(500);val final=position(map).zoom;receipt.put("twoFingerTap",final)
        SystemClock.sleep(800);assertEquals(final,position(map).zoom,.02)
        compose.onNodeWithTag("radar").assertIsDisplayed()
        compose.onNodeWithTag("map-global").performClick()
        compose.waitUntil(5000){abs(position(map).zoom-1.0)<.02}
        receipt.put("globalReset",position(map).zoom)
        export("phase312-zoom-$orientation.json",receipt.toString())
    }
    @Test fun nativeZoomCommandsAndGesturesWorkInPortrait(){exerciseZoom("portrait")}
    @Test fun nativeZoomCommandsAndGesturesWorkInLandscape() {
        val previous=compose.activity.requestedOrientation
        try {
            compose.runOnUiThread {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(10000){view().width>view().height}
            exerciseZoom("landscape")
        } finally {compose.runOnUiThread {compose.activity.requestedOrientation=previous}}
    }
    @Test fun phase314CameraLimitsRapidCommandsAndPanPreserveCenterAndZoom() {
        val map=map()
        var min=0.0;var max=0.0
        compose.runOnIdle {min=map.minZoomLevel;max=map.maxZoomLevel;map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0,-140.0),5.0))}
        repeat(6) {i->
            compose.runOnUiThread {map.animateCamera(if(i%2==0)CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut(),250)}
            SystemClock.sleep(40)
        }
        SystemClock.sleep(500)
        val rapid=position(map)
        assertTrue(rapid.zoom.isFinite());assertTrue(rapid.zoom in min..max)
        assertEquals(0.0,rapid.target!!.latitude,.001);assertEquals(-140.0,rapid.target!!.longitude,.001)
        compose.runOnIdle {map.animateCamera(CameraUpdateFactory.zoomBy(.75),250)}
        compose.waitUntil(5000){abs(position(map).zoom-rapid.zoom-.75)<.02}
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.zoomBy(100.0))}
        assertEquals(max,position(map).zoom,.02)
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.zoomBy(-100.0))}
        assertEquals(min,position(map).zoom,.02)
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0,-140.0),5.0))}
        val (x,y)=center();val down=SystemClock.uptimeMillis()
        event(down,MotionEvent.ACTION_DOWN,listOf(x to y),false)
        repeat(12) {i->SystemClock.sleep(20);event(down,MotionEvent.ACTION_MOVE,listOf((x+12*(i+1)) to y),false)}
        event(down,MotionEvent.ACTION_UP,listOf((x+144) to y))
        SystemClock.sleep(800)
        val panned=position(map)
        assertEquals(5.0,panned.zoom,.02)
        assertTrue(abs(panned.target!!.longitude+140)>.1)
        compose.runOnIdle {
            // Navigation bearing/tilt is programmatic; preserve the original gesture configuration.
            assertFalse(map.uiSettings.isRotateGesturesEnabled);assertFalse(map.uiSettings.isTiltGesturesEnabled)
        }
    }
    @Test fun phase314StyleReadinessDoesNotReplayTheInitialCameraRequest() {
        val camera=AtomicReference<MapLibreMap>()
        var moved=false
        val renderer=MapLibreRenderer()
        compose.runOnUiThread {compose.activity.setContent {
            renderer.Content(MapScene(emptyList(),CameraTarget(0.0,-140.0,1.0),null,true),Modifier.fillMaxSize(),
                onPin={},onBackground={},onProjection={},onCamera={_,_,_->},onMarker={},onPlaces={_,_->},onSprites={},
                onRadarViewport={
                    if(!moved) {
                        moved=true
                        fun find(v:View):MapView? {
                            if(v is MapView)return v
                            if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
                            return null
                        }
                        // Emulate native interaction after style readiness, before its Compose effect runs.
                        find(compose.activity.window.decorView)!!.getMapAsync {map->
                            camera.set(map);map.moveCamera(CameraUpdateFactory.zoomTo(5.0))
                        }
                    }
                })
        }}
        compose.waitUntil(20000){camera.get()!=null}
        SystemClock.sleep(2000)
        assertEquals(5.0,position(camera.get()).zoom,.02)
    }
    @Test fun phase314UpdatesKeyboardAndExplicitCameraActionsKeepOneMapView() {
        val map=map();val native=view()
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.5074,-.1278),14.0))}
        val original=position(map)
        repeat(2) {
            compose.onNodeWithTag("layer-places").performClick()
            compose.onNodeWithTag("layer-alerts").performClick()
        }
        compose.onNodeWithTag("address-input").performClick().performTextReplacement("London test")
        compose.waitUntil(5000){androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        SystemClock.sleep(1800)
        assertEquals(original,position(map));assertSame(native,view())
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val result=AddressResult("phase314","TEST SEARCH RESULT",48.858296,2.294479)
        compose.runOnIdle {compose.activity.tracker.search(object:GeocodingProvider {
            override val configured=true
            override suspend fun search(query:String)=listOf(result)
        })}
        compose.waitUntil(5000){compose.activity.tracker.searchResults==listOf(result)}
        compose.onNodeWithTag("search-result-0").performClick()
        compose.waitUntil(6000){abs(position(map).zoom-13.0)<.02 && abs(position(map).target!!.latitude-result.lat)<.001}
        assertEquals(result.lon,position(map).target!!.longitude,.001)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.runOnIdle {compose.activity.locationProvider=object:DeviceLocationProvider {
            override suspend fun currentFix()=DeviceFix(40.1,44.5,8f,true)
        }}
        compose.onNodeWithTag("map-center").performClick()
        compose.waitUntil(6000){abs(position(map).target!!.latitude-40.1)<.001 && abs(position(map).target!!.longitude-44.5)<.001 && abs(position(map).zoom-13.0)<.02}
        assertEquals(13.0,position(map).zoom,.02)
        compose.waitUntil(5000){compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey].any {it.id=="user"}}
        val user=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey].first {it.id=="user"}
        compose.runOnIdle {
            val projected=map.projection.toScreenLocation(LatLng(40.1,44.5))
            assertEquals(projected.x,user.x,2f);assertEquals(projected.y,user.y,2f)
        }
        compose.onNodeWithTag("map-global").performClick()
        compose.waitUntil(6000){position(map).let {abs(it.zoom-1.0)<.02 && abs(it.target!!.latitude-15.973899714245507)<.001 && abs(it.target!!.longitude+49.9007427311529)<.001}}
        assertEquals(15.973899714245507,position(map).target!!.latitude,.001)
        assertEquals(-49.9007427311529,position(map).target!!.longitude,.001)
        assertSame(native,view())
    }
    @Test fun phase314PinchAcrossMarkerAndRadarTransparentCornerReachNativeMap() {
        val map=map()
        compose.runOnIdle {
            val f=compose.activity.mapFilters
            if(!f.state.placesEnabled)f.toggle(MarkerKind.Place)
            attractionCategories.filter {it !in f.state.placeCategories}.forEach(f::toggleCategory)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(48.858296,2.294479),14.0))
        }
        compose.waitUntil(8000){compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey].any {it.id=="wikidata:Q243"}}
        pinch(120f,360f)
        compose.waitUntil(5000){position(map).zoom>14.3}
        val sprites=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey]
        val marker=sprites.first {it.id=="wikidata:Q243"}
        compose.runOnIdle {
            val p=map.projection.toScreenLocation(LatLng(marker.lat,marker.lon))
            assertEquals(p.x,marker.x,2f);assertEquals(p.y,marker.y,2f)
        }
        // The top-left corner is outside the twelve-sided radar and both attached controls.
        val bounds=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
        val decor=IntArray(2);compose.runOnUiThread {compose.activity.window.decorView.getLocationOnScreen(decor)}
        val corner=(decor[0]+bounds.left+2) to (decor[1]+bounds.top+2)
        val before=position(map).zoom
        val location=IntArray(2);val native=view()
        var anchor=LatLng()
        compose.runOnIdle {
            native.getLocationOnScreen(location)
            anchor=map.projection.fromScreenLocation(PointF(corner.first-location[0],corner.second-location[1]))
        }
        repeat(2) {
            val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,listOf(corner),false)
            SystemClock.sleep(40);event(down,MotionEvent.ACTION_UP,listOf(corner),false);SystemClock.sleep(70)
        }
        compose.waitUntil(5000){abs(position(map).zoom-before-1)<.02}
        compose.runOnIdle {
            val projected=map.projection.toScreenLocation(anchor)
            assertEquals(corner.first-location[0],projected.x,3f)
            assertEquals(corner.second-location[1],projected.y,3f)
        }
    }
    private fun snapshot(map:MapLibreMap):Bitmap {
        val image=AtomicReference<Bitmap>()
        compose.runOnUiThread {map.snapshot {image.set(it)}}
        compose.waitUntil(10000){image.get()!=null}
        return image.get()
    }
    @Test fun screenRingStaysCircularUnderTiltAndClusterHighlightIsStatic() {
        val map=map()
        compose.runOnIdle {
            // Uniform contrast isolates ring shape from changing road/label pixels beneath it.
            val layers=org.json.JSONObject(compose.activity.assets.open("tracker-style.json").bufferedReader().use {it.readText()}).getJSONArray("layers")
            for(i in 0 until layers.length())layers.getJSONObject(i).let {layer->
                if(layer.optString("source")=="osm")map.style!!.getLayer(layer.getString("id"))?.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility("none"))
            }
            val f=compose.activity.mapFilters
            if(!f.state.placesEnabled)f.toggle(MarkerKind.Place)
            attractionCategories.filter {it !in f.state.placeCategories}.forEach(f::toggleCategory)
            map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(48.858296,2.294479)).zoom(17.0).bearing(45.0).tilt(45.0).build()))
        }
        compose.waitUntil(10000){compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[ProjectedMarkersKey].any {it.id=="wikidata:Q243"}}
        val old=android.provider.Settings.Global.getString(compose.activity.contentResolver,"animator_duration_scale") ?: "1"
        fun scale(value:String) {
            val fd=automation.executeShellCommand("settings put global animator_duration_scale $value")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
        }
        fun effects()=compose.onNodeWithTag("map-marker-sprites").fetchSemanticsNode().config[MarkerEffectsKey]
        try {
            scale("0")
            compose.waitUntil(5000){effects().pulse(MarkerKind.Place)?.radius==25f}
            SystemClock.sleep(600)
            var point=android.graphics.PointF()
            compose.runOnIdle {
                point=map.projection.toScreenLocation(LatLng(48.858296,2.294479))
            }
            val native=view();val location=IntArray(2)
            compose.runOnUiThread {native.getLocationOnScreen(location)}
            point.offset(location[0].toFloat(),location[1].toFloat())
            val withRing=automation.takeScreenshot()
            compose.onNodeWithTag("layer-places").performClick()
            compose.waitUntil(5000){MarkerKind.Place !in effects().visibleKinds}
            SystemClock.sleep(200)
            val without=automation.takeScreenshot()
            val density=compose.activity.resources.displayMetrics.density
            val radius=(31*density).toInt();val xs=mutableListOf<Int>();val ys=mutableListOf<Int>()
            for(y in (point.y.toInt()-radius).coerceAtLeast(0)..(point.y.toInt()+radius).coerceAtMost(withRing.height-1))
                for(x in (point.x.toInt()-radius).coerceAtLeast(0)..(point.x.toInt()+radius).coerceAtMost(withRing.width-1)) {
                    val a=withRing.getPixel(x,y);val b=without.getPixel(x,y)
                    if((abs(x-point.x)>18*density || abs(y-point.y)>18*density) && abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))>12){xs.add(x);ys.add(y)}
                }
            assertTrue("No ring pixels found",xs.size>30)
            val w=xs.max()-xs.min()+1;val h=ys.max()-ys.min()+1
            assertEquals(w.toFloat(),h.toFloat(),3f)
            assertEquals(point.x,(xs.max()+xs.min())/2f,2f);assertEquals(point.y,(ys.max()+ys.min())/2f,2f)
            assertTrue(w>45*density && w<58*density)
            export("phase312-ring.json",org.json.JSONObject().put("widthPx",w).put("heightPx",h).put("density",density).put("bearing",45).put("tilt",45).put("centerErrorXPx",abs(point.x-(xs.max()+xs.min())/2f)).put("centerErrorYPx",abs(point.y-(ys.max()+ys.min())/2f)).toString())
            compose.onNodeWithTag("layer-places").performClick()
            compose.waitUntil(5000){MarkerKind.Place in effects().visibleKinds}
            SystemClock.sleep(200)
            val screen=automation.takeScreenshot()
            val file=java.io.File(compose.activity.getExternalFilesDir(null),"phase312-ring.png")
            file.outputStream().use {screen.compress(Bitmap.CompressFormat.PNG,100,it)}
            val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phase312-ring.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
            var cluster=0f
            compose.runOnIdle {cluster=map.style!!.getLayerAs<CircleLayer>("places-cluster-glow")!!.circleRadius.value!!}
            SystemClock.sleep(200)
            compose.runOnIdle {assertEquals(cluster,map.style!!.getLayerAs<CircleLayer>("places-cluster-glow")!!.circleRadius.value!!,0f)}
        } finally {scale(old)}
    }
}
