package com.spidertracker.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    lateinit var tracker:TrackerViewModel; private set
    lateinit var mapFilters:MapFiltersViewModel; private set
    lateinit var geocoder:GeocodingProvider
    lateinit var locationProvider:DeviceLocationProvider
    lateinit var navigation:NavigationRuntime; private set
    private var navigationPermissionPending=false
    private val notificationRequest=registerForActivityResult(ActivityResultContracts.RequestPermission()) {startNavigationService()}
    private var permissionPending=false
    private var centerLocationPending=true
    private val permissionRequest=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants->
        permissionPending=false
        if(navigationPermissionPending) {
            navigationPermissionPending=false
            if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true)requestNavigationNotification()
            else navigation.controller.pause("PRECISE LOCATION PERMISSION DENIED")
            return@registerForActivityResult
        }
        if(grants[Manifest.permission.ACCESS_COARSE_LOCATION]==true || grants[Manifest.permission.ACCESS_FINE_LOCATION]==true)locateDevice(centerLocationPending)
        else tracker.locationDenied(!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun requestNavigation() {
        if(permissionPending)return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)requestNavigationNotification()
        else {
            permissionPending=true;navigationPermissionPending=true
            permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    private fun requestNavigationNotification() {
        val preferences=getSharedPreferences("navigation",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !preferences.getBoolean("notificationAsked",false)) {
            preferences.edit().putBoolean("notificationAsked",true).apply()
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startNavigationService()
    }
    private fun startNavigationService() {
        try {ContextCompat.startForegroundService(this,Intent(this,NavigationService::class.java))}
        catch(error:Exception){navigation.controller.pause("NAVIGATION SERVICE COULD NOT START")}
    }
    fun requestCurrentLocation() {
        requestLocation(true)
    }
    private fun locateDevice(center:Boolean) {tracker.locate(locationProvider,center) {
        if(navigation.controller.state.value.active)navigation.controller.follow(true)
    }}
    private fun requestLocation(centerCamera:Boolean) {
        if(tracker.locating)return
        if(permissionPending)return
        // Explicit recenter replaces an automatic route-origin lookup.
        centerLocationPending=centerCamera
        val granted=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(granted)locateDevice(centerCamera)
        else {permissionPending=true;centerLocationPending=centerCamera;permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    }
    override fun onStop() {if(::tracker.isInitialized)tracker.cancelLocation();super.onStop()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        tracker=ViewModelProvider(this)[TrackerViewModel::class.java]
        mapFilters=ViewModelProvider(this,object:ViewModelProvider.Factory {
            override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T {
                @Suppress("UNCHECKED_CAST")
                return MapFiltersViewModel(getSharedPreferences("map-layers",MODE_PRIVATE),globalAttractions=CachedGlobalAttractionsRepository(this@MainActivity)) as T
            }
        })[MapFiltersViewModel::class.java]
        tracker.advancePhase(BootPhase.Live)
        geocoder=PhotonGeocoder(getSharedPreferences("address-search",MODE_PRIVATE),BuildConfig.GEOCODER_URL)
        locationProvider=AndroidLocationProvider(this)
        navigation=NavigationRuntime.get(this)
        if(tracker.destination==null) {
            val session=navigation.controller.state.value
            if(session.active || session.state==NavigationState.Paused)session.request?.destination?.let {tracker.chooseMapDestination(it.lat,it.lon)}
            else navigation.controller.stop()
        }
        val capture=BuildConfig.DEBUG && intent.getBooleanExtra("capture",false)
        if(capture) {
            tracker.advancePhase(BootPhase.Live)
            when(intent.getStringExtra("screen")) {
                "menu"->tracker.openSection(Section.Menu)
                "log"->tracker.openSection(Section.Log)
                "events"->tracker.openSection(Section.Events)
                "report"->tracker.openSection(Section.Report)
                "webwatch"->tracker.openSection(Section.WebWatch)
                "help"->tracker.openSection(Section.Help)
                "videos"->tracker.openSection(Section.Videos)
                "detail"->{tracker.selectPin(tracker.pins.first { it.kind==PinKind.Rumored },true);tracker.activatePin()}
                "confirmed-off"->tracker.toggleFilter(PinKind.Confirmed)
                "both-off"->{tracker.toggleFilter(PinKind.Confirmed);tracker.toggleFilter(PinKind.Rumored)}
                "card"->tracker.selectPin(tracker.pins.first {it.kind==PinKind.Rumored},true)
                "media"->{tracker.selectPin(tracker.pins.first {it.kind==PinKind.Rumored},true);tracker.activatePin();tracker.openDetailMedia()}
                "gallery"->{tracker.openSection(Section.Videos);tracker.galleryExpanded=true}
                "sound"->tracker.advancePhase(BootPhase.Sound)
                "tutorial"->tracker.advancePhase(BootPhase.Tutorial)
            }
        }
        val map=MapLibreRenderer()
        setContent { TrackerApp(tracker,map,capture,onSearch={tracker.search(geocoder)},onSearchEdit={mapFilters.closeDetails();mapFilters.closeFilters();tracker.editSearch(it,geocoder)},onCurrentLocation=::requestCurrentLocation,filters=mapFilters,navigation=navigation,onStartNavigation=::requestNavigation,onRouteLocation={requestLocation(false)}) }
    }
}
