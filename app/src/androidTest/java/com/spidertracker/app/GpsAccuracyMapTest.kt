package com.spidertracker.app

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.SymbolLayer
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class GpsAccuracyMapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun singleNativeMarkerAndMeterBoundaryTrackGpsAcrossCameraChangesAndRecenter() {
        compose.runOnIdle {compose.activity.setContent {
            TrackerApp(compose.activity.tracker,MapLibreRenderer(),true,{}, {},compose.activity.mapFilters)
        }}
        fun find(v:View):MapView? {
            if(v is MapView)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
            return null
        }
        val ready=AtomicReference<MapLibreMap>();val rendered=AtomicBoolean();var view:MapView?=null
        compose.runOnIdle {
            view=find(compose.activity.window.decorView)
            view!!.addOnDidFinishRenderingFrameListener {_,_,_->rendered.set(true)}
            view!!.getMapAsync {it.getStyle {_->ready.set(it)}}
        }
        // A loaded style can precede the native renderer; querying then can crash MapLibre.
        compose.waitUntil(20000){ready.get()!=null && rendered.get()}
        val map=ready.get()
        var fix=DeviceFix(40.17,44.50,125f,true)
        fun locate(center:Boolean) {
            compose.runOnIdle {compose.activity.tracker.locate(object:DeviceLocationProvider {
                override suspend fun currentFix()=fix
            },center)}
            compose.waitUntil(5000){compose.activity.tracker.userFix==fix}
        }
        fun renderedAt(lat:Double,lon:Double,layer:String):Int {
            assertSame(view,find(compose.activity.window.decorView))
            val p=map.projection.toScreenLocation(LatLng(lat,lon))
            return map.queryRenderedFeatures(p,layer).size
        }
        fun aligned():Boolean {
            var result=false
            compose.runOnIdle {result=renderedAt(fix.lat,fix.lon,"user-marker")==1 && renderedAt(fix.lat,fix.lon,"user-accuracy")==1}
            return result
        }
        locate(false)
        // The same geographic probes must stay inside/outside after zoom, rotation, pitch and pan.
        for((zoom,bearing,tilt) in listOf(Triple(12.5,0.0,0.0),Triple(15.0,90.0,0.0),Triple(17.0,215.0,50.0))) {
            compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(LatLng(fix.lat+.0003,fix.lon+.0003)).zoom(zoom).bearing(bearing).tilt(tilt).build()))}
            compose.waitUntil(8000){aligned()}
            compose.runOnIdle {
                assertNull("Old native dot duplicates the pixel sprite",map.style!!.getLayer("user-dot"))
                assertEquals(listOf("user-marker"),map.style!!.layers.filterIsInstance<SymbolLayer>().filter {it.sourceId=="user"}.map {it.id})
                assertEquals("user",map.style!!.getLayerAs<FillLayer>("user-accuracy")!!.sourceId)
                assertEquals(1,renderedAt(fix.lat+.0005,fix.lon,"user-accuracy")) // ~56 meters
                assertEquals(0,renderedAt(fix.lat+.002,fix.lon,"user-accuracy")) // ~222 meters
                val p=map.projection.toScreenLocation(LatLng(fix.lat,fix.lon))
                val density=compose.activity.resources.displayMetrics.density
                assertEquals(1,map.queryRenderedFeatures(PointF(p.x+10*density,p.y),"user-marker").size)
                assertEquals(0,map.queryRenderedFeatures(PointF(p.x+20*density,p.y),"user-marker").size)
            }
        }
        compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(map.cameraPosition).padding(0.0,200.0,0.0,0.0).build()))}
        locate(true)
        compose.waitUntil(8000){
            var centered=false
            compose.runOnIdle {
                val p=map.projection.toScreenLocation(LatLng(fix.lat,fix.lon))
                centered=abs(p.x-view!!.width/2f)<2 && abs(p.y-view!!.height/2f)<2 && abs(map.cameraPosition.zoom-16.0)<.01
            }
            centered
        }
        compose.waitUntil(8000){aligned()}
        fix=fix.copy(lat=fix.lat+.001,lon=fix.lon+.001,accuracy=40f)
        locate(false)
        compose.waitUntil(8000){aligned()}
        compose.runOnIdle {
            assertEquals(0,renderedAt(fix.lat+.0005,fix.lon,"user-accuracy"))
            assertEquals(0,renderedAt(fix.lat-.001,fix.lon-.001,"user-marker"))
            compose.activity.tracker.locationDenied()
        }
        compose.waitUntil(8000){
            var empty=false
            compose.runOnIdle {empty=renderedAt(fix.lat,fix.lon,"user-marker")==0 && renderedAt(fix.lat,fix.lon,"user-accuracy")==0}
            empty
        }
    }
}
