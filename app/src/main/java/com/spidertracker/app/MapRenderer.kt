package com.spidertracker.app

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.expressions.Expression.*
import android.graphics.RectF
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString

data class MapScene(val pins:List<TrackerPin>,val camera:CameraTarget,val selected:TrackerPin?,val mobile:Boolean,val destination:AddressResult?=null,
    val markers:List<LayerMarker> = emptyList(),val placesEnabled:Boolean=false,val userFix:DeviceFix?=null,
    val reducedMotion:Boolean=false,val capture:Boolean=false,
    val onAttractionViewport:(AttractionBounds,Double)->Unit={_,_->},val navigation:NavigationSnapshot=NavigationSnapshot(),
    val onGesture:()->Unit={},val onCoordinate:(Double,Double)->Unit={_,_->},val navigationHudHeight:Int=0)

data class ProjectedMarker(val id:String,val kind:MarkerKind,val x:Float,val y:Float,val count:Int=1,val lat:Double=0.0,val lon:Double=0.0,val demo:Boolean=false)
private class LayerRenderWork {var active=false;var destroyed=false;var frameReady=false;var projectionPending=true;var idleRefreshPending=true;var lastProjection=0L;var points:List<ProjectedMarker> = emptyList();var refreshJob:Job?=null;var appliedCamera:CameraTarget?=null}

interface TrackerMapRenderer {
    @Composable fun Content(scene:MapScene,modifier:Modifier,onPin:(TrackerPin)->Unit,onBackground:()->Unit,
        onProjection:(Offset?)->Unit,onCamera:(Double,Double,Double)->Unit,
        onMarker:(String)->Unit,onPlaces:(List<OsmPlaceRecord>,String)->Unit,onSprites:(List<ProjectedMarker>)->Unit,
        onRadarViewport:(RadarSnapshot)->Unit)
}

class MapLibreRenderer : TrackerMapRenderer {
    @Composable override fun Content(scene:MapScene,modifier:Modifier,onPin:(TrackerPin)->Unit,onBackground:()->Unit,
        onProjection:(Offset?)->Unit,onCamera:(Double,Double,Double)->Unit,
        onMarker:(String)->Unit,onPlaces:(List<OsmPlaceRecord>,String)->Unit,onSprites:(List<ProjectedMarker>)->Unit,
        onRadarViewport:(RadarSnapshot)->Unit) {
        val context=LocalContext.current
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        val density=LocalDensity.current.density
        val latestScene by rememberUpdatedState(scene)
        val pinCallback by rememberUpdatedState(onPin)
        val backgroundCallback by rememberUpdatedState(onBackground)
        val projectionCallback by rememberUpdatedState(onProjection)
        val cameraCallback by rememberUpdatedState(onCamera)
        val markerCallback by rememberUpdatedState(onMarker)
        val spritesCallback by rememberUpdatedState(onSprites)
        val radarCallback by rememberUpdatedState(onRadarViewport)
        val work=remember {LayerRenderWork()}
        val scope=rememberCoroutineScope()
        var map by remember { mutableStateOf<MapLibreMap?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var cameraMoving by remember { mutableStateOf(false) }
        val view=remember {
            MapLibre.getInstance(context)
            MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
        }
        fun projectRadar() {
            val current=map ?: return
            if(!loaded || view.width==0 || view.height==0 || !work.active || work.destroyed)return
            val target=current.cameraPosition.target ?: return
            val projection=current.projection
            val bounds=projection.visibleRegion.latLngBounds
            latestScene.onAttractionViewport(AttractionBounds.fromMap(bounds.latitudeSouth,bounds.longitudeWest,bounds.latitudeNorth,bounds.longitudeEast),current.cameraPosition.zoom)
            radarCallback(viewportRadar(latestScene.markers,latestScene.userFix,target.latitude,target.longitude) {lat,lon->
                // Bounds may cross the dateline or include a repeated world at global zoom.
                val wrapped=lon+360*kotlin.math.round((bounds.center.longitude-lon)/360)
                if(lat !in bounds.latitudeSouth..bounds.latitudeNorth || wrapped !in bounds.longitudeWest..bounds.longitudeEast)null
                else projection.toScreenLocation(LatLng(lat,wrapped)).let {p->
                    (2*p.x/view.width-1) to (2*p.y/view.height-1)
                }
            })
        }
        fun project() {
            val current=map ?: return
            val selected=latestScene.selected
            projectionCallback(if(cameraMoving)null else selected?.let { current.projection.toScreenLocation(LatLng(it.lat,it.lon)).let { p -> Offset(p.x/density,p.y/density) } })
        }
        fun projectSprites() {
            val current=map ?: return
            if(!loaded || !work.active || work.destroyed || !work.frameReady)return
            val result=mutableListOf<ProjectedMarker>()
            listOf(MarkerKind.Place to "places",MarkerKind.Alert to "alerts",MarkerKind.User to "user").forEach {(kind,source)->
                if(kind==MarkerKind.User && latestScene.userFix==null)return@forEach
                if(kind!=MarkerKind.User && latestScene.markers.none {it.kind==kind})return@forEach
                current.style?.getSourceAs<GeoJsonSource>(source)?.querySourceFeatures(null).orEmpty().forEach featureLoop@{feature->
                    val point=feature.geometry() as? Point ?: return@featureLoop
                    val center=current.cameraPosition.target?.longitude ?: 0.0
                    val longitude=point.longitude()+360*kotlin.math.round((center-point.longitude())/360)
                    val p=current.projection.toScreenLocation(LatLng(point.latitude(),longitude))
                    if(p.x < -34*density || p.y < -34*density || p.x>view.width+34*density || p.y>view.height+34*density)return@featureLoop
                    val count=if(feature.hasProperty("point_count"))feature.getNumberProperty("point_count").toInt() else 1
                    val id=if(count>1)"cluster-${feature.getNumberProperty("cluster_id")}" else feature.getStringProperty("id")
                    val demo=kind==MarkerKind.Alert && (if(count>1)latestScene.markers.any {it.kind==MarkerKind.Alert && it.demo} else feature.hasProperty("demo") && feature.getBooleanProperty("demo"))
                    result.add(ProjectedMarker(id,kind,p.x,p.y,count,point.latitude(),point.longitude(),demo))
                }
            }
            latestScene.destination?.let {d->
                val p=current.projection.toScreenLocation(LatLng(d.lat,d.lon))
                if(p.x in 0f..view.width.toFloat() && p.y in 0f..view.height.toFloat())result.add(ProjectedMarker("destination",MarkerKind.User,p.x,p.y,1,d.lat,d.lon))
            }
            val visible=result.distinctBy {it.kind to it.id}.sortedByDescending {it.kind==MarkerKind.User}.take(1000)
            work.points=visible
            spritesCallback(visible)
        }
        fun settleSources() {
            work.refreshJob?.cancel()
            if(!work.active || work.destroyed)return
            // Native source events can precede the asynchronous cluster bucket update.
            work.refreshJob=scope.launch {
                repeat(6) {
                    delay(150)
                    if(!work.active || work.destroyed)return@launch
                    projectSprites()
                }
            }
        }
        AndroidView(factory={ view.apply { getMapAsync { current ->
            if(work.destroyed)return@getMapAsync
            map=current
            current.uiSettings.apply { isCompassEnabled=false;isLogoEnabled=false;isAttributionEnabled=false;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false;isZoomGesturesEnabled=true;isDoubleTapGesturesEnabled=true;isQuickZoomGesturesEnabled=true;isScrollGesturesEnabled=true }
            current.setMinZoomPreference(1.0)
            val initial=latestScene.camera
            current.cameraPosition=CameraPosition.Builder().target(LatLng(initial.lat,initial.lon)).zoom(initial.zoom).build()
            work.appliedCamera=initial
            current.setStyle(Style.Builder().fromUri("asset://tracker-style.json")) { style ->
                if(work.destroyed)return@setStyle
                style.addSource(GeoJsonSource("navigation-routes",FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(LineLayer("route-casing","navigation-routes").withProperties(lineColor("#04101f"),lineWidth(11f),lineJoin("round"),lineCap("round")))
                style.addLayer(LineLayer("route-alternatives","navigation-routes").withFilter(eq(get("selected"),false)).withProperties(lineColor("#537693"),lineWidth(5f),lineJoin("round"),lineCap("round")))
                style.addLayer(LineLayer("route-selected","navigation-routes").withFilter(eq(get("selected"),true)).withProperties(lineColor("#00d7fa"),lineWidth(6f),lineJoin("round"),lineCap("round")))
                style.addLayer(SymbolLayer("route-direction","navigation-routes").withFilter(eq(get("selected"),true)).withProperties(
                    symbolPlacement("line"),symbolSpacing(110f),textField("›"),textFont(arrayOf("Noto Sans Regular")),textSize(22f),textColor("#04101f"),textRotationAlignment("map"),textKeepUpright(false)))
                PinKind.entries.forEach { style.addImage(it.name,markerBitmap(it,64).apply { this.density=160 }) }
                style.addSource(GeoJsonSource("sightings", features(latestScene.pins)))
                PinKind.entries.sortedBy { when(it) {PinKind.Rumored->0;PinKind.Confirmed->1;PinKind.Event->2;PinKind.Trailer->3} }.forEach { kind ->
                    style.addLayer(SymbolLayer("pins-${kind.name}","sightings").withFilter(org.maplibre.android.style.expressions.Expression.eq(get("kind"),kind.name)).withProperties(
                        iconImage(kind.name),iconSize(if(latestScene.mobile)0.4f else 0.5f),iconAllowOverlap(true),iconIgnorePlacement(true)))
                }
                style.addSource(GeoJsonSource("destination",destinationFeatures(latestScene.destination)))
                style.addLayer(CircleLayer("destination-marker","destination").withProperties(circleRadius(7f),circleColor("#00d7fa"),circleStrokeColor("#071623"),circleStrokeWidth(3f)))
                addSmartLayers(style)
                loaded=true;project();projectRadar();work.projectionPending=true
            }
            current.addOnMapClickListener mapClick@{ coordinate ->
                val screen=current.projection.toScreenLocation(coordinate)
                val marker=work.points.filter {kotlin.math.abs(it.x-screen.x)<=24*density && kotlin.math.abs(it.y-screen.y)<=24*density}
                    .minByOrNull {(it.x-screen.x)*(it.x-screen.x)+(it.y-screen.y)*(it.y-screen.y)}
                if(marker!=null) {
                    if(marker.count>1) {
                        backgroundCallback()
                        current.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(marker.lat,marker.lon),(current.cameraPosition.zoom+2).coerceIn(current.minZoomLevel,current.maxZoomLevel)),500)
                    } else if(marker.kind!=MarkerKind.User)markerCallback(marker.id)
                    return@mapClick true
                }
                val hit=latestScene.pins.map { it to current.projection.toScreenLocation(LatLng(it.lat,it.lon)) }
                    .filter { (_,p) -> kotlin.math.abs(screen.x-p.x)<24*density && kotlin.math.abs(screen.y-p.y)<24*density }
                    .minByOrNull { (_,p) -> (screen.x-p.x)*(screen.x-p.x)+(screen.y-p.y)*(screen.y-p.y) }?.first
                if(hit!=null) pinCallback(hit) else backgroundCallback()
                true
            }
            current.addOnMapLongClickListener {coordinate->latestScene.onGesture();latestScene.onCoordinate(coordinate.latitude,coordinate.longitude);true}
            current.addOnCameraMoveListener cameraMove@{ if(!work.active || work.destroyed)return@cameraMove;project();val now=SystemClock.elapsedRealtime();if(now-work.lastProjection>=33){
                work.lastProjection=now;projectRadar()
                work.points=work.points.map {point->val center=current.cameraPosition.target?.longitude ?: 0.0;val lon=point.lon+360*kotlin.math.round((center-point.lon)/360);val p=current.projection.toScreenLocation(LatLng(point.lat,lon));point.copy(x=p.x,y=p.y)}
                spritesCallback(work.points)
            };val c=current.cameraPosition; c.target?.let { cameraCallback(it.latitude,it.longitude,c.zoom) } }
            current.addOnCameraMoveStartedListener { reason -> cameraMoving=true;projectionCallback(null);if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE){latestScene.onGesture();backgroundCallback()} }
            current.addOnCameraIdleListener cameraIdle@{if(!work.active || work.destroyed)return@cameraIdle;cameraMoving=false;project();projectRadar();work.projectionPending=true;current.triggerRepaint();val c=current.cameraPosition;c.target?.let {cameraCallback(it.latitude,it.longitude,c.zoom)}}
            view.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->projectRadar()}
            view.addOnSourceChangedListener {source->
                if(source in setOf("places","alerts","user","destination")){work.projectionPending=true;work.idleRefreshPending=true;settleSources()}
            }
            view.addOnDidBecomeIdleListener {
                if(work.idleRefreshPending && work.active && work.frameReady && !work.destroyed) {
                    work.idleRefreshPending=false;projectSprites()
                }
            }
            view.addOnDidFinishRenderingFrameListener {fully,_,_->
                if(loaded && work.active && !work.destroyed) {
                    work.frameReady=true
                    if(work.projectionPending && !cameraMoving){work.projectionPending=false;projectSprites();projectRadar()}
                }
            }
        } } },modifier=modifier)
        LaunchedEffect(scene.pins,scene.mobile,loaded) {
            if(loaded) map?.style?.let { style ->
                style.getSourceAs<GeoJsonSource>("sightings")?.setGeoJson(features(scene.pins))
                PinKind.entries.forEach { style.getLayerAs<SymbolLayer>("pins-${it.name}")?.setProperties(iconSize(if(scene.mobile)0.4f else 0.5f)) }
            }
        }
        LaunchedEffect(scene.destination,loaded) {
            if(loaded)map?.style?.getSourceAs<GeoJsonSource>("destination")?.setGeoJson(destinationFeatures(scene.destination))
        }
        LaunchedEffect(scene.navigation.routeRevision,loaded) {
            if(loaded)map?.style?.getSourceAs<GeoJsonSource>("navigation-routes")?.setGeoJson(FeatureCollection.fromFeatures(
                scene.navigation.routes.withIndex().sortedBy {it.index==scene.navigation.selected}.map {(index,route)->
                    Feature.fromGeometry(LineString.fromLngLats(route.points.map {Point.fromLngLat(it.lon,it.lat)})).apply {addBooleanProperty("selected",index==scene.navigation.selected)}
                }))
        }
        LaunchedEffect(scene.navigation.routeRevision,scene.navigationHudHeight,loaded) {
            val nav=scene.navigation
            if(loaded && nav.state==NavigationState.RoutePreview && !nav.loading)nav.route?.let {route->
                val bounds=LatLngBounds.Builder().includes(route.points.map {LatLng(it.lat,it.lon)}).build()
                val top=(scene.navigationHudHeight+48*density).toInt().coerceAtMost((view.height*.60).toInt())
                val bottom=(150*density).toInt().coerceAtMost((view.height*.25).toInt())
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,(24*density).toInt(),top,(24*density).toInt(),bottom),800)
            }
        }
        LaunchedEffect(scene.navigation.fix?.elapsedMs,scene.navigation.following,scene.navigation.active,loaded) {
            val nav=scene.navigation;val fix=nav.fix
            if(loaded && nav.active && nav.following && nav.gpsReliable && fix!=null)map?.let {current->
                val zoom=when {nav.toManeuver<100->17.0;fix.speed>22->15.5;else->16.5}
                val driving=nav.request?.mode==TravelMode.Driving
                current.cancelTransitions()
                current.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                    .target(LatLng(fix.point.lat,fix.point.lon)).zoom(zoom).bearing(fix.bearing ?: current.cameraPosition.bearing)
                    .tilt(if(driving)40.0 else 0.0).padding(16*density.toDouble(),(scene.navigationHudHeight+70*density).toDouble(),16*density.toDouble(),100*density.toDouble()).build()),850)
            }
        }
        LaunchedEffect(scene.markers,scene.userFix,loaded) {
            if(loaded)map?.style?.let {style->
                listOf(MarkerKind.Place to "places",MarkerKind.Alert to "alerts").forEach {(kind,id)->
                    style.getSourceAs<GeoJsonSource>(id)?.setGeoJson(layerFeatures(scene.markers.filter {it.kind==kind}))
                }
                style.getSourceAs<GeoJsonSource>("user")?.setGeoJson(userFeatures(scene.userFix))
                val radius=scene.userFix?.let {it.accuracy/(78271.51696*kotlin.math.cos(Math.toRadians(it.lat)).coerceAtLeast(.00001))} ?: 0.0
                style.getLayerAs<CircleLayer>("user-accuracy")?.setProperties(circleRadius(interpolate(exponential(2),zoom(),stop(0,radius),stop(22,radius*4194304))))
                work.projectionPending=true;work.idleRefreshPending=true;map?.triggerRepaint();settleSources()
                projectRadar()
            }
        }
        LaunchedEffect(loaded,scene.userFix!=null,scene.reducedMotion,scene.capture,lifecycle) {
            if(loaded && scene.userFix!=null && !scene.reducedMotion && !scene.capture)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while(true) {
                    val opacity=(.09+.035*kotlin.math.sin(SystemClock.elapsedRealtime()/450.0)).toFloat()
                    map?.style?.getLayerAs<CircleLayer>("user-accuracy")?.setProperties(circleOpacity(opacity))
                    delay(90)
                }
            }
        }
        LaunchedEffect(scene.camera,loaded) {
            if(loaded) map?.let { current->
                // Style readiness and recomposition must not replay a consumed camera request.
                if(work.appliedCamera==scene.camera || (scene.navigation.active && scene.navigation.following))return@let
                work.appliedCamera=scene.camera
                val position=current.cameraPosition
                val zoom=scene.camera.zoom.coerceIn(current.minZoomLevel,current.maxZoomLevel)
                if(position.target!=LatLng(scene.camera.lat,scene.camera.lon) || kotlin.math.abs(position.zoom-zoom)>.001) {
                    current.cancelTransitions()
                    current.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(scene.camera.lat,scene.camera.lon)).zoom(zoom).bearing(0.0).tilt(0.0).build()),1500)
                }
            }
        }
        LaunchedEffect(scene.selected,loaded) { project() }
        DisposableEffect(lifecycle,view) {
            val observer=LifecycleEventObserver { _,event -> when(event) {
                Lifecycle.Event.ON_START->{work.active=true;view.onStart()};Lifecycle.Event.ON_RESUME->{work.active=true;view.onResume();work.projectionPending=true;map?.triggerRepaint()}
                Lifecycle.Event.ON_PAUSE->{work.active=false;work.frameReady=false;view.onPause()};Lifecycle.Event.ON_STOP->{work.active=false;view.onStop()};else->Unit
            } }
            lifecycle.addObserver(observer)
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){work.active=true;view.onStart()}
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
            onDispose {work.destroyed=true;work.active=false;map=null;lifecycle.removeObserver(observer);view.onPause();view.onStop();view.onDestroy()}
        }
    }
    private fun addSmartLayers(style:Style) {
        listOf("places","alerts","user").forEach {id->
            val options=if(id=="user")GeoJsonOptions() else GeoJsonOptions().withCluster(true).withClusterRadius(42).withClusterMaxZoom(15)
            style.addSource(GeoJsonSource(id,FeatureCollection.fromFeatures(emptyList()),options))
        }
        style.addLayer(CircleLayer("user-accuracy","user").withProperties(circleColor("#00d7fa"),circleOpacity(.1f),circleStrokeColor("#00d7fa"),circleStrokeWidth(1f),circleStrokeOpacity(.25f),circlePitchScale("map")))
        style.addLayer(CircleLayer("user-dot","user").withProperties(circleRadius(6f),circleColor("#00d7fa"),circleStrokeWidth(2f),circleStrokeColor("#04101f")))
        listOf("places" to "#45d26a","alerts" to "#ff4d50","user" to "#00d7fa","destination" to "#00d7fa").forEach {(id,color)->
            if(id=="places" || id=="alerts")style.addLayer(CircleLayer("$id-cluster-glow",id).withFilter(has("point_count")).withProperties(
                circleRadius(21f),circleColor(color),circleOpacity(.12f),circleStrokeWidth(1f),circleStrokeColor(color),circleStrokeOpacity(.3f),
                circlePitchAlignment("viewport"),circlePitchScale("viewport")))
            if(id!="destination")style.addLayer(CircleLayer(if(id=="user")"user-hit" else "$id-hits",id).withProperties(circleRadius(17f),circleOpacity(0f)))
        }
    }
    private fun layerFeatures(markers:List<LayerMarker>)=FeatureCollection.fromFeatures(markers.map {
        Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)).apply {addStringProperty("id",it.id);addBooleanProperty("demo",it.demo)}
    })
    private fun userFeatures(fix:DeviceFix?)=FeatureCollection.fromFeatures(fix?.let {
        listOf(Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)).apply {addStringProperty("id","user")})
    } ?: emptyList())
    private fun destinationFeatures(destination:AddressResult?)=FeatureCollection.fromFeatures(destination?.let {
        listOf(Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)))
    } ?: emptyList())
    private fun features(pins:List<TrackerPin>) = FeatureCollection.fromFeatures(pins.map { pin ->
        Feature.fromGeometry(Point.fromLngLat(pin.lon,pin.lat)).apply { addStringProperty("id",pin.id);addStringProperty("kind",pin.kind.name) }
    })
}
