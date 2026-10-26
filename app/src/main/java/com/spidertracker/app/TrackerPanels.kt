package com.spidertracker.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val menuItems=listOf(Section.Log,Section.Report,Section.WebWatch,Section.Videos,Section.Events,Section.Help)
private val dateFormat=DateTimeFormatter.ofPattern("MMM d, yyyy",Locale.US)

@Composable fun TrackerPanels(vm:TrackerViewModel,m:TrackerLayout,capture:Boolean) {
    AnimatedVisibility(vm.section==Section.Menu,enter=fadeIn(tween(220))+slideInHorizontally(tween(240)){-it},exit=fadeOut(tween(180))+slideOutHorizontally(tween(240)){-it}) {
        Box(Modifier.fillMaxSize().clickable {vm.openSection(Section.Map)}) {
            Box(Modifier.fillMaxHeight().width((if(m.width<=500)m.mapWidth else 320f).dp).background(Ink.Navy).clickable{}.testTag("navigation-panel"),contentAlignment=Alignment.Center) {
                Column(Modifier.width((if(m.mobile)190 else 220).dp).heightIn(max=(m.mapHeight-20).dp).verticalScroll(rememberScrollState())) {
                    menuItems.forEachIndexed {index,section->
                        var visible by remember {mutableStateOf(capture)}
                        LaunchedEffect(Unit){kotlinx.coroutines.delay((90+index*36).toLong());visible=true}
                        val opacity by animateFloatAsState(if(visible)1f else 0f,tween(220),label="menu-item")
                        val label=if(section==Section.WebWatch)"WEB WATCH" else section.title
                        PixelText(label,Modifier.fillMaxWidth().alpha(opacity).clickable(role=Role.Button){vm.openSection(section)}
                            .padding(10.dp).testTag("nav-${section.name.lowercase()}"),if(m.mobile)14 else 17,Ink.Pale)
                        if(index<menuItems.lastIndex) Canvas(Modifier.fillMaxWidth().height(1.dp)) {for(x in 0 until size.width.toInt() step 6)drawLine(Ink.Pale.copy(alpha=.2f),Offset(x.toFloat(),0f),Offset(x+3f,0f),1f)}
                    }
                }
            }
        }
    }
    AnimatedVisibility(vm.section==Section.Log,enter=fadeIn(tween(220))+slideInVertically(tween(560, easing=CubicBezierEasing(.16f,1f,.3f,1f))){22},exit=fadeOut(tween(220))) {
        Column(Modifier.fillMaxSize().background(Ink.Charcoal).testTag("activity-log")) {
            ScanLines(Modifier.fillMaxWidth().height(0.dp))
            PanelHeader("ACTIVITY LOG",{vm.openSection(Section.Map)},m)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ScanLines(Modifier.fillMaxSize())
                LazyColumn(Modifier.align(Alignment.TopCenter).widthIn(max=600.dp).fillMaxSize().padding(end=if(m.width<=600)12.dp else 30.dp).testTag("activity-list")) {
                    items(vm.logPins,key={it.id}) {pin->
                        Row(Modifier.fillMaxWidth().alpha(if(vm.isSeen(pin.id)).35f else 1f).clickable(role=Role.Button){vm.selectPin(pin,true)}
                            .padding(horizontal=12.dp,vertical=8.dp).testTag("log-${pin.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            PinGlyph(pin.kind,Modifier.size(20.dp))
                            if(m.width>600) PixelText(pin.kind.name.uppercase(),Modifier.background(Ink.Bevel.copy(alpha=.3f)).padding(3.dp),9)
                            PixelText(pin.title,Modifier.weight(1f),if(m.mobile)10 else 12,maxLines=1)
                            PixelText(pin.date.format(dateFormat).uppercase(),size=if(m.mobile)9 else 11,color=Color(0xff888888),maxLines=1)
                        }
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Ink.Cyan.copy(alpha=.5f)))
                    }
                }
            }
        }
    }
    AnimatedVisibility(vm.section==Section.WebWatch,enter=fadeIn(tween(220))+slideInVertically(tween(280)){10},exit=fadeOut(tween(220))) {WebWatchPanel(vm,m)}
    if(vm.section==Section.Help) HelpPanel({vm.openSection(Section.Map)},m)
    AnimatedVisibility(vm.explanation!=null,enter=fadeIn(tween(500)),exit=fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(Ink.Navy).clickable {vm.dismissExplanation()}.testTag("section-explanation"),contentAlignment=Alignment.Center) {
            val section=vm.explanation ?: vm.section
            Column(Modifier.padding(16.dp).widthIn(max=400.dp).background(Ink.Edge).padding(4.dp).clickable{}) {
                Row(Modifier.fillMaxWidth().background(Ink.Orange).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    PixelText("${section.title} INFO",Modifier.weight(1f),12,Color.Black)
                    CloseText(vm::dismissExplanation,Color.Black,"explanation-close")
                }
                PixelText(when(section){Section.Log->"A running list of confirmed, rumored and event observations. Select a report to find it on the map.";Section.Events->"Browse the field meetups and observations collected for this demo. Each event has a location and media.";else->"A collection of profiles from the field. Browse the observations to see the different signals."},Modifier.fillMaxWidth().background(Ink.Charcoal).padding(15.dp),12,Ink.Cream)
            }
        }
    }
}

@Composable fun CloseText(onClick:()->Unit,color:Color=Ink.Cyan,tag:String="panel-close") {
    Box(Modifier.heightIn(min=48.dp).clickable(role=Role.Button,onClick=onClick).semantics {contentDescription="Close panel"}.testTag(tag),contentAlignment=Alignment.Center) {
        PixelText("× CLOSE",Modifier.padding(horizontal=5.dp),12,color.copy(alpha=.65f),maxLines=1)
    }
}

@Composable private fun PanelHeader(title:String,onClose:()->Unit,m:TrackerLayout) {
    Row(Modifier.fillMaxWidth().padding(start=(if(m.width<=550)20 else 30).dp,end=20.dp,top=(if(m.width<=550)20 else 30).dp,bottom=0.dp),verticalAlignment=Alignment.CenterVertically) {
        PixelText("$title ▌",Modifier.weight(1f),if(m.width<=550)12 else 14)
        CloseText(onClose)
    }
}

@Composable private fun ScanLines(modifier:Modifier) {
    Canvas(modifier) {val pitch=6.dp.toPx();repeat((size.height/pitch).toInt()){i->drawLine(Ink.Cyan.copy(alpha=.045f),Offset(0f,i*pitch),Offset(size.width,i*pitch),1.dp.toPx())}}
}

@Composable private fun MapTexture(modifier:Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xff11254c))
        val step=28.dp.toPx()
        repeat((size.width/step).toInt()+25){i->val x=i*step-size.height*.6f;drawLine(Ink.Cyan.copy(alpha=.055f),Offset(x,0f),Offset(x+size.height*.6f,size.height),2.dp.toPx())}
        repeat((size.height/step).toInt()+10){i->val y=i*step;drawLine(Ink.Cyan.copy(alpha=.04f),Offset(0f,y),Offset(size.width,y-size.width*.35f),1.dp.toPx())}
    }
}

@Composable fun HelpPanel(onClose:()->Unit,m:TrackerLayout) {
    Box(Modifier.fillMaxSize().clickable(role=Role.Button,onClick=onClose).semantics {contentDescription="Help, tap anywhere to close"}.testTag("help-panel")) {
        MapTexture(Modifier.fillMaxSize())
        PixelText("NAVIGATION",Modifier.offset(18.dp,42.dp),10)
        PixelText("MAP FILTERS",Modifier.offset(12.dp,77.dp),10)
        PixelText("MESSAGE\nCENTER",Modifier.align(Alignment.TopEnd).padding(top=46.dp,end=14.dp),10,align=TextAlign.End)
        Column(Modifier.align(Alignment.Center).widthIn(max=320.dp).padding(bottom=40.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            PixelText("WELCOME! HERE'S A HANDY GUIDE TO\nHELP YOU NAVIGATE THE EXPERIENCE.",Modifier.padding(horizontal=12.dp,vertical=25.dp),if(m.mobile)12 else 16,Color(0xffffc619),align=TextAlign.Center)
            Row(Modifier.padding(start=36.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {PixelText("CONFIRMED\nSIGHTING",size=10);Spacer(Modifier.width(12.dp));PinGlyph(PinKind.Confirmed,Modifier.size(30.dp))}
            Row(Modifier.padding(end=66.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {PixelText("RUMORED\nSIGHTING",size=10);Spacer(Modifier.width(12.dp));PinGlyph(PinKind.Rumored,Modifier.size(30.dp))}
            Row(Modifier.padding(start=54.dp),verticalAlignment=Alignment.CenterVertically) {PixelText("EVENT",size=10);Spacer(Modifier.width(12.dp));PinGlyph(PinKind.Event,Modifier.size(30.dp))}
        }
        PixelText("GLOBAL MAP  ◎\n\nCENTER MAP  ⊙",Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=28.dp),10,align=TextAlign.End)
    }
}

private val watchNames=listOf("ROOFTOP OBSERVER","CROSSROADS SIGNAL","NIGHT COURIER","THE LOOKOUT","RIVER SIGNAL","CITY SCANNER")
private val watchCopy=listOf("A quiet silhouette above the skyline. Our observers are watching the rooftops for a second signal.","A brief flash at the crossroads. Compare the field reports and look for a matching pattern.","The late shift spotted something moving between the towers. The report is still unconfirmed.")

@Composable private fun WebWatchPanel(vm:TrackerViewModel,m:TrackerLayout) {
    var index by remember {mutableIntStateOf(0)}
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val height=max(220f,m.mapHeight-145)
    val itemWidth=height*2/3+180
    LaunchedEffect(index){if(!m.mobile)scroll.animateScrollTo(with(density){(index*(itemWidth+50)).dp.roundToPx()})}
    Box(Modifier.fillMaxSize().testTag("webwatch-panel")) {
        MapTexture(Modifier.fillMaxSize())
        Column {
            PanelHeader("WEB WATCH 1.0",{vm.openSection(Section.Map)},m)
            if(m.mobile) LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=10.dp).testTag("webwatch-list"),verticalArrangement=Arrangement.spacedBy(50.dp)) {
                items(watchNames.indices.toList()){i->WatchArticle(i,m.width<=550,220f,Modifier.fillMaxWidth())}
                item {Spacer(Modifier.height(20.dp))}
            } else {
                Row(Modifier.weight(1f).padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    PixelButton("‹",{index=max(0,index-1)},Modifier.width(30.dp).height(78.dp).alpha(if(index==0).2f else 1f),Ink.Water,Ink.Cyan,22)
                    Row(Modifier.weight(1f).horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(50.dp)) {watchNames.indices.forEach {WatchArticle(it,false,height,Modifier.width(itemWidth.dp))}}
                    PixelButton("›",{index=min(watchNames.lastIndex,index+1)},Modifier.width(30.dp).height(78.dp).testTag("webwatch-next"),Ink.Water,Ink.Cyan,22)
                }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {watchNames.indices.forEach {i->Box(Modifier.size(12.dp,6.dp).background(Ink.Cyan.copy(alpha=if(i==index)1f else .2f)).clickable {index=i})}}
            }
        }
    }
}

@Composable private fun WatchArticle(index:Int,stacked:Boolean,height:Float,modifier:Modifier) {
    if(stacked) Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        FieldIllustration(index,Modifier.width(200.dp).height(300.dp))
        Column(Modifier.width(200.dp).padding(top=8.dp)) {PixelText(watchNames[index],size=14);PixelText(watchCopy[index%3],size=10,color=Color(0xffd7f5ff))}
    } else Row(modifier.height(height.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(30.dp)) {
        FieldIllustration(index,Modifier.width((height*2/3).dp).fillMaxHeight())
        Column(Modifier.width(150.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){PixelText(watchNames[index],size=16);PixelText(watchCopy[index%3],size=11,color=Color(0xffd7f5ff))}
    }
}

@Composable fun SidePanel(vm:TrackerViewModel,section:Section,width:Float,height:Float) {
    val panelWidth=min(350f,width)
    Box(Modifier.fillMaxSize().clickable {vm.openSection(Section.Map)}) {
        Column(Modifier.align(Alignment.TopEnd).width(panelWidth.dp).height(height.dp).background(if(section==Section.Events)Color(0xf2111515) else Ink.Navy).clickable{}.testTag("side-panel")) {
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=10.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                PixelText(if(section==Section.Events)"✦ EVENTS" else "X  SPIDER TRACKER",Modifier.weight(1f),12,if(section==Section.Events)Color.White else Ink.Cyan)
                CloseText({vm.openSection(Section.Map)},if(section==Section.Events)Color.White else Ink.Cyan)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=24.dp).testTag("side-panel-list"),verticalArrangement=Arrangement.spacedBy(30.dp)) {
                if(section==Section.Report) {
                    item {Column(Modifier.background(Ink.Cyan.copy(alpha=.05f)).padding(8.dp)){PixelText("SPIDER TRACKER",size=14);PixelText("@field_scanner",size=10);Spacer(Modifier.height(16.dp));PixelText("Have a sighting? This independent field tracker gathers demo observations from around the world. Select a marker to explore its report.",size=12)}}
                    items((0..7).toList()){i->Column {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PinGlyph(PinKind.Confirmed,Modifier.size(28.dp));Column{PixelText("FIELD OBSERVER ${i+1}",size=14);PixelText("@observer_${i+1} · AUG ${11-i}, 2026",size=10)}};Spacer(Modifier.height(16.dp));if(i%3==2)FieldIllustration(i,Modifier.fillMaxWidth().height(180.dp));PixelText(watchCopy[i%3],size=12)}}
                } else {
                    items(vm.pins.filter {it.kind==PinKind.Event}){pin->Column {
                        PixelText(pin.title,size=14,color=Color.White)
                        PixelText("⌖ ${pin.city}",Modifier.padding(vertical=10.dp),11,Color.White)
                        PixelText("A local night watch brings observers together near the city center. An original demo event for the field tracker.",size=12,color=Color.White)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(350.dp).clickable {vm.openMedia(pin.id)}) {
                            FieldIllustration(pin.art,Modifier.fillMaxSize())
                            PixelButton("▶",{vm.openSection(Section.Videos)},Modifier.align(Alignment.Center).size(56.dp),Ink.Cream,size=22)
                        }
                    }}
                }
            }
            if(section==Section.Report) PixelButton(if(vm.toast.isEmpty())"REPORT YOUR SIGHTING" else "DEMO · REPORTING UNAVAILABLE",{vm.notify("REPORTING IS A DEMO SERVICE")},Modifier.align(Alignment.CenterHorizontally).padding(vertical=24.dp),Ink.Water,Ink.Cyan,11)
        }
        if(vm.explanation==section) Box(Modifier.align(Alignment.TopEnd).width(panelWidth.dp).height(height.dp).background(Ink.Navy),contentAlignment=Alignment.Center) {
            Column(Modifier.padding(16.dp).background(Ink.Charcoal)) {
                Row(Modifier.background(Ink.Orange).padding(8.dp),verticalAlignment=Alignment.CenterVertically){PixelText("EVENTS INFO",Modifier.weight(1f),12,Color.Black);CloseText(vm::dismissExplanation,Color.Black)}
                PixelText("Explore the event observations collected for this demo. Select a card or find its marker on the map.",Modifier.padding(15.dp),12,Ink.Cream)
            }
        }
    }
}

@Composable fun MessageDetail(vm:TrackerViewModel,m:TrackerLayout) {
    val pin=vm.detail
    AnimatedVisibility(pin!=null,enter=slideInVertically(tween(600)){ -it }+fadeIn(tween(300)),exit=fadeOut(tween(300))) {
        if(pin!=null) {
            val width=if(m.mobile)250 else 350
            val x=m.width-(if(m.mobile)12 else 28)-width
            Column(Modifier.offset(x.dp,(if(m.mobile)60 else 80).dp).width(width.dp).testTag("message-detail")) {
                Box(Modifier.align(Alignment.End).width((if(m.mobile)167 else 223).dp).height((if(m.mobile)45 else 60).dp).background(Color(0xff35604d)).padding(8.dp),contentAlignment=Alignment.Center) {
                    PixelText("SIGHTING BY @OBSERVER",Modifier.fillMaxWidth().background(Color(0xff73bc8b)).padding(4.dp),if(m.mobile)10 else 14,Color.Black,maxLines=1)
                }
                Row(Modifier.fillMaxWidth().height((if(m.mobile)185 else 205).dp).background(Ink.Edge).padding(5.dp).background(Ink.Cream).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FieldIllustration(pin.art,Modifier.size((if(m.mobile)88 else 120).dp).clickable(role=Role.Button){vm.openDetailMedia()}.semantics{contentDescription="Open sighting image"}.testTag("detail-media"))
                    Column(Modifier.weight(1f)) {PixelText(pin.city,size=12,color=Color.Black);PixelText("@observer_${pin.art+1}",size=10,color=Color.Black);PixelText("A signal seen near the city rooftops.",Modifier.padding(top=8.dp),10,Color(0xff333333))}
                }
            }
            PixelButton("✦",vm::closeDetail,Modifier.offset((m.width-(if(m.mobile)12 else 28)-(if(m.mobile)48 else 64)).dp,(if(m.mobile)14 else 18).dp).size((if(m.mobile)48 else 64).dp).testTag("message-close"),Ink.Orange,size=24)
        }
    }
}

@Composable fun MediaPanel(vm:TrackerViewModel,width:Float,height:Float,time:Float,capture:Boolean) {
    val video=vm.section==Section.Videos
    val imagePin=vm.pins.find {it.id==vm.mediaId}
    val mediaWidth=if(video)min(min(1240f,width-20),max(100f,height-96)*16/9) else min(600f,width-20)
    val mediaHeight=if(video)mediaWidth*9/16 else min(height*.60f,mediaWidth*1.5f)
    val thumbHeight=if(width<=420)56f else if(width<=640)62f else 100f
    val thumbWidth=thumbHeight*1.6f
    val visibleCount=if(width<=420)2 else if(width<=800)3 else 4
    val galleryWidth=min(width-20,min(780f,visibleCount*thumbWidth+(visibleCount-1)*6+60))
    val galleryScroll=rememberScrollState()
    val density=LocalDensity.current
    var firstVideo by remember {mutableIntStateOf(0)}
    LaunchedEffect(firstVideo) {galleryScroll.animateScrollTo(with(density){(firstVideo*(thumbWidth+6)).dp.roundToPx()})}
    LaunchedEffect(video) {if(video && !capture){vm.galleryExpanded=true;kotlinx.coroutines.delay(1000);vm.galleryExpanded=false}}
    Box(Modifier.fillMaxSize().background(Color(0xf2081228)).clickable {if(video)vm.openSection(Section.Map)else vm.closeMedia()}.testTag("media-modal"),contentAlignment=Alignment.Center) {
        Box(Modifier.width((if(video)max(mediaWidth,galleryWidth)else mediaWidth).dp).height((mediaHeight+if(video)64 else 32).dp).clickable{}) {
            Row(Modifier.align(Alignment.TopCenter).width(mediaWidth.dp).height(32.dp).background(Color(0xff258cb1)),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                CloseText({if(video)vm.openSection(Section.Map)else vm.closeMedia()})
            }
            Box(Modifier.align(Alignment.TopCenter).offset(y=32.dp).width(mediaWidth.dp).height(mediaHeight.dp).background(Color.Black)) {
                FieldIllustration(imagePin?.art ?: vm.videoIndex,Modifier.fillMaxSize(),if(video && vm.videoPlaying)(sin(time)*.5f+.5f) else 0f)
                if(video) {
                    PixelText("FIELD REPORT ${vm.videoIndex+1} · ORIGINAL DEMO",Modifier.align(Alignment.TopStart).padding(12.dp),12,Color.White)
                    PixelButton(if(vm.videoPlaying)"Ⅱ" else "▶",{vm.videoPlaying=!vm.videoPlaying},Modifier.align(Alignment.Center).size(48.dp),Ink.Cream,size=22)
                    PixelText("${if(vm.videoPlaying)"PLAYING" else "PAUSED"} · DEMO ANIMATION",Modifier.align(Alignment.BottomStart).padding(10.dp),10,Color.White)
                }
            }
            if(video) {
                Column(Modifier.align(Alignment.BottomCenter).width(galleryWidth.dp).background(Ink.Cyan).padding(2.dp).background(Ink.Navy).padding(6.dp)) {
                    Row(Modifier.fillMaxWidth().height(24.dp).clickable{vm.galleryExpanded=!vm.galleryExpanded}.testTag("gallery-toggle"),verticalAlignment=Alignment.CenterVertically) {PixelText("NOW PLAYING: FIELD REPORT ${vm.videoIndex+1}",Modifier.weight(1f),if(width<=420)9 else if(width<=640)10 else 12,maxLines=1);PixelText(if(vm.galleryExpanded)"⌄" else "⌃",Modifier.padding(start=6.dp),14)}
                    AnimatedVisibility(vm.galleryExpanded,enter=expandVertically(tween(320))+fadeIn(),exit=shrinkVertically(tween(320))+fadeOut()) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(16.dp).height(thumbHeight.dp).clickable(role=Role.Button){firstVideo=max(0,firstVideo-1)}.semantics{contentDescription="Previous video thumbnails"},contentAlignment=Alignment.Center){PixelText("‹",size=24)}
                            Row(Modifier.weight(1f).clipToBounds().horizontalScroll(galleryScroll),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                (0..4).forEach {i->Column(Modifier.width(thumbWidth.dp).clickable{vm.videoIndex=i}.testTag("video-$i")){FieldIllustration(i,Modifier.fillMaxWidth().height(thumbHeight.dp));PixelText("REPORT ${i+1}",Modifier.fillMaxWidth(),9,if(i==vm.videoIndex)Color.White else Ink.Cyan,align=TextAlign.Center)}}
                            }
                            Box(Modifier.width(16.dp).height(thumbHeight.dp).clickable(role=Role.Button){firstVideo=min(5-visibleCount,firstVideo+1)}.semantics{contentDescription="Next video thumbnails"},contentAlignment=Alignment.Center){PixelText("›",size=24)}
                        }
                    }
                }
            }
        }
    }
}
