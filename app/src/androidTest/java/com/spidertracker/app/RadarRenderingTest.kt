package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.awaitCancellation

@RunWith(AndroidJUnit4::class)
class RadarRenderingTest {
    @get:Rule val compose=createComposeRule()

    @Test fun locationTargetStaysRingFreeAndClickableAcrossPortraitSizesWhileLocating() {
        val filters=MapFiltersViewModel()
        val vm=TrackerViewModel(SavedStateHandle())
        var screen by mutableStateOf(320f to 640f)
        var clicks=0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(screen.first.dp,screen.second.dp).background(Color.Magenta).testTag("hud-test-root")) {
                    RadarOverlay(TrackerLayout(screen.first,screen.second),{0f},filters,vm,
                        RadarSnapshot(RadarMode.Map,0.0,0.0,20,emptyList()),{clicks++},{})
                }
            }
        }
        val gps=compose.onNodeWithTag("map-center")
        try {
            for(size in listOf(320f to 640f,390f to 693f,430f to 844f)) {
                compose.runOnIdle {vm.cancelLocation();screen=size}
                gps.assertIsDisplayed().assertHasClickAction()
                val idle=gps.captureToImage().asAndroidBitmap()
                val target=gps.fetchSemanticsNode().boundsInRoot
                val globe=compose.onNodeWithTag("map-global").fetchSemanticsNode().boundsInRoot
                val root=compose.onNodeWithTag("hud-test-root").fetchSemanticsNode().boundsInRoot
                assertEquals(48f,target.width,0f);assertEquals(48f,target.height,0f)
                assertTrue(target.left>=root.left && target.right<=root.right)
                assertTrue(target.bottom<=root.top+TrackerLayout(size.first,size.second).shelfY)
                assertTrue("Globe and GPS touch targets overlap",globe.bottom<=target.top)
                compose.runOnIdle {vm.locate(object:DeviceLocationProvider {
                    override suspend fun currentFix():DeviceFix=awaitCancellation()
                })}
                compose.waitUntil(3000){vm.locating}
                gps.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Obtaining device location"))
                compose.onNodeWithTag("location-loading",useUnmergedTree=true).assertDoesNotExist()
                assertTrue("Loading adds an outer ring or changes the target",idle.sameAs(gps.captureToImage().asAndroidBitmap()))
                gps.performTouchInput {click()}
            }
            compose.runOnIdle {assertEquals(3,clicks)}
        } finally {compose.runOnIdle {vm.cancelLocation()}}
    }

    @Test fun navyPolygonMasksMapWhileRepositoryAndGpsDotsStayVisible() {
        val filters=MapFiltersViewModel()
        filters.acceptPlaces(listOf(OsmPlaceRecord("radar-attraction",51.51,-.1,mapOf("subclass" to "attraction"))),"OSM")
        filters.setRadarRange(5)
        val vm=TrackerViewModel(SavedStateHandle())
        compose.setContent {
            Box(Modifier.size(300.dp,400.dp).background(Color.Magenta)) {
                RadarOverlay(TrackerLayout(300f,400f),{0f},filters,vm,
                    viewportRadar(filters.visibleMarkers,null,51.5,-.1) {lat,lon->((lon+.1)*20).toFloat() to ((51.5-lat)*20).toFloat()},{},{})
            }
        }
        compose.runOnIdle {vm.locate(object:DeviceLocationProvider {
            override suspend fun currentFix()=DeviceFix(51.5,-.1,8f)
        })}
        compose.waitUntil(5000){vm.userFix!=null}
        val radar=compose.onNodeWithTag("radar")
        val snapshot=radar.fetchSemanticsNode().config[RadarSnapshotKey]
        assertEquals(RadarMode.Gps,snapshot.mode);assertFalse(snapshot.testLocation)
        assertEquals(MarkerKind.entries.toSet(),snapshot.dots.map {it.kind}.toSet())
        val bitmap=radar.captureToImage().asAndroidBitmap()
        assertEquals(Color(0xff0e1c3b).toArgb(),bitmap.getPixel((bitmap.width*.33f).toInt(),(bitmap.height*.65f).toInt()))
        assertEquals(Color.Magenta.toArgb(),bitmap.getPixel(0,0))
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        listOf(Ink.Pale,Ink.pin(PinKind.Confirmed),Ink.pin(PinKind.Rumored),Color(0xff00d7fa)).forEach {color->
            assertTrue("Grid or geographic dot hidden: $color",pixels.any {it==color.toArgb()})
        }
    }
}
