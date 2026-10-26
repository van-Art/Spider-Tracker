package com.spidertracker.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val radarRangesKm=listOf(1,5,20,100)
enum class RadarMode { Gps, Map, Unavailable }

data class RadarFeatureDot(val id:String,val x:Float,val y:Float,val kind:MarkerKind,
    val lat:Double,val lon:Double,val distanceKm:Double,val bearingDegrees:Double,val demo:Boolean=false)

data class RadarSnapshot(val mode:RadarMode,val originLat:Double?,val originLon:Double?,
    val rangeKm:Int,val dots:List<RadarFeatureDot>,val testLocation:Boolean=false) {
    val label:String get()=(when(mode) {
        RadarMode.Gps->if(testLocation)"TEST GPS" else "GPS"
        RadarMode.Map->"MAP VIEWPORT"
        RadarMode.Unavailable->"RADAR ORIGIN UNAVAILABLE"
    })+(if(mode==RadarMode.Map)" · VISIBLE MAP AREA" else " · $rangeKm KM · NORTH UP")
    val demo:Boolean get()=dots.any {it.kind==MarkerKind.Alert && it.demo}
}

// One crisp dot of each kind per cell, bounded independently so alerts cannot hide attractions.
internal fun compactRadarDots(dots:List<RadarFeatureDot>):List<RadarFeatureDot> =
    dots.distinctBy {Triple(it.kind,((it.x+1)*12).toInt(),((it.y+1)*12).toInt())}
        .groupBy {it.kind}.values.flatMap {it.take(256)}

/** Projection comes from the native map, including its Mercator scale, bearing and longitude wrap. */
fun viewportRadar(markers:List<LayerMarker>,fix:DeviceFix?,lat:Double,lon:Double,
    project:(Double,Double)->Pair<Float,Float>?):RadarSnapshot {
    val dots=markers.distinctBy {it.kind to it.id}.mapNotNull {marker->
        val (x,y)=project(marker.lat,marker.lon) ?: return@mapNotNull null
        if(!x.isFinite() || !y.isFinite() || x !in -1f..1f || y !in -1f..1f)return@mapNotNull null
        // Map the viewport square into the web's inscribed disk without discarding its corners.
        RadarFeatureDot(marker.id,x*sqrt(1-y*y/2),y*sqrt(1-x*x/2),marker.kind,
            marker.lat,marker.lon,distanceKm(lat,lon,marker.lat,marker.lon),0.0,marker.demo)
    }.toMutableList()
    fix?.let {user->project(user.lat,user.lon)?.let {(x,y)->
        if(x.isFinite() && y.isFinite() && x in -1f..1f && y in -1f..1f)
            dots+=RadarFeatureDot("user-location",x*sqrt(1-y*y/2),y*sqrt(1-x*x/2),MarkerKind.User,
                user.lat,user.lon,distanceKm(lat,lon,user.lat,user.lon),0.0)
    }}
    return RadarSnapshot(RadarMode.Map,lat,lon,20,compactRadarDots(dots),fix?.test==true)
}

/** Pure geography, called when coordinates or filters change; screen animation does not call this. */
fun geographicRadar(markers:List<LayerMarker>,fix:DeviceFix?,lat:Double,lon:Double,rangeKm:Int=20):RadarSnapshot {
    require(rangeKm in radarRangesKm) {"Unsupported radar range"}
    val mode=if(fix!=null)RadarMode.Gps else if(validCoordinates(lat,lon))RadarMode.Map else RadarMode.Unavailable
    if(mode==RadarMode.Unavailable)return RadarSnapshot(mode,null,null,rangeKm,emptyList())
    // DeviceFix is the latest fix already accepted by the Android provider's age/accuracy validation.
    val originLat=fix?.lat ?: lat
    val originLon=fix?.lon ?: lon
    val originRadians=Math.toRadians(originLat)
    val dots=markers.distinctBy {it.kind to it.id}.mapNotNull {marker->
        val distance=distanceKm(originLat,originLon,marker.lat,marker.lon)
        if(distance>rangeKm+1e-9)return@mapNotNull null
        val latitude=Math.toRadians(marker.lat)
        val longitudeDelta=Math.toRadians(marker.lon-originLon)
        val bearing=if(distance==0.0)0.0 else atan2(sin(longitudeDelta)*cos(latitude),
            cos(originRadians)*sin(latitude)-sin(originRadians)*cos(latitude)*cos(longitudeDelta))
        val radius=(distance/rangeKm).coerceIn(0.0,1.0)
        RadarFeatureDot(marker.id,(radius*sin(bearing)).toFloat(),(-radius*cos(bearing)).toFloat(),marker.kind,
            marker.lat,marker.lon,distance,(Math.toDegrees(bearing)+360.0)%360.0,marker.demo)
    }.toMutableList()
    if(fix!=null)dots+=RadarFeatureDot("user-location",0f,0f,MarkerKind.User,fix.lat,fix.lon,0.0,0.0)
    return RadarSnapshot(mode,originLat,originLon,rangeKm,compactRadarDots(dots),fix?.test==true)
}
