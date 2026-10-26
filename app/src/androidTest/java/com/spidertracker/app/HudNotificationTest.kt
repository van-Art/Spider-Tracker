package com.spidertracker.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HudNotificationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Before fun enableLayers() {
        compose.runOnIdle {
            val filters=compose.activity.mapFilters
            if(!filters.state.placesEnabled)filters.toggle(MarkerKind.Place)
            if(!filters.state.alertsEnabled)filters.toggle(MarkerKind.Alert)
        }
    }

    private fun bounds(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertNotice(message:String):Float {
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onAllNodesWithTag("map-feedback").assertCountEquals(1)
        val notice=bounds("map-feedback")
        val map=bounds("map-viewport")
        val density=compose.activity.resources.displayMetrics.density
        assertEquals(map.top+50*density,notice.top,1f)
        assertEquals(map.center.x,notice.center.x,1f)
        assertTrue("Notice overlaps header",notice.top>=bounds("tracker-logo").bottom)
        assertTrue("Notice exceeds map width",notice.left>=map.left && notice.right<=map.right)
        return notice.width
    }

    @Test fun allMessagesShareGlobalAnchorAndWrapTheirText() {
        compose.onNodeWithTag("map-global").performClick()
        val globalWidth=assertNotice("CENTERING TO GLOBAL VIEW")
        listOf("layer-places" to "MAIN ATTRACTIONS", "layer-alerts" to "LIVE ALERTS").forEach {(tag,label)->
            compose.onNodeWithTag(tag).performClick()
            val offWidth=assertNotice("$label: OFF")
            compose.onNodeWithText("CENTERING TO GLOBAL VIEW").assertDoesNotExist()
            compose.onNodeWithTag(tag).performClick()
            val onWidth=assertNotice("$label: ON")
            assertTrue("Notice width should follow text",onWidth<offWidth && offWidth<globalWidth)
        }
        compose.onNodeWithTag("map-global").performClick()
        assertNotice("CENTERING TO GLOBAL VIEW")
        compose.onNodeWithText("LIVE ALERTS: ON").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1.0,compose.activity.tracker.camera.zoom,0.0)}
    }

    @Test fun rapidPressesReplaceAndLayerNoticeDismissesAfter1800Millis() {
        repeat(3) {
            compose.onNodeWithTag("layer-places").performClick()
            compose.onNodeWithTag("layer-alerts").performClick()
        }
        val started=SystemClock.uptimeMillis()
        assertNotice("LIVE ALERTS: OFF")
        compose.onNodeWithText("MAIN ATTRACTIONS: OFF").assertDoesNotExist()
        SystemClock.sleep(1200)
        compose.onNodeWithText("LIVE ALERTS: OFF").assertIsDisplayed()
        compose.waitUntil(1800){compose.onAllNodesWithTag("map-feedback").fetchSemanticsNodes().isEmpty()}
        assertTrue("Notice did not dismiss promptly",SystemClock.uptimeMillis()-started<2500)
    }

    @Test fun mapPanPassesThroughOutsideNotice() {
        val result=AtomicReference<MapLibreMap>()
        fun find(view:View):MapView? {
            if(view is MapView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let {return it}
            return null
        }
        compose.runOnIdle {find(compose.activity.window.decorView)!!.getMapAsync {map->map.getStyle {result.set(map)}}}
        compose.waitUntil(20000){result.get()!=null}
        SystemClock.sleep(1800)
        var before=0.0
        compose.runOnIdle {before=result.get().cameraPosition.target!!.longitude}
        compose.onNodeWithTag("layer-places").performClick()
        assertNotice("MAIN ATTRACTIONS: OFF")
        onView(isAssignableFrom(MapView::class.java)).perform(swipeLeft())
        compose.waitUntil(5000) {
            var moved=false
            compose.runOnUiThread {moved=kotlin.math.abs(result.get().cameraPosition.target!!.longitude-before)>.01}
            moved
        }
    }
}
