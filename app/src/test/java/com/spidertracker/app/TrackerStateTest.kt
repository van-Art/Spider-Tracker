package com.spidertracker.app

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class TrackerStateTest {
    @Test fun filtersKeepEventsAndTrailerAndLogUsesSameData() {
        val vm=TrackerViewModel(SavedStateHandle())
        assertEquals(61,vm.visiblePins.size)
        vm.toggleFilter(PinKind.Confirmed)
        assertEquals(38,vm.visiblePins.size)
        assertFalse(vm.logPins.any {it.kind==PinKind.Confirmed})
        vm.toggleFilter(PinKind.Rumored)
        assertEquals(9,vm.visiblePins.size)
        assertTrue(vm.visiblePins.all {it.kind==PinKind.Event||it.kind==PinKind.Trailer})
        vm.toggleFilter(PinKind.Confirmed)
        assertEquals(32,vm.visiblePins.size)
    }
    @Test fun detailCloseClearsSelectionAndBackUnwindsMediaFirst() {
        val vm=TrackerViewModel(SavedStateHandle())
        val pin=vm.pins.first {it.kind==PinKind.Rumored}
        vm.selectPin(pin,true);vm.activatePin()
        assertEquals(pin,vm.detail)
        vm.openDetailMedia();assertTrue(vm.back());assertNull(vm.mediaId);assertNotNull(vm.detail)
        assertTrue(vm.back());assertNull(vm.detail);assertNull(vm.selected)
        vm.advancePhase(BootPhase.Live);assertFalse(vm.back())
    }
    @Test fun newCameraRequestSupersedesPreviousAndFixturesAreValid() {
        val vm=TrackerViewModel(SavedStateHandle())
        assertEquals(vm.pins.size,vm.pins.map {it.id}.toSet().size)
        assertTrue(vm.pins.all {it.lat in -78.0..81.0 && it.lon in -180.0..180.0})
        vm.selectPin(vm.pins[1],true);val revision=vm.camera.revision
        vm.selectPin(vm.pins[2],true)
        assertEquals(revision+1,vm.camera.revision)
        assertEquals(vm.pins[2].id,vm.selectedId)
        assertTrue(vm.isSeen(vm.pins[1].id))
        assertEquals(vm.logPins.sortedByDescending {it.date},vm.logPins)
    }
    @Test fun savedStateRestoresFiltersPhaseAndSection() {
        val saved=SavedStateHandle()
        TrackerViewModel(saved).apply {toggleFilter(PinKind.Rumored);chooseSound(true);openSection(Section.Log)}
        val restored=TrackerViewModel(saved)
        assertFalse(restored.rumored);assertTrue(restored.sound)
        assertEquals(BootPhase.Tutorial,restored.phase);assertEquals(Section.Log,restored.section)
    }
    @Test fun layoutsFillAvailableSpaceAndRadarTargetsStaySeparate() {
        listOf(390f to 693f,390f to 844f,320f to 640f,844f to 390f,768f to 1024f,390f to 330f).forEach {(w,h)->
            val m=TrackerLayout(w,h)
            assertEquals(h,m.pageHeight)
            assertEquals(27f,m.mapX);assertEquals(28f,m.mapY)
            assertEquals(w-54,m.mapWidth);assertEquals(h-86,m.mapHeight)
            assertEquals(h-58,m.shelfY)
            assertTrue(m.globalX>=0 && m.globalX+48<=w)
            assertTrue(m.locationX>=0 && m.locationX+48<=w)
            assertEquals(m.globalY+48,m.locationY)
            assertTrue(m.locationY+48<=m.shelfY)
        }
    }
    @Test fun explanationIsOncePerSectionAndToastReplacementIsSafe() {
        val vm=TrackerViewModel(SavedStateHandle())
        vm.openSection(Section.Log);vm.explainIfNeeded();assertEquals(Section.Log,vm.explanation)
        vm.dismissExplanation();vm.openSection(Section.Log);vm.explainIfNeeded();assertNull(vm.explanation)
        vm.notify("first");val old=vm.toastRevision;vm.notify("second");vm.clearToast(old)
        assertEquals("second",vm.toast)
    }
}
