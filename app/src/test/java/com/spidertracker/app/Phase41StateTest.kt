package com.spidertracker.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase41StateTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun teardown(){Dispatchers.resetMain()}
    private val destination=AddressResult("to","Opera, Yerevan",40.1913,44.5155,"Opera","Yerevan","Armenia","theatre","Tumanyan Street")
    private suspend fun select(vm:TrackerViewModel,result:AddressResult) {
        vm.editSearch("address")
        vm.search(object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=listOf(result)})
        yield()
        vm.chooseDestination(result)
    }
    @Test fun selectionDetailsPlannerAndBackPreserveThePlace()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle())
        select(vm,destination)
        assertEquals(PlaceUiState.PlaceDetails,vm.uiState)
        assertEquals(destination,vm.destination)
        assertEquals(40.1913,vm.camera.lat,0.0)
        vm.showRoutePlanner();assertEquals(PlaceUiState.RoutePlanning,vm.uiState)
        vm.routeBack();assertEquals(PlaceUiState.PlaceDetails,vm.uiState)
        assertEquals(destination,vm.destination)
        vm.clearDestination();assertEquals(PlaceUiState.Map,vm.uiState)
    }
    @Test fun manualOriginAndChangedDestinationUseSearchResults()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());select(vm,destination);vm.showRoutePlanner()
        val origin=AddressResult("from","Republic Square",40.1772,44.5126)
        vm.searchEndpoint(RouteEndpoint.Origin);assertEquals(PlaceUiState.Search,vm.uiState)
        select(vm,origin);assertEquals(origin,vm.manualOrigin);assertEquals(destination,vm.destination)
        assertEquals(PlaceUiState.RoutePlanning,vm.uiState)
        vm.searchEndpoint(RouteEndpoint.Destination);select(vm,origin)
        assertEquals(origin,vm.destination);assertEquals(PlaceUiState.RoutePlanning,vm.uiState)
        vm.gpsOrigin();assertNull(vm.manualOrigin)
    }
    @Test fun markerSelectionRetainsOnlySuppliedMetadata() {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.navigateToPlace(LayerMarker("place",MarkerKind.Place,40.19,44.51,"Museum","Museum",source="OSM",locality="Yerevan"))
        assertEquals(PlaceUiState.PlaceDetails,vm.uiState)
        assertEquals("MUSEUMS",vm.destination!!.category);assertEquals("Yerevan",vm.destination!!.locality)
        assertNull(vm.destination!!.address);assertNull(vm.destination!!.country)
    }
    @Test fun locationUpdatesMoveDotWithoutRepeatedCameraMovementAndStopAfterTwentySeconds()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());val camera=vm.camera
        var closed=false
        val provider=object:DeviceLocationProvider {
            override suspend fun currentFix():DeviceFix=error("stream expected")
            override fun updates()=flow {
                try {
                    delay(1000);emit(DeviceFix(40.17,44.50,5f,true))
                    delay(1000);emit(DeviceFix(40.171,44.501,7f,true))
                    awaitCancellation()
                } finally {closed=true}
            }
        }
        vm.locate(provider);runCurrent();assertTrue(vm.locating);assertEquals(camera,vm.camera)
        advanceTimeBy(1001);runCurrent();assertFalse(vm.locating);assertEquals(16.0,vm.camera.zoom,0.0)
        val centered=vm.camera
        vm.exploreRadar();advanceTimeBy(1000);runCurrent()
        assertEquals(40.171,vm.userFix!!.lat,0.0);assertEquals(centered,vm.camera);assertFalse(vm.radarGpsCentered)
        advanceUntilIdle();assertTrue(closed);assertFalse(vm.toast.contains("TIMED OUT"))
    }
    @Test fun cachedAndFutureLocationsCannotSatisfyFreshRequest() {
        assertFalse(freshRequestFix(999,1000,1100))
        assertFalse(freshRequestFix(1200,1000,1100))
        assertFalse(freshRequestFix(1000,1000,13001))
        assertTrue(freshRequestFix(1001,1000,1100))
    }
    @Test fun gestureWhileWaitingCancelsOnlyAutomaticCameraMovement()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());val camera=vm.camera
        var followed=false
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix {delay(1000);return DeviceFix(40.0,44.0,5f)}},onCentered={followed=true})
        runCurrent();vm.exploreRadar();advanceUntilIdle()
        assertNotNull(vm.userFix);assertEquals(camera,vm.camera);assertFalse(vm.radarGpsCentered);assertFalse(followed)
    }
}
