package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.util.Locale
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput

/** Last root sibling: radar drawing and pointer input stay below the opaque panel. */
@Composable fun DetailPanelOverlay(filters:MapFiltersViewModel,vm:TrackerViewModel) {
    if(vm.placeState!=PlaceUiState.PlaceDetails || vm.searchVisible || vm.section!=Section.Map)return
    val destination=vm.destination ?: return
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)
        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
        val m=TrackerLayout(maxWidth.value,maxHeight.value)
        val marker=filters.selected
        val width=(m.mapWidth-16).coerceIn(1f,320f)
        val top=56f.coerceAtMost((m.shelfY-8).coerceAtLeast(0f))
        val height=(m.shelfY-top-8).coerceIn(1f,340f)
        val modifier=Modifier.offset((m.mapX+(m.mapWidth-width)/2).dp,(m.shelfY-height-8).coerceAtLeast(0f).dp).size(width.dp,height.dp)
        val close={filters.closeDetails();vm.clearDestination()}
        val route={filters.closeDetails();vm.showRoutePlanner()}
        if(marker!=null)TrackerPlaceDetailPanel(marker,vm.userFix,close,route,modifier)
        else AddressPlaceDetails(destination,vm.userFix,close,route,modifier)
    }
}

@Composable fun TrackerPlaceDetailPanel(marker:LayerMarker,fix:DeviceFix?,onClose:()->Unit,onNavigate:()->Unit,modifier:Modifier=Modifier) {
    val place=marker.kind==MarkerKind.Place
    val uri=LocalUriHandler.current
    Column(modifier.background(Ink.Cyan).padding(1.dp).background(Color(0xff0e1c3b))
        .pointerInput(Unit) {detectTapGestures {}}
        .testTag("layer-marker-detail")) {
        Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically) {
            SuppliedImage(if(place)R.drawable.confirmed_sightings_location else R.drawable.rumored_sightings_location,null,Modifier.size(32.dp))
            PixelText(if(place)"MAIN ATTRACTION" else if(marker.demo)"DEMO ALERT" else "LIVE ALERT",Modifier.weight(1f).padding(8.dp),11,maxLines=2)
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                PixelButton("×",onClose,Modifier.fillMaxSize().testTag("marker-detail-close").semantics {contentDescription="Close place details"},size=18)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Cyan))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            val category=if(place)PlaceCategory.entries.firstOrNull {it.name==marker.category}?.label else AlertCategory.entries.firstOrNull {it.name==marker.category}?.label
            PixelText(if(place)marker.name ?: "NAME NOT PROVIDED" else category ?: marker.category,size=15,color=Ink.Cream)
            if(place) {
                DetailField("TYPE",category ?: marker.category)
                marker.country?.let {DetailField("COUNTRY",it)}
                marker.locality?.let {DetailField("CITY / REGION",it)}
                DetailField("ADDRESS",marker.address ?: "ADDRESS NOT PROVIDED")
            } else {
                if(marker.demo)PixelText("DEMONSTRATION DATA — NOT A REAL INCIDENT",Modifier.padding(top=12.dp),11,Ink.Cream)
                marker.severity?.let {DetailField("SEVERITY",it)}
                DetailField("DESCRIPTION",marker.description ?: "DESCRIPTION NOT PROVIDED")
                DetailField("REPORT TIME",marker.reportedAt ?: "NOT PROVIDED")
            }
            val distance=fix?.let {distanceKm(it.lat,it.lon,marker.lat,marker.lon)}
            PixelText(if(distance==null)"DISTANCE: LOCATION UNAVAILABLE" else (if(fix.test)"TEST LOCATION · " else "APPROX. DISTANCE: ")+String.format(Locale.ROOT,"%.2f KM",distance),Modifier.padding(top=12.dp),9)
            DetailField("SOURCE",marker.source)
            DetailField("STATUS",marker.verification ?: "VERIFICATION NOT PROVIDED")
            marker.infoUrl?.takeIf {it.matches(Regex("https://www\\.wikidata\\.org/wiki/Q[1-9][0-9]*"))}?.let {link->
                PixelButton("SOURCE RECORD",{uri.openUri(link)},Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=48.dp),Ink.Water,Ink.Cyan,11)
            }
        }
        PixelButton("ROUTE",onNavigate,Modifier.fillMaxWidth().padding(8.dp).heightIn(min=48.dp).testTag("place-navigate"),Ink.Water,Ink.Cyan,size=12)
    }
}

@Composable private fun AddressPlaceDetails(place:AddressResult,fix:DeviceFix?,onClose:()->Unit,onRoute:()->Unit,modifier:Modifier) {
    Column(modifier.background(Ink.Cyan).padding(1.dp).background(Color(0xff0e1c3b))
        .pointerInput(Unit) {detectTapGestures {}}.testTag("place-details")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            PixelText("PLACE DETAILS",Modifier.weight(1f).padding(12.dp),12)
            PixelButton("×",onClose,Modifier.size(48.dp).testTag("place-details-close").semantics {contentDescription="Close place details"},size=18)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            PixelText(place.name ?: place.label,size=15,color=Ink.Cream)
            place.category?.let {DetailField("CATEGORY",it)}
            place.address?.let {DetailField("ADDRESS",it)}
            listOfNotNull(place.locality,place.country).distinct().joinToString(" · ").takeIf {it.isNotBlank()}?.let {DetailField("CITY / COUNTRY",it)}
            fix?.let {DetailField("DISTANCE",navigationDistance(distanceKm(it.lat,it.lon,place.lat,place.lon)*1000))}
        }
        PixelButton("ROUTE",onRoute,Modifier.fillMaxWidth().padding(8.dp).heightIn(min=48.dp).testTag("place-route"),Ink.Water,Ink.Cyan)
    }
}

@Composable private fun DetailField(label:String,value:String) {
    PixelText(label,Modifier.padding(top=12.dp,bottom=4.dp),9)
    PixelText(value,size=11,color=Ink.Cream)
}
