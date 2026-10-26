package com.spidertracker.app

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import android.os.SystemClock
import kotlin.math.*

val PixelFont=FontFamily(Font(R.font.tracker_pixel))

@Composable fun PixelText(text:String,modifier:Modifier=Modifier,size:Int=12,color:Color=Ink.Cyan,
    maxLines:Int=Int.MAX_VALUE,align:TextAlign=TextAlign.Start,overflow:TextOverflow=TextOverflow.Ellipsis) {
    BasicText(text,modifier,style=TextStyle(color=color,fontFamily=PixelFont,fontSize=size.sp,
        lineHeight=(size*1.25f).sp,textAlign=align),maxLines=maxLines,overflow=overflow)
}

data class TrackerLayout(val width:Float,val height:Float) {
    val mobile=width<=768
    val pageHeight=height
    val trackerHeight=height
    val outer=2f
    val top=2f
    val edge=25f
    val mapX=outer+edge
    val mapY=28f
    val mapWidth=(width-2*mapX).coerceAtLeast(1f)
    val shelfY=(height-58f).coerceAtLeast(mapY+8)
    val mapHeight=(shelfY-mapY).coerceAtLeast(1f)
    val radarSize=(width*.265f).coerceIn(92f,120f)
    val radarX=mapX+mapWidth-14-radarSize
    val radarY=mapY+mapHeight-11-radarSize
    val radarCenterX=radarX+radarSize/2
    val radarCenterY=radarY+radarSize/2
    val locationX=radarCenterX+radarSize*.325f-24
    val locationY=(radarCenterY+radarSize*.405f-24).coerceAtMost(shelfY-48)
    val globalX=radarX+radarSize-24
    val globalY=locationY-48
}

@Composable fun PixelButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,fill:Color=Ink.Cream,
    color:Color=Ink.Edge,size:Int=12,maxLines:Int=1) {
    Box(modifier.clickable(role=Role.Button,onClick=onClick).defaultMinSize(minWidth=48.dp,minHeight=32.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            pixelPath(steppedPath(this.size.width,this.size.height,2.dp.toPx()),Ink.Edge)
            val inset=2.dp.toPx()
            with(drawContext.canvas) {save();translate(inset,inset);pixelPath(steppedPath(this@Canvas.size.width-inset*2,this@Canvas.size.height-inset*2,inset),fill);restore()}
            pixelRect(Color.White.copy(alpha=.18f),Offset(5.dp.toPx(),3.dp.toPx()),Size(this.size.width-10.dp.toPx(),this.size.height*.35f))
        }
        PixelText(label,Modifier.padding(horizontal=10.dp,vertical=5.dp),size,color,maxLines=maxLines,align=TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrackerApp(vm:TrackerViewModel,renderer:TrackerMapRenderer,capture:Boolean=false,
    onSearch:()->Unit,onCurrentLocation:()->Unit,filters:MapFiltersViewModel,onSearchEdit:(String)->Unit={vm.editSearch(it)},
    navigation:NavigationRuntime?=null,onStartNavigation:()->Unit={},onRouteLocation:()->Unit=onCurrentLocation) {
    val nav=navigation?.controller?.state?.collectAsStateWithLifecycle()?.value ?: NavigationSnapshot()
    val endpoint=navigation?.endpoint?.collectAsStateWithLifecycle()?.value.orEmpty()
    val voiceStatus=navigation?.voiceStatus?.collectAsStateWithLifecycle()?.value.orEmpty()
    var mode by remember {mutableStateOf(nav.request?.mode ?: TravelMode.Driving)}
    val manualOrigin=vm.manualOrigin?.let {NavPoint(it.lat,it.lon)}
    var picked by remember {mutableStateOf<NavPoint?>(null)}
    var navigationHudHeight by remember {mutableIntStateOf(0)}
    val effectiveFix=nav.fix?.takeIf {nav.active || nav.state==NavigationState.Arrived}?.let {DeviceFix(it.point.lat,it.point.lon,it.accuracy.toFloat(),it.simulated,it.elapsedMs)} ?: vm.userFix
    val planning=vm.placeState in setOf(PlaceUiState.RoutePlanning,PlaceUiState.RouteReady,PlaceUiState.Error)
    var stopConfirmation by remember {mutableStateOf(false)}
    fun stopNavigation() {navigation?.controller?.stop();vm.cancelLocation();vm.routeBack();stopConfirmation=false}
    fun calculateRoute() {
        val destination=vm.destination ?: return
        val origin=manualOrigin ?: vm.userFix?.takeIf {SystemClock.elapsedRealtime()-it.elapsedMs in 0..12000}?.let {NavPoint(it.lat,it.lon)}
        if(origin==null) {navigation?.controller?.locationError("LOCATION REQUIRED · SEARCH FROM OR LONG-PRESS MAP");onRouteLocation();return}
        navigation?.controller?.preview(RouteRequest(origin,NavPoint(destination.lat,destination.lon),mode,navigationLanguage(Locale.getDefault())))
    }
    LaunchedEffect(vm.destination,manualOrigin,mode,endpoint,planning,vm.userFix!=null) {
        if(navigation!=null && vm.destination!=null && planning && !nav.active && nav.state!=NavigationState.Paused)calculateRoute()
        else if(!nav.active && !planning && nav.state==NavigationState.RoutePreview)navigation?.controller?.stop()
    }
    LaunchedEffect(nav.state,nav.loading,nav.route) {
        if(nav.active)vm.routeState(PlaceUiState.Navigating)
        else if(nav.state==NavigationState.Paused)vm.routeState(PlaceUiState.RouteReady)
        else if(planning)vm.routeState(when {
            nav.state==NavigationState.Error->PlaceUiState.Error
            nav.route!=null && !nav.loading->PlaceUiState.RouteReady
            else->PlaceUiState.RoutePlanning
        })
    }
    LaunchedEffect(nav.fix?.elapsedMs) {nav.fix?.takeIf {nav.gpsReliable}?.let {vm.navigationFix(it)}}
    LaunchedEffect(filters.state) {
        if(vm.placeState==PlaceUiState.PlaceDetails && vm.destinationMarkerId!=null && filters.visibleMarkers.none {it.id==vm.destinationMarkerId})vm.clearDestination()
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val clock=remember {ForegroundAnimationClock()}
    val reducedMotion=rememberReducedMotion()
    LaunchedEffect(lifecycle,capture,reducedMotion) {if(!capture && !reducedMotion)clock.run(lifecycle)}
    val timeProvider=remember(clock,capture,reducedMotion) {{if(reducedMotion)0f else if(capture).7f else clock.seconds}}
    var projectedMarkers by remember {mutableStateOf<List<ProjectedMarker>>(emptyList())}
    var anchor by remember {mutableStateOf<Offset?>(null)}
    var radarViewport by remember {mutableStateOf(RadarSnapshot(RadarMode.Unavailable,null,null,20,emptyList()))}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val selectMarker:(String)->Unit={id->filters.visibleMarkers.firstOrNull {it.id==id}?.let {
        if(nav.active)vm.notify("STOP NAVIGATION TO CHANGE DESTINATION") else {
            val changing=vm.routeSearch!=null
            focus.clearFocus();keyboard?.hide();navigation?.controller?.stop();vm.navigateToPlace(it)
            if(!changing)filters.select(it)
        }
    }}
    val uriHandler=LocalUriHandler.current
    val tone=remember {ToneGenerator(AudioManager.STREAM_MUSIC,25)}
    DisposableEffect(tone) {onDispose {tone.release()}}
    LaunchedEffect(vm.toastRevision,vm.sound) {if(vm.sound && !capture)tone.startTone(ToneGenerator.TONE_PROP_BEEP,60)}
    BackHandler(nav.active || planning || vm.destination!=null || picked!=null || filters.filterKind!=null || filters.selected!=null || vm.searchVisible || vm.phase!=BootPhase.Live || vm.section!=Section.Map || vm.selected!=null || vm.detail!=null || vm.mediaId!=null || vm.explanation!=null) {when {
        stopConfirmation->stopConfirmation=false
        navigation?.controller?.state?.value?.active==true->stopConfirmation=true
        picked!=null->picked=null
        vm.searchVisible->{vm.hideSearch();focus.clearFocus();keyboard?.hide()}
        filters.filterKind!=null->filters.closeFilters()
        planning->{navigation?.controller?.stop();vm.routeBack()}
        vm.destination!=null->{filters.closeDetails();vm.clearDestination()}
        filters.selected!=null->filters.closeDetails()
        else->vm.back()
    }}
    LaunchedEffect(vm.phase) {
        when(vm.phase) {
            BootPhase.Boot->{delay(2000);vm.advancePhase(BootPhase.Sound)}
            BootPhase.Tutorial->{delay(7000);vm.advancePhase(BootPhase.Loading)}
            BootPhase.Loading->{delay(1500);vm.advancePhase(BootPhase.Live)}
            else->Unit
        }
    }
    LaunchedEffect(vm.section) {if(!capture){delay(800);vm.explainIfNeeded()}}
    LaunchedEffect(vm.toastRevision) {
        val revision=vm.toastRevision
        if(vm.toast.isNotEmpty())filters.showNotice(vm.toast,5000)
        delay(5000);vm.clearToast(revision)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal))).semantics {stateDescription=vm.uiState.name}.testTag("tracker-root")) {
            val m=TrackerLayout(maxWidth.value,maxHeight.value)
            FrameChrome(m)
            Box(Modifier.offset(m.mapX.dp,m.mapY.dp).size(m.mapWidth.dp,m.mapHeight.dp).clipToBounds()
                .semantics {stateDescription="${filters.visibleMarkers.size} layer markers; native MapLibre map"}.testTag("map-viewport")) {
                renderer.Content(MapScene(emptyList(),vm.camera,vm.selected,m.mobile,vm.destination,filters.visibleMarkers,false,effectiveFix,reducedMotion,capture,filters::loadViewport,
                    navigation=nav,onGesture={navigation?.controller?.follow(false)},onCoordinate={lat,lon->if(navigation!=null){if(nav.active)vm.notify("STOP NAVIGATION TO CHANGE ROUTE") else {focus.clearFocus();keyboard?.hide();vm.hideSearch();filters.closeDetails();navigation.controller.follow(false);picked=NavPoint(lat,lon)}}},navigationHudHeight=navigationHudHeight),Modifier.fillMaxSize(),
                    onPin={vm.selectPin(it)},onBackground={vm.exploreRadar();vm.clearCard();vm.closeDetail();if(vm.placeState==PlaceUiState.PlaceDetails){filters.closeDetails();vm.clearDestination()}},
                    onProjection={anchor=it},onCamera={_,_,zoom->vm.observeZoom(zoom)},
                    onMarker=selectMarker,
                    onPlaces=filters::acceptPlaces,onSprites={projectedMarkers=it},onRadarViewport={radarViewport=it})
                MapGrid()
                MapMarkerSprites(projectedMarkers,Modifier.fillMaxSize(),selectMarker,timeProvider,reducedMotion)
                Row(Modifier.align(Alignment.BottomStart).background(Ink.Water.copy(alpha=.85f)).padding(horizontal=2.dp,vertical=1.dp)) {
                    listOf("© OpenStreetMap" to "https://www.openstreetmap.org/copyright"," · OpenMapTiles" to "https://www.openmaptiles.org/"," · OpenFreeMap" to "https://openfreemap.org/").forEach {(label,url)->
                        PixelText(label,Modifier.clickable(role=Role.Button){uriHandler.openUri(url)},6,Color(0xffb4cad2),maxLines=1)
                    }
                }
                vm.selected?.takeIf {anchor!=null}?.let {pin->
                    val p=anchor!!
                    val x=(p.x-75).coerceIn(0f,max(0f,m.mapWidth-150))
                    val y=(p.y-116).coerceIn(0f,max(0f,m.mapHeight-100))
                    PinCard(pin,Modifier.offset(x.dp,y.dp),vm::activatePin)
                }
                TrackerPanels(vm,m,capture)
                MapLayerPanels(filters,vm,m,visiblePlaceCount=projectedMarkers.count {it.kind==MarkerKind.Place})
                if(vm.phase!=BootPhase.Live)Onboarding(vm,m,timeProvider())
                HudNoticeBanner(filters,reducedMotion,Modifier.align(Alignment.TopCenter))
            }
            val logoWidth=(m.width*.49f).coerceAtMost(280f)
            SuppliedImage(R.drawable.tracker_logo,"Spider Tracker",Modifier.offset(((m.width-logoWidth)/2).dp,16.dp).size(logoWidth.dp,(logoWidth*47/280).dp).testTag("tracker-logo"))
            SuppliedImage(R.drawable.left_up_logo,null,Modifier.offset(12.dp,13.dp).size(40.dp).testTag("left-logo"))
            SuppliedImage(R.drawable.right_up_logo,null,Modifier.offset((m.width-52).dp,13.dp).size(40.dp).testTag("right-logo"))
            // Only bottom overlays consume IME insets; the native map and fixed chrome keep their constraints.
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
                val searchLayout=TrackerLayout(maxWidth.value,maxHeight.value.coerceAtLeast(110f))
                val radarTopCorrection=(12f-searchLayout.radarY).coerceAtLeast(0f)
                FrameChrome(searchLayout,bottomOnly=true)
                if(m.mapHeight>=150 && vm.section==Section.Map && vm.phase==BootPhase.Live)RadarOverlay(searchLayout,timeProvider,filters,vm,radarViewport,onCurrentLocation,selectMarker,radarTopCorrection,
                    effectiveFix,if(nav.active)nav.following else vm.radarGpsCentered,{navigation?.controller?.follow(false);vm.globalView()})
                if(navigation!=null && (planning || nav.active || nav.state==NavigationState.Arrived || nav.state==NavigationState.Paused || picked!=null) && vm.section==Section.Map && !vm.searchVisible && filters.selected==null && filters.filterKind==null)NavigationPanel(
                    navigation,nav,vm.destination,searchLayout,mode,{mode=it},manualOrigin,{vm.gpsOrigin();onRouteLocation()},::calculateRoute,
                    {navigation.controller.follow(true);onStartNavigation()},picked,
                    {picked?.let {vm.chooseMapDestination(it.lat,it.lon);vm.showRoutePlanner()};picked=null},
                    {picked?.let {vm.setMapOrigin(it)};picked=null;if(vm.destination==null)vm.notify("START SET · SEARCH OR LONG-PRESS FOR DESTINATION")},
                    {picked=null},voiceStatus,{if(nav.active)stopConfirmation=true else {navigation.controller.stop();vm.routeBack()}},{navigationHudHeight=it},
                    vm.manualOrigin?.label,vm::searchEndpoint)
                BottomSearchOverlay(vm,searchLayout,clock,capture,reducedMotion,onSearch,onSearchEdit,onFocus={filters.closeDetails();filters.closeFilters()},searchRight=if(radarTopCorrection>0)searchLayout.radarX-8 else searchLayout.width)
            }
            MapLayerControls(filters,m)
            MessageDetail(vm,m)
            listOf(Section.Report,Section.Events).forEach {section->
                androidx.compose.animation.AnimatedVisibility(vm.section==section,enter=slideInHorizontally(tween(300)){it}+fadeIn(tween(220)),exit=slideOutHorizontally(tween(240)){it}+fadeOut(tween(220))) {SidePanel(vm,section,maxWidth.value,maxHeight.value)}
            }
            if(vm.mediaId!=null || vm.section==Section.Videos)MediaPanel(vm,maxWidth.value,maxHeight.value,timeProvider(),capture)
            DetailPanelOverlay(filters,vm)
            if(stopConfirmation)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.65f)),contentAlignment=Alignment.Center) {
                Column(Modifier.padding(40.dp).background(Ink.Water).padding(16.dp).testTag("stop-navigation-confirmation")) {
                    PixelText("STOP NAVIGATION?",size=15)
                    PixelButton("KEEP NAVIGATING",{stopConfirmation=false},Modifier.fillMaxWidth().padding(top=12.dp).heightIn(min=48.dp))
                    PixelButton("STOP NAVIGATION",::stopNavigation,Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=48.dp).testTag("confirm-navigation-stop"))
                }
            }
        }
    }
}

private val FrameCyan=Color(0xff00befc)
@Composable private fun FrameChrome(m:TrackerLayout,bottomOnly:Boolean=false) {
    val height=if(bottomOnly)m.height-m.shelfY else m.shelfY
    // The two non-overlapping slices keep the upper frame fixed and close the HUD at the IME edge.
    Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.align(if(bottomOnly)Alignment.BottomStart else Alignment.TopStart).fillMaxWidth().height(height.dp)
        .testTag(if(bottomOnly)"bottom-hud-frame" else "fixed-hud-frame")) {
      if(bottomOnly)drawRect(Color.Black)
      clipRect { translate(top=if(bottomOnly)size.height-m.height.dp.toPx() else 0f) {
        val px=1.dp.toPx()
        fun rect(x:Float,y:Float,w:Float,h:Float,c:Color)=pixelRect(c,Offset(x*px,y*px),Size(w.coerceAtLeast(0f)*px,h.coerceAtLeast(0f)*px))
        drawContext.canvas.save();drawContext.canvas.translate(2*px,2*px)
        pixelPath(steppedPath((m.width-4)*px,(m.height-4)*px,5*px),Color(0xff0063a7))
        drawContext.canvas.translate(4*px,4*px)
        pixelPath(steppedPath((m.width-12)*px,(m.height-12)*px,5*px),Ink.Edge)
        drawContext.canvas.translate(2*px,2*px)
        pixelPath(steppedPath((m.width-16)*px,(m.height-16)*px,5*px),FrameCyan)
        drawContext.canvas.restore()
        rect(17f,17f,m.width-34,11f,Color(0xff004f72))
        rect(17f,m.mapY,10f,m.mapHeight,Color(0xff006087))
        rect(m.width-27,m.mapY,10f,m.mapHeight,Color(0xff006087))
        rect(m.mapX-1,m.mapY-1,m.mapWidth+2,m.mapHeight+2,Ink.Edge)
        rect(m.mapX,m.mapY,m.mapWidth,m.mapHeight,Ink.Water)
      } }
    }
    }
}

@Composable private fun MapGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val px=1.dp.toPx()
        repeat((size.width/(4*px)).toInt()) {i->val dx=5*px+i*4*px;val h=if(i%20==0)10 else if(i%5==0)6 else 3;pixelLine(Ink.Cyan.copy(alpha=.65f),Offset(dx,21*px),Offset(dx,(21+h)*px),px)}
        repeat((size.height/(4*px)).toInt()) {i->val dy=21*px+i*4*px;val w=if(i%20==0)8 else if(i%5==0)5 else 2;pixelLine(Ink.Cyan.copy(alpha=.65f),Offset(5*px,dy),Offset((5+w)*px,dy),px)}
        repeat(5) {i->val gy=size.height*(.08f+i*.21f);pixelLine(Ink.Cyan.copy(alpha=.13f),Offset(0f,gy),Offset(size.width,gy),px)}
        repeat(5) {i->val gx=size.width*(.03f+i*.24f);pixelLine(Ink.Cyan.copy(alpha=.10f),Offset(gx,0f),Offset(gx,size.height),px)}
    }
}

@Composable private fun PinCard(pin:TrackerPin,modifier:Modifier,onClick:()->Unit) {
    Column(modifier.size(150.dp,100.dp).background(Ink.Edge).padding(3.dp).clickable(role=Role.Button,onClick=onClick).testTag("pin-card")) {
        FieldIllustration(pin.art,Modifier.fillMaxWidth().weight(1f))
        val text=when(pin.kind){PinKind.Event->"VIEW EVENT";PinKind.Trailer->"PLAY VIDEO";else->"VIEW SIGHTING"}
        PixelText(text,Modifier.fillMaxWidth().background(Ink.Cream).padding(vertical=4.dp),10,Color.Black,align=TextAlign.Center)
    }
}

@Composable private fun Onboarding(vm:TrackerViewModel,m:TrackerLayout,time:Float) {
    if(vm.phase==BootPhase.Tutorial) {
        HelpPanel({vm.advancePhase(BootPhase.Loading)},m)
        return
    }
    Box(Modifier.fillMaxSize().background(Ink.Charcoal).testTag("onboarding")) {
        Canvas(Modifier.fillMaxSize()) {repeat((size.height/6.dp.toPx()).toInt()){i->pixelLine(Ink.Cyan.copy(alpha=.045f),Offset(0f,i*6.dp.toPx()),Offset(size.width,i*6.dp.toPx()),1.dp.toPx())}}
        if(vm.phase==BootPhase.Loading) PixelText("INITIALIZING MAP...",Modifier.align(Alignment.Center),16,align=TextAlign.Center)
        else {
            Canvas(Modifier.fillMaxWidth().height((m.mapHeight*.62f).dp)) {val x=size.width/2;pixelLine(Ink.Cyan,Offset(x,0f),Offset(x,size.height-40.dp.toPx()),2.dp.toPx());drawRect(Ink.Orange,Offset(x-17.dp.toPx(),size.height-45.dp.toPx()),Size(34.dp.toPx(),38.dp.toPx()));drawRect(Ink.Water,Offset(x-10.dp.toPx(),size.height-36.dp.toPx()),Size(20.dp.toPx(),14.dp.toPx()))}
            if(vm.phase==BootPhase.Sound) Column(Modifier.align(Alignment.BottomCenter).padding(bottom=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                PixelText("WELCOME TO THE SPIDER TRACKER.\nINTERACT WITH THE MAP TO VIEW\nSIGHTINGS ALL OVER THE WORLD.",size=14,align=TextAlign.Center)
                Spacer(Modifier.height(28.dp));PixelText("CHOOSE YOUR SETTINGS AND START TRACKING",size=10)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    PixelButton("SOUND ON",{vm.chooseSound(true)},fill=Ink.Bevel,color=Ink.Cyan)
                    PixelButton("SOUND OFF",{vm.chooseSound(false)},Modifier.testTag("sound-off-choice"),Ink.Bevel,Ink.Cyan)
                }
            }
        }
    }
}
