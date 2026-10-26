package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Phase316MarkerTest {
    @get:Rule val compose=createComposeRule()
    @Test fun everyKindAndClusterPulsesBehindUnchangedSpritesAndStopsWhenHidden() {
        var seconds by mutableFloatStateOf(0f)
        var reduced by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var density=1f
        compose.setContent {
            density=LocalDensity.current.density
            val points=if(visible)listOf(
                ProjectedMarker("green",MarkerKind.Place,50*density,70*density),
                ProjectedMarker("red",MarkerKind.Alert,130*density,70*density),
                ProjectedMarker("cyan",MarkerKind.User,210*density,70*density),
                ProjectedMarker("cluster",MarkerKind.Place,290*density,70*density,10)) else emptyList()
            MapMarkerSprites(points,Modifier.size(340.dp,150.dp).background(Color.Black),time={seconds},reducedMotion=reduced)
        }
        val node=compose.onNodeWithTag("map-marker-sprites")
        fun effects()=node.fetchSemanticsNode().config[MarkerEffectsKey]
        val first=node.captureToImage().asAndroidBitmap()
        assertEquals(MarkerKind.entries.toSet(),effects().visibleKinds);assertEquals(1,effects().visibleClusters)
        val start=effects().clusterPulse!!
        listOf(50 to 0,130 to 1,210 to 2,290 to 0).forEach {(x,kind)->
            val pixel=first.getPixel(((x+20)*density).toInt(),(70*density).toInt())
            val r=android.graphics.Color.red(pixel);val g=android.graphics.Color.green(pixel);val b=android.graphics.Color.blue(pixel)
            when(kind){0->assertTrue(g>r && g>b);1->assertTrue(r>g && r>b);else->assertTrue(b>r && g>r)}
        }
        compose.runOnIdle {seconds=.8f}
        val next=node.captureToImage().asAndroidBitmap()
        assertTrue(effects().clusterPulse!!.radius>start.radius)
        MarkerKind.entries.forEach {assertTrue(effects().pulse(it)!!.radius>20)}
        for(x in listOf(50,130,210,290))for(dx in -8..8)for(dy in -8..8) {
            val px=((x+dx)*density).toInt();val py=((70+dy)*density).toInt()
            assertEquals("Sprite changed with pulse",first.getPixel(px,py),next.getPixel(px,py))
        }
        compose.runOnIdle {reduced=true;seconds=20f}
        node.captureToImage()
        MarkerKind.entries.forEach {assertEquals(25f,effects().pulse(it)!!.radius,0f)}
        assertEquals(.2f,effects().clusterPulse!!.opacity,0f)
        compose.runOnIdle {visible=false}
        node.captureToImage()
        assertTrue(effects().visibleKinds.isEmpty());assertEquals(0,effects().visibleClusters)
    }
}
