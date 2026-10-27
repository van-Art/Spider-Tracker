package com.spidertracker.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

val RadarSnapshotKey=SemanticsPropertyKey<RadarSnapshot>("RadarSnapshot")
private val RadarTouchShape=GenericShape {size,_->
    val r=size.minDimension/2
    repeat(12) {j->val a=j*2*PI/12;val x=size.width/2+r*cos(a).toFloat();val y=size.height/2+r*sin(a).toFloat();if(j==0)moveTo(x,y)else lineTo(x,y)}
    close()
}

/** Full-size web and attached controls share the same overlay geometry above the IME. */
@Composable fun RadarOverlay(m:TrackerLayout,time:()->Float,filters:MapFiltersViewModel,vm:TrackerViewModel,
    viewport:RadarSnapshot,onLocation:()->Unit,onFeature:(String)->Unit,topCorrection:Float=0f,
    userFix:DeviceFix?=vm.userFix,gpsCentered:Boolean=vm.radarGpsCentered,onGlobal:()->Unit={vm.globalView()}) {
    val snapshot=remember(filters.visibleMarkers,userFix,gpsCentered,viewport,filters.radarRangeKm) {
        if(gpsCentered && userFix!=null)
            geographicRadar(filters.visibleMarkers,userFix,0.0,0.0,filters.radarRangeKm)
        else viewport.copy(rangeKm=filters.radarRangeKm)
    }
    val currentSnapshot by rememberUpdatedState(snapshot)
    val chooseFeature by rememberUpdatedState(onFeature)
    val cycleRange={filters.setRadarRange(radarRangesKm[(radarRangesKm.indexOf(filters.radarRangeKm)+1)%radarRangesKm.size])}
    val status={vm.notify(snapshot.label+(if(snapshot.demo)" · DEMO ALERTS — NOT REAL INCIDENTS" else "")+" · TAP EMPTY WEB TO CHANGE GPS RANGE")}
    val showStatus by rememberUpdatedState(status)
    Box(Modifier.fillMaxSize().offset(y=topCorrection.dp).zIndex(1f).testTag("radar-overlay")) {
        Canvas(Modifier.offset(m.radarX.dp,m.radarY.dp).size(m.radarSize.dp).clip(RadarTouchShape).testTag("radar")
            .semantics {
                this[RadarSnapshotKey]=snapshot
                contentDescription="Geographic radar"
                stateDescription=snapshot.label+(if(snapshot.demo)" · DEMO ALERTS" else "")+" · ${snapshot.dots.size} dots"
                customActions=radarRangesKm.map {km->CustomAccessibilityAction("Radar range $km km"){filters.setRadarRange(km);true}}+
                    snapshot.dots.filter {it.kind!=MarkerKind.User}.take(100).map {dot->
                        CustomAccessibilityAction((if(dot.demo)"DEMO alert " else "Attraction ")+dot.id){onFeature(dot.id);true}
                    }+CustomAccessibilityAction("Radar mode and data status"){status();true}
            }.pointerInput(Unit) {
                detectTapGestures(onLongPress={showStatus()},onTap={position->
                    val r=min(size.width,size.height)/2f
                    val radius=(r-3.dp.toPx())*cos(PI/12).toFloat()
                    val dot=currentSnapshot.dots.minByOrNull {hypot(position.x-r-radius*it.x,position.y-r-radius*it.y)}
                    if(dot!=null && hypot(position.x-r-radius*dot.x,position.y-r-radius*dot.y)<=14.dp.toPx()) {
                        if(dot.kind==MarkerKind.User)showStatus() else chooseFeature(dot.id)
                    } else cycleRange()
                })
            }) {
            val r=size.minDimension/2;val c=this.center;val angle=time()*1.08f
            val background=Path().apply {
                repeat(12) {j->val a=j*2*PI/12;val x=c.x+r*cos(a).toFloat();val y=c.y+r*sin(a).toFloat();if(j==0)moveTo(x,y)else lineTo(x,y)}
                close()
            }
            // Opaque pixel fill is above both native map layers and Compose marker pulses.
            pixelPath(background,Color(0xff0e1c3b))
            clipPath(background) {
            repeat(5) {i->val radius=r*(i+1)/5;val p=Path();repeat(13){j->val a=j*2*PI/12;val o=Offset(c.x+radius*cos(a).toFloat(),c.y+radius*sin(a).toFloat());if(j==0)p.moveTo(o.x,o.y)else p.lineTo(o.x,o.y)};pixelPath(p,Ink.Pale.copy(alpha=if(i==4)1f else .55f),width=(if(i==4)1.2f else .45f).dp.toPx())}
            repeat(12){i->val a=i*2*PI/12;pixelLine(Ink.Pale.copy(alpha=.6f),c,Offset(c.x+r*cos(a).toFloat(),c.y+r*sin(a).toFloat()),.45.dp.toPx())}
            snapshot.dots.forEach {dot->
                val color=when(dot.kind){MarkerKind.Place->Ink.pin(PinKind.Confirmed);MarkerKind.Alert->Ink.pin(PinKind.Rumored);MarkerKind.User->Color(0xff00d7fa)}
                // The web is polygonal; inset dots just enough to keep their full artwork inside it.
                val radius=(r-3.dp.toPx())*cos(PI/12).toFloat()
                pixelCircle(color,2.dp.toPx(),Offset(c.x+radius*dot.x,c.y+radius*dot.y))
            }
            drawArc(Ink.Cyan.copy(alpha=.12f),(angle*180/PI).toFloat()-99,99f,true,Offset.Zero,size)
            pixelLine(Ink.Cyan,c,Offset(c.x+r*cos(angle),c.y+r*sin(angle)),.7.dp.toPx())
            }
        }
        PixelText((if(snapshot.mode==RadarMode.Gps)(if(snapshot.testLocation)"TEST GPS" else "GPS")+" · ${snapshot.rangeKm}KM" else "MAP · VIEWPORT")+(if(snapshot.demo)" · DEMO" else ""),
            Modifier.offset(m.radarX.dp,(m.radarY-12).dp).width(m.radarSize.dp).background(Ink.Water).testTag("radar-status"),7,maxLines=1)
        Box(Modifier.offset(m.globalX.dp,m.globalY.dp).size(48.dp).clickable(role=Role.Button,onClick=onGlobal).semantics {contentDescription="Global map"}.testTag("map-global")) {
            SuppliedImage(R.drawable.zoom_to_global,null,Modifier.align(Alignment.Center).offset(y=(m.radarCenterY-m.globalY-24).dp).size(28.dp))
        }
        Box(Modifier.offset(m.locationX.dp,m.locationY.dp).size(48.dp).clickable(role=Role.Button,onClick=onLocation).semantics {contentDescription="Current location";stateDescription=if(vm.locating)"Obtaining device location" else "Ready"}.testTag("map-center"),contentAlignment=Alignment.Center) {
            SuppliedImage(R.drawable.center,null,Modifier.offset(y=(m.radarCenterY+m.radarSize*.405f-m.locationY-24).dp).size(28.dp))
        }
    }
}
