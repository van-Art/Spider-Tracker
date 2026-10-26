package com.spidertracker.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class RadarGeographyTest {
    private fun marker(id:String,lat:Double,lon:Double,kind:MarkerKind=MarkerKind.Place,demo:Boolean=false)=
        LayerMarker(id,kind,lat,lon,"TEST",source="TEST FIXTURE",demo=demo)

    @Test fun cardinalBearingsAreNorthUpWithLinearDistance() {
        val dots=geographicRadar(listOf(marker("north",.01,0.0),marker("east",0.0,.01),
            marker("south",-.01,0.0),marker("west",0.0,-.01)),null,0.0,0.0,5).dots.associateBy {it.id}
        assertEquals(0.0,dots.getValue("north").bearingDegrees,1e-8)
        assertEquals(90.0,dots.getValue("east").bearingDegrees,1e-8)
        assertEquals(180.0,dots.getValue("south").bearingDegrees,1e-8)
        assertEquals(270.0,dots.getValue("west").bearingDegrees,1e-8)
        assertTrue(dots.getValue("north").y<0f && abs(dots.getValue("north").x)<1e-7)
        assertTrue(dots.getValue("east").x>0f && abs(dots.getValue("east").y)<1e-7)
        assertTrue(dots.getValue("south").y>0f && dots.getValue("west").x<0f)
        dots.values.forEach {assertEquals(it.distanceKm/5,hypot(it.x.toDouble(),it.y.toDouble()),1e-7)}
        val near=geographicRadar(listOf(marker("near",.005,0.0)),null,0.0,0.0,5).dots.single()
        assertEquals(dots.getValue("north").y/2,near.y,1e-7f)
    }

    @Test fun fixedRadiusIncludesBoundaryAndOmitsBeyondIt() {
        radarRangesKm.forEach {range->
            val edgeLatitude=Math.toDegrees(range/6371.0)
            val snapshot=geographicRadar(listOf(marker("inside",edgeLatitude*.5,0.0),
                marker("edge",edgeLatitude,0.0),marker("outside",edgeLatitude*1.001,0.0)),null,0.0,0.0,range)
            assertEquals(listOf("inside","edge"),snapshot.dots.map {it.id})
            assertEquals(1.0,hypot(snapshot.dots.last().x.toDouble(),snapshot.dots.last().y.toDouble()),1e-7)
        }
    }

    @Test fun datelineAndPolarPositionsRemainFinite() {
        val crossing=geographicRadar(listOf(marker("east",0.0,-179.99)),null,0.0,179.99,5).dots.single()
        assertEquals(2.2239,crossing.distanceKm,.001)
        assertEquals(90.0,crossing.bearingDegrees,1e-6)
        assertTrue(crossing.x>0)
        val polar=geographicRadar(listOf(marker("polar",89.999,179.0)),null,89.999,-179.0,1).dots.single()
        assertTrue(polar.x.isFinite() && polar.y.isFinite() && polar.bearingDegrees.isFinite())
    }

    @Test fun gpsUsesDeviceOriginAndOnlyItCreatesCenteredUserDot() {
        val fix=DeviceFix(51.5,-.1,8f,true)
        val markers=listOf(marker("nearby",51.51,-.1))
        val gps=geographicRadar(markers,fix,-50.0,100.0,5)
        assertEquals(RadarMode.Gps,gps.mode)
        assertEquals(fix.lat,gps.originLat!!,0.0);assertEquals(fix.lon,gps.originLon!!,0.0)
        assertEquals(2,gps.dots.size)
        val user=gps.dots.single {it.kind==MarkerKind.User}
        assertEquals(0f,user.x,0f);assertEquals(0f,user.y,0f)
        assertEquals(fix.lat,user.lat,0.0);assertEquals(fix.lon,user.lon,0.0)
        assertTrue(gps.label.contains("TEST GPS"))
        val map=geographicRadar(markers,null,51.51,-.1,5)
        assertEquals(RadarMode.Map,map.mode);assertFalse(map.dots.any {it.kind==MarkerKind.User})
        assertTrue(map.label.contains("MAP VIEWPORT"))
        assertEquals(0.0,map.dots.single().distanceKm,0.0)
        assertEquals(gps,geographicRadar(markers,fix,0.0,0.0,5))
    }

    @Test fun cameraAndGpsChangesRefreshPositionsWithoutMutatingInputs() {
        val marker=marker("real-record",.02,0.0)
        val markers=listOf(marker)
        val initial=geographicRadar(markers,null,0.0,0.0,5)
        val moved=geographicRadar(markers,null,.01,0.0,5)
        assertEquals(initial.dots.single().distanceKm/2,moved.dots.single().distanceKm,1e-8)
        val gpsMoved=geographicRadar(markers,DeviceFix(.015,0.0,10f),0.0,0.0,5)
        assertEquals(initial.dots.single().distanceKm/4,gpsMoved.dots.first().distanceKm,1e-8)
        assertEquals(listOf(marker),markers)
        assertEquals("real-record",gpsMoved.dots.first().id)
        assertEquals(.02,gpsMoved.dots.first().lat,0.0)
    }

    @Test fun disabledLayerInputRemovesOnlyItsDotsAndDemoNeverLooksLive() {
        val place=marker("attraction",.01,0.0)
        val demo=marker("demo",0.0,.01,MarkerKind.Alert,true)
        val both=geographicRadar(listOf(place,demo),null,0.0,0.0,5)
        assertTrue(both.demo);assertTrue(both.dots.single {it.id=="demo"}.demo)
        val places=geographicRadar(listOf(place),null,0.0,0.0,5)
        assertFalse(places.demo);assertEquals(listOf("attraction"),places.dots.map {it.id})
        val alerts=geographicRadar(listOf(demo),null,0.0,0.0,5)
        assertEquals(listOf("demo"),alerts.dots.map {it.id});assertTrue(alerts.demo)
        assertTrue(geographicRadar(emptyList(),null,0.0,0.0,5).dots.isEmpty())
        assertEquals(listOf(MarkerKind.User),geographicRadar(emptyList(),DeviceFix(0.0,0.0,1f),0.0,0.0,5).dots.map {it.kind})
        assertFalse(geographicRadar(listOf(demo.copy(lat=20.0)),null,0.0,0.0,5).demo)
    }

    @Test fun invalidCameraHasExplicitUnavailableOriginButGpsStillWorks() {
        listOf(Double.NaN to 0.0,91.0 to 0.0,0.0 to 181.0).forEach {(lat,lon)->
            val invalid=geographicRadar(emptyList(),null,lat,lon,20)
            assertEquals(RadarMode.Unavailable,invalid.mode)
            assertNull(invalid.originLat);assertNull(invalid.originLon);assertTrue(invalid.dots.isEmpty())
            assertTrue(invalid.label.contains("UNAVAILABLE"))
            assertEquals(RadarMode.Gps,geographicRadar(emptyList(),DeviceFix(0.0,0.0,1f),lat,lon,20).mode)
        }
    }

    @Test fun invalidRangesAreRejectedAndRepeatedRecordsKeepStableIds() {
        listOf(0,2,Int.MAX_VALUE).forEach {range->
            try {geographicRadar(emptyList(),null,0.0,0.0,range);fail("Invalid range accepted")}
            catch(expected:IllegalArgumentException) {assertEquals("Unsupported radar range",expected.message)}
        }
        val place=marker("stable",0.0,.01)
        val result=geographicRadar(listOf(place,place),null,0.0,0.0,5)
        assertEquals(listOf("stable"),result.dots.map {it.id})
        assertTrue(result.label.contains("VISIBLE MAP AREA"))
    }

    @Test fun viewportShowsDistantRecordsAndUsesOnlyVisibleProjectedPositions() {
        val records=listOf(marker("paris",48.85826,2.2945),marker("alert",51.507,-.128,MarkerKind.Alert))
        val world=viewportRadar(records,null,0.0,0.0) {lat,lon->(lon/180).toFloat() to (-lat/90).toFloat()}
        assertEquals(records.map {it.id},world.dots.map {it.id})
        assertTrue(world.dots.all {it.distanceKm>100})
        assertFalse(world.dots.any {it.kind==MarkerKind.User})
        val zoomed=viewportRadar(records,null,48.85826,2.2945) {lat,lon->if(lat<50)0f to 0f else 2f to 2f}
        assertEquals(listOf("paris"),zoomed.dots.map {it.id})
        assertEquals(0f,zoomed.dots.single().x,0f)
        val panned=viewportRadar(records,null,51.5,-.1) {lat,lon->if(lat>50).5f to -.5f else null}
        assertEquals(listOf("alert"),panned.dots.map {it.id})
        assertTrue(viewportRadar(emptyList(),null,0.0,0.0) {_,_->0f to 0f}.dots.isEmpty())
    }

    @Test fun viewportCornersStayInsideWebAndInvalidProjectionCannotCreateDots() {
        listOf(-1f to -1f,-1f to 1f,1f to -1f,1f to 1f).forEach {point->
            val dot=viewportRadar(listOf(marker("corner",0.0,0.0)),null,0.0,0.0) {_,_->point}.dots.single()
            assertTrue(hypot(dot.x,dot.y)<=1.000001f)
        }
        listOf(Float.NaN to 0f,0f to Float.POSITIVE_INFINITY,1.01f to 0f).forEach {point->
            assertTrue(viewportRadar(listOf(marker("bad",0.0,0.0)),null,0.0,0.0) {_,_->point}.dots.isEmpty())
        }
        val fix=DeviceFix(51.5,-.1,8f)
        val user=viewportRadar(emptyList(),fix,0.0,0.0) {_,_->.5f to 0f}.dots.single()
        assertEquals(MarkerKind.User,user.kind);assertEquals(.5f,user.x,0f)
        assertTrue(viewportRadar(emptyList(),fix,0.0,0.0) {_,_->null}.dots.isEmpty())
    }

    @Test fun denseFeaturesAreBoundedPerKindAndKeepRepositoryIds() {
        val records=(0 until 20000).map {marker("place-$it",it*.001,0.0)}+
            marker("alert",0.0,0.0,MarkerKind.Alert)
        val dense=viewportRadar(records,null,0.0,0.0) {_,_->0f to 0f}
        assertEquals(listOf("place-0","alert"),dense.dots.map {it.id})
        val spread=viewportRadar(records,null,0.0,0.0) {lat,_->
            val i=(lat*1000).toInt();(i%100/50f-1) to (i/100%100/50f-1)
        }
        assertTrue(spread.dots.size<=512)
        assertTrue(spread.dots.any {it.kind==MarkerKind.Alert})
    }
}
