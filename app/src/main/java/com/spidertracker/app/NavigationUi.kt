package com.spidertracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun navigationDistance(meters:Double)=if(meters<1000)"${meters.roundToInt()} M" else String.format(Locale.ROOT,"%.1f KM",meters/1000)

@Composable fun NavigationPanel(runtime:NavigationRuntime,s:NavigationSnapshot,destination:AddressResult?,m:TrackerLayout,
    mode:TravelMode,onMode:(TravelMode)->Unit,manualOrigin:NavPoint?,onGpsOrigin:()->Unit,onRetry:()->Unit,onStart:()->Unit,
    picked:NavPoint?,onPickDestination:()->Unit,onPickOrigin:()->Unit,onDismissPick:()->Unit,voiceStatus:String,onStop:()->Unit,onHeight:(Int)->Unit={},
    originLabel:String?=null,onSearchEndpoint:(RouteEndpoint)->Unit={}) {
    if(destination==null && picked==null && s.state==NavigationState.Idle)return
    var settings by remember {mutableStateOf(false)}
    val savedEndpoint by runtime.endpoint.collectAsStateWithLifecycle()
    var endpoint by remember(savedEndpoint) {mutableStateOf(savedEndpoint)}
    var configError by remember {mutableStateOf("")}
    val width=(m.width-90).coerceAtLeast(180f)
    val available=(m.radarY-80).coerceAtLeast(100f).coerceAtMost(350f)
    Column(Modifier.offset(54.dp,72.dp).width(width.dp).heightIn(max=available.dp)
        .onSizeChanged {onHeight(it.height)}.background(Ink.Water).border(2.dp,Ink.Cyan).verticalScroll(rememberScrollState()).padding(8.dp).testTag("navigation-panel")) {
        if(picked!=null) {
            PixelText("SELECTED MAP POINT",size=10)
            PixelText(String.format(Locale.ROOT,"%.5f, %.5f",picked.lat,picked.lon),size=9)
            NavButton("SET DESTINATION",onPickDestination,"map-destination")
            NavButton("SET START POINT",onPickOrigin,"map-origin")
            NavButton("CANCEL",onDismissPick,"map-point-cancel")
            return@Column
        }
        if(s.active || s.state==NavigationState.Arrived) {
            PixelText(if(s.state==NavigationState.Arrived)"ARRIVED" else "${navigationDistance(s.toManeuver)} · ${turnLabel(s.next?.type)}",size=12,color=Ink.Cream)
            PixelText(s.next?.instruction.orEmpty(),Modifier.padding(top=6.dp).testTag("next-maneuver"),11,maxLines=3)
            s.next?.street?.takeIf {it.isNotBlank()}?.let {PixelText(it,Modifier.padding(top=5.dp),9,Ink.Cream,maxLines=1)}
            val arrival=Instant.ofEpochMilli(System.currentTimeMillis()+(s.seconds*1000).toLong()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            PixelText("${navigationDistance(s.remaining)} · ${(s.seconds/60).roundToInt()} MIN · ETA $arrival",Modifier.padding(top=8.dp).testTag("navigation-remaining"),9)
            val progress=s.route?.let {(s.along/it.length).coerceIn(0.0,1.0)} ?: 0.0
            Box(Modifier.fillMaxWidth().padding(top=6.dp).height(4.dp).background(Ink.Edge).semantics {contentDescription="Route progress ${(progress*100).toInt()} percent"}) {
                Box(Modifier.fillMaxWidth(progress.toFloat()).fillMaxHeight().background(Ink.Cyan))
            }
            if(s.fix?.simulated==true)PixelText("SIMULATED LOCATION",Modifier.padding(top=6.dp),8,Ink.Cream)
            if(s.message.isNotEmpty())PixelText(s.message,Modifier.padding(top=6.dp).testTag("navigation-message"),9,Ink.Cream)
            if(voiceStatus.isNotEmpty())PixelText(voiceStatus,Modifier.padding(top=6.dp),8,Ink.Cream)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                NavButton(if(s.muted)"UNMUTE" else "MUTE",runtime.controller::mute,"navigation-mute",Modifier.weight(1f))
                NavButton("STOP",onStop,"navigation-stop",Modifier.weight(1f))
            }
            if(s.active) {
                if(!s.following)NavButton("RECENTER",{runtime.controller.follow(true)},"navigation-recenter")
                NavButton("PAUSE",{runtime.controller.pause()},"navigation-pause")
            }
            return@Column
        }
        PixelText("ROUTE · ${destination?.label ?: "RECOVER SESSION"}",size=10,color=Ink.Cream,maxLines=2)
        NavButton("FROM: ${originLabel ?: "MY CURRENT LOCATION"}",{onSearchEndpoint(RouteEndpoint.Origin)},"route-from")
        NavButton("TO: ${destination?.name ?: destination?.label.orEmpty()}",{onSearchEndpoint(RouteEndpoint.Destination)},"route-to")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            TravelMode.entries.forEach {value->
                PixelButton(value.label,{onMode(value)},Modifier.weight(1f).heightIn(min=48.dp).testTag("mode-${value.costing}"),
                    if(mode==value)Ink.Cyan else Ink.Water,if(mode==value)Ink.Edge else Ink.Cyan,9)
            }
        }
        PixelText("LONG-PRESS MAP TO SET START / DESTINATION",Modifier.padding(top=4.dp),7)
        if(manualOrigin!=null)NavButton("USE CURRENT GPS",onGpsOrigin,"route-gps-origin")
        if(s.loading)PixelText("CALCULATING REAL ROUTE…",Modifier.padding(vertical=8.dp),10)
        s.routes.forEachIndexed {index,route->
            NavButton("${if(index==s.selected)"●" else "○"} ${navigationDistance(route.meters)} · ${(route.seconds/60).roundToInt()} MIN",{runtime.controller.select(index)},"route-alternative-$index")
        }
        if(s.message.isNotEmpty())PixelText(s.message,Modifier.padding(top=6.dp).testTag("navigation-message"),9,Ink.Cream)
        if(s.route!=null && !s.loading)NavButton(if(s.state==NavigationState.Paused)"RESUME NAVIGATION" else "START NAVIGATION",onStart,"navigation-start")
        if(!s.loading)NavButton("CALCULATE / RETRY",onRetry,"route-retry")
        NavButton("ROUTING SERVICE",{settings=!settings},"routing-settings")
        if(settings) {
            PixelText("YOUR VALHALLA BASE URL",size=8)
            BasicTextField(endpoint,{endpoint=it},Modifier.fillMaxWidth().heightIn(min=48.dp).background(Ink.Edge).padding(8.dp)
                .testTag("routing-endpoint").semantics {contentDescription="Valhalla base URL"},
                textStyle=TextStyle(color=Ink.Cyan,fontFamily=PixelFont,fontSize=11.sp),cursorBrush=SolidColor(Ink.Cyan),singleLine=true)
            if(configError.isNotEmpty())PixelText(configError,size=8,color=Ink.Cream)
            NavButton("SAVE SERVICE",{
                if(runtime.configure(endpoint)){configError="";settings=false;onRetry()}
                else configError="USE HTTPS · DEBUG ALSO ALLOWS LOCALHOST / 10.0.2.2"
            },"routing-save")
        }
        NavButton("CLOSE ROUTE",onStop,"route-close")
    }
}

@Composable private fun NavButton(label:String,onClick:()->Unit,tag:String,modifier:Modifier=Modifier) {
    PixelButton(label,onClick,modifier.fillMaxWidth().padding(top=4.dp).heightIn(min=48.dp).testTag(tag),Ink.Water,Ink.Cyan,10)
}
private fun turnLabel(type:Int?)=when(type) {
    9,10,11->"RIGHT";14,15,16->"LEFT";12,13->"U-TURN";26,27->"ROUNDABOUT";4,5,6->"DESTINATION";else->"NEXT"
}
