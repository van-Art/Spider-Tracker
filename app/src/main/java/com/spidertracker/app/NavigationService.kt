package com.spidertracker.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Locale

class NavigationRuntime private constructor(context:Context) {
    private val preferences=context.getSharedPreferences("navigation",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val endpoint=MutableStateFlow(preferences.getString("endpoint",BuildConfig.VALHALLA_URL).orEmpty())
    val voiceStatus=MutableStateFlow("")
    val controller=NavigationController(scope,ValhallaRoutingProvider({endpoint.value}),SystemClock::elapsedRealtime)
    init {
        runCatching {
            preferences.getString("session",null)?.let {text->
                val o=JSONObject(text)
                controller.recover(RouteRequest(NavPoint(o.getDouble("lat"),o.getDouble("lon")),NavPoint(o.getDouble("lat"),o.getDouble("lon")),TravelMode.valueOf(o.getString("mode"))))
            }
        }
        scope.launch {
            controller.state.map {s->if(s.active || s.state==NavigationState.Paused)s.request else null}.distinctUntilChanged().collect {r->
                preferences.edit().apply {if(r==null)remove("session") else putString("session",JSONObject().put("lat",r.destination.lat).put("lon",r.destination.lon).put("mode",r.mode.name).toString())}.apply()
            }
        }
    }
    fun configure(value:String):Boolean {
        val clean=value.trim().trimEnd('/')
        if(!validRoutingEndpoint(clean,BuildConfig.DEBUG))return false
        if(endpoint.value!=clean)controller.stop()
        endpoint.value=clean;preferences.edit().putString("endpoint",clean).apply();return true
    }
    companion object {
        @Volatile private var instance:NavigationRuntime?=null
        fun get(context:Context):NavigationRuntime=instance ?: synchronized(this){instance ?: NavigationRuntime(context.applicationContext).also {instance=it}}
    }
}

data class SpeechCue(val key:String,val text:String,val language:String)
class ManeuverSpeech {
    private val spoken=mutableSetOf<String>()
    fun cue(s:NavigationSnapshot):SpeechCue? {
        if(s.muted || !s.gpsReliable || (!s.active && s.state!=NavigationState.Arrived))return null
        val route=s.route ?: return null
        val step=s.next ?: return null
        val threshold=if(s.request?.mode==TravelMode.Driving)350 else 100
        val stage=when {s.state==NavigationState.Arrived->"arrival";s.toManeuver<=35->"now";s.toManeuver<=threshold->"approach";else->return null}
        val key="${s.routeRevision}:${s.maneuver}:$stage"
        if(key in spoken)return null
        val text=if(s.state==NavigationState.Arrived)route.maneuvers.last().verbal else step.verbal
        return SpeechCue(key,text,route.language)
    }
    fun delivered(cue:SpeechCue) {spoken.add(cue.key)}
}

class NavigationVoice(context:Context,private val status:(String)->Unit) {
    private var ready=false
    private val policy=ManeuverSpeech()
    private var latest:NavigationSnapshot?=null
    private var closed=false
    private var tts:TextToSpeech?=null
    init {
        tts=TextToSpeech(context.applicationContext) {result->
            if(!closed) {
                ready=result==TextToSpeech.SUCCESS
                status(if(ready)"" else "VOICE ENGINE UNAVAILABLE")
                if(ready)latest?.let {update(it)}
            }
        }
    }
    fun update(s:NavigationSnapshot) {
        latest=s
        if(s.muted || (!s.active && s.state!=NavigationState.Arrived)){tts?.stop();return}
        if(!ready)return
        val cue=policy.cue(s) ?: return
        val engine=tts ?: return
        val locale=Locale.forLanguageTag(cue.language)
        if(engine.isLanguageAvailable(locale)<TextToSpeech.LANG_AVAILABLE) {status("VOICE LANGUAGE UNAVAILABLE · READ THE HUD");return}
        engine.language=locale
        if(engine.speak(cue.text,TextToSpeech.QUEUE_FLUSH,null,cue.key)==TextToSpeech.SUCCESS) {policy.delivered(cue);status("")}
        else status("VOICE PLAYBACK FAILED")
    }
    fun close() {closed=true;tts?.stop();tts?.shutdown();tts=null;ready=false}
}

class NavigationService:Service(),LocationListener {
    private lateinit var runtime:NavigationRuntime
    private lateinit var manager:LocationManager
    private lateinit var voice:NavigationVoice
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var tracking=false
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        runtime=NavigationRuntime.get(this);manager=getSystemService(LocationManager::class.java)
        voice=NavigationVoice(this){runtime.voiceStatus.value=it}
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("navigation","GPS navigation",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") {runtime.controller.stop();stopSelf();return START_NOT_STICKY}
        try {
            ServiceCompat.startForeground(this,41,notification(runtime.controller.state.value),if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                runtime.controller.pause("PRECISE LOCATION PERMISSION REQUIRED");stopSelf();return START_NOT_STICKY
            }
            if(!LocationManagerCompat.isLocationEnabled(manager)) {
                runtime.controller.pause("LOCATION SERVICES ARE OFF");stopSelf();return START_NOT_STICKY
            }
            if(!tracking) {
                runtime.controller.start()
                if(!runtime.controller.state.value.active){stopSelf();return START_NOT_STICKY}
                tracking=true
                val providers=manager.getProviders(true).filter {it==LocationManager.GPS_PROVIDER || it==LocationManager.NETWORK_PROVIDER}
                if(providers.isEmpty()){runtime.controller.pause("NO LOCATION PROVIDER AVAILABLE");stopSelf();return START_NOT_STICKY}
                providers.forEach {manager.requestLocationUpdates(it,1000L,0f,this,Looper.getMainLooper())}
                scope.launch {
                    runtime.controller.state.collect {s->
                        voice.update(s)
                        getSystemService(NotificationManager::class.java).notify(41,notification(s))
                        if(!s.active) {if(s.state==NavigationState.Arrived)delay(2500);stopSelf()}
                    }
                }
                scope.launch {while(isActive){delay(3000);if(ContextCompat.checkSelfPermission(this@NavigationService,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){runtime.controller.pause("LOCATION PERMISSION REVOKED");return@launch};runtime.controller.tick()}}
            }
        } catch(error:Exception) {runtime.controller.pause("NAVIGATION SERVICE COULD NOT START");stopSelf()}
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location:Location) {
        if(!location.hasAccuracy() || !validCoordinates(location.latitude,location.longitude))return
        val mock=if(Build.VERSION.SDK_INT>=31)location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
        val emulator=Build.PRODUCT.contains("sdk_gphone") || Build.FINGERPRINT.startsWith("generic")
        val bearing=location.bearing.toDouble().takeIf {location.hasBearing() && location.hasSpeed() && location.speed>=1.5f && (!location.hasBearingAccuracy() || location.bearingAccuracyDegrees<=30)}
        runtime.controller.accept(NavFix(NavPoint(location.latitude,location.longitude),location.accuracy.toDouble(),location.elapsedRealtimeNanos/1_000_000,
            if(location.hasSpeed())location.speed.toDouble() else 0.0,bearing,mock || emulator))
    }
    override fun onProviderDisabled(provider:String) {if(!LocationManagerCompat.isLocationEnabled(manager))runtime.controller.locationError("GPS DISABLED · ENABLE DEVICE LOCATION")}
    override fun onProviderEnabled(provider:String)=Unit
    @Deprecated("Legacy provider callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
    override fun onDestroy() {
        manager.removeUpdates(this);scope.cancel();voice.close();runtime.voiceStatus.value=""
        if(runtime.controller.state.value.active)runtime.controller.pause("NAVIGATION INTERRUPTED · RESUME FROM THE APP")
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    private fun notification(s:NavigationSnapshot):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,NavigationService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"navigation").setSmallIcon(R.drawable.ic_tracker).setContentTitle("Spider Tracker · ${s.state.name}")
            .setContentText(s.message.ifBlank {s.next?.instruction ?: "GPS navigation"}).setContentIntent(open).setOngoing(s.active)
            .setOnlyAlertOnce(true).addAction(0,"Stop navigation",stop).setCategory(NotificationCompat.CATEGORY_NAVIGATION).build()
    }
}
