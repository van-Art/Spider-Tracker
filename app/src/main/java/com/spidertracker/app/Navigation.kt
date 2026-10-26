package com.spidertracker.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

data class NavPoint(val lat:Double,val lon:Double) {
    init {require(validCoordinates(lat,lon))}
    fun distance(other:NavPoint)=distanceKm(lat,lon,other.lat,other.lon)*1000
}
enum class TravelMode(val costing:String,val label:String,val maxSpeed:Double) {
    Driving("auto","DRIVE",75.0), Walking("pedestrian","WALK",8.0), Cycling("bicycle","CYCLE",25.0)
}
enum class NavigationState { Idle, RoutePreview, Navigating, Recalculating, Arrived, Paused, Error }
data class Maneuver(val begin:Int,val end:Int,val instruction:String,val street:String,val seconds:Double,val type:Int,
    val verbal:String=instruction)
data class NavRoute(val points:List<NavPoint>,val meters:Double,val seconds:Double,val maneuvers:List<Maneuver>,val language:String="en-US") {
    val cumulative=DoubleArray(points.size).also {a->for(i in 1 until points.size)a[i]=a[i-1]+points[i-1].distance(points[i])}
    val length get()=cumulative.last()
    init {
        require(points.size in 2..200000 && meters.isFinite() && meters>0 && seconds.isFinite() && seconds>0)
        require(length>0 && maneuvers.isNotEmpty())
        require(maneuvers.all {it.begin in points.indices && it.end in it.begin..points.lastIndex && it.seconds.isFinite() && it.seconds>=0})
        require(maneuvers.zipWithNext().all {(a,b)->a.begin<=b.begin})
    }
}
data class RouteRequest(val origin:NavPoint,val destination:NavPoint,val mode:TravelMode,val language:String="en-US")
interface RoutingProvider {suspend fun routes(request:RouteRequest):List<NavRoute>}
class RoutingFailure(message:String):Exception(message)
data class NavFix(val point:NavPoint,val accuracy:Double,val elapsedMs:Long,val speed:Double=0.0,val bearing:Double?=null,val simulated:Boolean=false)
data class RouteMatch(val point:NavPoint,val along:Double,val distance:Double)

/** Segment projection uses wrapped longitudes, including routes across the antimeridian. */
fun matchRoute(route:NavRoute,point:NavPoint,previous:Double?=null,maxAdvance:Double=Double.POSITIVE_INFINITY):RouteMatch {
    var best:RouteMatch?=null
    val longitudeScale=111195*cos(Math.toRadians(point.lat)).coerceAtLeast(.000001)
    fun longitude(lon:Double)=((lon-point.lon+540)%360-180)*longitudeScale
    for(i in 0 until route.points.lastIndex) {
        if(previous!=null && (route.cumulative[i+1]<previous-35 || route.cumulative[i]>previous+maxAdvance))continue
        val a=route.points[i];val b=route.points[i+1]
        val ax=longitude(a.lon);val ay=(a.lat-point.lat)*111195
        val bx=longitude(b.lon);val by=(b.lat-point.lat)*111195
        val dx=bx-ax;val dy=by-ay;val square=dx*dx+dy*dy
        val t=if(square==0.0)0.0 else (-(ax*dx+ay*dy)/square).coerceIn(0.0,1.0)
        val along=route.cumulative[i]+(route.cumulative[i+1]-route.cumulative[i])*t
        if(previous!=null && (along<previous-35 || along>previous+maxAdvance))continue
        val distance=hypot(ax+t*dx,ay+t*dy)
        if(best==null || distance<best.distance) {
            val lon=point.lon+(ax+t*dx)/longitudeScale
            best=RouteMatch(NavPoint(a.lat+(b.lat-a.lat)*t,((lon+540)%360)-180),along,distance)
        }
    }
    return best ?: matchRoute(route,point)
}

data class NavigationSnapshot(
    val state:NavigationState=NavigationState.Idle,val routes:List<NavRoute> = emptyList(),val selected:Int=0,
    val request:RouteRequest?=null,val loading:Boolean=false,val message:String="",val fix:NavFix?=null,
    val along:Double=0.0,val maneuver:Int=0,val toManeuver:Double=0.0,val remaining:Double=0.0,val seconds:Double=0.0,
    val following:Boolean=true,val muted:Boolean=false,val routeRevision:Int=0,val gpsReliable:Boolean=false
) {
    val route get()=routes.getOrNull(selected)
    val active get()=state==NavigationState.Navigating || state==NavigationState.Recalculating
    val next get()=route?.maneuvers?.getOrNull(maneuver)
}
data class NavigationConfig(val deviationMeters:Double=45.0,val deviationMs:Long=8000,val cooldownMs:Long=30000,
    val maxAccuracy:Double=50.0,val staleMs:Long=12000)

/** Main-thread confined; routing is cancellable and every result also carries a generation check. */
class NavigationController(private val scope:CoroutineScope,private val provider:RoutingProvider,
    private val now:()->Long,private val config:NavigationConfig=NavigationConfig()) {
    private val mutable=MutableStateFlow(NavigationSnapshot())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var generation=0
    private var deviationSince:Long?=null
    private var deviations=0
    private var lastAttempt=Long.MIN_VALUE/2
    private var arrivalSince:Long?=null
    private var arrivals=0
    private var lastAccepted:NavFix?=null
    private var requestInFlight:RouteRequest?=null

    fun preview(request:RouteRequest) {
        if(mutable.value.loading && requestInFlight==request)return
        lastAccepted=null;resetConfirmation()
        calculate(request,false)
    }
    private fun calculate(request:RouteRequest,reroute:Boolean) {
        job?.cancel();val revision=++generation;requestInFlight=request
        val old=mutable.value
        mutable.value=if(reroute)old.copy(state=NavigationState.Recalculating,loading=true,message="RECALCULATING…")
            else NavigationSnapshot(state=NavigationState.RoutePreview,request=request,loading=true,muted=old.muted,routeRevision=old.routeRevision)
        lastAttempt=now()
        job=scope.launch {
            try {
                val routes=provider.routes(request)
                require(routes.isNotEmpty()) {"NO AVAILABLE ROUTE"}
                if(revision!=generation)return@launch
                val current=mutable.value
                mutable.value=current.copy(state=if(reroute)NavigationState.Navigating else NavigationState.RoutePreview,
                    routes=routes,selected=0,request=request,loading=false,message="",along=0.0,maneuver=0,
                    remaining=routes[0].meters,seconds=routes[0].seconds,toManeuver=0.0,routeRevision=current.routeRevision+1)
                resetConfirmation()
                if(reroute)lastAccepted?.let {updateProgress(it,unrestricted=true)}
            } catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception) {
                if(revision==generation)mutable.value=mutable.value.copy(
                    state=if(reroute)NavigationState.Navigating else NavigationState.Error,loading=false,
                    message=if(error is RoutingFailure)error.message.orEmpty() else "INVALID ROUTING RESPONSE")
                resetConfirmation()
            } finally {if(revision==generation)requestInFlight=null}
        }
    }
    fun select(index:Int) {
        val s=mutable.value
        if(s.state!=NavigationState.RoutePreview || index !in s.routes.indices)return
        val route=s.routes[index]
        mutable.value=s.copy(selected=index,along=0.0,remaining=route.meters,seconds=route.seconds,routeRevision=s.routeRevision+1)
    }
    fun start() {
        val s=mutable.value
        if(s.route==null || s.loading)return
        lastAccepted=null;resetConfirmation()
        mutable.value=s.copy(state=NavigationState.Navigating,message="WAITING FOR RELIABLE GPS",gpsReliable=false,following=true)
    }
    fun pause(message:String="NAVIGATION PAUSED") {
        job?.cancel();generation++;requestInFlight=null
        mutable.value=mutable.value.copy(state=NavigationState.Paused,loading=false,message=message,gpsReliable=false)
    }
    fun recover(request:RouteRequest) {mutable.value=NavigationSnapshot(state=NavigationState.Paused,request=request,message="SESSION INTERRUPTED · CALCULATE A NEW ROUTE")}
    fun stop() {job?.cancel();generation++;requestInFlight=null;lastAccepted=null;resetConfirmation();mutable.value=NavigationSnapshot(muted=mutable.value.muted,routeRevision=mutable.value.routeRevision+1)}
    fun follow(value:Boolean) {mutable.value=mutable.value.copy(following=value)}
    fun mute() {mutable.value=mutable.value.copy(muted=!mutable.value.muted)}
    fun locationError(message:String) {resetConfirmation();mutable.value=mutable.value.copy(message=message,gpsReliable=false)}
    fun tick() {
        if(mutable.value.active && (lastAccepted==null || now()-lastAccepted!!.elapsedMs>config.staleMs))locationError("GPS SIGNAL LOST · PROGRESS HELD")
    }
    fun accept(fix:NavFix) {
        if(!mutable.value.active)return
        val previous=lastAccepted
        val age=now()-fix.elapsedMs
        if(!fix.accuracy.isFinite() || fix.accuracy !in 0.0..config.maxAccuracy || age !in 0..config.staleMs ||
            !fix.speed.isFinite() || fix.speed<0 || (fix.bearing!=null && (!fix.bearing.isFinite() || fix.bearing !in 0.0..360.0))) {
            locationError("GPS INACCURATE OR STALE · PROGRESS HELD");return
        }
        if(previous!=null) {
            val dt=(fix.elapsedMs-previous.elapsedMs)/1000.0
            if(dt<=0)return
            if(previous.point.distance(fix.point)>mutable.value.request!!.mode.maxSpeed*dt+previous.accuracy+fix.accuracy) {
                locationError("GPS JUMP IGNORED");return
            }
        }
        lastAccepted=fix
        mutable.value=mutable.value.copy(fix=fix,gpsReliable=true,
            message=if(mutable.value.message.startsWith("GPS") || mutable.value.message.startsWith("WAITING"))"" else mutable.value.message)
        val s=mutable.value;val route=s.route ?: return
        val match=matchRoute(route,fix.point)
        val threshold=max(config.deviationMeters,fix.accuracy*2)
        if(match.distance>threshold) {
            arrivalSince=null;arrivals=0
            if(deviationSince==null)deviationSince=now()
            deviations++
            if(s.state==NavigationState.Navigating && deviations>=3 && now()-deviationSince!!>=config.deviationMs && now()-lastAttempt>=config.cooldownMs) {
                calculate(s.request!!.copy(origin=fix.point),true)
            }
            return
        }
        deviationSince=null;deviations=0
        if(s.state==NavigationState.Recalculating)return
        val dt=if(previous==null)0.0 else (fix.elapsedMs-previous.elapsedMs)/1000.0
        updateProgress(fix,previous==null,max(120.0,dt*(s.request?.mode?.maxSpeed ?: 75.0)+fix.accuracy))
    }
    private fun updateProgress(fix:NavFix,unrestricted:Boolean=false,maxAdvance:Double=Double.POSITIVE_INFINITY) {
        val s=mutable.value;val route=s.route ?: return
        val match=matchRoute(route,fix.point,if(unrestricted)null else s.along,maxAdvance)
        if(match.distance>max(config.deviationMeters,fix.accuracy*2))return
        val along=max(s.along,match.along).coerceAtMost(route.length)
        val index=route.maneuvers.indexOfFirst {route.cumulative[it.begin]>along+8}.let {if(it<0)route.maneuvers.lastIndex else it}
        val remaining=route.meters*(1-along/route.length)
        // shortcut: ETA uses remaining maneuver durations, upgrade with observed segment speeds after device calibration.
        val seconds=route.maneuvers.sumOf {m->
            val begin=route.cumulative[m.begin];val end=route.cumulative[m.end]
            when {along>=end->0.0;along<=begin->m.seconds;end>begin->m.seconds*(end-along)/(end-begin);else->0.0}
        }
        mutable.value=s.copy(along=along,maneuver=index,toManeuver=(route.cumulative[route.maneuvers[index].begin]-along).coerceAtLeast(0.0),remaining=remaining,seconds=seconds)
        if(remaining<=35 && fix.point.distance(route.points.last())<=25 && fix.accuracy<=25) {
            if(arrivalSince==null)arrivalSince=now()
            arrivals++
            if(arrivals>=3 && now()-arrivalSince!!>=3000) {
                job?.cancel();generation++
                mutable.value=mutable.value.copy(state=NavigationState.Arrived,loading=false,remaining=0.0,seconds=0.0,message="DESTINATION REACHED")
            }
        } else {arrivalSince=null;arrivals=0}
    }
    private fun resetConfirmation() {deviationSince=null;deviations=0;arrivalSince=null;arrivals=0}
}
