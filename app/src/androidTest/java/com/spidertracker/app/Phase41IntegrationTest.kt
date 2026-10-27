package com.spidertracker.app

import android.Manifest
import android.content.Context
import android.location.*
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Point
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Phase41IntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private val a=NavPoint(40.1772,44.5126)
    private val b=NavPoint(40.1913,44.5155)
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun grantLocation() {
        automation.grantRuntimePermission(compose.activity.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(compose.activity.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private fun map():MapLibreMap {
        fun find(view:View):MapView?=if(view is MapView)view else if(view is ViewGroup)(0 until view.childCount).firstNotNullOfOrNull {find(view.getChildAt(it))} else null
        var result:MapLibreMap?=null;val ready=CountDownLatch(1)
        compose.runOnIdle {find(compose.activity.window.decorView)!!.getMapAsync {result=it;ready.countDown()}}
        assertTrue(ready.await(5,TimeUnit.SECONDS))
        compose.waitUntil(10000){result!!.style?.getLayer("user-marker")!=null}
        return result!!
    }
    private fun inject(manager:LocationManager,p:NavPoint,age:Long=0) {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
            latitude=p.lat;longitude=p.lon;accuracy=5f;time=System.currentTimeMillis()-age
            elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()-age*1000000
        })
    }
    @Suppress("DEPRECATION")
    @Test fun freshGpsCentersNativeCameraUpdatesMarkerAndRadarAndHonorsManualMovement() {
        grantLocation();shell("appops set com.spidertracker.app android:mock_location allow")
        val manager=compose.activity.getSystemService(LocationManager::class.java)
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
        try {
            val native=map()
            compose.runOnIdle {compose.activity.locationProvider=AndroidLocationProvider(compose.activity);compose.activity.tracker.locationDenied()}
            inject(manager,a,30000)
            val before=compose.activity.tracker.camera
            compose.onNodeWithTag("map-center").performClick()
            compose.onNodeWithTag("location-loading",useUnmergedTree=true).assertExists()
            SystemClock.sleep(600)
            compose.runOnIdle {assertTrue(compose.activity.tracker.locating);assertEquals(before,compose.activity.tracker.camera)}
            inject(manager,a)
            compose.waitUntil(8000){compose.activity.tracker.userFix?.lat==a.lat}
            compose.waitUntil(8000){var centered=false;compose.runOnIdle {centered=native.cameraPosition.target!!.distanceTo(LatLng(a.lat,a.lon))<10 && native.cameraPosition.zoom>15.9};centered}
            val radar=compose.onNodeWithTag("radar").fetchSemanticsNode().config[RadarSnapshotKey]
            compose.runOnIdle {
                assertEquals(RadarMode.Gps,radar.mode);assertEquals(a.lat,radar.originLat!!,0.00001)
                assertEquals(a.lon,radar.dots.single {it.kind==MarkerKind.User}.lon,0.00001)
                assertNotNull(native.style!!.getLayer("user-accuracy"))
            }
            val camera=compose.activity.tracker.camera
            compose.runOnIdle {native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(a.lat+.02,a.lon+.02),16.0));compose.activity.tracker.exploreRadar()}
            SystemClock.sleep(1100);val moved=NavPoint(a.lat+.0002,a.lon+.0002);inject(manager,moved)
            compose.waitUntil(5000){compose.activity.tracker.userFix?.lat==moved.lat}
            compose.runOnIdle {assertEquals(camera,compose.activity.tracker.camera);assertTrue(native.cameraPosition.target!!.distanceTo(LatLng(moved.lat,moved.lon))>1000)}
            compose.onNodeWithTag("map-center").performClick();SystemClock.sleep(500);inject(manager,moved)
            compose.waitUntil(6000){compose.activity.tracker.camera.lat==moved.lat && !compose.activity.tracker.locating}
            compose.waitUntil(6000){var matches=false;compose.runOnIdle {
                val points=native.style!!.getSourceAs<GeoJsonSource>("user")!!.querySourceFeatures(null).mapNotNull {it.geometry() as? Point}
                matches=points.any {kotlin.math.abs(it.latitude()-moved.lat)<.00001 && kotlin.math.abs(it.longitude()-moved.lon)<.00001}
            };matches}
            compose.onNodeWithTag("map-global").performClick()
            compose.runOnIdle {assertEquals(1.0,compose.activity.tracker.camera.zoom,0.0);assertFalse(compose.activity.tracker.radarGpsCentered)}
        } finally {
            compose.runOnIdle {compose.activity.tracker.cancelLocation()}
            manager.removeTestProvider(LocationManager.GPS_PROVIDER);shell("appops set com.spidertracker.app android:mock_location default")
        }
    }
    @Test fun searchDetailsManualOriginRealRouteAlternativesAndBack():Unit=runBlocking {
        val runtime=compose.activity.navigation;val prior=runtime.endpoint.value
        try {
            grantLocation()
            compose.runOnIdle {
                runtime.configure("http://10.0.2.2:8002")
                compose.activity.locationProvider=object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(a.lat,a.lon,5f,true)}
                compose.activity.geocoder=object:GeocodingProvider {
                    override val configured=true
                    override suspend fun search(query:String)=listOf(AddressResult("opera","Opera, Yerevan",b.lat,b.lon,"Opera","Yerevan","Armenia","theatre"))
                }
            }
            compose.onNodeWithTag("address-input").performTextReplacement("Opera")
            compose.onNodeWithTag("search-submit").performClick()
            compose.waitUntil(5000){compose.activity.tracker.searchResults.isNotEmpty()}
            compose.onNodeWithTag("search-result-0").performClick()
            compose.onNodeWithTag("place-details").assertIsDisplayed()
            assertEquals(NavigationState.Idle,runtime.controller.state.value.state)
            compose.onNodeWithTag("place-route").performClick()
            compose.waitUntil(30000){runtime.controller.state.value.route!=null || runtime.controller.state.value.state==NavigationState.Error}
            assertNotNull(runtime.controller.state.value.message,runtime.controller.state.value.route)
            compose.onNodeWithTag("route-from").assertExists();compose.onNodeWithTag("route-to").assertExists()
            assertTrue(runtime.controller.state.value.routes.size>1)
            compose.onNodeWithTag("route-alternative-1").performScrollTo().performClick()
            compose.runOnIdle {assertEquals(1,runtime.controller.state.value.selected)}
            compose.onNodeWithTag("mode-pedestrian").performScrollTo().performClick()
            compose.waitUntil(30000){!runtime.controller.state.value.loading && runtime.controller.state.value.request?.mode==TravelMode.Walking}
            assertNotNull(runtime.controller.state.value.route)
            compose.onNodeWithTag("route-from").performScrollTo().performClick()
            compose.onNodeWithTag("address-input").performTextReplacement("Origin")
            compose.onNodeWithTag("search-submit").performClick()
            compose.waitUntil(5000){compose.activity.tracker.searchResults.isNotEmpty()}
            compose.onNodeWithTag("search-result-0").performClick()
            compose.runOnIdle {assertNotNull(compose.activity.tracker.manualOrigin)}
            compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithTag("place-details").assertIsDisplayed()
            compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithTag("place-details").assertDoesNotExist()
            compose.onNodeWithTag("radar").assertIsDisplayed();compose.onNodeWithTag("search-component").assertIsDisplayed()
        } finally {
            compose.runOnIdle {runtime.controller.stop();compose.activity.tracker.clearDestination();runtime.endpoint.value=prior}
            compose.activity.getSharedPreferences("navigation",Context.MODE_PRIVATE).edit().putString("endpoint",prior).commit()
        }
    }
    @Test fun disabledLocationServicesReportErrorWithoutMovingCamera() {
        grantLocation();val manager=compose.activity.getSystemService(LocationManager::class.java)
        compose.waitForIdle()
        val wasEnabled=androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)
        try {
            shell("cmd location set-location-enabled false")
            compose.runOnIdle {compose.activity.locationProvider=AndroidLocationProvider(compose.activity)}
            val before=compose.activity.tracker.camera
            compose.onNodeWithTag("map-center").performClick()
            compose.waitUntil(5000){compose.activity.tracker.toast.contains("SERVICES ARE OFF")}
            compose.runOnIdle {assertEquals(before,compose.activity.tracker.camera);assertNull(compose.activity.tracker.userFix)}
        } finally {shell("cmd location set-location-enabled $wasEnabled")}
    }
    @Test fun locationButtonRequestsPermissionAndDenialIsUseful() {
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,androidx.core.content.ContextCompat.checkSelfPermission(compose.activity,Manifest.permission.ACCESS_COARSE_LOCATION))
        compose.onNodeWithTag("map-center").performClick()
        fun deny(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(node==null)return false
            if(node.viewIdResourceName?.endsWith(":id/permission_deny_button")==true)return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            return (0 until node.childCount).any {deny(node.getChild(it))}
        }
        var denied=false
        compose.waitUntil(8000){if(!denied)denied=deny(automation.rootInActiveWindow);denied}
        compose.waitUntil(5000){compose.activity.tracker.toast.contains("DENIED") || compose.activity.tracker.toast.contains("APP SETTINGS")}
        compose.runOnIdle {assertNull(compose.activity.tracker.userFix);assertEquals(1.0,compose.activity.tracker.camera.zoom,0.0)}
    }
    @Suppress("DEPRECATION")
    @Test fun approximatePermissionUsesFreshNetworkLocation() {
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,androidx.core.content.ContextCompat.checkSelfPermission(compose.activity,Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,androidx.core.content.ContextCompat.checkSelfPermission(compose.activity,Manifest.permission.ACCESS_COARSE_LOCATION))
        shell("appops set com.spidertracker.app android:mock_location allow")
        val manager=compose.activity.getSystemService(LocationManager::class.java)
        manager.addTestProvider(LocationManager.NETWORK_PROVIDER,true,false,true,false,false,false,false,Criteria.POWER_LOW,Criteria.ACCURACY_COARSE)
        manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER,true)
        try {
            compose.runOnIdle {compose.activity.locationProvider=AndroidLocationProvider(compose.activity)}
            compose.onNodeWithTag("map-center").performClick()
            SystemClock.sleep(500)
            manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER,Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude=a.lat;longitude=a.lon;accuracy=1200f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            })
            compose.waitUntil(8000){compose.activity.tracker.userFix!=null && !compose.activity.tracker.locating}
            compose.runOnIdle {
                val fix=compose.activity.tracker.userFix!!
                assertTrue(fix.accuracy>=1000);assertTrue(validCoordinates(fix.lat,fix.lon));assertEquals(fix.lat,compose.activity.tracker.camera.lat,0.0)
                assertEquals(16.0,compose.activity.tracker.camera.zoom,0.0)
            }
        } finally {
            compose.runOnIdle {compose.activity.tracker.cancelLocation()}
            manager.removeTestProvider(LocationManager.NETWORK_PROVIDER);shell("appops set com.spidertracker.app android:mock_location default")
        }
    }
}
