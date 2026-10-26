package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Phase316OverlayTest {
    @get:Rule val compose=createComposeRule()
    private fun show(kind:MarkerKind):Pair<MapFiltersViewModel,TrackerViewModel> {
        val alerts=object:LiveAlertsRepository {
            override val status="TEST"
            override fun reports()=listOf(LayerMarker("test-alert",MarkerKind.Alert,0.0,0.0,"Hazard",description="Long test description ".repeat(100),source="TEST",verification="TEST END"))
        }
        val filters=MapFiltersViewModel(alertsRepository=alerts)
        filters.acceptPlaces(listOf(OsmPlaceRecord("test-place",0.0,0.0,mapOf("subclass" to "museum","name" to "TEST ATTRACTION"))),"TEST")
        filters.select(filters.visibleMarkers.first {it.kind==kind})
        val vm=TrackerViewModel(SavedStateHandle())
        vm.navigateToPlace(filters.selected!!)
        compose.setContent {
            Box(Modifier.size(390.dp,340.dp).background(Color.Black)) {
                RadarOverlay(TrackerLayout(390f,340f),{0f},filters,vm,RadarSnapshot(RadarMode.Map,0.0,0.0,20,emptyList()),{}, {})
                DetailPanelOverlay(filters,vm)
            }
        }
        return filters to vm
    }
    @Test fun attractionPanelCoversRadarAndNavigateReceivesPhysicalTap() {
        val (filters,vm)=show(MarkerKind.Place)
        val radar=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
        val panel=compose.onNodeWithTag("layer-marker-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.overlaps(radar))
        // Sample the panel's blank right edge where radar grid used to paint over it.
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(0xff0e1c3b.toInt(),image.getPixel((panel.right-6).toInt(),(radar.top+15).toInt()))
        compose.onNodeWithTag("place-navigate").performTouchInput {click(Offset(width*.9f,height/2f))}
        compose.runOnIdle {assertEquals("osm:test-place",vm.destination!!.id);assertNull(filters.selected);assertEquals(20,filters.radarRangeKm)}
    }
    @Test fun longAlertScrollsWhileHeaderStaysAccessibleAndPanelAbsorbsRadarTaps() {
        val (filters,_)=show(MarkerKind.Alert)
        compose.onNodeWithTag("layer-marker-detail").performTouchInput {click(Offset(width-8f,height/2f))}
        compose.runOnIdle {assertEquals(20,filters.radarRangeKm);assertNotNull(filters.selected)}
        compose.onNodeWithText("TEST END").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("marker-detail-close").assertIsDisplayed().performTouchInput {click()}
        compose.onNodeWithTag("layer-marker-detail").assertDoesNotExist()
    }
}
