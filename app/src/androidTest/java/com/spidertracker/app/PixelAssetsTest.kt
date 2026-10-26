package com.spidertracker.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelAssetsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun searchCapsRetainOriginalPixelsAcrossDifferentWidths() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val original=BitmapFactory.decodeResource(context.resources,R.drawable.search_bar,BitmapFactory.Options().apply {inScaled=false})
        compose.setContent {PixelSearchBackground(Modifier.size(250.dp,40.dp).testTag("sliced-search"))}
        val capture=compose.onNodeWithTag("sliced-search").captureToImage().asAndroidBitmap()
        val scale=capture.height/original.height
        assertTrue(scale>=1);assertEquals(original.height*scale,capture.height)
        for(y in 0 until original.height)for(x in 0 until 7) {
            if(android.graphics.Color.alpha(original.getPixel(x,y))==255)assertEquals(original.getPixel(x,y),capture.getPixel(x*scale,y*scale))
            if(android.graphics.Color.alpha(original.getPixel(original.width-1-x,y))==255)assertEquals(original.getPixel(original.width-1-x,y),capture.getPixel(capture.width-1-x*scale,y*scale))
        }
    }
    @Test fun markerSpritePreservesColorsWithNearestNeighborDrawing() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val original=BitmapFactory.decodeResource(context.resources,R.drawable.confirmed_sightings_location,BitmapFactory.Options().apply {inScaled=false})
        compose.setContent {SuppliedImage(R.drawable.confirmed_sightings_location,null,Modifier.size(34.dp).testTag("png-marker"))}
        val capture=compose.onNodeWithTag("png-marker").captureToImage().asAndroidBitmap()
        val scale=capture.width/original.width
        assertEquals(original.width*scale,capture.width)
        for(y in 0 until original.height)for(x in 0 until original.width) {
            if(android.graphics.Color.alpha(original.getPixel(x,y))==255)assertEquals(original.getPixel(x,y),capture.getPixel(x*scale,y*scale))
        }
    }
    @Test fun compactSuggestionAreaRetainsSelectableRowsAboveInput() {
        val vm=TrackerViewModel(androidx.lifecycle.SavedStateHandle())
        val result=AddressResult("test-compact","TEST PLACE, TEST COUNTRY",10.0,20.0,"TEST PLACE",null,"TEST COUNTRY")
        val provider=object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=listOf(result)}
        compose.setContent {Box(Modifier.size(360.dp,190.dp)){SearchShelf(vm,TrackerLayout(360f,190f),{vm.search(provider)})}}
        compose.runOnIdle {vm.editSearch("TEST");vm.search(provider)}
        compose.waitUntil(5000){vm.searchResults.isNotEmpty()}
        val panel=compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
        val field=compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.bottom<field.top)
        compose.onNodeWithTag("search-result-0").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {assertEquals(result,vm.destination);assertFalse(vm.searchVisible)}
    }

    @Test fun sharedPreferencesKeepIndependentFlagsAndAnEmptyCategorySet() {
        val preferences=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("map-layers-test",0)
        preferences.edit().clear().commit()
        try {
            val vm=MapFiltersViewModel(preferences)
            vm.toggle(MarkerKind.Place)
            PlaceCategory.entries.forEach(vm::toggleCategory)
            vm.toggleCategory(AlertCategory.Hazard)
            val restored=MapFiltersViewModel(preferences)
            assertEquals(vm.state,restored.state)
            assertFalse(restored.state.placesEnabled);assertTrue(restored.state.alertsEnabled)
            assertTrue(restored.state.placeCategories.isEmpty())
        } finally {preferences.edit().clear().commit()}
    }
    @Test fun placeDetailsDoNotInventAddressAndNavigateSetsOnlyDestination() {
        val filters=MapFiltersViewModel()
        filters.acceptPlaces(listOf(OsmPlaceRecord("test-place",51.5,-.12,mapOf("subclass" to "cafe","name" to "TEST CAFE"))),"TEST")
        val place=filters.visibleMarkers.first {it.kind==MarkerKind.Place}
        filters.select(place)
        val vm=TrackerViewModel(androidx.lifecycle.SavedStateHandle())
        compose.setContent {Box(Modifier.size(336.dp,607.dp)){DetailPanelOverlay(filters,vm)}}
        compose.onNodeWithText("TEST CAFE").assertIsDisplayed()
        compose.onNodeWithText("ADDRESS NOT PROVIDED").assertIsDisplayed()
        compose.onNodeWithText("DISTANCE: LOCATION UNAVAILABLE").assertIsDisplayed()
        compose.onNodeWithTag("place-navigate").performClick()
        compose.runOnIdle {assertEquals(place.id,vm.destination!!.id);assertTrue(vm.toast.contains("CHOOSE ROUTE MODE"))}
    }
}
