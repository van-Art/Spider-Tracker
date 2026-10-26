package com.spidertracker.app

import org.junit.Assert.*
import org.junit.Test

class Phase316AttractionsTest {
    private fun marker(id:String,lat:Double,lon:Double)=LayerMarker(id,MarkerKind.Place,lat,lon,"Historic",source="TEST")
    @Test fun spatialQueriesDeduplicateAndCrossDatelineWithoutLosingPoles() {
        val east=marker("east",0.0,179.9);val west=marker("west",0.0,-179.9)
        val north=marker("north",90.0,180.0);val elsewhere=marker("elsewhere",40.0,44.0)
        val index=AttractionSpatialIndex(listOf(east,west,east,north,elsewhere))
        assertEquals(setOf("east","west"),index.query(AttractionBounds(-1.0,179.0,1.0,-179.0)).map {it.id}.toSet())
        assertEquals(listOf(north),index.query(AttractionBounds(89.0,170.0,90.0,180.0)))
        assertEquals(4,index.query(AttractionBounds(-90.0,-180.0,90.0,180.0)).size)
        assertTrue(index.query(AttractionBounds(-10.0,-10.0,10.0,10.0)).isEmpty())
        assertThrows(IllegalArgumentException::class.java){AttractionBounds(-91.0,0.0,0.0,1.0)}
        assertThrows(IllegalArgumentException::class.java){AttractionBounds(0.0,170.0,1.0,181.0)}
        assertEquals(AttractionBounds(-10.0,170.0,10.0,-170.0),AttractionBounds.fromMap(-10.0,170.0,10.0,190.0))
        assertEquals(AttractionBounds(-90.0,-180.0,90.0,180.0),AttractionBounds.fromMap(-90.0,-300.0,90.0,300.0))
    }
    @Test fun clusterPulseHasOneSlowerSubtleCycleAndStaticReducedMotion() {
        val start=MarkerAnimationState.sample(MarkerKind.Place,0f,cluster=true)
        val half=MarkerAnimationState.sample(MarkerKind.Place,1.4f,cluster=true)
        assertTrue(half.radius>start.radius);assertTrue(half.opacity<start.opacity)
        assertEquals(start,MarkerAnimationState.sample(MarkerKind.Alert,2.8f,cluster=true))
        assertTrue(start.opacity<MarkerAnimationState.sample(MarkerKind.Place,0f).opacity)
        assertEquals(MarkerAnimationState.sample(MarkerKind.User,0f,true,true),MarkerAnimationState.sample(MarkerKind.User,100f,true,true))
    }
}
