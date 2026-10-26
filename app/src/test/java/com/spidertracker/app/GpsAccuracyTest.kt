package com.spidertracker.app

import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class GpsAccuracyTest {
    @Test fun accuracyBoundaryUsesMetersAroundTheExactMarkerCoordinate() {
        for((lat,lon) in listOf(0.0 to 0.0,40.17 to 44.50,80.0 to 12.0,-70.0 to -179.999)) {
            for(accuracy in listOf(0f,8f,125f,2000f)) {
                val fix=DeviceFix(lat,lon,accuracy,elapsedMs=1)
                val features=userLocationFeatures(fix).features()!!
                val marker=features.single {it.geometry() is Point}.geometry() as Point
                assertEquals(lon,marker.longitude(),0.0);assertEquals(lat,marker.latitude(),0.0)
                val ring=(features.single {it.geometry() is Polygon}.geometry() as Polygon).coordinates().single()
                assertEquals(65,ring.size);assertEquals(ring.first(),ring.last())
                ring.forEach {point->assertEquals(accuracy.toDouble(),distanceKm(marker.latitude(),marker.longitude(),point.latitude(),point.longitude())*1000,.00001)}
            }
        }
    }

    @Test fun missingFixRemovesBothMarkerAndAccuracyArea() {
        assertTrue(userLocationFeatures(null).features()!!.isEmpty())
    }
}
