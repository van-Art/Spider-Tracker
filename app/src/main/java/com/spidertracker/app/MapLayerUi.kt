package com.spidertracker.app

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt
import java.util.Locale

val ProjectedMarkersKey=SemanticsPropertyKey<List<ProjectedMarker>>("ProjectedMarkers")
class MarkerEffectsState {
    var visibleKinds:Set<MarkerKind> = emptySet();internal set
    var visibleClusters:Int=0;internal set
    var clusterPulse:MarkerPulse?=null;internal set
    internal val samples=java.util.EnumMap<MarkerKind,MarkerPulse>(MarkerKind::class.java)
    fun pulse(kind:MarkerKind)=samples[kind]
}
val MarkerEffectsKey=SemanticsPropertyKey<MarkerEffectsState>("MarkerEffects")
@Composable fun MapMarkerSprites(points:List<ProjectedMarker>,modifier:Modifier=Modifier,onMarker:(String)->Unit={},time:()->Float={0f},reducedMotion:Boolean=false) {
    val green=suppliedBitmap(R.drawable.confirmed_sightings_location)
    val red=suppliedBitmap(R.drawable.rumored_sightings_location)
    val user=suppliedBitmap(R.drawable.main_point_location)
    val effects=remember {MarkerEffectsState()}
    val ordered=remember(points){points.sortedBy {it.kind.ordinal}}
    val context=LocalContext.current
    val countPaint=remember(context) {Paint().apply {isAntiAlias=false;color=android.graphics.Color.WHITE;textAlign=Paint.Align.CENTER;typeface=ResourcesCompat.getFont(context,R.font.tracker_pixel)}}
    Canvas(modifier.testTag("map-marker-sprites").semantics {
        this[ProjectedMarkersKey]=points;this[MarkerEffectsKey]=effects;stateDescription="${points.size} visible geographic marker sprites"
        contentDescription=points.take(100).joinToString("; "){(if(it.id=="destination")"Search destination" else if(it.demo)"DEMO alert" else if(it.kind==MarkerKind.Place)"Main attraction" else "User location")+if(it.count>1)" cluster ${it.count}" else " ${it.id}"}
        customActions=points.filter {it.kind!=MarkerKind.User && it.count==1}.take(100).map {p->CustomAccessibilityAction((if(p.demo)"DEMO alert " else "Attraction ")+p.id){onMarker(p.id);true}}
    }) {
        val side=34.dp.toPx().roundToInt()
        effects.visibleKinds=points.asSequence().filter {it.x in 0f..size.width && it.y in 0f..size.height}.map {it.kind}.toSet()
        effects.visibleClusters=points.count {it.count>1 && it.x in 0f..size.width && it.y in 0f..size.height}
        val seconds=if(effects.visibleKinds.isEmpty() || reducedMotion)0f else time()
        effects.visibleKinds.forEach {kind->effects.samples[kind]=MarkerAnimationState.sample(kind,seconds,reducedMotion)}
        if(effects.visibleClusters>0)effects.clusterPulse=MarkerAnimationState.sample(MarkerKind.Place,seconds,reducedMotion,cluster=true)
        points.forEach {point->
            if(point.x !in 0f..size.width || point.y !in 0f..size.height)return@forEach
            val pulse=if(point.count>1)effects.clusterPulse!! else effects.samples.getValue(point.kind)
            val color=when(point.kind){MarkerKind.Place->Color(0xff45d26a);MarkerKind.Alert->Color(0xffff4d50);MarkerKind.User->Color(0xff00d7fa)}
            drawCircle(color.copy(alpha=pulse.opacity),pulse.radius.dp.toPx(),androidx.compose.ui.geometry.Offset(point.x,point.y),style=Stroke(2.dp.toPx()))
        }
        countPaint.textSize=8.dp.toPx().roundToInt().toFloat()
        val bounds=pixelBounds(green.width,green.height,side,side)
        ordered.forEach {point->
            if(point.id=="destination" || point.id=="user")return@forEach
            val bitmap=when(point.kind){MarkerKind.Place->green;MarkerKind.Alert->red;MarkerKind.User->user}
            val x=(point.x-bounds.width/2f).roundToInt();val y=(point.y-bounds.height/2f).roundToInt()
            drawImage(bitmap,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset(x,y),dstSize=IntSize(bounds.width,bounds.height),filterQuality=FilterQuality.None)
            val label=if(point.demo)"DEMO"+(if(point.count>1)" · ${point.count}" else "") else if(point.count>1)point.count.toString() else null
            if(label!=null) {
                val labelWidth=countPaint.measureText(label)+4.dp.toPx()
                pixelRect(Ink.Water,androidx.compose.ui.geometry.Offset(point.x-labelWidth/2,y+bounds.height.toFloat()),androidx.compose.ui.geometry.Size(labelWidth,11.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(label,point.x.roundToInt().toFloat(),(y+bounds.height+8.dp.toPx()).roundToInt().toFloat(),countPaint)
            }
        }
    }
}

@Composable fun MapLayerControls(filters:MapFiltersViewModel,m:TrackerLayout) {
    if(m.mapHeight<160)return
    val y=140f.coerceAtMost(m.shelfY-104).coerceAtLeast(58f)
    listOf(MarkerKind.Place,MarkerKind.Alert).forEachIndexed {index,kind->
        val enabled=if(kind==MarkerKind.Place)filters.state.placesEnabled else filters.state.alertsEnabled
        val name=if(kind==MarkerKind.Place)"Main Attractions" else "Live Alerts · DEMO"
        val asset=if(!enabled)R.drawable.off_button else if(kind==MarkerKind.Place)R.drawable.confirmed_sightings_button else R.drawable.rumored_sightings_button
        Column(Modifier.offset(0.dp,(y+index*52).dp).size(48.dp,52.dp)
            .combinedClickable(role=Role.Checkbox,onClick={filters.toggle(kind)},onLongClickLabel="Category filters",onLongClick={filters.showFilters(kind)})
            .semantics {contentDescription=name;stateDescription=if(enabled)"Enabled" else "Disabled";toggleableState=if(enabled)androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off}
            .testTag(if(kind==MarkerKind.Place)"layer-places" else "layer-alerts"),horizontalAlignment=Alignment.CenterHorizontally) {
            SuppliedImage(asset,null,Modifier.size(48.dp,40.dp))
        }
    }
}

@Composable fun MapLayerPanels(filters:MapFiltersViewModel,vm:TrackerViewModel,m:TrackerLayout,searchShelfY:Float=m.shelfY,visiblePlaceCount:Int?=null) {
    filters.filterKind?.let {kind->
        val height=(searchShelfY-m.mapY-80).coerceIn(100f,390f)
        Column(Modifier.offset(8.dp,58.dp).width((m.mapWidth-16).coerceAtMost(320f).dp).height(height.dp)
            .background(Ink.Cyan).padding(1.dp).background(Color(0xff0e1c3b)).testTag("layer-filters")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                PixelText(if(kind==MarkerKind.Place)"MAIN ATTRACTIONS" else "LIVE ALERTS · DEMO",Modifier.weight(1f).padding(8.dp),12)
                PixelButton("×",filters::closeFilters,Modifier.size(48.dp).testTag("filters-close"),size=18)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if(kind==MarkerKind.Place)attractionCategories.forEach {category->
                    CategoryRow(category.label,category in filters.state.placeCategories,"place-category-${category.name}"){filters.toggleCategory(category)}
                } else AlertCategory.entries.forEach {category->
                    CategoryRow(category.label,category in filters.state.alertCategories,"alert-category-${category.name}"){filters.toggleCategory(category)}
                }
            }
            PixelText(if(kind==MarkerKind.Place) {
                if(!filters.state.placesEnabled)"LAYER OFF · CATALOG INCOMPLETE"
                else if(visiblePlaceCount==0)"NO CATALOG ATTRACTIONS IN THIS VIEW · COVERAGE INCOMPLETE"
                else "CURATED CATALOG · COVERAGE INCOMPLETE"
            } else filters.alertsStatus,Modifier.padding(8.dp),9,maxLines=3)
        }
    }
}

@Composable fun HudNoticeBanner(filters:MapFiltersViewModel,reducedMotion:Boolean,modifier:Modifier=Modifier) {
    var displayed by remember {mutableStateOf("")}
    val message=filters.layerNotice
    SideEffect {if(message!=null)displayed=message}
    Box(modifier.fillMaxWidth().padding(top=50.dp,start=12.dp,end=12.dp),contentAlignment=Alignment.TopCenter) {
        AnimatedVisibility(message!=null,enter=fadeIn(tween(if(reducedMotion)0 else 120)),exit=fadeOut(tween(if(reducedMotion)0 else 100))) {
            Box(Modifier.testTag("map-feedback").semantics {liveRegion=LiveRegionMode.Polite}
                .background(Ink.Cyan).padding(1.dp).background(Color(0xff0e1c3b)).padding(8.dp)) {
                PixelText(displayed,size=11,maxLines=3,align=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable private fun CategoryRow(label:String,selected:Boolean,tag:String,onClick:()->Unit) {
    PixelText((if(selected)"[X] " else "[ ] ")+label,Modifier.fillMaxWidth().heightIn(min=48.dp)
        .toggleable(selected,role=Role.Checkbox,onValueChange={onClick()}).padding(10.dp).testTag(tag),11,Ink.Cream)
}
