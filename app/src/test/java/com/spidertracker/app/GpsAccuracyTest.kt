package com.spidertracker.app

import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class GpsAccuracyTest {
    @Test fun accuracyBoundaryUsesMetersAroundExactlyOneUserPoint() {
        for((lat,lon) in listOf(0.0 to 0.0,40.17 to 44.50,80.0 to 12.0,-70.0 to -179.999,40.0 to 179.999)) {
            for(accuracy in listOf(0f,8f,125f,2000f)) {
                val features=userLocationFeatures(DeviceFix(lat,lon,accuracy,elapsedMs=1)).features()!!
                assertEquals(2,features.size)
                val user=features.single {it.geometry() is Point}
                assertEquals("user",user.getStringProperty("id"))
                val marker=user.geometry() as Point
                assertEquals(lon,marker.longitude(),0.0);assertEquals(lat,marker.latitude(),0.0)
                val ring=(features.single {it.geometry() is Polygon}.geometry() as Polygon).coordinates().single()
                assertEquals(65,ring.size);assertEquals(ring.first(),ring.last())
                ring.forEach {point->assertEquals(accuracy.toDouble(),distanceKm(lat,lon,point.latitude(),point.longitude())*1000,.00001)}
            }
        }
    }

    @Test fun missingFixRemovesBothMarkerAndAccuracyArea() {
        assertTrue(userLocationFeatures(null).features()!!.isEmpty())
    }
}
