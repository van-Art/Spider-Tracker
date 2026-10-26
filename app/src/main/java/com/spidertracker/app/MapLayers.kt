package com.spidertracker.app

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.*

enum class PlaceCategory(val label:String) {
    Food("CAFES / RESTAURANTS"), Hospital("HOSPITALS"), Pharmacy("PHARMACIES"),
    Parking("PARKING"), Fuel("GAS STATIONS"), Supermarket("SUPERMARKETS"), Attraction("ATTRACTIONS"),
    Historic("HISTORIC LANDMARKS"), Museum("MUSEUMS"), Monument("MONUMENTS"), Building("FAMOUS BUILDINGS"),
    Park("NATIONAL PARKS"), Natural("NATURAL LANDMARKS"), Cultural("CULTURAL ATTRACTIONS")
}
val attractionCategories=setOf(PlaceCategory.Historic,PlaceCategory.Museum,PlaceCategory.Monument,PlaceCategory.Building,PlaceCategory.Park,PlaceCategory.Natural,PlaceCategory.Cultural)

enum class AlertCategory(val label:String) {
    Accident("ACCIDENTS"), Closure("ROAD CLOSURES"), Roadworks("ROADWORKS"),
    Obstacle("OBSTACLES"), Restriction("RESTRICTIONS"), Hazard("REPORTED HAZARDS")
}
enum class MarkerKind { Place, Alert, User }
data class MapLayerState(val placesEnabled:Boolean=true,val alertsEnabled:Boolean=true,
    val placeCategories:Set<PlaceCategory> = PlaceCategory.entries.toSet(),
    val alertCategories:Set<AlertCategory> = AlertCategory.entries.toSet())

data class LayerMarker(val id:String,val kind:MarkerKind,val lat:Double,val lon:Double,
    val category:String,val name:String?=null,val address:String?=null,val description:String?=null,
    val reportedAt:String?=null,val source:String,val verification:String?=null,val demo:Boolean=false,
    val country:String?=null,val locality:String?=null,val severity:String?=null,val infoUrl:String?=null,val localizedNames:Map<String,String> = emptyMap()) {
    init {require(id.isNotBlank() && validCoordinates(lat,lon) && kind!=MarkerKind.User)}
}
data class OsmPlaceRecord(val id:String,val lat:Double,val lon:Double,val tags:Map<String,String>)
interface ConfirmedPlacesRepository {fun places(records:List<OsmPlaceRecord>):List<LayerMarker>}

/** Uses the existing map's OSM vector POI data, without a second network service. */
class OsmConfirmedPlacesRepository:ConfirmedPlacesRepository {
    override fun places(records:List<OsmPlaceRecord>)=records.asSequence().mapNotNull {record->
        if(!validCoordinates(record.lat,record.lon) || record.id.isBlank())return@mapNotNull null
        val t=record.tags
        fun text(key:String)=t[key]?.replace(Regex("[\\p{Cntrl}\\s]+")," ")?.trim()?.take(300)?.takeIf {it.isNotBlank()}
        val category=placeCategory(text("subclass"),text("class"),text("amenity"),text("shop"),text("tourism")) ?: return@mapNotNull null
        val name=text("name:en") ?: text("name:latin") ?: text("name") ?: text("name_en")
        val address=text("addr:full") ?: listOfNotNull(text("addr:housenumber"),text("addr:street"),text("addr:city"),text("addr:postcode")).joinToString(" ").takeIf {it.isNotBlank()}
        LayerMarker("osm:${record.id}",MarkerKind.Place,record.lat,record.lon,category.name,name,address,
            source="OpenStreetMap · OpenFreeMap tiles",verification="OSM DATA · NOT INDEPENDENTLY VERIFIED")
    }.distinctBy {it.id}.take(1000).toList()
}

fun placeCategory(subclass:String?,klass:String?,amenity:String?=null,shop:String?=null,tourism:String?=null):PlaceCategory? {
    val specific=setOfNotNull(subclass,amenity,shop,tourism)
    val values=if(specific.isEmpty())setOfNotNull(klass) else specific
    return when {
        values.any {it in setOf("cafe","restaurant","fast_food","food_court")}->PlaceCategory.Food
        "hospital" in values->PlaceCategory.Hospital
        "pharmacy" in values->PlaceCategory.Pharmacy
        "parking" in values->PlaceCategory.Parking
        "fuel" in values->PlaceCategory.Fuel
        "supermarket" in values->PlaceCategory.Supermarket
        values.any {it in setOf("attraction","museum","viewpoint","theme_park","zoo")}->PlaceCategory.Attraction
        else->null
    }
}

interface LiveAlertsRepository {
    val status:String
    fun reports():List<LayerMarker>
}
class DemoLiveAlertsRepository:LiveAlertsRepository {
    override val status="DEMO ALERTS · NO LIVE FEED"
    override fun reports()=AlertCategory.entries.mapIndexed {i,category->
        LayerMarker("demo-alert-$i",MarkerKind.Alert,51.507+i*.004,-.128+i*.006,category.name,
            description="DEMO ${category.label.lowercase()} scenario. This is not an actual incident or traffic report.",
            source="Bundled demonstration",verification="DEMO · UNVERIFIED",demo=true)
    }
}

class MapFiltersViewModel(private val preferences:SharedPreferences?=null,
    private val placesRepository:ConfirmedPlacesRepository=OsmConfirmedPlacesRepository(),
    private val alertsRepository:LiveAlertsRepository=DemoLiveAlertsRepository(),
    private val globalAttractions:GlobalAttractionsRepository?=null,
    private val attractionDispatcher:CoroutineDispatcher=Dispatchers.IO):ViewModel() {
    var state by mutableStateOf(readState());private set
    var radarRangeKm by mutableStateOf((preferences?.getInt("radar-range-km",20) ?: 20).takeIf {it in radarRangesKm} ?: 20);private set
    fun setRadarRange(km:Int) {require(km in radarRangesKm);radarRangeKm=km;preferences?.edit()?.putInt("radar-range-km",km)?.apply()}
    var filterKind by mutableStateOf<MarkerKind?>(null);private set
    var selected by mutableStateOf<LayerMarker?>(null);private set
    var placesStatus by mutableStateOf("ZOOM IN FOR OSM PLACES");private set
    val alertsStatus=alertsRepository.status
    var layerNotice by mutableStateOf<String?>(null);private set
    private var noticeJob:Job?=null
    private var places=emptyList<LayerMarker>()
    private var attractionJob:Job?=null
    private var attractionViewport:Pair<AttractionBounds,Boolean>?=null
    private val alerts=alertsRepository.reports().filter {it.kind==MarkerKind.Alert}.distinctBy {it.id}.take(1000)
    var visibleMarkers by mutableStateOf<List<LayerMarker>>(emptyList());private set
    init {
        refresh()
        if(globalAttractions!=null)attractionJob=viewModelScope.launch {
            places=withContext(attractionDispatcher){globalAttractions.load()};refresh()
        }
    }
    fun loadViewport(bounds:AttractionBounds,zoom:Double) {
        val repository=globalAttractions ?: return
        val request=bounds to (zoom>=4)
        if(request==attractionViewport)return
        attractionViewport=request
        attractionJob?.cancel()
        attractionJob=viewModelScope.launch {
            delay(250)
            val result=runCatching {withContext(attractionDispatcher){repository.loadViewport(bounds,zoom)}}
            result.onSuccess {places=it;refresh()}.onFailure {error->
                if(error is kotlinx.coroutines.CancellationException)throw error
                placesStatus="REGIONAL DATA UNAVAILABLE · COVERAGE INCOMPLETE"
            }
        }
    }
    private fun readState():MapLayerState {
        fun <T:Enum<T>> read(key:String,values:List<T>):Set<T> {
            val stored=preferences?.getStringSet(key,null) ?: return values.toSet()
            return values.filter {it.name in stored}.toSet()
        }
        return MapLayerState(preferences?.getBoolean("places-enabled",true) ?: true,preferences?.getBoolean("alerts-enabled",true) ?: true,
            (if(preferences?.contains("attraction-categories")==true)read("attraction-categories",PlaceCategory.entries)
            else {val old=preferences?.getStringSet("place-categories",null);if(old!=null && old.isEmpty())emptySet() else PlaceCategory.entries.toSet()}),
            read("alert-categories",AlertCategory.entries))
    }
    private fun refresh() {
        val selectedPlaces=state.placeCategories.map {it.name}.toSet()
        val selectedAlerts=state.alertCategories.map {it.name}.toSet()
        visibleMarkers=(if(state.placesEnabled)places.filter {it.category in selectedPlaces} else emptyList())+
            (if(state.alertsEnabled)alerts.filter {it.category in selectedAlerts} else emptyList())
        selected=selected?.let {previous->visibleMarkers.firstOrNull {it.id==previous.id && it.kind==previous.kind}}
    }
    private fun update(value:MapLayerState) {
        state=value
        preferences?.edit()?.putBoolean("places-enabled",value.placesEnabled)?.putBoolean("alerts-enabled",value.alertsEnabled)
            ?.putStringSet("attraction-categories",value.placeCategories.map {it.name}.toSet())
            ?.putStringSet("alert-categories",value.alertCategories.map {it.name}.toSet())?.apply()
        refresh()
    }
    fun toggle(kind:MarkerKind) {
        when(kind) {
            MarkerKind.Place->update(state.copy(placesEnabled=!state.placesEnabled))
            MarkerKind.Alert->update(state.copy(alertsEnabled=!state.alertsEnabled))
            else->return
        }
        val enabled=if(kind==MarkerKind.Place)state.placesEnabled else state.alertsEnabled
        showNotice((if(kind==MarkerKind.Place)"MAIN ATTRACTIONS" else "LIVE ALERTS")+": "+if(enabled)"ON" else "OFF")
    }
    fun showNotice(message:String,durationMillis:Long=1800) {
        layerNotice=message
        noticeJob?.cancel()
        noticeJob=viewModelScope.launch {delay(durationMillis);layerNotice=null}
    }
    fun toggleCategory(category:PlaceCategory) {update(state.copy(placeCategories=state.placeCategories.toMutableSet().apply {if(!add(category))remove(category)}))}
    fun toggleCategory(category:AlertCategory) {update(state.copy(alertCategories=state.alertCategories.toMutableSet().apply {if(!add(category))remove(category)}))}
    fun showFilters(kind:MarkerKind) {if(kind!=MarkerKind.User){filterKind=kind;selected=null}}
    fun closeFilters(){filterKind=null}
    fun select(marker:LayerMarker) {if(marker in visibleMarkers){selected=marker;filterKind=null}}
    fun closeDetails(){selected=null}
    fun acceptPlaces(records:List<OsmPlaceRecord>,status:String) {if(globalAttractions==null){places=placesRepository.places(records);placesStatus=status;refresh()}}
}

data class MarkerPulse(val radius:Float,val opacity:Float)
object MarkerAnimationState {
    fun period(kind:MarkerKind)=when(kind){MarkerKind.User->1.8f;MarkerKind.Place->2.4f;MarkerKind.Alert->1.6f}
    fun sample(kind:MarkerKind,seconds:Float,reducedMotion:Boolean=false,cluster:Boolean=false):MarkerPulse {
        if(reducedMotion)return MarkerPulse(25f,if(cluster).2f else .45f)
        val period=if(cluster)2.8f else period(kind)
        val fraction=(seconds.coerceAtLeast(0f)%period)/period
        return MarkerPulse(20+18*fraction,(if(cluster).3f else .7f)*(1-fraction))
    }
}
fun distanceKm(lat:Double,lon:Double,targetLat:Double,targetLon:Double):Double {
    val a=sin(Math.toRadians(targetLat-lat)/2).pow(2)+cos(Math.toRadians(lat))*cos(Math.toRadians(targetLat))*sin(Math.toRadians(targetLon-lon)/2).pow(2)
    return 6371*2*atan2(sqrt(a.coerceIn(0.0,1.0)),sqrt((1-a).coerceIn(0.0,1.0)))
}
