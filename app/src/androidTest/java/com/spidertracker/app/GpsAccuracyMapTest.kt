package com.spidertracker.app

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
import kotlin.math.abs

class GpsAccuracyMapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun nativeMarkerAndAccuracyStayTogetherAcrossCameraChangesAndRecenter() {
        compose.runOnIdle {compose.activity.setContent {
            TrackerApp(compose.activity.tracker,MapLibreRenderer(),true,{}, {},compose.activity.mapFilters)
        }}
        fun find(v:View):MapView? {
            if(v is MapView)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
            return null
        }
        val ready=AtomicReference<MapLibreMap>();var view:MapView?=null
        compose.runOnIdle {view=find(compose.activity.window.decorView);view!!.getMapAsync {it.getStyle {_->ready.set(it)}}}
        compose.waitUntil(20000){ready.get()!=null}
        val map=ready.get()
        var fix=DeviceFix(40.17,44.50,125f,true)
        fun locate(center:Boolean) {
            compose.runOnIdle {compose.activity.tracker.locate(object:DeviceLocationProvider {
                override suspend fun currentFix()=fix
            },center)}
            compose.waitUntil(5000){compose.activity.tracker.userFix==fix}
        }
        fun aligned():Boolean {
            var result=false
            compose.runOnIdle {
                val p=map.projection.toScreenLocation(LatLng(fix.lat,fix.lon))
                result=map.queryRenderedFeatures(p,"user-marker").any {it.getStringProperty("id")=="user"} &&
                    map.queryRenderedFeatures(p,"user-accuracy").isNotEmpty()
            }
            return result
        }
        locate(false)
        for((zoom,bearing,tilt) in listOf(Triple(12.5,0.0,0.0),Triple(15.0,90.0,0.0),Triple(17.0,215.0,50.0))) {
            compose.runOnIdle {map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(LatLng(fix.lat+.0003,fix.lon+.0003)).zoom(zoom).bearing(bearing).tilt(tilt).build()))}
            compose.waitUntil(8000){aligned()}
            compose.runOnIdle {
                assertEquals("user",map.style!!.getLayerAs<FillLayer>("user-accuracy")!!.sourceId)
                assertEquals("user",map.style!!.getLayerAs<SymbolLayer>("user-marker")!!.sourceId)
                val p=map.projection.toScreenLocation(LatLng(fix.lat,fix.lon))
                val density=compose.activity.resources.displayMetrics.density
                assertTrue(map.queryRenderedFeatures(android.graphics.PointF(p.x+10*density,p.y),"user-marker").isNotEmpty())
                assertTrue(map.queryRenderedFeatures(android.graphics.PointF(p.x+20*density,p.y),"user-marker").isEmpty())
            }
        }
        // Navigation padding must not survive an explicit current-location recenter.
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
        compose.runOnIdle {compose.activity.tracker.locationDenied()}
        compose.waitUntil(8000){!aligned()}
    }
}
