package com.spidertracker.app

import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AddressResult(val id:String,val label:String,val lat:Double,val lon:Double,
    val name:String?=null,val locality:String?=null,val country:String?=null,
    val category:String?=null,val address:String?=null) {
    init {require(id.isNotBlank() && label.isNotBlank() && label.length<=400 && validCoordinates(lat,lon))}
}
fun validCoordinates(lat:Double,lon:Double)=lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

interface GeocodingProvider {
    val configured:Boolean
    suspend fun search(query:String):List<AddressResult>
}
class SearchUnavailable(message:String,val retryAfterMs:Long=0):IOException(message)
internal fun normalizedSearchQuery(value:String)=value.trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)

/** Debounced callers share throttling and a bounded cache with explicit submission. */
class PhotonGeocoder(private val preferences:SharedPreferences,private val endpoint:String,
    private val fetch:suspend (URL)->String=::fetchPhoton):GeocodingProvider {
    private val memory=LinkedHashMap<String,Pair<Long,List<AddressResult>>>(32,.75f,true)
    private var retryAfter=0L
    private fun remember(key:String,at:Long,results:List<AddressResult>):List<AddressResult> {
        memory[key]=at to results
        while(memory.size>32)memory.remove(memory.keys.first())
        return results
    }
    override val configured=runCatching {URI(endpoint).let {it.scheme=="https" && !it.host.isNullOrBlank() && it.userInfo==null && it.query==null && it.fragment==null}}.getOrDefault(false)

    override suspend fun search(query:String):List<AddressResult> = withContext(Dispatchers.IO) {
        if(!configured)throw SearchUnavailable("SEARCH UNAVAILABLE · NO PROVIDER CONFIGURED")
        val cleaned=query.trim().replace(Regex("\\s+")," ")
        require(cleaned.length in 3..160) {"Enter 3–160 characters"}
        val key=endpoint.trimEnd('/')+"|"+normalizedSearchQuery(cleaned)
        gate.withLock {
            val now=System.currentTimeMillis()
            memory[key]?.let {(at,results)->if(now-at in 0..CACHE_AGE_MS)return@withContext results else memory.remove(key)}
            val cache=runCatching {JSONArray(preferences.getString("queries","[]"))}.getOrElse {JSONArray()}
            val entries=(0 until cache.length()).mapNotNull {cache.optJSONObject(it)}
                .filter {now-it.optLong("at") in 0..CACHE_AGE_MS}
            entries.firstOrNull {it.optString("query")==key}?.let {entry->
                runCatching {parsePhoton(entry.getString("body"))}.getOrNull()?.let {return@withContext remember(key,entry.optLong("at"),it)}
            }
            if(SystemClock.elapsedRealtime()<retryAfter)throw SearchUnavailable("SEARCH BUSY · TRY AGAIN LATER")
            delay((1100-(SystemClock.elapsedRealtime()-lastRequest)).coerceAtLeast(0))
            ensureActive()
            lastRequest=SystemClock.elapsedRealtime()
            val url=URL(endpoint.trimEnd('/')+"/api/?q="+URLEncoder.encode(cleaned,"UTF-8")+"&limit=5&lang=en")
            val body=try {fetch(url)} catch(error:SearchUnavailable) {
                if(error.retryAfterMs>0)retryAfter=SystemClock.elapsedRealtime()+error.retryAfterMs.coerceIn(30000,3600000)
                throw error
            } finally {
                // Start spacing from the actual attempt, including first-use HTTP/URL initialization.
                lastRequest=SystemClock.elapsedRealtime()
            }
            ensureActive()
            val results=parsePhoton(body)
            val updated=JSONArray().put(JSONObject().put("query",key).put("at",now).put("body",body))
            entries.filter {it.optString("query")!=key}.take(19).forEach {updated.put(it)}
            preferences.edit().putString("queries",updated.toString()).apply()
            remember(key,now,results)
        }
    }
    companion object {
        private val gate=Mutex()
        private var lastRequest=0L
        private const val CACHE_AGE_MS=7*24*60*60*1000L
    }
}

internal fun parsePhoton(body:String):List<AddressResult> {
    require(body.length<=262144) {"Oversized geocoder response"}
    val features=JSONObject(body).getJSONArray("features")
    return (0 until features.length().coerceAtMost(100)).mapNotNull {index->
        runCatching {
            val feature=features.getJSONObject(index)
            val geometry=feature.getJSONObject("geometry")
            require(geometry.getString("type")=="Point")
            val coordinates=geometry.getJSONArray("coordinates")
            val lon=coordinates.getDouble(0);val lat=coordinates.getDouble(1)
            val properties=feature.getJSONObject("properties")
            fun text(key:String)=properties.optString(key,"").takeUnless {it=="null"}.orEmpty().replace(Regex("[\\p{Cntrl}\\s]+")," ").trim().take(200)
            val street=listOf(text("housenumber"),text("street")).filter {it.isNotEmpty()}.joinToString(" ")
            val name=text("name").takeIf {it.isNotBlank()}
            val locality=listOf("city","town","village","locality","county","state").firstNotNullOfOrNull {key->text(key).takeIf {it.isNotBlank()}}
            val country=text("country").takeIf {it.isNotBlank()}
            val label=listOf(name.orEmpty(),street,locality.orEmpty(),text("state"),country.orEmpty()).filter {it.isNotEmpty()}.distinct().joinToString(", ").take(400)
            val id=text("osm_type")+text("osm_id")+":$lat:$lon"
            AddressResult(id,label,lat,lon,name,locality,country,text("osm_value").takeIf {it.isNotBlank()},street.takeIf {it.isNotBlank()})
        }.getOrNull()
    }.distinctBy {it.id}.take(5)
}

private val searchHttpClient=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build()
private suspend fun fetchPhoton(url:URL):String=suspendCancellableCoroutine { continuation->
    val request=Request.Builder().url(url).header("User-Agent","SpiderTrackerAndroid/0.3.1.2 (debounced address search)").header("Accept","application/geo+json, application/json").build()
    val call=searchHttpClient.newCall(request)
    continuation.invokeOnCancellation {call.cancel()}
    call.enqueue(object:Callback {
        override fun onFailure(call:Call,e:IOException) {if(continuation.isActive)continuation.resumeWithException(SearchUnavailable("SEARCH UNAVAILABLE · CHECK CONNECTION"))}
        override fun onResponse(call:Call,response:Response) {
            try {
                val text=response.use {
                    if(it.code==429) {
                        val seconds=it.header("Retry-After")?.toLongOrNull()?.coerceIn(30,3600) ?: 30
                        throw SearchUnavailable("SEARCH BUSY · TRY AGAIN LATER",seconds*1000)
                    }
                    if(!it.isSuccessful)throw SearchUnavailable("SEARCH PROVIDER UNAVAILABLE")
                    val reader=it.body?.charStream() ?: throw IOException("Empty response")
                    val result=StringBuilder();val buffer=CharArray(4096)
                    while(true) {val count=reader.read(buffer);if(count<0)break;require(result.length+count<=262144);result.append(buffer,0,count)}
                    result.toString()
                }
                if(continuation.isActive)continuation.resume(text)
            } catch(error:Exception) {if(continuation.isActive)continuation.resumeWithException(error)}
        }
    })
}
