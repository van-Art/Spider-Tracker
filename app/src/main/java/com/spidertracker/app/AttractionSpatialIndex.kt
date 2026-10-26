package com.spidertracker.app

import kotlin.math.floor

data class AttractionBounds(val south:Double,val west:Double,val north:Double,val east:Double) {
    companion object {
        fun fromMap(south:Double,west:Double,north:Double,east:Double):AttractionBounds {
            fun wrap(lon:Double)=((lon+180)%360+360)%360-180
            return if(east-west>=360)AttractionBounds(south,-180.0,north,180.0)
                else AttractionBounds(south,wrap(west),north,wrap(east))
        }
    }
    init {require(south.isFinite() && north.isFinite() && south>=-90 && north<=90 && south<=north);require(west in -180.0..180.0 && east in -180.0..180.0)}
    fun contains(lat:Double,lon:Double)=lat in south..north && (if(west<=east)lon in west..east else lon>=west || lon<=east)
    fun intersects(other:AttractionBounds):Boolean = south<=other.north && north>=other.south &&
        (containsLongitude(other.west) || containsLongitude(other.east) || other.containsLongitude(west))
    private fun containsLongitude(lon:Double)=if(west<=east)lon in west..east else lon>=west || lon<=east
}

/** Five-degree buckets bound queries; IDs deduplicate records arriving from overlapping packs. */
class AttractionSpatialIndex(records:List<LayerMarker>) {
    private val cells=records.distinctBy {it.id}.groupBy {cell(it.lat,it.lon)}
    fun query(bounds:AttractionBounds):List<LayerMarker> = cells.asSequence().filter {(key,_)->
        val south=key.first*5.0-90;val west=key.second*5.0-180
        bounds.intersects(AttractionBounds(south,west,(south+5).coerceAtMost(90.0),(west+5).coerceAtMost(180.0)))
    }.flatMap {it.value.asSequence()}.filter {bounds.contains(it.lat,it.lon)}.toList()
    private fun cell(lat:Double,lon:Double)=floor((lat+90)/5).toInt().coerceIn(0,35) to floor((lon+180)/5).toInt().coerceIn(0,71)
}
