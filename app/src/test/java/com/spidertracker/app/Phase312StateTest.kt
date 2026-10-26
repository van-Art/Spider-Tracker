package com.spidertracker.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase312StateTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun start(){Dispatchers.setMain(dispatcher)}
    @After fun end(){Dispatchers.resetMain()}
    private fun provider(block:suspend (String)->List<AddressResult>)=object:GeocodingProvider {
        override val configured=true
        override suspend fun search(query:String)=block(query)
    }
    @Test fun sightingSelectionUsesObservedZoomAndExplicitFocusStillRecenters() {
        val vm=TrackerViewModel(SavedStateHandle())
        val initial=vm.camera
        vm.observeZoom(15.0)
        vm.selectPin(vm.pins.first())
        assertEquals(initial,vm.camera)
        assertNotNull(vm.selected)
        vm.selectPin(vm.pins.first(),true)
        assertEquals(initial.revision+1,vm.camera.revision)
        vm.observeZoom(2.0)
        vm.selectPin(vm.pins.last())
        assertEquals(vm.pins.last().lat,vm.camera.lat,0.0)
    }
    @Test fun autocompleteDebouncesRequiresThreeAndDeduplicatesQueries()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());val calls=mutableListOf<String>()
        val result=AddressResult("test","New York",40.7,-74.0)
        val p=provider {calls.add(it);listOf(result)}
        vm.editSearch("Ne",p);advanceTimeBy(1000);runCurrent();assertTrue(calls.isEmpty())
        vm.editSearch("New",p);advanceTimeBy(300)
        vm.editSearch("New York",p);advanceTimeBy(424);runCurrent();assertTrue(calls.isEmpty())
        advanceTimeBy(1);runCurrent();assertEquals(listOf("New York"),calls)
        vm.editSearch(" NEW   YORK ",p);advanceTimeBy(1000);runCurrent();assertEquals(1,calls.size)
        vm.search(p);runCurrent();assertEquals(1,calls.size)
        assertEquals(result,vm.searchResults.single())
    }
    @Test fun cancellationRejectsEvenAnUncooperativeStaleResult()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());var cancelled=false
        val old=provider {try {withContext(NonCancellable){delay(1000)}}finally {cancelled=true};listOf(AddressResult("old","Old",1.0,1.0))}
        val current=AddressResult("current","Current",2.0,2.0)
        vm.editSearch("Old",old);advanceTimeBy(425);runCurrent()
        vm.editSearch("Current",provider {listOf(current)});advanceUntilIdle()
        assertTrue(cancelled);assertEquals(listOf(current),vm.searchResults)
    }
    @Test fun dismissalAndSelectionCannotStartSearchFromTheSelectedLabel()=runTest(dispatcher) {
        val saved=SavedStateHandle();val vm=TrackerViewModel(saved);var calls=0
        val result=AddressResult("test","Selected place",3.0,4.0)
        val p=provider {calls++;listOf(result)}
        vm.editSearch("Typed address",p);vm.hideSearch();advanceUntilIdle();assertEquals(0,calls);assertFalse(vm.searchVisible)
        vm.editSearch("Typed address",p);advanceUntilIdle();assertEquals(1,calls)
        vm.chooseDestination(result);advanceUntilIdle();assertEquals(1,calls);assertFalse(vm.searchVisible)
        assertEquals(3.0,vm.camera.lat,0.0)
        assertEquals(result.label,TrackerViewModel(saved).searchQuery)
        vm.editSearch("",p);advanceUntilIdle();assertFalse(vm.searchVisible);assertTrue(vm.searchResults.isEmpty())
    }
    @Test fun emptyAndErrorKeepQueryAndPermitRetry()=runTest(dispatcher) {
        val vm=TrackerViewModel(SavedStateHandle());var calls=0
        val p=provider {if(calls++==0)throw SearchUnavailable("OFFLINE") else emptyList()}
        vm.editSearch("Remote town",p);advanceUntilIdle();assertEquals(SearchState.Unavailable,vm.searchState)
        assertEquals("Remote town",vm.searchQuery);assertEquals("OFFLINE",vm.searchMessage)
        vm.search(p);advanceUntilIdle();assertEquals(SearchState.Empty,vm.searchState);assertEquals(2,calls)
        assertNull(vm.destination)
    }
    @Test fun layerNotificationsReplaceAndExpireWithoutStartupEvent()=runTest(dispatcher) {
        val vm=MapFiltersViewModel();assertNull(vm.layerNotice)
        vm.toggle(MarkerKind.Place);assertEquals("MAIN ATTRACTIONS: OFF",vm.layerNotice)
        advanceTimeBy(1000);runCurrent()
        vm.toggle(MarkerKind.Alert);assertEquals("LIVE ALERTS: OFF",vm.layerNotice)
        advanceTimeBy(801);runCurrent();assertEquals("LIVE ALERTS: OFF",vm.layerNotice)
        advanceTimeBy(999);runCurrent();assertNull(vm.layerNotice)
        vm.toggle(MarkerKind.Place);assertEquals("MAIN ATTRACTIONS: ON",vm.layerNotice)
        vm.toggle(MarkerKind.Alert);assertEquals("LIVE ALERTS: ON",vm.layerNotice)
        advanceUntilIdle();assertNull(vm.layerNotice)
    }
    @Test fun globalAndLayerNoticesReplaceEachOtherWithoutStaleDismissal()=runTest(dispatcher) {
        val vm=MapFiltersViewModel()
        vm.showNotice("CENTERING TO GLOBAL VIEW",5000)
        advanceTimeBy(1000);runCurrent()
        vm.toggle(MarkerKind.Place)
        advanceTimeBy(1799);runCurrent();assertEquals("MAIN ATTRACTIONS: OFF",vm.layerNotice)
        advanceTimeBy(1);runCurrent();assertNull(vm.layerNotice)
        vm.toggle(MarkerKind.Alert)
        advanceTimeBy(500);runCurrent()
        vm.showNotice("CENTERING TO GLOBAL VIEW",5000)
        advanceTimeBy(4999);runCurrent();assertEquals("CENTERING TO GLOBAL VIEW",vm.layerNotice)
        advanceTimeBy(1);runCurrent();assertNull(vm.layerNotice)
    }
    @Test fun globalCatalogIsNotReplacedByViewportPoiCallbacks()=runTest(dispatcher) {
        val marker=LayerMarker("test-global",MarkerKind.Place,35.0,139.0,PlaceCategory.Historic.name,"TEST landmark",source="TEST")
        val repo=object:GlobalAttractionsRepository {
            override fun load(language:String)=listOf(marker)
            override fun applyUpdates(payload:String)=Unit
        }
        val vm=MapFiltersViewModel(globalAttractions=repo,attractionDispatcher=dispatcher)
        runCurrent()
        vm.acceptPlaces(emptyList(),"ZOOM IN")
        assertEquals(listOf(marker),vm.visibleMarkers.filter {it.kind==MarkerKind.Place})
        vm.select(marker);vm.toggle(MarkerKind.Place);assertNull(vm.selected)
        vm.toggle(MarkerKind.Place);assertTrue(marker in vm.visibleMarkers)
    }
}
