package com.spidertracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import java.time.LocalDate

enum class PinKind { Confirmed, Rumored, Event, Trailer }
enum class Section(val title: String) {
    Map(""), Menu("NAVIGATION"), Log("ACTIVITY LOG"), Report("REPORT SIGHTINGS"),
    WebWatch("WEB WATCH 1.0"), Videos("VIDEOS"), Events("EVENTS"), Help("HELP")
}
enum class BootPhase { Boot, Sound, Tutorial, Loading, Live }
enum class SearchState { Idle, Loading, Results, Empty, Unavailable, Error }
enum class PlaceUiState { Map, Search, PlaceDetails, RoutePlanning, RouteReady, Navigating, Error }
enum class RouteEndpoint { Origin, Destination }
data class CameraTarget(val lat: Double, val lon: Double, val zoom: Double, val revision: Int = 0)
data class TrackerPin(val id: String, val title: String, val city: String, val lat: Double, val lon: Double,
    val kind: PinKind, val date: LocalDate, val art: Int)

object MockSightings {
    private val cities = listOf(
        Triple("New York",40.72,-74.0), Triple("London",51.50,-0.12), Triple("Paris",48.85,2.35),
        Triple("Columbus",40.00,-83.01), Triple("Los Angeles",34.05,-118.24), Triple("Chicago",41.88,-87.63),
        Triple("Mexico City",19.43,-99.13), Triple("São Paulo",-23.55,-46.63), Triple("Buenos Aires",-34.60,-58.38),
        Triple("Bogotá",4.71,-74.07), Triple("Madrid",40.42,-3.70), Triple("Rome",41.90,12.49),
        Triple("Berlin",52.52,13.40), Triple("Istanbul",41.01,28.98), Triple("Cairo",30.04,31.24),
        Triple("Tokyo",35.68,139.69), Triple("Hong Kong",22.32,114.17), Triple("Singapore",1.34,103.85),
        Triple("Jakarta",-6.21,106.85), Triple("Sydney",-33.87,151.21), Triple("Auckland",-36.85,174.76),
        Triple("Vancouver",49.28,-123.12), Triple("Toronto",43.65,-79.38), Triple("Lisbon",38.72,-9.14),
        Triple("Seoul",37.57,126.98), Triple("Bangkok",13.76,100.50), Triple("Lima",-12.05,-77.04),
        Triple("Dubai",25.20,55.27), Triple("Riyadh",24.71,46.67)
    )
    private val headlines = listOf("ROOFTOP SIGNAL ABOVE THE RIVERSIDE", "RED LIGHT MOVING ACROSS THE SKYLINE",
        "A NEW REPORT FROM THE OLD TOWN", "MYSTERY AT THE CENTRAL STATION", "NIGHT WATCH: A FIGURE ON THE BRIDGE",
        "NEIGHBORHOOD WATCH AT THE CITY SQUARE", "A DISTANT SIGNAL NEAR THE WATERFRONT")
    val pins: List<TrackerPin> = buildList {
        add(TrackerPin("trailer", "FIELD REPORT: FIRST CONTACT", "New York",40.744,-73.921,PinKind.Trailer,LocalDate.of(2026,6,16),0))
        repeat(52) { i ->
            val city=cities[i % cities.size]
            add(TrackerPin("sighting-$i",headlines[i%headlines.size],city.first,
                city.second + (i/cities.size)*0.12,city.third + (i/cities.size)*0.15,
                if(i<23) PinKind.Confirmed else PinKind.Rumored,LocalDate.of(2026,8,11).minusDays((i/4).toLong()),i%6))
        }
        listOf(20,4,6,12,15,17,27,28).forEachIndexed { i,index ->
            val city=cities[index]
            add(TrackerPin("event-$i","NIGHT WATCH MEETUP · ${city.first.uppercase()}",city.first,
                city.second,city.third,PinKind.Event,LocalDate.of(2026,8,5).minusDays(i.toLong()),i%6))
        }
    }
}

class TrackerViewModel(private val saved: SavedStateHandle) : ViewModel() {
    var phase by mutableStateOf(BootPhase.valueOf(saved["phase"] ?: "Boot")); private set
    var section by mutableStateOf(Section.valueOf(saved["section"] ?: "Map")); private set
    var confirmed by mutableStateOf(saved["confirmed"] ?: true); private set
    var rumored by mutableStateOf(saved["rumored"] ?: true); private set
    var sound by mutableStateOf(saved["sound"] ?: false); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var detailId by mutableStateOf<String?>(null); private set
    var mediaId by mutableStateOf<String?>(null); private set
    var explanation by mutableStateOf<Section?>(null); private set
    var camera by mutableStateOf(CameraTarget(15.973899714245507,-49.9007427311529,1.0)); private set
    var toast by mutableStateOf(""); private set
    var toastRevision by mutableIntStateOf(0); private set
    var credits by mutableStateOf(false)
    var videoPlaying by mutableStateOf(true)
    var videoIndex by mutableIntStateOf(0)
    var galleryExpanded by mutableStateOf(false)
    var searchQuery by mutableStateOf(saved["query"] ?: ""); private set
    var searchState by mutableStateOf(SearchState.Idle); private set
    var searchResults by mutableStateOf<List<AddressResult>>(emptyList()); private set
    var searchMessage by mutableStateOf(""); private set
    var searchVisible by mutableStateOf(false); private set
    var destination by mutableStateOf<AddressResult?>(null); private set
    var destinationMarkerId:String?=null; private set
    var placeState by mutableStateOf(PlaceUiState.Map); private set
    val uiState get()=if(searchVisible)PlaceUiState.Search else placeState
    var routeSearch by mutableStateOf<RouteEndpoint?>(null); private set
    var manualOrigin by mutableStateOf<AddressResult?>(null); private set
    fun showRoutePlanner() {if(destination!=null)placeState=PlaceUiState.RoutePlanning}
    fun routeState(value:PlaceUiState) {placeState=value}
    fun searchEndpoint(value:RouteEndpoint) {routeSearch=value;searchVisible=true;searchQuery="";searchResults=emptyList();searchState=SearchState.Idle;searchMessage="SEARCH OR LONG-PRESS MAP"}
    fun gpsOrigin() {manualOrigin=null;showRoutePlanner()}
    fun setMapOrigin(point:NavPoint) {manualOrigin=AddressResult("origin:${point.lat}:${point.lon}","SELECTED MAP POINT",point.lat,point.lon);hideSearch();showRoutePlanner()}
    fun routeBack() {hideSearch();routeSearch=null;placeState=if(destination==null)PlaceUiState.Map else PlaceUiState.PlaceDetails}
    var locating by mutableStateOf(false); private set
    var userFix by mutableStateOf<DeviceFix?>(null); private set
    fun navigationFix(fix:NavFix) {userFix=DeviceFix(fix.point.lat,fix.point.lon,fix.accuracy.toFloat(),fix.simulated,fix.elapsedMs)}
    var radarGpsCentered by mutableStateOf(false); private set
    private var centerOnFix=false
    fun exploreRadar() {radarGpsCentered=false;centerOnFix=false}
    private var searchJob:Job?=null
    private var locationJob:Job?=null
    private var searchRequestKey:String?=null
    private var searchRequestProvider:GeocodingProvider?=null
    private var searchRevision=0
    private var locationRevision=0
    private var mapZoom=1.0
    private val explained = (saved.get<ArrayList<String>>("explained") ?: arrayListOf()).toMutableSet()
    private val seen = (saved.get<ArrayList<String>>("seen") ?: arrayListOf()).toMutableSet()
    val pins = MockSightings.pins
    val selected get() = pins.find { it.id==selectedId }
    val detail get() = pins.find { it.id==detailId }
    val visiblePins get() = pins.filter { when(it.kind) {
        PinKind.Confirmed -> confirmed
        PinKind.Rumored -> rumored
        else -> true
    } }
    val logPins get() = visiblePins.sortedByDescending { it.date }
    fun isSeen(id:String) = id in seen
    fun advancePhase(value:BootPhase) { phase=value; saved["phase"]=value.name }
    fun chooseSound(value:Boolean) { sound=value; saved["sound"]=value; advancePhase(BootPhase.Tutorial) }
    fun toggleSound() { sound=!sound; saved["sound"]=sound }
    fun openSection(value:Section) {
        section=value; saved["section"]=value.name; selectedId=null; detailId=null; explanation=null
    }
    fun explainIfNeeded() {
        if(section in listOf(Section.Log,Section.Events,Section.WebWatch) && section.name !in explained) explanation=section
    }
    fun dismissExplanation() {
        explanation?.let { explained.add(it.name); saved["explained"]=ArrayList(explained) }; explanation=null
    }
    fun toggleMenu() { openSection(if(section==Section.Menu) Section.Map else Section.Menu) }
    fun toggleFilter(kind:PinKind) {
        when(kind) {
            PinKind.Confirmed -> { confirmed=!confirmed; saved["confirmed"]=confirmed }
            PinKind.Rumored -> { rumored=!rumored; saved["rumored"]=rumored }
            else -> return
        }
        selectedId=null
        notify("${kind.name.uppercase()} SIGHTINGS: ${if(if(kind==PinKind.Confirmed) confirmed else rumored) "ON" else "OFF"}")
    }
    fun selectPin(pin:TrackerPin, focus:Boolean=false) {
        cancelLocation();hideSearch()
        openSection(Section.Map)
        selectedId=pin.id; seen.add(pin.id); saved["seen"]=ArrayList(seen)
        if(focus || mapZoom<5) camera=CameraTarget(pin.lat,pin.lon,12.597060635860297,camera.revision+1)
    }
    fun clearCard() { selectedId=null }
    fun activatePin() {
        val pin=selected ?: return
        selectedId=null
        when(pin.kind) {
            PinKind.Rumored -> detailId=pin.id
            PinKind.Event -> openSection(Section.Events)
            PinKind.Trailer -> openSection(Section.Videos)
            PinKind.Confirmed -> mediaId=pin.id
        }
    }
    fun closeDetail() { detailId=null }
    fun openDetailMedia() { mediaId=detailId }
    fun openMedia(id:String) { if(pins.any {it.id==id}) mediaId=id }
    fun closeMedia() { mediaId=null }
    fun globalView() {
        exploreRadar()
        cancelLocation();hideSearch()
        selectedId=null; camera=CameraTarget(15.973899714245507,-49.9007427311529,1.0,camera.revision+1)
        notify("CENTERING TO GLOBAL VIEW")
    }
    fun editSearch(value:String,provider:GeocodingProvider?=null) {
        searchQuery=value.take(160);saved["query"]=searchQuery
        if(provider!=null && normalizedSearchQuery(searchQuery).length>=3) {
            requestSearch(provider,425)
        } else {
            searchJob?.cancel();searchJob=null;searchRevision++
            searchState=SearchState.Idle;searchResults=emptyList();searchVisible=false;searchMessage=""
            searchRequestKey=null;searchRequestProvider=null
        }
    }
    fun hideSearch() {
        searchJob?.cancel();searchJob=null;searchRevision++;searchVisible=false
        routeSearch=null
        if(searchState==SearchState.Loading){searchState=SearchState.Idle;searchMessage=""}
    }
    fun reopenSearch() {if(searchState!=SearchState.Idle)searchVisible=true}
    fun search(provider:GeocodingProvider)=requestSearch(provider,0)
    private fun requestSearch(provider:GeocodingProvider,debounceMs:Long) {
        val key=normalizedSearchQuery(searchQuery)
        if(searchRequestProvider===provider && searchRequestKey==key &&
            (searchJob?.isActive==true || searchState==SearchState.Results || searchState==SearchState.Empty)) {
            searchVisible=true;return
        }
        searchJob?.cancel();val revision=++searchRevision
        searchRequestKey=key;searchRequestProvider=provider
        searchVisible=true;searchResults=emptyList()
        if(key.length<3){searchState=SearchState.Error;searchMessage="ENTER AT LEAST THREE CHARACTERS";return}
        if(!provider.configured){searchState=SearchState.Unavailable;searchMessage="SEARCH UNAVAILABLE · NO PROVIDER CONFIGURED";return}
        searchState=SearchState.Loading;searchMessage="SEARCHING..."
        val query=searchQuery
        searchJob=viewModelScope.launch {
            try {
                delay(debounceMs)
                val results=provider.search(query).distinctBy {it.id}.take(5)
                if(revision!=searchRevision)return@launch
                searchResults=results
                searchState=if(results.isEmpty())SearchState.Empty else SearchState.Results
                searchMessage=if(results.isEmpty())"NO MATCHES · TRY ANOTHER ADDRESS" else "SELECT AN ADDRESS"
            } catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(revision==searchRevision){searchState=SearchState.Unavailable;searchMessage=if(error is SearchUnavailable)error.message.orEmpty() else "SEARCH UNAVAILABLE · TRY AGAIN"}}
        }
    }
    fun chooseDestination(result:AddressResult) {
        if(result !in searchResults)return
        if(routeSearch==RouteEndpoint.Origin) {
            manualOrigin=result;hideSearch();showRoutePlanner();return
        }
        val changing=routeSearch==RouteEndpoint.Destination
        exploreRadar()
        cancelLocation();searchJob?.cancel();searchRevision++
        destination=result;destinationMarkerId=null;searchQuery=result.label.take(160);saved["query"]=searchQuery
        searchVisible=false;searchState=SearchState.Idle;searchResults=emptyList()
        openSection(Section.Map)
        routeSearch=null;placeState=if(changing)PlaceUiState.RoutePlanning else PlaceUiState.PlaceDetails
        camera=CameraTarget(result.lat,result.lon,13.0,camera.revision+1)
    }
    fun navigateToPlace(marker:LayerMarker) {
        val changing=routeSearch
        val category=if(marker.kind==MarkerKind.Place)PlaceCategory.entries.firstOrNull {it.name==marker.category}?.label else AlertCategory.entries.firstOrNull {it.name==marker.category}?.label
        val result=AddressResult(marker.id,marker.name ?: category ?: marker.category,marker.lat,marker.lon,marker.name,marker.locality,marker.country,category ?: marker.category,marker.address)
        if(changing==RouteEndpoint.Origin){manualOrigin=result;hideSearch();showRoutePlanner();return}
        exploreRadar()
        cancelLocation();hideSearch();clearCard()
        destination=result;destinationMarkerId=marker.id;placeState=if(changing==RouteEndpoint.Destination)PlaceUiState.RoutePlanning else PlaceUiState.PlaceDetails
        camera=CameraTarget(marker.lat,marker.lon,13.0,camera.revision+1)
    }
    fun chooseMapDestination(lat:Double,lon:Double) {
        if(!validCoordinates(lat,lon))return
        cancelLocation();hideSearch();clearCard();openSection(Section.Map)
        destination=AddressResult("map:$lat:$lon",java.lang.String.format(java.util.Locale.ROOT,"MAP POINT %.5f, %.5f",lat,lon),lat,lon)
        destinationMarkerId=null
        placeState=PlaceUiState.PlaceDetails
        camera=CameraTarget(lat,lon,13.0,camera.revision+1)
    }
    fun clearDestination() {destination=null;destinationMarkerId=null;placeState=PlaceUiState.Map;routeSearch=null}
    fun observeZoom(zoom:Double) {if(zoom.isFinite())mapZoom=zoom}
    fun cancelLocation() {locationRevision++;locationJob?.cancel();locationJob=null;locating=false}
    fun locationDenied(permanent:Boolean=false) {userFix=null;cancelLocation();notify(if(permanent)"ENABLE LOCATION PERMISSION IN APP SETTINGS" else "LOCATION PERMISSION DENIED")}
    fun locate(provider:DeviceLocationProvider,centerCamera:Boolean=true,onCentered:()->Unit={}) {
        cancelLocation();val revision=locationRevision
        centerOnFix=centerCamera
        locating=true;hideSearch();clearCard()
        locationJob=viewModelScope.launch {
            try {
                // A short foreground session updates the dot without leaving idle GPS running.
                withTimeout(20000) {provider.updates().collect {fix->
                    if(revision==locationRevision) {
                        val first=locating
                        userFix=fix;locating=false
                        if(first) {
                            radarGpsCentered=centerOnFix || !centerCamera
                            if(centerOnFix) {camera=CameraTarget(fix.lat,fix.lon,16.0,camera.revision+1);onCentered()}
                            notify(if(fix.test)"TEST LOCATION · DEVICE PROVIDER" else "LOCATION FOUND · ±${fix.accuracy.toInt()} M")
                        }
                    }
                }}
            } catch(timeout:TimeoutCancellationException){if(revision==locationRevision && locating){userFix=null;notify("LOCATION TIMED OUT · TRY AGAIN OUTDOORS")}}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(revision==locationRevision){userFix=null;notify(if(error is LocationUnavailable)error.message.orEmpty() else "LOCATION UNAVAILABLE")}}
            finally {if(revision==locationRevision)locating=false}
        }
    }
    fun notify(text:String) { toast=text; toastRevision++ }
    fun clearToast(revision:Int) { if(revision==toastRevision) toast="" }
    fun back():Boolean = when {
        searchVisible -> {hideSearch();true}
        mediaId!=null -> { closeMedia(); true }
        explanation!=null -> { dismissExplanation(); true }
        detailId!=null -> { closeDetail(); true }
        selectedId!=null -> { clearCard(); true }
        credits -> { credits=false; true }
        section!=Section.Map -> { openSection(Section.Map); true }
        phase!=BootPhase.Live -> { advancePhase(BootPhase.Live); true }
        else -> false
    }
}
