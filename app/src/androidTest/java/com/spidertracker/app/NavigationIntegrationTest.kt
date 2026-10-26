package com.spidertracker.app

import android.Manifest
import android.content.Context
import android.location.*
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import kotlin.concurrent.thread

/** Local service uses actual Armenia OSM tiles. No public routing demo or fabricated route fixture. */
class NavigationIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val a=NavPoint(40.1772,44.5126)
    private val b=NavPoint(40.1913,44.5155)
    private val provider=ValhallaRoutingProvider({"http://10.0.2.2:8002"},true)

    @Test fun changingRoutingEndpointCancelsThePreviousRequest()=runBlocking {
        val runtime=compose.activity.navigation
        val original=runtime.endpoint.value
        try {
            withContext(Dispatchers.Main) {
                runtime.configure("http://10.0.2.2:8002")
                runtime.controller.preview(RouteRequest(a,b,TravelMode.Driving))
                runtime.configure("http://127.0.0.1:8002")
            }
            delay(500)
            assertEquals(NavigationState.Idle,runtime.controller.state.value.state)
            assertTrue(runtime.controller.state.value.routes.isEmpty())
        } finally {
            withContext(Dispatchers.Main){runtime.controller.stop();runtime.endpoint.value=original}
            compose.activity.getSharedPreferences("navigation",Context.MODE_PRIVATE).edit().putString("endpoint",original).commit()
        }
    }

    @Test fun realRoutesAllModesAlternativesAndStrictParsing()=runBlocking {
        TravelMode.entries.forEach {mode->
            val routes=provider.routes(RouteRequest(a,b,mode))
            assertTrue(routes.isNotEmpty());val route=routes.first()
            assertTrue(route.points.size>10);assertTrue(route.meters>a.distance(b));assertTrue(route.maneuvers.size>2)
            assertTrue(route.maneuvers.all {it.instruction.isNotBlank()});assertTrue(route.points.last().distance(b)<200)
            if(mode==TravelMode.Driving)assertTrue(routes.size>1)
        }
        assertThrows(Exception::class.java){parseValhalla("{}")}
        assertThrows(Exception::class.java){decodePolyline6("???")}
        assertThrows(Exception::class.java){decodePolyline6("~".repeat(40))}
        assertThrows(RoutingFailure::class.java){parseValhalla("""{"error_code":442,"error":"No path"}""")}
        val body=JSONObject(valhallaRequest(RouteRequest(a,b,TravelMode.Driving)))
        assertFalse(body.has("date_time"));assertEquals("freeflow",body.getJSONObject("costing_options").getJSONObject("auto").getJSONArray("speed_types").getString(0))
        assertFalse(validRoutingEndpoint("https://user:secret@example.com",true));assertFalse(validRoutingEndpoint("http://example.com",true))
        assertTrue(validRoutingEndpoint("http://10.0.2.2:8002",true));assertFalse(validRoutingEndpoint("http://10.0.2.2:8002",false))
        assertTrue(runCatching {provider.routes(RouteRequest(NavPoint(0.0,0.0),b,TravelMode.Driving))}.exceptionOrNull() is RoutingFailure)
    }

    @Test fun actualBackendRecalculationAndSimulatedProgress()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var time=SystemClock.elapsedRealtime();var calls=0
        val counting=object:RoutingProvider {override suspend fun routes(request:RouteRequest):List<NavRoute> {calls++;return provider.routes(request)}}
        val controller=NavigationController(scope,counting,{time})
        try {
            withContext(Dispatchers.Main){controller.preview(RouteRequest(a,b,TravelMode.Driving))}
            await {controller.state.value.route!=null}
            val route=controller.state.value.route!!
            withContext(Dispatchers.Main){controller.start();controller.accept(NavFix(route.points.first(),5.0,time,simulated=true))}
            val midway=route.points[route.points.size/2]
            time+=((route.points.first().distance(midway)/10)*1000).toLong().coerceAtLeast(31000)
            withContext(Dispatchers.Main){controller.accept(NavFix(midway,5.0,time,simulated=true))}
            assertTrue(controller.state.value.along>100);assertTrue(controller.state.value.remaining<route.meters)
            val deviated=NavPoint(40.1826,44.5218)
            assertTrue(matchRoute(route,deviated).distance>90)
            repeat(3){time+=15000;withContext(Dispatchers.Main){controller.accept(NavFix(deviated,5.0,time,simulated=true))}}
            await {calls>=2 && !controller.state.value.loading}
            assertEquals(NavigationState.Navigating,controller.state.value.state)
            assertEquals(deviated,controller.state.value.request!!.origin)
            assertTrue(controller.state.value.routeRevision>=2)
            assertTrue(controller.state.value.route!!.points.first().distance(deviated)<200)
            withContext(Dispatchers.Main){controller.stop()};assertEquals(NavigationState.Idle,controller.state.value.state)
        } finally {scope.cancel()}
    }
    @Test fun realHttpClientReportsNoRoadNoRouteUnavailableAndInvalidResponses()=runBlocking {
        val cases=listOf(400 to """{"error_code":171}""",400 to """{"error_code":442}""",503 to "{}",200 to "broken")
        val expected=listOf("NO ROUTABLE ROAD","NO AVAILABLE ROUTE","SERVICE UNAVAILABLE","INVALID ROUTING RESPONSE")
        cases.forEachIndexed {index,(code,body)->
            ServerSocket(0).use {server->
                val worker=thread {
                    server.accept().use {socket->
                        val reader=socket.getInputStream().bufferedReader()
                        while(true){val line=reader.readLine() ?: break;if(line.isEmpty())break}
                        socket.getOutputStream().write("HTTP/1.1 $code Response\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    }
                }
                val failure=runCatching {ValhallaRoutingProvider({"http://127.0.0.1:${server.localPort}"},true).routes(RouteRequest(a,b,TravelMode.Driving))}.exceptionOrNull()
                worker.join(2000);assertTrue(failure is RoutingFailure);assertTrue(failure!!.message!!.contains(expected[index]))
            }
        }
    }

    @Test fun foregroundGpsHudRecenterStopAndActivityRecreation()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission("com.spidertracker.app",Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission("com.spidertracker.app",Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission("com.spidertracker.app",Manifest.permission.POST_NOTIFICATIONS)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set com.spidertracker.app android:mock_location allow")).use {it.readBytes()}
        val manager=compose.activity.getSystemService(LocationManager::class.java)
        @Suppress("DEPRECATION")
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val runtime=compose.activity.navigation
        val priorEndpoint=runtime.endpoint.value
        try {
            compose.runOnIdle {
                runtime.configure("http://10.0.2.2:8002")
                compose.activity.tracker.chooseMapDestination(b.lat,b.lon)
                compose.activity.tracker.showRoutePlanner()
            }
            inject(manager,a)
            compose.runOnIdle {compose.activity.tracker.locate(object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(a.lat,a.lon,5f,true)})}
            compose.waitUntil(30000){val s=runtime.controller.state.value;!s.loading && (s.route!=null || s.state==NavigationState.Error)}
            assertNotNull(runtime.controller.state.value.message,runtime.controller.state.value.route)
            if(runtime.controller.state.value.routes.size>1)compose.onNodeWithTag("route-alternative-1").performScrollTo().performClick()
            val selectedRoute=runtime.controller.state.value.route
            compose.onNodeWithTag("navigation-start").performScrollTo().performClick()
            compose.waitUntil(10000){runtime.controller.state.value.active}
            assertSame(selectedRoute,runtime.controller.state.value.route)
            compose.onNodeWithTag("next-maneuver").assertExists()
            compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithTag("stop-navigation-confirmation").assertIsDisplayed()
            compose.onNodeWithText("KEEP NAVIGATING").performClick()
            val first=runtime.controller.state.value.route!!.points.first()
            inject(manager,first)
            compose.waitUntil(10000){runtime.controller.state.value.gpsReliable}
            compose.onNodeWithTag("next-maneuver").assertExists()
            val route=runtime.controller.state.value.route!!
            val map=nativeMap()
            compose.runOnIdle {
                val rendered=map.style!!.getSourceAs<GeoJsonSource>("navigation-routes")!!.querySourceFeatures(null)
                    .filter {it.getBooleanProperty("selected")}.flatMap {feature->when(val geometry=feature.geometry()) {
                        is LineString->geometry.coordinates();is MultiLineString->geometry.coordinates().flatten();else->emptyList()
                    }}
                // Native source queries return clipped and simplified tile geometry.
                assertTrue(rendered.size>=2)
                assertTrue(rendered.all {matchRoute(route,NavPoint(it.latitude(),it.longitude())).distance<15})
                assertNotNull(map.style!!.getLayer("building-footprints"));assertNotNull(map.style!!.getLayer("surface-motorway"))
            }
            val index=route.cumulative.indexOfFirst {it>=30}.coerceAtLeast(1)
            // Give the platform time to deliver a second mock GPS fix.
            delay(2000);inject(manager,route.points[index])
            compose.waitUntil(10000){runtime.controller.state.value.along>10}
            val bounds=compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
            val x=bounds.left+bounds.width*.65f;val y=bounds.top+bounds.height*.65f
            val down=SystemClock.uptimeMillis()
            fun touch(action:Int,x:Float) {
                val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                try {instrumentation.uiAutomation.injectInputEvent(event,true)} finally {event.recycle()}
            }
            touch(android.view.MotionEvent.ACTION_DOWN,x)
            repeat(12){i->SystemClock.sleep(20);touch(android.view.MotionEvent.ACTION_MOVE,x-(i+1)*12)}
            touch(android.view.MotionEvent.ACTION_UP,x-144)
            compose.waitUntil(5000){!runtime.controller.state.value.following}
            compose.onNodeWithTag("map-center").performClick()
            compose.runOnIdle {assertFalse(runtime.controller.state.value.following)}
            delay(1100);inject(manager,route.points[index])
            compose.waitUntil(8000){runtime.controller.state.value.following}
            compose.runOnIdle {assertTrue(runtime.controller.state.value.following)}
            delay(1200)
            instrumentation.uiAutomation.takeScreenshot().let {bitmap->
                java.io.File(compose.activity.getExternalFilesDir(null),"phase4-navigation.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            val before=runtime.controller.state.value.along
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000){compose.activity.navigation.controller.state.value.active}
            compose.runOnIdle {assertTrue(runtime.controller.state.value.along>=before)}
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            delay(2000)
            val nextIndex=route.cumulative.indexOfFirst {it>=before+50}.coerceAtLeast(index+1)
            inject(manager,route.points[nextIndex])
            await {runtime.controller.state.value.along>before+20}
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.onNodeWithTag("navigation-mute").performScrollTo().performClick()
            compose.runOnIdle {assertTrue(runtime.controller.state.value.muted)}
            compose.onNodeWithTag("navigation-stop").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-navigation-stop").performClick()
            compose.waitUntil(5000){runtime.controller.state.value.state==NavigationState.Idle}
            compose.runOnIdle {assertEquals(route.points[nextIndex].lat,compose.activity.tracker.userFix!!.lat,.00001)}
            compose.onNodeWithTag("search-component").assertExists()
            compose.onNodeWithTag("map-marker-sprites").assertExists()
            Unit
        } finally {
            withContext(Dispatchers.Main){runtime.controller.stop()}
            manager.removeTestProvider(LocationManager.GPS_PROVIDER)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set com.spidertracker.app android:mock_location default")).use {it.readBytes()}
            runtime.endpoint.value=priorEndpoint
        }
    }

    @Test fun androidSpeechEngineCanAcceptRealManeuverText()=runBlocking {
        val route=provider.routes(RouteRequest(a,b,TravelMode.Driving)).first()
        val initialized=CountDownLatch(1)
        var result=android.speech.tts.TextToSpeech.ERROR
        lateinit var speech:android.speech.tts.TextToSpeech
        withContext(Dispatchers.Main) {speech=android.speech.tts.TextToSpeech(compose.activity){result=it;initialized.countDown()}}
        try {
            assertTrue("TTS initialization timed out",initialized.await(15,TimeUnit.SECONDS))
            assertEquals("Emulator TTS engine unavailable",android.speech.tts.TextToSpeech.SUCCESS,result)
            assertTrue(speech.isLanguageAvailable(java.util.Locale.US)>=0)
            val completed=CountDownLatch(1);var playbackError=false
            speech.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id:String?)=Unit
                override fun onDone(id:String?) {completed.countDown()}
                @Deprecated("legacy callback") override fun onError(id:String?) {playbackError=true;completed.countDown()}
            })
            speech.language=java.util.Locale.US
            assertEquals(0,speech.speak(route.maneuvers.first().verbal,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"real-maneuver"))
            assertTrue("TTS playback did not complete",completed.await(20,TimeUnit.SECONDS));assertFalse(playbackError)
        } finally {speech.stop();speech.shutdown()}
    }
    private fun inject(manager:LocationManager,point:NavPoint) {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
            latitude=point.lat;longitude=point.lon;accuracy=5f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos();speed=5f;bearing=0f
        })
    }
    private fun nativeMap():MapLibreMap {
        fun find(view:View):MapView?=if(view is MapView)view else if(view is ViewGroup)(0 until view.childCount).firstNotNullOfOrNull {find(view.getChildAt(it))} else null
        var map:MapLibreMap?=null
        val latch=CountDownLatch(1)
        compose.runOnIdle {find(compose.activity.window.decorView)!!.getMapAsync {map=it;latch.countDown()}}
        assertTrue(latch.await(5,TimeUnit.SECONDS));return map!!
    }
    private suspend fun await(condition:()->Boolean) {withTimeout(30000){while(!condition())delay(50)}}
}
