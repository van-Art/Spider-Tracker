package com.spidertracker.app

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.view.View
import android.view.ViewGroup
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TrackerInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var previousSoftwareKeyboard:String?=null
    private var allowAccessoryIme=false
    private fun keyboardSetting(value:String?) {
        val command=if(value==null)"settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $value"
        val fd=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }
    @Before fun liveMap() {
        previousSoftwareKeyboard=android.provider.Settings.Secure.getString(compose.activity.contentResolver,"show_ime_with_hard_keyboard")
        keyboardSetting("1")
        compose.runOnIdle {compose.activity.tracker.advancePhase(BootPhase.Live)}
    }
    @org.junit.After fun restoreKeyboardSetting(){keyboardSetting(previousSoftwareKeyboard)}

    @Test fun startupUsesSuppliedArtAndRemovedControlsStayAbsent() {
        listOf("tracker-logo","left-logo","right-logo","spider-sprite","radar","address-input","map-global","map-center").forEach {compose.onNodeWithTag(it).assertIsDisplayed()}
        listOf("menu-toggle","filter-confirmed","filter-rumored","watch-trailer","sound-toggle","credits").forEach {compose.onNodeWithTag(it).assertDoesNotExist()}
        compose.onNodeWithText("FIELD OBSERVATIONS").assertDoesNotExist()
        compose.onNodeWithTag("map-viewport").assertHeightIsAtLeast(500.dp)
    }
    @Test fun launcherManifestRequestsPortrait() {
        val activity=compose.activity
        @Suppress("DEPRECATION")
        val info=activity.packageManager.getActivityInfo(activity.componentName,0)
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,info.screenOrientation)
    }
    @Test fun recreationRetainsSearchDestinationCameraAndLayerState() {
        val result=AddressResult("rotation-fixture","TEST · London",51.5,-.12)
        compose.runOnIdle {
            compose.activity.tracker.editSearch("London")
            compose.activity.tracker.search(object:GeocodingProvider {
                override val configured=true
                override suspend fun search(query:String)=listOf(result)
            })
        }
        compose.waitUntil(5000){compose.activity.tracker.searchResults==listOf(result)}
        compose.runOnIdle {
            compose.activity.tracker.chooseDestination(result)
            compose.activity.mapFilters.toggle(MarkerKind.Alert)
        }
        val before=nativeMap()
        compose.waitUntil(6000) {
            var ready=false
            compose.runOnUiThread {ready=kotlin.math.abs(before.cameraPosition.target!!.latitude-result.lat)<.001 && kotlin.math.abs(before.cameraPosition.zoom-13)<.01}
            ready
        }
        val tracker=compose.activity.tracker
        val filters=compose.activity.mapFilters
        val state=filters.state
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("address-input").assertTextEquals(result.label)
        compose.runOnIdle {
            assertSame(tracker,compose.activity.tracker);assertSame(filters,compose.activity.mapFilters)
            assertEquals(result,compose.activity.tracker.destination)
            assertEquals(state,compose.activity.mapFilters.state)
        }
        val after=nativeMap()
        compose.waitUntil(6000) {
            var ready=false
            compose.runOnUiThread {ready=kotlin.math.abs(after.cameraPosition.target!!.latitude-result.lat)<.001 && kotlin.math.abs(after.cameraPosition.target!!.longitude-result.lon)<.001 && kotlin.math.abs(after.cameraPosition.zoom-13)<.01}
            ready
        }
        listOf("radar","search-submit","layer-places","layer-alerts").forEach {compose.onNodeWithTag(it).assertIsDisplayed()}
    }
    @Test fun retainedFiltersLogAndMediaStillWork() {
        compose.runOnIdle {compose.activity.tracker.apply {toggleFilter(PinKind.Confirmed);toggleFilter(PinKind.Rumored);openSection(Section.Log);dismissExplanation()}}
        compose.onNodeWithTag("activity-log").assertIsDisplayed()
        compose.runOnIdle {assertTrue(compose.activity.tracker.logPins.all {it.kind==PinKind.Event || it.kind==PinKind.Trailer})}
    }
    @Test fun autocompleteResultsMoveCameraAndDrawDestination() {
        var calls=0
        val result=AddressResult("test-fixture","TEST · London",51.5,-.12)
        compose.runOnIdle {compose.activity.geocoder=object:GeocodingProvider {
            override val configured=true
            override suspend fun search(query:String):List<AddressResult> {calls++;return listOf(result)}
        }}
        compose.onNodeWithTag("address-input").performTextInput("London")
        compose.waitUntil(5000){calls==1}
        compose.onNodeWithTag("search-result-0").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,calls);assertEquals(result,compose.activity.tracker.destination);assertEquals(51.5,compose.activity.tracker.camera.lat,0.0)}
        val map=nativeMap()
        compose.waitUntil(6000) {
            var ready=false
            compose.runOnUiThread {ready=kotlin.math.abs(map.cameraPosition.target!!.latitude-51.5)<.01 && kotlin.math.abs(map.cameraPosition.zoom-13)<.01}
            ready
        }
        compose.runOnIdle {
            assertNotNull(map.style!!.getLayer("destination-marker"))
            val source=map.style!!.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("destination")!!
            val features=source.querySourceFeatures(null)
            assertTrue(features.isNotEmpty())
            assertEquals(1,features.map {it.geometry().toString()}.distinct().size)
        }
    }
    @Test fun unavailableSearchPreservesEditableFieldAndDismissal() {
        compose.runOnIdle {compose.activity.geocoder=object:GeocodingProvider {
            override val configured=false
            override suspend fun search(query:String):List<AddressResult> = error("disabled provider called")
        }}
        compose.onNodeWithTag("address-input").performTextInput("Paris")
        compose.onNodeWithTag("search-submit").performClick()
        compose.onNodeWithText("SEARCH UNAVAILABLE · NO PROVIDER CONFIGURED").assertIsDisplayed()
        compose.onNodeWithTag("search-dismiss").performClick()
        compose.onNodeWithTag("search-results").assertDoesNotExist()
        compose.onNodeWithTag("address-input").assertTextEquals("Paris")
        compose.runOnIdle {assertNull(compose.activity.tracker.destination)}
    }
    @Test fun keyboardOverlaysWithoutChangingNativeViewportOrCamera() {
        val map=nativeMap()
        android.os.SystemClock.sleep(1800)
        compose.runOnIdle {
            compose.activity.geocoder=object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=List(5){AddressResult("test-$it","Paris result $it",48.8566,2.3522)}}
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(15.0,-45.0)).zoom(5.0).bearing(37.0).tilt(30.0).build()))
        }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        android.os.SystemClock.sleep(300)
        val before=compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val radar=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
        val shelf=compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot
        val frame=compose.onNodeWithTag("bottom-hud-frame").fetchSemanticsNode().boundsInRoot
        val submit=compose.onNodeWithTag("search-submit").fetchSemanticsNode().boundsInRoot
        val character=compose.onNodeWithTag("spider-sprite").fetchSemanticsNode().boundsInRoot
        val globe=compose.onNodeWithTag("map-global").fetchSemanticsNode().boundsInRoot
        val gps=compose.onNodeWithTag("map-center").fetchSemanticsNode().boundsInRoot
        val fixedChrome=listOf("tracker-logo","left-logo","right-logo","layer-places","layer-alerts")
            .associateWith {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        val initialBottom=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val native=nativeView();val w=native.width;val h=native.height
        var camera=map.cameraPosition
        compose.runOnIdle {camera=map.cameraPosition}
        val layers=compose.activity.mapFilters.state
        captureKeyboardFrame("closed")
        compose.onNodeWithTag("address-input").performTouchInput {click()}
        compose.onNodeWithTag("address-input").assertIsFocused().performTextInput("Paris")
        try {
            if(allowAccessoryIme) {
                compose.waitUntil(5000){compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isAcceptingText}
                android.os.SystemClock.sleep(600)
                val bottom=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
                if(bottom==0) {
                    val navigationBottom=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    assertEquals(shelf.top-(navigationBottom-initialBottom),compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top,1f)
                } else compose.waitUntil(5000){compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top<shelf.top-100}
            } else compose.waitUntil(5000){compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top<shelf.top-100}
        }
        catch(error:Throwable) {
            val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            val file=java.io.File(compose.activity.getExternalFilesDir(null),"phase312-ime-failure.png")
            file.outputStream().use {automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phase312-ime-failure.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
            val dumpFd=automation.executeShellCommand("dumpsys input_method")
            val dump=android.os.ParcelFileDescriptor.AutoCloseInputStream(dumpFd).use {it.bufferedReader().readText()}
            val dumpFile=java.io.File(compose.activity.getExternalFilesDir(null),"phase312-ime-dump.txt");dumpFile.writeText(dump)
            val copy=automation.executeShellCommand("cp ${dumpFile.absolutePath} /sdcard/Download/phase312-ime-dump.txt")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use {it.readBytes()}
            throw AssertionError("focus=${compose.activity.currentFocus?.javaClass?.name}; windowFocus=${compose.activity.window.decorView.hasWindowFocus()}; IME opening shelf before=$shelf after="+compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot+"; insets="+androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()),error)
        }
        assertEquals(before,compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot)
        fixedChrome.forEach {(tag,bounds)->assertEquals(tag,bounds,compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)}
        val raisedRadar=compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot
        val shift=compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top-shelf.top
        val raisedFrame=compose.onNodeWithTag("bottom-hud-frame").fetchSemanticsNode().boundsInRoot
        assertEquals(frame.top+shift,raisedFrame.top,1f)
        assertEquals(frame.size,raisedFrame.size)
        assertEquals(submit.top+shift,compose.onNodeWithTag("search-submit").fetchSemanticsNode().boundsInRoot.top,1f)
        val insets=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
        val overlap=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime() or androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
        val root=compose.onNodeWithTag("tracker-root").fetchSemanticsNode().boundsInRoot
        assertEquals("Frame must touch keyboard boundary",root.bottom-overlap,raisedFrame.bottom,1f)
        val radarShift=raisedRadar.top-radar.top
        // Tall landscape keyboards clamp the radar so its status label stays visible.
        assertTrue(radarShift>=shift-1f && radarShift<=1f);assertEquals(radar.size,raisedRadar.size)
        val status=compose.onNodeWithTag("radar-status").fetchSemanticsNode().boundsInRoot
        assertTrue(status.top>=compose.onNodeWithTag("tracker-root").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(character.top+shift,compose.onNodeWithTag("spider-sprite").fetchSemanticsNode().boundsInRoot.top,1f)
        assertEquals(globe.top+radarShift,compose.onNodeWithTag("map-global").fetchSemanticsNode().boundsInRoot.top,1f)
        assertEquals(gps.top+radarShift,compose.onNodeWithTag("map-center").fetchSemanticsNode().boundsInRoot.top,1f)
        compose.onNodeWithTag("radar").assertIsDisplayed();compose.onNodeWithTag("map-global").assertIsDisplayed();compose.onNodeWithTag("map-center").assertIsDisplayed()
        assertSame(native,nativeView());assertEquals(w,native.width);assertEquals(h,native.height)
        compose.runOnIdle {assertEquals(camera,map.cameraPosition);assertEquals(layers,compose.activity.mapFilters.state)}
        compose.onNodeWithTag("address-input").assertIsDisplayed()
        compose.onNodeWithTag("search-submit").assertIsDisplayed()
        compose.waitUntil(5000){compose.activity.tracker.searchResults.isNotEmpty()}
        val panel=compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
        val input=compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.bottom<input.top)
        assertTrue("Suggestions overlap fixed header",panel.top>=fixedChrome.filterKeys {it.endsWith("logo")}.values.maxOf {it.bottom})
        assertTrue("Suggestions overlap radar",panel.right<raisedRadar.left)
        assertTrue("Radar overlaps search",raisedRadar.bottom<input.top || input.right<raisedRadar.left)
        compose.onNodeWithTag("address-input").assertIsFocused().assertTextEquals("Paris")
        captureKeyboardFrame("open")
        compose.onNodeWithTag("search-result-4").performScrollTo().assertIsDisplayed()
        val screenshot=java.io.File(compose.activity.getExternalFilesDir(null),"radar-keyboard-open.png")
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        screenshot.outputStream().use {automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        val cp=automation.executeShellCommand("cp ${screenshot.absolutePath} /sdcard/Download/radar-keyboard-open.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(cp).use {it.readBytes()}
        val imeBottom=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom
        androidx.test.espresso.Espresso.pressBack()
        try {compose.waitUntil(5000){compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top>=shelf.top-1}}
        catch(error:Throwable) {throw AssertionError("shelf before=$shelf after="+compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot+"; ime="+androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()),error)}
        assertEquals(before,compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {assertEquals(camera,map.cameraPosition);assertEquals("Paris",compose.activity.tracker.searchQuery)}
        assertEquals(radar,compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot)
        assertEquals(frame,compose.onNodeWithTag("bottom-hud-frame").fetchSemanticsNode().boundsInRoot)
        assertEquals(shelf,compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot)
        assertEquals(submit,compose.onNodeWithTag("search-submit").fetchSemanticsNode().boundsInRoot)
        assertEquals(character,compose.onNodeWithTag("spider-sprite").fetchSemanticsNode().boundsInRoot)
        captureKeyboardFrame("restored")
        fixedChrome.forEach {(tag,bounds)->assertEquals(tag,bounds,compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)}
        val mode=android.provider.Settings.Secure.getInt(compose.activity.contentResolver,"navigation_mode",-1)
        val orientation=if(native.width>native.height)"landscape" else "portrait"
        val report=org.json.JSONObject().put("navigationMode",mode).put("orientation",orientation).put("softwareKeyboardWithHardwareEnabledForTest",true).put("nativeWidthPx",w).put("nativeHeightPx",h)
            .put("mapInstanceUnchanged",native===nativeView()).put("mapBoundsBefore",before.toString())
            .put("mapBoundsAfter",compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.toString())
            .put("cameraLat",camera.target!!.latitude).put("cameraLon",camera.target!!.longitude).put("zoom",camera.zoom)
            .put("bearing",camera.bearing).put("pitch",camera.tilt)
            .put("frameBottomPx",raisedFrame.bottom).put("keyboardTopPx",root.bottom-overlap)
            .put("searchBeforeTopPx",shelf.top).put("searchImeTopPx",input.top).put("searchAfterTopPx",compose.onNodeWithTag("search-component").fetchSemanticsNode().boundsInRoot.top)
            .put("imeBottomPx",imeBottom).put("imeMode",if(imeBottom==0)"physical-keyboard accessory; no bottom occlusion" else "docked software keyboard").put("radarRestored",radar==compose.onNodeWithTag("radar").fetchSemanticsNode().boundsInRoot).put("radarRaisedBounds",raisedRadar.toString()).put("radarBeforeBounds",radar.toString()).put("suggestionsBounds",panel.toString())
        val file=java.io.File(compose.activity.getExternalFilesDir(null),"radar-ime-$mode-$orientation.json");file.writeText(report.toString())
        val fd=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/radar-ime-$mode-$orientation.json")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }
    private fun captureKeyboardFrame(state:String) {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        val frame=compose.onNodeWithTag("bottom-hud-frame").fetchSemanticsNode().boundsInRoot
        val density=compose.activity.resources.displayMetrics.density
        assertEquals("Cyan bottom edge missing ($state)",0xff00befc.toInt(),bitmap.getPixel(frame.center.x.toInt(),(frame.bottom-10*density).toInt()))
        assertEquals("Map leaked below bottom frame ($state)",android.graphics.Color.BLACK,bitmap.getPixel(frame.center.x.toInt(),frame.bottom.toInt()-1))
        val mode=android.provider.Settings.Secure.getInt(compose.activity.contentResolver,"navigation_mode",-1)
        val file=java.io.File(compose.activity.getExternalFilesDir(null),"phase313-$mode-${bitmap.width}x${bitmap.height}-${(density*160).toInt()}-$state.png")
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        val fd=automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/${file.name}")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}
    }
    @Test fun landscapeAccessoryImeKeepsNativeMapAndSearchStable() {
        val previous=compose.activity.requestedOrientation
        try {
            compose.runOnUiThread {compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(10000){nativeView().width>nativeView().height}
            allowAccessoryIme=true
            keyboardOverlaysWithoutChangingNativeViewportOrCamera()
        } finally {allowAccessoryIme=false;compose.runOnUiThread {compose.activity.requestedOrientation=previous}}
    }
    @Test fun assetsDecodeAtNativeResolutionAndSpriteAdvances() {
        val expected=listOf(R.drawable.left_up_logo to (62 to 64),R.drawable.right_up_logo to (64 to 64),R.drawable.tracker_logo to (280 to 47),R.drawable.zoom_to_global to (24 to 24),R.drawable.center to (24 to 24),R.drawable.spider_sprite_sheet to (2048 to 43))
        expected.forEach {(resource,dimensions)->
            val b=android.graphics.BitmapFactory.decodeResource(compose.activity.resources,resource,android.graphics.BitmapFactory.Options().apply {inScaled=false})
            assertEquals(dimensions.first,b.width);assertEquals(dimensions.second,b.height)
            if(resource==R.drawable.spider_sprite_sheet) {
                val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
                val frames=extractSpriteFrames(BooleanArray(b.width){x->(0 until b.height).any {y->pixels[y*b.width+x] ushr 24!=0}},b.height)
                assertEquals(76,frames.size);assertTrue(frames.all {it.width==27 && it.height==43 && it.x+it.width<=b.width})
            }
        }
        fun frame()=compose.onNodeWithTag("spider-sprite").fetchSemanticsNode().config[SpriteFrameKey]
        val first=frame();compose.waitUntil(3000){frame()!=first}
    }
    @Test fun foregroundAnimationClockPausesAndResumes() {
        val clock=ForegroundAnimationClock()
        val owner=object:androidx.lifecycle.LifecycleOwner {
            val registry=androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle:androidx.lifecycle.Lifecycle get()=registry
        }
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main+kotlinx.coroutines.SupervisorJob())
        compose.runOnUiThread {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED;scope.launch {clock.run(owner.lifecycle)}}
        try {
            compose.waitUntil(3000){clock.seconds>0}
            compose.runOnUiThread {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.CREATED}
            android.os.SystemClock.sleep(100)
            val stopped=clock.seconds;android.os.SystemClock.sleep(250);assertEquals(stopped,clock.seconds,0f)
            compose.runOnUiThread {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED}
            compose.waitUntil(3000){clock.seconds>stopped}
        } finally {compose.runOnUiThread {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.DESTROYED;scope.cancel()}}
    }
    @Test fun rumorMediaAndCloseClearTheMessage() {
        compose.runOnIdle {
            val vm=compose.activity.tracker
            vm.selectPin(vm.pins.first{it.kind==PinKind.Rumored},true);vm.activatePin()
        }
        compose.onNodeWithTag("message-detail").assertIsDisplayed()
        compose.onNodeWithTag("detail-media").performClick()
        compose.onNodeWithTag("media-modal").assertIsDisplayed()
        compose.onNodeWithTag("panel-close").performClick()
        compose.onNodeWithTag("message-close").performClick()
        compose.runOnIdle {assertNull(compose.activity.tracker.detailId)}
    }

    @Test fun radarUsesGrantedDeviceProviderAndGlobalResetsCamera() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(compose.activity.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
        compose.runOnIdle {compose.activity.locationProvider=object:DeviceLocationProvider {override suspend fun currentFix()=DeviceFix(40.1,44.5,12f,true)}}
        compose.onNodeWithTag("map-center").performClick()
        compose.waitUntil(3000){compose.activity.tracker.camera.lat==40.1}
        compose.onNodeWithTag("map-feedback").assertIsDisplayed()
        compose.onNodeWithTag("map-global").performClick()
        compose.runOnIdle {assertEquals(1.0,compose.activity.tracker.camera.zoom,.001);assertEquals(-49.9007427311529,compose.activity.tracker.camera.lon,.001)}
    }

    @Test fun searchArtworkAndEditableLineRemainAligned() {
        fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        fun aligned() {
            val frame=bounds("search-art");val button=bounds("search-button-art")
            val input=bounds("address-input");val icon=bounds("search-icon")
            assertEquals(frame.top,button.top,1f);assertEquals(frame.bottom,button.bottom,1f)
            assertEquals(frame.center.y,input.center.y,1f);assertEquals(frame.center.y,icon.center.y,1f)
            assertTrue(input.left>icon.right && input.right<frame.right)
            assertTrue(input.top>=frame.top && input.bottom<=frame.bottom)
            assertTrue(button.left<frame.right && button.right<=bounds("search-component").right)
        }
        aligned()
        compose.onNodeWithTag("address-input").performTextInput("gyp London 221B Baker Street")
        compose.onNodeWithTag("address-input").performTextInputSelection(androidx.compose.ui.text.TextRange(0))
        compose.onNodeWithTag("address-input").performTextInput("X")
        compose.onNodeWithTag("address-input").assertTextEquals("Xgyp London 221B Baker Street")
        aligned()
        compose.onNodeWithTag("search-submit").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun autocompleteKeyboardSelectionClearAndQueryPersistence() {
        val results=(0 until 5).map {AddressResult("test-$it","TEST PLACE $it, London, United Kingdom",51.5+it*.01,-.12,"TEST PLACE $it","London","United Kingdom")}
        var calls=0
        compose.runOnIdle {compose.activity.geocoder=object:GeocodingProvider {override val configured=true;override suspend fun search(query:String):List<AddressResult>{calls++;return results}}}
        compose.onNodeWithTag("address-input").performClick().performTextInput("London")
        compose.waitUntil(5000){compose.activity.tracker.searchResults.size==5}
        compose.onNodeWithTag("search-result-0").assertTextEquals("TEST PLACE 0","London · United Kingdom")
        compose.onNodeWithTag("address-input").performKeyInput {pressKey(androidx.compose.ui.input.key.Key.DirectionDown)}
        compose.onNodeWithTag("search-result-0").assertIsSelected()
        compose.onNodeWithTag("address-input").performKeyInput {pressKey(androidx.compose.ui.input.key.Key.Enter)}
        compose.waitUntil(5000){!compose.activity.tracker.searchVisible && compose.activity.tracker.destination==results[0]}
        compose.onNodeWithTag("search-results").assertDoesNotExist()
        compose.onNodeWithTag("search-clear").performClick()
        compose.onNodeWithTag("address-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        compose.runOnIdle {assertEquals(1,calls);compose.activity.tracker.editSearch("PERSISTED QUERY")}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("address-input").assertTextEquals("PERSISTED QUERY")
    }
    @Test fun liveSearchEmptyAndNetworkErrorKeepEditableQuery() {
        compose.runOnIdle {compose.activity.geocoder=object:GeocodingProvider {
            override val configured=true
            override suspend fun search(query:String):List<AddressResult> {if(query=="offline")throw SearchUnavailable("TEST NETWORK ERROR");return emptyList()}
        }}
        compose.onNodeWithTag("address-input").performTextInput("nowhere")
        compose.waitUntil(5000){compose.activity.tracker.searchState==SearchState.Empty}
        compose.onNodeWithText("NO MATCHES · TRY ANOTHER ADDRESS").assertIsDisplayed()
        compose.onNodeWithTag("address-input").performTextReplacement("offline")
        compose.waitUntil(5000){compose.activity.tracker.searchState==SearchState.Unavailable}
        compose.onNodeWithText("TEST NETWORK ERROR").assertIsDisplayed()
        compose.onNodeWithTag("address-input").assertTextEquals("offline")
        compose.runOnIdle {assertNull(compose.activity.tracker.destination)}
    }

    @Test fun controlledTextCopiesAndPastesThroughTheEditableField() {
        compose.runOnIdle {compose.activity.geocoder=object:GeocodingProvider {override val configured=true;override suspend fun search(query:String)=emptyList<AddressResult>()}}
        compose.onNodeWithTag("address-input").performTextInput("London")
        compose.onNodeWithTag("address-input").performTextInputSelection(androidx.compose.ui.text.TextRange(0,6))
        compose.onNodeWithTag("address-input").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.CopyText){assertTrue(it())}
        compose.onNodeWithTag("address-input").performTextReplacement("City ")
        compose.onNodeWithTag("address-input").performTextInputSelection(androidx.compose.ui.text.TextRange(5))
        compose.onNodeWithTag("address-input").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.PasteText){assertTrue(it())}
        compose.onNodeWithTag("address-input").assertTextEquals("City London")
    }

    private fun nativeView():MapView {
        fun find(view:View):MapView? {
            if(view is MapView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
            return null
        }
        var result:MapView?=null
        compose.runOnIdle {result=find(compose.activity.window.decorView)}
        return result!!
    }

    private fun nativeMap():MapLibreMap {
        val native=AtomicReference<MapLibreMap>();val ready=AtomicBoolean(false)
        fun findMap(view:View):MapView? {
            if(view is MapView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let{return it}
            return null
        }
        compose.runOnIdle {findMap(compose.activity.window.decorView)!!.getMapAsync {map->native.set(map);map.getStyle {ready.set(true)}}}
        compose.waitUntil(20000){ready.get()}
        return native.get()
    }

    @Test fun mapLibreStyleLoadsAndNativeMapRespondsToPan() {
        val native=AtomicReference<MapLibreMap>()
        val ready=AtomicBoolean(false)
        fun findMap(view:View):MapView? {
            if(view is MapView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let{return it}
            return null
        }
        compose.runOnIdle {
            val view=findMap(compose.activity.window.decorView)
            assertNotNull(view)
            view!!.getMapAsync {map->native.set(map);map.getStyle {ready.set(true)}}
        }
        compose.waitUntil(20000) {ready.get()}
        var longitude=0.0
        compose.runOnIdle {
            val map=native.get()
            longitude=map.cameraPosition.target!!.longitude
            assertNotNull(map.style!!.getLayer("pins-Confirmed"))
            assertNotNull(map.style!!.getSource("osm"))
        }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(MapView::class.java))
            .perform(androidx.test.espresso.action.ViewActions.swipeLeft())
        compose.waitUntil(5000) {
            var moved=false
            compose.runOnUiThread {moved=kotlin.math.abs(native.get().cameraPosition.target!!.longitude-longitude)>1.0}
            moved
        }
    }
}
