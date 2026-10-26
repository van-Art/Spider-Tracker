package com.spidertracker.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MapLayersTest {
    private val dispatcher=kotlinx.coroutines.test.StandardTestDispatcher()
    @org.junit.Before fun start(){kotlinx.coroutines.Dispatchers.setMain(dispatcher)}
    @org.junit.After fun end(){kotlinx.coroutines.Dispatchers.resetMain()}
    private val records=listOf(
        OsmPlaceRecord("1",51.5,-.1,mapOf("class" to "food","subclass" to "cafe","name" to "OSM test cafe")),
        OsmPlaceRecord("2",51.6,-.2,mapOf("subclass" to "pharmacy")),
        OsmPlaceRecord("3",Double.NaN,-.2,mapOf("subclass" to "hospital")),
        OsmPlaceRecord("4",51.5,-.1,mapOf("subclass" to "bench")))
    @Test fun placesValidateCategoriesAndNeverInventMetadata() {
        val places=OsmConfirmedPlacesRepository().places(records+records)
        assertEquals(2,places.size);assertEquals("OSM test cafe",places[0].name)
        assertNull(places[0].address);assertNull(places[1].name)
        assertTrue(places.all {it.verification!!.contains("NOT INDEPENDENTLY VERIFIED") && !it.demo})
        listOf("restaurant","hospital","pharmacy","parking","fuel","supermarket","attraction").forEach {assertNotNull(placeCategory(it,null))}
        assertNull(placeCategory("bench","bench"))
    }
    @Test fun independentLayersAndCategoriesOnlyHideTheirOwnMarkers() {
        val vm=MapFiltersViewModel();vm.acceptPlaces(records,"OSM")
        assertEquals(8,vm.visibleMarkers.size)
        vm.toggle(MarkerKind.Place);assertEquals(6,vm.visibleMarkers.size);assertTrue(vm.visibleMarkers.all {it.kind==MarkerKind.Alert})
        vm.toggle(MarkerKind.Alert);assertTrue(vm.visibleMarkers.isEmpty())
        vm.toggle(MarkerKind.Place);assertEquals(2,vm.visibleMarkers.size)
        vm.toggleCategory(PlaceCategory.Food);assertEquals(1,vm.visibleMarkers.size)
        vm.select(vm.visibleMarkers.first());assertNotNull(vm.selected)
        vm.acceptPlaces(records.map {if(it.id=="2")it.copy(tags=it.tags+mapOf("name" to "Updated pharmacy")) else it},"OSM")
        assertEquals("Updated pharmacy",vm.selected!!.name)
        vm.toggleCategory(PlaceCategory.Pharmacy);assertNull(vm.selected);assertTrue(vm.visibleMarkers.isEmpty())
        vm.toggle(MarkerKind.Alert);vm.toggleCategory(AlertCategory.Accident);assertEquals(5,vm.visibleMarkers.size)
        assertTrue(vm.visibleMarkers.all {it.demo && it.reportedAt==null && it.verification!!.contains("DEMO")})
    }
    @Test fun fabricatedSelectionsCannotOpenDetails() {
        val vm=MapFiltersViewModel();val marker=DemoLiveAlertsRepository().reports().first()
        vm.toggle(MarkerKind.Alert);vm.select(marker);assertNull(vm.selected)
        vm.showFilters(MarkerKind.Place);assertEquals(MarkerKind.Place,vm.filterKind);vm.closeFilters();assertNull(vm.filterKind)
    }
    @Test fun pulsesUseRequestedPeriodsAndReducedMotionIsStatic() {
        listOf(MarkerKind.User to 1.8f,MarkerKind.Place to 2.4f,MarkerKind.Alert to 1.6f).forEach {(kind,period)->
            assertEquals(period,MarkerAnimationState.period(kind),0f)
            val start=MarkerAnimationState.sample(kind,0f);val half=MarkerAnimationState.sample(kind,period/2)
            assertTrue(half.radius>start.radius);assertTrue(half.opacity<start.opacity)
            assertEquals(start,MarkerAnimationState.sample(kind,period))
            assertEquals(MarkerAnimationState.sample(kind,0f,true),MarkerAnimationState.sample(kind,99f,true))
        }
    }
    @Test fun radarUsesCoordinatesAndDisabledLayersRemoveDots() {
        val vm=MapFiltersViewModel();vm.acceptPlaces(records,"OSM")
        val fix=DeviceFix(51.5,-.1,10f,true)
        val dots=geographicRadar(vm.visibleMarkers,fix,51.5,-.1,20).dots
        assertEquals(1,dots.count {it.kind==MarkerKind.User});assertTrue(dots.all {it.x*it.x+it.y*it.y<1.001})
        vm.toggle(MarkerKind.Place);vm.toggle(MarkerKind.Alert)
        assertEquals(listOf(MarkerKind.User),geographicRadar(vm.visibleMarkers,fix,51.5,-.1,20).dots.map {it.kind})
        assertTrue(distanceKm(0.0,179.9,0.0,-179.9)<23)
    }
    @Test fun pixelBoundsKeepAspectRatioAndIntegerPixelsAcrossDensities() {
        listOf(1f,1.5f,2f,2.625f,3f).forEach {density->
            val side=(34*density).toInt();val b=pixelBounds(34,34,side,side)
            assertEquals(b.width,b.height);assertTrue(b.x>=0 && b.y>=0)
            assertTrue(b.width<=side)
        }
        assertEquals(PixelBounds(0,0,68,68),pixelBounds(34,34,68,68))
        val button=pixelBounds(54,41,96,80)
        assertTrue(kotlin.math.abs(button.width.toFloat()/button.height-54f/41)<.03)
    }
}
