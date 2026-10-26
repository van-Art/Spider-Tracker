package com.spidertracker.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

interface GlobalAttractionsRepository {
    fun load(language:String=Locale.getDefault().language):List<LayerMarker>
    fun applyUpdates(payload:String)
    fun loadViewport(bounds:AttractionBounds,zoom:Double):List<LayerMarker> = load()
}

data class AttractionEntity(val marker:LayerMarker,val countryIds:Set<String> = emptySet(),val lastUpdated:String?=null,val revision:Long?=null)
internal data class AttractionsCatalog(val version:Long,val entities:List<AttractionEntity>,val removed:Set<String>) {
    val markers get()=entities.map {it.marker}
}
data class AttractionRegion(val id:String,val bounds:AttractionBounds)
fun interface AttractionDataSource {
    fun read():String
    fun regions():List<AttractionRegion> = emptyList()
    fun readRegion(id:String):String? = null
}

/** Local, atomic storage only; no public-service requests on camera movement. */
class AttractionCache(file:File) {
    private val file=AtomicFile(file)
    fun read():String?=runCatching {file.openRead().use {stream->require(stream.channel.size()<=16_000_000);stream.bufferedReader().readText().also {body->require(body.length<=4_000_000)}}}.getOrNull()
    fun write(body:String) {
        val stream=file.startWrite()
        try {stream.write(body.toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
        catch(error:Exception){file.failWrite(stream);throw error}
    }
}

class LocalAttractionDataSource(context:Context):AttractionDataSource {
    private val assets=context.applicationContext.assets
    private val directory=File(context.applicationContext.filesDir,"attraction-regions")
    override fun read()=assets.open("global-attractions.json").bufferedReader().use {it.readText()}
    override fun regions():List<AttractionRegion> {
        fun decode(body:String):Pair<Long,List<AttractionRegion>> {
            val json=JSONObject(body);require(json.getInt("schema")==1)
            val version=json.getLong("version");require(version>0)
            val rows=json.getJSONArray("regions");require(rows.length()<=1000)
            return version to (0 until rows.length()).map {i->
                val row=rows.getJSONObject(i);val id=row.getString("id");require(id.matches(Regex("Q[1-9][0-9]*")))
                AttractionRegion(id,AttractionBounds(row.getDouble("south"),row.getDouble("west"),row.getDouble("north"),row.getDouble("east")))
            }.distinctBy {it.id}
        }
        val stored=AttractionCache(File(directory,"index.json")).read()?.let {runCatching {decode(it)}.getOrNull()}
        val shipped=runCatching {assets.open("attraction-regions/index.json").bufferedReader().use {decode(it.readText())}}.getOrNull()
        return listOfNotNull(stored,shipped).maxByOrNull {it.first}?.second.orEmpty()
    }
    override fun readRegion(id:String):String? {
        require(id.matches(Regex("Q[1-9][0-9]*")))
        val cache=AttractionCache(File(directory,"$id.json"))
        val stored=cache.read()?.let {body->runCatching {parseAttractions(body).version to body}.getOrNull()}
        val shipped=runCatching {assets.open("attraction-regions/$id.json").bufferedReader().use {it.readText()}.let {parseAttractions(it).version to it}}.getOrNull()
        val newest=listOfNotNull(stored,shipped).maxByOrNull {it.first} ?: return null
        if(newest!=stored)cache.write(newest.second)
        return newest.second
    }
}
internal fun parseAttractions(payload:String):AttractionsCatalog {
    require(payload.length<=4_000_000) {"Attraction catalog is too large"}
    val json=JSONObject(payload)
    require(json.getInt("schema")==1 && json.getString("license")=="CC0-1.0" && json.getString("source")=="Wikidata")
    val version=json.getLong("version");require(version>0)
    val records=json.getJSONArray("attractions");require(records.length()<=20000)
    fun id(value:String):String {require(value.matches(Regex("wikidata:Q[1-9][0-9]*")));return value}
    val markers=(0 until records.length()).map {index->
        val row=records.getJSONObject(index)
        fun text(key:String)=row.optString(key,"").takeUnless {it=="null"}?.replace(Regex("[\\p{Cntrl}\\s]+")," ")?.trim()?.take(300)?.takeIf {it.isNotBlank()}
        val stableId=id(row.getString("id"));val name=text("name");require(name!=null)
        val category=PlaceCategory.valueOf(row.getString("category"));require(category in attractionCategories)
        val source=row.getString("source");require(source=="https://www.wikidata.org/wiki/"+stableId.removePrefix("wikidata:"))
        val names=row.optJSONObject("names") ?: JSONObject()
        val localized=names.keys().asSequence().filter {it.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]+)?"))}.associateWith {
            names.getString(it).replace(Regex("[\\p{Cntrl}\\s]+")," ").trim().take(300)
        }.filterValues {it.isNotBlank()}
        val marker=LayerMarker(stableId,MarkerKind.Place,row.getDouble("lat"),row.getDouble("lon"),category.name,name,
            address=text("address"),source="Wikidata · CC0",verification="CURATED CATALOG · COVERAGE INCOMPLETE",
            country=text("country"),locality=text("locality"),infoUrl=source,localizedNames=localized)
        val countries=row.optJSONArray("countryIds") ?: JSONArray()
        val countryIds=(0 until countries.length()).map {countries.getString(it).also {value->require(value.matches(Regex("Q[1-9][0-9]*")))}}.toSet()
        val updated=text("lastUpdated")?.also {java.time.Instant.parse(it)}
        AttractionEntity(marker,countryIds,updated,row.optLong("revision",0).takeIf {it>0})
    }
    require(markers.map {it.marker.id}.distinct().size==markers.size) {"Duplicate attraction IDs"}
    val removed=json.optJSONArray("removed") ?: JSONArray();require(removed.length()<=20000)
    val deleted=(0 until removed.length()).map {id(removed.getString(it))}.toSet()
    require(markers.none {it.marker.id in deleted}) {"Conflicting attraction deletion and upsert"}
    return AttractionsCatalog(version,markers,deleted)
}

/** Offline-first catalog. Camera movement never downloads or discards its records. */
class CachedGlobalAttractionsRepository(private val bundled:AttractionDataSource,cacheFile:File):GlobalAttractionsRepository {
    constructor(context:Context):this(LocalAttractionDataSource(context),File(context.applicationContext.filesDir,"global-attractions.json"))
    private val cache=AttractionCache(cacheFile)
    private val regionDirectory=File(cacheFile.parentFile,"attraction-regions")
    private val regions by lazy {bundled.regions()}
    private val regionIndexes=object:LinkedHashMap<String,AttractionSpatialIndex>(4,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,AttractionSpatialIndex>?)=size>4
    }
    private fun regionBody(id:String):String? {
        val stored=AttractionCache(File(regionDirectory,"$id.json")).read()?.let {body->runCatching {parseAttractions(body).version to body}.getOrNull()}
        val shipped=runCatching {bundled.readRegion(id)?.let {body->parseAttractions(body).version to body}}.getOrNull()
        return listOfNotNull(stored,shipped).maxByOrNull {it.first}?.second
    }
    private var current:AttractionsCatalog?=null
    @Synchronized private fun catalog():AttractionsCatalog {
        current?.let {return it}
        val base=parseAttractions(bundled.read())
        val stored=cache.read()?.let {runCatching {parseAttractions(it)}.getOrNull()}
        return (stored?.takeIf {it.version>=base.version} ?: base).also {current=it}
    }
    @Synchronized override fun load(language:String):List<LayerMarker> = catalog().markers.map {marker->
        marker.copy(name=marker.localizedNames[language] ?: marker.localizedNames[language.substringBefore('-')] ?: marker.name)
    }
    @Synchronized override fun loadViewport(bounds:AttractionBounds,zoom:Double):List<LayerMarker> {
        val base=load()
        if(zoom<4)return base
        val detailed=regions.filter {it.bounds.intersects(bounds)}.flatMap {region->
            val index=regionIndexes[region.id] ?: runCatching {
                regionBody(region.id)?.let {AttractionSpatialIndex(parseAttractions(it).markers)}
            }.getOrNull()?.also {regionIndexes[region.id]=it}
            index?.query(bounds).orEmpty()
        }
        val language=Locale.getDefault().language
        return (base+detailed.map {it.copy(name=it.localizedNames[language] ?: it.name)}).distinctBy {it.id}
    }
    @Synchronized fun countryCoverage():Map<String,Int> = catalog().entities.flatMap {entity->entity.countryIds.map {it to entity.marker.id}}
        .distinct().groupingBy {it.first}.eachCount()
    @Synchronized fun applyRegionUpdates(id:String,payload:String) {
        require(id.matches(Regex("Q[1-9][0-9]*")) && regions.any {it.id==id})
        val base=regionBody(id) ?: error("Regional base unavailable")
        CachedGlobalAttractionsRepository(AttractionDataSource {base},File(regionDirectory,"$id.json")).applyUpdates(payload)
        regionIndexes.remove(id)
    }
    @Synchronized override fun applyUpdates(payload:String) {
        val update=parseAttractions(payload);val old=catalog()
        if(update.version<=old.version)return
        val merged=old.entities.associateBy {it.marker.id}.toMutableMap()
        update.removed.forEach(merged::remove);update.entities.forEach {merged[it.marker.id]=it}
        require(merged.size<=20000)
        val rows=JSONArray()
        merged.values.sortedBy {it.marker.id}.forEach {entity->
            val marker=entity.marker
            rows.put(JSONObject().put("id",marker.id).put("name",marker.name).put("lat",marker.lat).put("lon",marker.lon)
                .put("category",marker.category).put("country",marker.country).put("locality",marker.locality)
                .put("address",marker.address).put("source",marker.infoUrl).put("names",JSONObject(marker.localizedNames))
                .put("countryIds",JSONArray(entity.countryIds.toList())).put("lastUpdated",entity.lastUpdated).put("revision",entity.revision))
        }
        val body=JSONObject().put("schema",1).put("version",update.version).put("license","CC0-1.0").put("source","Wikidata")
            .put("incomplete",true).put("attractions",rows).put("removed",JSONArray()).toString()
        val parsed=parseAttractions(body)
        cache.write(body)
        current=parsed
    }
}
