package com.spidertracker.app

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun validRoutingEndpoint(value:String,debug:Boolean):Boolean {
    val url=value.toHttpUrlOrNull() ?: return false
    return url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null &&
        (url.isHttps || (debug && url.host in setOf("localhost","127.0.0.1","10.0.2.2")))
}
fun navigationLanguage(locale:Locale):String = when(locale.language) {
    "ru"->"ru-RU";"fr"->"fr-FR";"de"->"de-DE";"es"->"es-ES";"it"->"it-IT";"pt"->"pt-PT";
    "nl"->"nl-NL";"uk"->"uk-UA";"tr"->"tr-TR";"ja"->"ja-JP";else->"en-US"
}
internal fun valhallaRequest(request:RouteRequest):String {
    fun location(point:NavPoint)=JSONObject().put("lat",point.lat).put("lon",point.lon).put("type","break").put("radius",100)
    val body=JSONObject().put("locations",JSONArray().put(location(request.origin)).put(location(request.destination)))
        .put("costing",request.mode.costing).put("units","kilometers").put("language",request.language)
        .put("directions_type","instructions").put("alternates",2)
    if(request.mode==TravelMode.Driving)body.put("costing_options",JSONObject().put("auto",JSONObject()
        .put("speed_types",JSONArray().put("freeflow").put("constrained")).put("ignore_closures",true)))
    return body.toString()
}

class ValhallaRoutingProvider(private val endpoint:()->String,private val debug:Boolean=BuildConfig.DEBUG):RoutingProvider {
    private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .callTimeout(25,TimeUnit.SECONDS).followRedirects(false).build()
    override suspend fun routes(request:RouteRequest):List<NavRoute> {
        val base=endpoint().trim().trimEnd('/')
        if(!validRoutingEndpoint(base,debug))throw RoutingFailure("CONFIGURE YOUR VALHALLA SERVICE URL")
        val body=suspendCancellableCoroutine<String> {continuation->
            val call=client.newCall(Request.Builder().url("$base/route")
                .post(valhallaRequest(request).toRequestBody("application/json".toMediaType()))
                .header("Accept","application/json").header("User-Agent","SpiderTrackerAndroid/0.4").build())
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    if(continuation.isActive)continuation.resumeWithException(RoutingFailure(
                        if(e is SocketTimeoutException || e is java.io.InterruptedIOException)"ROUTING REQUEST TIMED OUT" else "ROUTING UNAVAILABLE · CHECK NETWORK AND SERVICE"))
                }
                override fun onResponse(call:Call,response:Response) {
                    try {
                        val text=response.use {r->
                            val reader=r.body?.charStream() ?: throw RoutingFailure("EMPTY ROUTING RESPONSE")
                            val output=StringBuilder();val buffer=CharArray(8192)
                            while(true) {val n=reader.read(buffer);if(n<0)break;if(output.length+n>4_000_000)throw RoutingFailure("OVERSIZED ROUTING RESPONSE");output.append(buffer,0,n)}
                            if(!r.isSuccessful) {
                                val code=runCatching {JSONObject(output.toString()).optInt("error_code")}.getOrDefault(0)
                                throw RoutingFailure(when(code) {
                                    171->"NO ROUTABLE ROAD NEAR LOCATION";442->"NO AVAILABLE ROUTE";170,172->"COORDINATES OUTSIDE SERVICE COVERAGE"
                                    else->if(r.code>=500)"ROUTING SERVICE UNAVAILABLE" else "ROUTING REQUEST REJECTED (${r.code})"
                                })
                            }
                            output.toString()
                        }
                        if(continuation.isActive)continuation.resume(text)
                    } catch(error:Exception) {if(continuation.isActive)continuation.resumeWithException(if(error is RoutingFailure)error else RoutingFailure("INVALID ROUTING RESPONSE"))}
                }
            })
        }
        return withContext(Dispatchers.Default) {
            try {
                parseValhalla(body).also {routes->require(routes.all {it.points.first().distance(request.origin)<300 && it.points.last().distance(request.destination)<300})}
            }catch(error:RoutingFailure){throw error}catch(error:Exception){throw RoutingFailure("INVALID ROUTING RESPONSE")}
        }
    }
}

internal fun decodePolyline6(encoded:String):List<NavPoint> {
    require(encoded.length in 2..2_000_000)
    var index=0;var lat=0L;var lon=0L
    fun coordinate():Long {
        var value=0L;var shift=0
        while(true) {
            require(index<encoded.length && shift<=30)
            val b=encoded[index++].code-63;require(b in 0..63)
            value=value or ((b and 31).toLong() shl shift);shift+=5
            if(b<32)break
        }
        return if(value and 1L!=0L)(value shr 1).inv() else value shr 1
    }
    return buildList {
        while(index<encoded.length) {
            lat+=coordinate();lon+=coordinate()
            add(NavPoint(lat/1_000_000.0,lon/1_000_000.0));require(size<=200000)
        }
    }.also {require(it.size>=2)}
}

internal fun parseValhalla(body:String):List<NavRoute> {
    require(body.length<=4_000_000)
    val root=JSONObject(body)
    if(root.has("error_code"))throw RoutingFailure("NO AVAILABLE ROUTE (${root.optInt("error_code")})")
    fun trip(value:JSONObject):NavRoute {
        require(value.optInt("status",0)==0)
        val factor=when(value.getString("units")){"kilometers"->1000.0;"miles"->1609.344;else->error("Unknown route units")}
        val summary=value.getJSONObject("summary")
        val legs=value.getJSONArray("legs");require(legs.length() in 1..20)
        val points=mutableListOf<NavPoint>();val maneuvers=mutableListOf<Maneuver>()
        for(i in 0 until legs.length()) {
            val leg=legs.getJSONObject(i);val shape=decodePolyline6(leg.getString("shape"))
            if(points.isNotEmpty())require(points.last().distance(shape.first())<5) {"Disconnected legs"}
            val offset=if(points.isEmpty())0 else points.lastIndex
            points.addAll(if(points.isEmpty())shape else shape.drop(1));require(points.size<=200000)
            val steps=leg.getJSONArray("maneuvers");require(steps.length() in 1..10000)
            for(j in 0 until steps.length()) {
                val m=steps.getJSONObject(j)
                val begin=m.getInt("begin_shape_index");val end=m.getInt("end_shape_index")
                require(begin in shape.indices && end in begin..shape.lastIndex)
                val street=m.optJSONArray("street_names")?.let {names->(0 until names.length().coerceAtMost(5)).joinToString(" / "){names.getString(it)}}.orEmpty().take(300)
                val instruction=m.getString("instruction").trim().take(1000);require(instruction.isNotEmpty())
                val verbal=m.optString("verbal_pre_transition_instruction","").ifBlank {m.optString("verbal_transition_alert_instruction",instruction)}.take(1000)
                maneuvers.add(Maneuver(begin+offset,end+offset,instruction,street,m.getDouble("time"),m.getInt("type"),verbal))
            }
        }
        return NavRoute(points,summary.getDouble("length")*factor,summary.getDouble("time"),maneuvers,value.optString("language","en-US"))
    }
    val primary=trip(root.getJSONObject("trip"))
    val alternatives=root.optJSONArray("alternates")
    return buildList {
        add(primary)
        if(alternatives!=null)for(i in 0 until alternatives.length().coerceAtMost(2)) {
            // A malformed alternative must not erase a valid primary route.
            runCatching {trip(alternatives.getJSONObject(i).getJSONObject("trip"))}.getOrNull()?.let {add(it)}
        }
    }
}
