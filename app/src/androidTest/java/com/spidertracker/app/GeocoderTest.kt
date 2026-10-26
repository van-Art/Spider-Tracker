package com.spidertracker.app

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeocoderTest {
    private val body="""{"features":[
      {"geometry":{"type":"Point","coordinates":[-.12,51.5]},"properties":{"osm_type":"R","osm_id":1,"name":"London","city":"London","country":"United Kingdom"}},
      {"geometry":{"type":"Point","coordinates":[200,100]},"properties":{"name":"Invalid"}},
      {"geometry":{"type":"Polygon","coordinates":[]},"properties":{"name":"Wrong geometry"}},
      {"geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}
    ]}"""
    @Test fun parserValidatesGeometryCoordinatesAndLabels() {
        val results=parsePhoton(body)
        assertEquals(1,results.size);assertEquals("London, United Kingdom",results[0].label)
        assertEquals(51.5,results[0].lat,0.0);assertEquals(-.12,results[0].lon,0.0)
        assertEquals("London",results[0].name);assertEquals("London",results[0].locality)
        assertEquals("United Kingdom",results[0].country)
        val town=parsePhoton("""{"features":[{"geometry":{"type":"Point","coordinates":[1,2]},"properties":{"name":"TEST PLACE","town":"TEST TOWN"}}]}""").single()
        assertEquals("TEST TOWN",town.locality);assertNull(town.country)
        assertEquals("TEST PLACE, TEST TOWN",town.label)
        assertTrue(parsePhoton("{\"features\":[]}").isEmpty())
        assertThrows(Exception::class.java){parsePhoton("broken")}
        assertThrows(Exception::class.java){parsePhoton("x".repeat(262145))}
    }
    @Test fun realProviderCodeCachesNormalizesAndThrottlesRequests()=runBlocking {
        val preferences=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("geocoder-test",Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val starts=mutableListOf<Long>();val urls=mutableListOf<String>()
        val provider=PhotonGeocoder(preferences,"https://example.org"){url->starts.add(SystemClock.elapsedRealtime());urls.add(url.toString());body}
        try {
            assertTrue(provider.configured)
            val first=provider.search("  London  ")
            assertEquals(first,provider.search("london"));assertEquals(1,starts.size)
            provider.search("Paris France");assertEquals(2,starts.size)
            assertTrue(starts[1]-starts[0]>=1090)
            assertTrue(urls[1].contains("q=Paris+France&limit=5"))
            val restored=PhotonGeocoder(preferences,"https://example.org"){error("cache not used")}
            assertEquals(first,restored.search("London"))
            assertFalse(PhotonGeocoder(preferences,"").configured)
            assertFalse(PhotonGeocoder(preferences,"http://example.org").configured)
            assertFalse(PhotonGeocoder(preferences,"https://user:secret@example.org").configured)
        } finally {preferences.edit().clear().commit()}
    }
    @Test fun inMemoryCacheIsBoundedAndKeepsRecentQueries()=runBlocking {
        val preferences=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("geocoder-memory-${UUID.randomUUID()}",Context.MODE_PRIVATE)
        val entries=JSONArray();val now=System.currentTimeMillis()
        repeat(33){index->entries.put(JSONObject().put("query","https://example.org|cached$index").put("at",now).put("body",body))}
        preferences.edit().putString("queries",entries.toString()).commit()
        var calls=0
        val provider=PhotonGeocoder(preferences,"https://example.org"){calls++;body}
        try {
            repeat(33){index->provider.search("cached$index")}
            assertEquals(0,calls)
            preferences.edit().clear().commit()
            provider.search("fresh query");assertEquals(1,calls)
            provider.search("cached0");assertEquals(2,calls)
            provider.search("cached32");assertEquals(2,calls)
        } finally {preferences.edit().clear().commit()}
    }
    @Test fun busyProviderCooldownPreventsRepeatedRequests()=runBlocking {
        val preferences=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("geocoder-busy-${UUID.randomUUID()}",Context.MODE_PRIVATE)
        var calls=0
        val provider=PhotonGeocoder(preferences,"https://example.org"){calls++;throw SearchUnavailable("BUSY",30000)}
        try {
            assertTrue(runCatching {provider.search("London")}.exceptionOrNull() is SearchUnavailable)
            assertEquals(1,calls)
            val next=runCatching {provider.search("Berlin")}.exceptionOrNull()
            assertTrue(next is SearchUnavailable);assertTrue(next!!.message!!.contains("BUSY"))
            assertEquals(1,calls)
        } finally {preferences.edit().clear().commit()}
    }

}
