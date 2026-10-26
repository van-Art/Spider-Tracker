package com.spidertracker.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3StateTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun mainDispatcher(){Dispatchers.setMain(dispatcher)}
    @After fun resetDispatcher(){Dispatchers.resetMain()}
    private fun provider(configured:Boolean=true,search:suspend (String)->List<AddressResult>)=object:GeocodingProvider {
        override val configured=configured
        override suspend fun search(query:String)=search(query)
    }
    @Test fun searchRequiresSubmissionAndSelectionMovesCamera()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());var calls=0
        val result=AddressResult("test","London test fixture",51.5,-.12)
        val service=provider {calls++;listOf(result)}
        vm.editSearch("London");runCurrent();assertEquals(0,calls)
        vm.search(service);assertEquals(SearchState.Loading,vm.searchState);runCurrent()
        assertEquals(1,calls);assertEquals(listOf(result),vm.searchResults)
        vm.chooseDestination(result.copy(id="forged"));assertNull(vm.destination)
        vm.chooseDestination(result);assertEquals(result,vm.destination)
        assertEquals(51.5,vm.camera.lat,0.0);assertEquals(13.0,vm.camera.zoom,0.0)
        assertFalse(vm.searchVisible)
    }
    @Test fun editsCancelStaleSearchAndUnavailableNeverSucceeds()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.editSearch("old");vm.search(provider {delay(500);listOf(AddressResult("old","Old",1.0,2.0))});runCurrent()
        vm.editSearch("new");advanceUntilIdle();assertEquals(SearchState.Idle,vm.searchState);assertTrue(vm.searchResults.isEmpty())
        vm.search(provider(false){error("must not run")});assertEquals(SearchState.Unavailable,vm.searchState)
        vm.search(provider {emptyList()});runCurrent();assertEquals(SearchState.Empty,vm.searchState)
        vm.search(provider {throw SearchUnavailable("OFFLINE")});runCurrent();assertEquals("OFFLINE",vm.searchMessage)
        vm.editSearch("a");vm.search(provider {error("must not run")});assertEquals(SearchState.Error,vm.searchState)
        assertNull(vm.destination)
    }
    @Test fun gpsFixAndGlobalCancellationCannotJumpToAnOldRequest()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix {delay(500);return DeviceFix(40.1,44.5,10f,true)}})
        runCurrent();assertTrue(vm.locating);vm.globalView();advanceUntilIdle()
        assertEquals(1.0,vm.camera.zoom,0.0);assertFalse(vm.locating)
        vm.observeZoom(15.0)
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(40.1,44.5,10f,true)})
        runCurrent();assertEquals(40.1,vm.camera.lat,0.0);assertEquals(16.0,vm.camera.zoom,0.0)
        assertTrue(vm.toast.startsWith("TEST LOCATION"));assertFalse(vm.locating)
        assertTrue(vm.radarGpsCentered)
        vm.globalView();assertFalse(vm.radarGpsCentered);assertNotNull(vm.userFix)
    }
    @Test fun gpsTimeoutDisabledServicesAndDenialAreExplicit()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix {awaitCancellation()}})
        advanceUntilIdle();assertFalse(vm.locating);assertTrue(vm.toast.contains("TIMED OUT"))
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix=throw LocationUnavailable("LOCATION SERVICES ARE OFF")})
        runCurrent();assertEquals("LOCATION SERVICES ARE OFF",vm.toast)
        vm.locationDenied();assertTrue(vm.toast.contains("DENIED"))
        vm.locationDenied(true);assertTrue(vm.toast.contains("APP SETTINGS"))
        assertEquals(1.0,vm.camera.zoom,0.0)
    }
    @Test fun failedLocationRefreshClearsPreviousRadarOrigin()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(40.1,44.5,10f,true)})
        runCurrent();assertNotNull(vm.userFix)
        val camera=vm.camera
        vm.locate(object:DeviceLocationProvider {override suspend fun currentFix():DeviceFix=throw LocationUnavailable("LOCATION SERVICES ARE OFF")})
        runCurrent();assertNull(vm.userFix);assertEquals(camera,vm.camera)
        assertEquals(RadarMode.Map,geographicRadar(emptyList(),vm.userFix,0.0,0.0).mode)
    }
    @Test fun coordinateAndFreshnessValidationRejectInvalidDeviceData() {
        assertTrue(usableFix(40.0,44.0,10f,12000))
        assertFalse(usableFix(40.0,44.0,10f,12001))
        assertFalse(usableFix(40.0,44.0,10f,-1))
        assertFalse(usableFix(Double.NaN,44.0,10f,1))
        assertFalse(usableFix(91.0,44.0,10f,1))
        assertFalse(usableFix(40.0,181.0,10f,1))
        assertFalse(usableFix(40.0,44.0,-1f,1))
    }
    @Test fun spriteExtractionUsesTransparentGapsAndCommonPadding() {
        val frames=extractSpriteFrames(booleanArrayOf(false,true,true,false,false,true,true,true,false),43)
        assertEquals(listOf(SpriteFrame(0,5,43),SpriteFrame(4,5,43)),frames)
        assertThrows(IllegalArgumentException::class.java){extractSpriteFrames(BooleanArray(10),43)}
    }
}
