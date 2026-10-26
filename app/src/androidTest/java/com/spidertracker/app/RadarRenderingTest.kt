package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarRenderingTest {
    @get:Rule val compose=createComposeRule()

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
