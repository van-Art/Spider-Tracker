package com.spidertracker.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

// elapsedMs is monotonic device time, never wall-clock time.
data class DeviceFix(val lat:Double,val lon:Double,val accuracy:Float,val test:Boolean=false,
    val elapsedMs:Long=SystemClock.elapsedRealtime()) {
    init {require(validCoordinates(lat,lon) && accuracy.isFinite() && accuracy>=0)}
}
interface DeviceLocationProvider {
    suspend fun currentFix():DeviceFix
    fun updates():Flow<DeviceFix> = flow {emit(currentFix())}
}
class LocationUnavailable(message:String):Exception(message)
fun usableFix(lat:Double,lon:Double,accuracy:Float,ageMs:Long)=validCoordinates(lat,lon) && accuracy.isFinite() && accuracy>=0 && ageMs in 0..12000
internal fun freshRequestFix(fixMs:Long,requestMs:Long,nowMs:Long)=fixMs>=requestMs && nowMs-fixMs in 0..12000

class AndroidLocationProvider(context:Context):DeviceLocationProvider {
    private val app=context.applicationContext
    private val manager=app.getSystemService(LocationManager::class.java)
    override suspend fun currentFix():DeviceFix=updates().first()
    override fun updates():Flow<DeviceFix> = callbackFlow {
        val fine=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse)throw LocationUnavailable("LOCATION PERMISSION REQUIRED")
        if(!LocationManagerCompat.isLocationEnabled(manager))throw LocationUnavailable("LOCATION SERVICES ARE OFF · ENABLE DEVICE LOCATION")
        val providers=manager.getProviders(true).filter {it==LocationManager.NETWORK_PROVIDER || (fine && it==LocationManager.GPS_PROVIDER)}
        if(providers.isEmpty())throw LocationUnavailable("NO LOCATION PROVIDER AVAILABLE")
        val requested=SystemClock.elapsedRealtime()
        val handler=Handler(Looper.getMainLooper())
        var best:DeviceFix?=null
        var delivered=false
        val fallback=Runnable {best?.let {delivered=true;trySend(it)}}
        val listener=object:LocationListener {
            override fun onLocationChanged(location:Location) {
                val at=location.elapsedRealtimeNanos/1000000
                if(!location.hasAccuracy() || !freshRequestFix(at,requested,SystemClock.elapsedRealtime()) ||
                    !usableFix(location.latitude,location.longitude,location.accuracy,SystemClock.elapsedRealtime()-at))return
                val mock=if(Build.VERSION.SDK_INT>=31)location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
                val fix=DeviceFix(location.latitude,location.longitude,location.accuracy,mock,at)
                if(best==null || fix.accuracy<=best!!.accuracy || at-best!!.elapsedMs>5000)best=fix
                if(delivered) {trySend(fix);return}
                if(!fine || LocationManager.GPS_PROVIDER !in providers || fix.accuracy<=50 || SystemClock.elapsedRealtime()-requested>=5000) {
                    delivered=true;handler.removeCallbacks(fallback);trySend(best!!)
                }
            }
            override fun onProviderDisabled(provider:String) {
                if(providers.none {manager.isProviderEnabled(it)})close(LocationUnavailable("LOCATION SERVICES ARE OFF · ENABLE DEVICE LOCATION"))
            }
            override fun onProviderEnabled(provider:String)=Unit
            @Deprecated("Legacy provider callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
        }
        try {
            // Only callbacks newer than this request count; cached fixes never fulfill a recenter.
            providers.forEach {manager.requestLocationUpdates(it,1000,0f,listener,Looper.getMainLooper())}
            handler.postDelayed(fallback,5000)
        } catch(error:Exception) {
            manager.removeUpdates(listener);handler.removeCallbacks(fallback)
            throw LocationUnavailable("LOCATION UNAVAILABLE · CHECK PERMISSION AND DEVICE LOCATION")
        }
        awaitClose {manager.removeUpdates(listener);handler.removeCallbacks(fallback)}
    }
}
