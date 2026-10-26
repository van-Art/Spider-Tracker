package com.spidertracker.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTest {
    private val points=(0..10).map {NavPoint(0.0,it*.001)}
    private val route=NavRoute(points,1112.0,120.0,listOf(
        Maneuver(0,5,"Head east","First",60.0,1),Maneuver(5,10,"Turn right","Second",60.0,10),Maneuver(10,10,"Arrive","",0.0,4)))
    private val request=RouteRequest(points.first(),points.last(),TravelMode.Driving)
    private fun provider(block:suspend (RouteRequest)->List<NavRoute>)=object:RoutingProvider {override suspend fun routes(request:RouteRequest)=block(request)}
    @Test fun projectionUsesSegmentsAndHandlesDateline() {
        val match=matchRoute(route,NavPoint(.0001,.0025))
        assertEquals(278.0,match.along,2.0);assertEquals(11.1,match.distance,1.0)
        val crossing=NavRoute(listOf(NavPoint(0.0,179.99),NavPoint(0.0,-179.99)),2224.0,200.0,listOf(Maneuver(0,1,"Continue","",200.0,1)))
        assertTrue(matchRoute(crossing,NavPoint(0.0,180.0)).distance<1)
    }
    @Test fun routeValidationRejectsInvalidManeuvers() {
        assertThrows(IllegalArgumentException::class.java){route.copy(meters=Double.NaN)}
        assertThrows(IllegalArgumentException::class.java){route.copy(maneuvers=listOf(Maneuver(99,100,"Invalid","",1.0,1)))}
    }
    @Test fun previewAlternativesModesAndStop()=runTest {
        val calls=mutableListOf<RouteRequest>()
        val c=NavigationController(backgroundScope,provider {calls.add(it);listOf(route,route.copy(seconds=150.0))},{testScheduler.currentTime})
        TravelMode.entries.forEach {mode->c.preview(request.copy(mode=mode));runCurrent();assertEquals(NavigationState.RoutePreview,c.state.value.state)}
        assertEquals(TravelMode.entries,calls.map {it.mode});c.select(1);assertEquals(150.0,c.state.value.seconds,0.0)
        c.start();assertTrue(c.state.value.active);c.follow(false);assertFalse(c.state.value.following);c.follow(true)
        c.mute();assertTrue(c.state.value.muted);c.stop();assertEquals(NavigationState.Idle,c.state.value.state);assertTrue(c.state.value.routes.isEmpty())
    }
    @Test fun progressManeuversDistanceAndEtaAdvanceWithReliableFixes()=runTest {
        val c=NavigationController(backgroundScope,provider {listOf(route)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start()
        c.accept(NavFix(points[0],5.0,currentTime))
        assertEquals(1,c.state.value.maneuver)
        advanceTimeBy(10000);c.accept(NavFix(points[6],5.0,currentTime))
        assertEquals(2,c.state.value.maneuver);assertTrue(c.state.value.remaining<500);assertTrue(c.state.value.seconds<60)
        val before=c.state.value.along
        advanceTimeBy(1000);c.accept(NavFix(NavPoint(1.0,1.0),5.0,currentTime))
        assertEquals(before,c.state.value.along,0.0);assertEquals("GPS JUMP IGNORED",c.state.value.message)
    }
    @Test fun noiseStaleLossAndOutOfOrderDoNotAdvance()=runTest {
        var calls=0
        val c=NavigationController(backgroundScope,provider {calls++;listOf(route)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start();c.accept(NavFix(points[0],5.0,currentTime))
        advanceTimeBy(20000);c.accept(NavFix(points[4],500.0,currentTime));c.tick()
        assertFalse(c.state.value.gpsReliable);assertEquals(0.0,c.state.value.along,0.0)
        c.accept(NavFix(points[5],5.0,0));assertEquals(0.0,c.state.value.along,0.0)
        assertEquals(1,calls)
    }
    @Test fun sustainedDeviationMakesRealProviderRequestAndFailurePreservesRoute()=runTest {
        var calls=0;val requests=mutableListOf<RouteRequest>()
        val c=NavigationController(backgroundScope,provider {requests.add(it);calls++;if(calls>1)throw RoutingFailure("SERVICE DOWN");listOf(route)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start();c.accept(NavFix(points[0],5.0,currentTime))
        advanceTimeBy(31000)
        repeat(3) {c.accept(NavFix(NavPoint(.001,.001),5.0,currentTime));advanceTimeBy(4500)}
        runCurrent();assertEquals(2,calls);assertEquals(NavPoint(.001,.001),requests.last().origin)
        assertEquals(route,c.state.value.route);assertEquals(NavigationState.Navigating,c.state.value.state)
        assertEquals("SERVICE DOWN",c.state.value.message)
        repeat(3){c.accept(NavFix(NavPoint(.001,.001),5.0,currentTime));advanceTimeBy(4500)}
        assertEquals(2,calls)
    }
    @Test fun successfulReroutingReplacesRouteAndResetsProgress()=runTest {
        var calls=0
        val replacement=route.copy(points=points.map {NavPoint(.001,it.lon)})
        val c=NavigationController(backgroundScope,provider {calls++;listOf(if(calls==1)route else replacement)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start();advanceTimeBy(31000)
        repeat(3){c.accept(NavFix(NavPoint(.001,.001),5.0,currentTime));advanceTimeBy(4500)}
        runCurrent();assertEquals(2,calls);assertEquals(replacement,c.state.value.route);assertEquals(NavigationState.Navigating,c.state.value.state)
    }
    @Test fun inaccurateOrIsolatedDeviationDoesNotReroute()=runTest {
        var calls=0
        val c=NavigationController(backgroundScope,provider {calls++;listOf(route)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start();advanceTimeBy(40000)
        c.accept(NavFix(NavPoint(.001,.001),500.0,currentTime));advanceTimeBy(10000)
        c.accept(NavFix(NavPoint(.001,.001),5.0,currentTime));advanceTimeBy(2000)
        c.accept(NavFix(points[1],5.0,currentTime));advanceTimeBy(10000);runCurrent()
        assertEquals(1,calls)
    }
    @Test fun newestRequestWinsEvenIfOldProviderIgnoresCancellation()=runTest {
        val c=NavigationController(backgroundScope,provider {r->if(r.mode==TravelMode.Driving)withContext(NonCancellable){delay(1000)};listOf(route.copy(seconds=if(r.mode==TravelMode.Driving)120.0 else 300.0))},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.preview(request.copy(mode=TravelMode.Walking));runCurrent()
        advanceTimeBy(2000);runCurrent();assertEquals(300.0,c.state.value.route!!.seconds,0.0)
    }
    @Test fun emptyAndFailedRoutesNeverBecomeSuccessfulPreview()=runTest {
        val c=NavigationController(backgroundScope,provider {emptyList()},{testScheduler.currentTime})
        c.preview(request);runCurrent();assertEquals(NavigationState.Error,c.state.value.state);assertNull(c.state.value.route)
        c.start();assertFalse(c.state.value.active)
    }
    @Test fun arrivalRequiresSeveralReliableEndpointFixesAndPauseStopsProgress()=runTest {
        val c=NavigationController(backgroundScope,provider {listOf(route)},{testScheduler.currentTime})
        c.preview(request);runCurrent();c.start();c.accept(NavFix(points.last(),5.0,currentTime))
        assertEquals(NavigationState.Navigating,c.state.value.state)
        repeat(2){advanceTimeBy(2000);c.accept(NavFix(points.last(),5.0,currentTime))}
        assertEquals(NavigationState.Arrived,c.state.value.state);assertEquals(0.0,c.state.value.remaining,0.0)
        c.pause("PERMISSION DENIED");c.accept(NavFix(points[0],5.0,currentTime));assertEquals(NavigationState.Paused,c.state.value.state)
    }
    @Test fun voiceOnlyUsesBackendTextAndDeduplicatesStages() {
        val speech=ManeuverSpeech()
        val s=NavigationSnapshot(state=NavigationState.Navigating,routes=listOf(route),request=request,maneuver=1,toManeuver=200.0,gpsReliable=true)
        val cue=speech.cue(s)!!;assertEquals("Turn right",cue.text);speech.delivered(cue)
        assertNull(speech.cue(s));assertNotNull(speech.cue(s.copy(toManeuver=20.0)))
        assertNull(speech.cue(s.copy(muted=true)));assertNull(speech.cue(s.copy(gpsReliable=false)))
        assertNull(speech.cue(s.copy(state=NavigationState.Paused)))
        assertNotNull(speech.cue(s.copy(routeRevision=2)))
    }
    @Test fun routeOriginLookupDoesNotOverrideTheDestinationCamera()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm=TrackerViewModel(androidx.lifecycle.SavedStateHandle())
            vm.chooseMapDestination(40.19,44.51)
            val camera=vm.camera
            val gps=object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(40.17,44.50,5f,true)}
            vm.locate(gps,centerCamera=false);runCurrent()
            assertEquals(camera,vm.camera);assertEquals(40.17,vm.userFix!!.lat,0.0)
            vm.locate(gps);runCurrent();assertEquals(40.17,vm.camera.lat,0.0)
        } finally {Dispatchers.resetMain()}
    }
}
