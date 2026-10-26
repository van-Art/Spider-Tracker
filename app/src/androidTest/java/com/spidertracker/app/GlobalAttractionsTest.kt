package com.spidertracker.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GlobalAttractionsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun withCache(test:(File)->Unit) {
        val directory=File(context.cacheDir,"attractions-test-${UUID.randomUUID()}").apply {mkdirs()}
        try {test(File(directory,"catalog.json"))} finally {directory.deleteRecursively()}
    }
    // Synthetic records are isolated parser/update fixtures, never application catalog data.
    private fun row(id:String,name:String="TEST ATTRACTION",lat:Double=48.8,lon:Double=2.3)=JSONObject()
        .put("id","wikidata:$id").put("name",name).put("lat",lat).put("lon",lon)
        .put("category","Building").put("country","TEST COUNTRY")
        .put("source","https://www.wikidata.org/wiki/$id")
        .put("names",JSONObject().put("en",name).put("fr","TEST FRANÇAIS"))
    private fun catalog(version:Long,rows:List<JSONObject>,removed:List<String> = emptyList())=JSONObject()
        .put("schema",1).put("version",version).put("license","CC0-1.0").put("source","Wikidata")
        .put("attractions",JSONArray(rows)).put("removed",JSONArray(removed))

    @Test fun bundledCatalogHasGroundedGlobalMetadataAndOfflineLocalizedNames()=withCache {file->
        val body=context.assets.open("global-attractions.json").bufferedReader().use {it.readText()}
        val raw=JSONObject(body);val rows=raw.getJSONArray("attractions")
        var reads=0
        val repository=CachedGlobalAttractionsRepository({reads++;body},file)
        val english=repository.load("en")
        assertEquals(94,english.size);assertEquals(94,english.map {it.id}.distinct().size)
        assertTrue(english.mapNotNull {it.country}.distinct().size>=55)
        assertTrue(english.any {it.lat<0});assertTrue(english.any {it.lat>0})
        assertTrue(english.any {it.lon<0});assertTrue(english.any {it.lon>0})
        assertTrue(raw.getBoolean("incomplete"));assertEquals("CC0-1.0",raw.getString("license"))
        english.forEach {marker->
            assertEquals(MarkerKind.Place,marker.kind);assertFalse(marker.demo)
            assertTrue(validCoordinates(marker.lat,marker.lon));assertFalse(marker.name.isNullOrBlank())
            assertFalse(marker.country.isNullOrBlank());assertEquals("Wikidata · CC0",marker.source)
            assertEquals("https://www.wikidata.org/wiki/"+marker.id.removePrefix("wikidata:"),marker.infoUrl)
            assertTrue(marker.verification!!.contains("INCOMPLETE"))
            assertTrue(PlaceCategory.valueOf(marker.category) in attractionCategories)
        }
        for(i in 0 until rows.length()) {
            assertTrue(rows.getJSONObject(i).getLong("revision")>0)
            assertNotNull(java.time.Instant.parse(rows.getJSONObject(i).getString("lastUpdated")))
        }
        assertTrue(repository.countryCoverage().size>=55)
        assertNull(repository.countryCoverage()["Q999999999"])
        val french=repository.load("fr-CA")
        val eiffel=french.first {it.id=="wikidata:Q243"}
        assertEquals(eiffel.localizedNames["fr"],eiffel.name)
        assertNotEquals(english.first {it.id==eiffel.id}.name,eiffel.name)
        assertEquals(english,repository.load("zz"))
        val default=repository.load()
        val language=Locale.getDefault().language
        default.forEach {marker->
            val original=english.first {it.id==marker.id}
            assertEquals(original.localizedNames[language] ?: original.name,marker.name)
        }
        assertEquals(1,reads)
        assertFalse(file.exists())
    }

    @Test fun deltaAtomicallyAddsUpdatesRemovesAndRestoresWithoutVersionRollback()=withCache {file->
        val base=catalog(1,listOf(row("Q243","TEST OLD"),row("Q9202","TEST REMOVE"))).toString()
        val repository=CachedGlobalAttractionsRepository({base},file)
        assertEquals(2,repository.load("en").size)
        repository.applyUpdates(catalog(2,listOf(row("Q243","TEST UPDATED",49.0,3.0),row("Q9141","TEST ADD")),listOf("wikidata:Q9202")).toString())
        val updated=repository.load("en")
        assertEquals(setOf("wikidata:Q243","wikidata:Q9141"),updated.map {it.id}.toSet())
        val first=updated.first {it.id=="wikidata:Q243"}
        assertEquals("TEST UPDATED",first.name);assertEquals(49.0,first.lat,0.0);assertEquals(3.0,first.lon,0.0)
        assertEquals(updated,CachedGlobalAttractionsRepository({base},file).load("en"))
        val bytes=file.readBytes()
        repository.applyUpdates(catalog(1,listOf(row("Q10285","TEST STALE"))).toString())
        repository.applyUpdates(catalog(2,emptyList(),listOf("wikidata:Q243")).toString())
        assertEquals(updated,repository.load("en"));assertArrayEquals(bytes,file.readBytes())
        assertEquals(2L,parseAttractions(file.readText()).version)
    }

    @Test fun malformedOrUntrustedDeltaCannotPartiallyChangeSavedCatalog()=withCache {file->
        val base=catalog(1,listOf(row("Q243"))).toString()
        val repository=CachedGlobalAttractionsRepository({base},file)
        repository.applyUpdates(catalog(2,listOf(row("Q9141"))).toString())
        val before=repository.load("en");val bytes=file.readBytes()
        val invalid=listOf(
            catalog(3,listOf(row("Q243"))).put("schema",2),
            catalog(3,listOf(row("Q243"))).put("license","CC-BY-SA"),
            catalog(3,listOf(row("Q243"))).put("source","UNKNOWN"),
            catalog(0,listOf(row("Q243"))),
            catalog(3,listOf(row("Q243",lat=91.0))),
            catalog(3,listOf(row("Q243",lon=181.0))),
            catalog(3,listOf(row("Q243").put("source","https://example.org/Q243"))),
            catalog(3,listOf(row("Q243").put("source","https://www.wikidata.org/wiki/Q9141"))),
            catalog(3,listOf(row("Q01"))),
            catalog(3,listOf(row("Q243").put("category","Food"))),
            catalog(3,listOf(row("Q243",name="   "))),
            catalog(3,listOf(row("Q243"),row("Q243"))),
            catalog(3,emptyList(),listOf("Q243")),
            catalog(3,listOf(row("Q243")),listOf("wikidata:Q243")))
        invalid.forEach {payload->
            assertThrows(IllegalArgumentException::class.java){repository.applyUpdates(payload.toString())}
            assertEquals(before,repository.load("en"));assertArrayEquals(bytes,file.readBytes())
        }
        assertThrows(Exception::class.java){repository.applyUpdates("{broken")}
        assertEquals(before,CachedGlobalAttractionsRepository({base},file).load("en"))
    }

    @Test fun corruptOrOlderCacheFallsBackToBundledCatalog()=withCache {file->
        val base=catalog(5,listOf(row("Q243","TEST BUNDLED"))).toString()
        file.writeText("{truncated")
        assertEquals("TEST BUNDLED",CachedGlobalAttractionsRepository({base},file).load("en").single().name)
        file.writeText(catalog(4,listOf(row("Q9141","TEST OLD CACHE"))).toString())
        assertEquals("TEST BUNDLED",CachedGlobalAttractionsRepository({base},file).load("en").single().name)
        assertThrows(IllegalArgumentException::class.java){parseAttractions(catalog(1,listOf(row("Q243"),row("Q243"))).toString())}
    }

    @Test fun regionalPacksLoadOnlyInTheirBoundsAndDeduplicateAgainstOverview()=withCache {file->
        val base=catalog(1,listOf(row("Q243"))).toString()
        var reads=0
        val source=object:AttractionDataSource {
            override fun read()=base
            override fun regions()=listOf(AttractionRegion("Q142",AttractionBounds(48.0,2.0,49.0,3.0)))
            override fun readRegion(id:String):String {reads++;return catalog(2,listOf(row("Q243"),row("Q9141",lat=48.9,lon=2.4))).toString()}
        }
        val repository=CachedGlobalAttractionsRepository(source,file)
        val paris=AttractionBounds(48.0,2.0,49.0,3.0)
        assertEquals(1,repository.loadViewport(paris,1.0).size);assertEquals(0,reads)
        assertEquals(1,repository.loadViewport(AttractionBounds(-20.0,10.0,-10.0,20.0),10.0).size);assertEquals(0,reads)
        assertEquals(2,repository.loadViewport(paris,5.0).size);assertEquals(1,reads)
        assertEquals(2,repository.loadViewport(paris,17.0).size);assertEquals(1,reads)
        assertEquals(1,repository.loadViewport(paris,1.0).size)
        assertEquals(2,geographicRadar(repository.loadViewport(paris,17.0),null,48.8,2.3,100).dots.count {it.kind==MarkerKind.Place})
        repository.applyRegionUpdates("Q142",catalog(3,listOf(row("Q9141","TEST UPDATED REGION",48.9,2.4)
            .put("countryIds",JSONArray(listOf("Q142"))).put("lastUpdated","2026-10-25T00:00:00Z"))).toString())
        assertEquals("TEST UPDATED REGION",repository.loadViewport(paris,17.0).first {it.id=="wikidata:Q9141"}.name)
        val stored=File(file.parentFile,"attraction-regions/Q142.json")
        assertEquals("2026-10-25T00:00:00Z",parseAttractions(stored.readText()).entities.first {it.marker.id=="wikidata:Q9141"}.lastUpdated)
        val bytes=stored.readBytes()
        assertThrows(IllegalArgumentException::class.java){repository.applyRegionUpdates("../Q142",catalog(4,emptyList()).toString())}
        assertArrayEquals(bytes,stored.readBytes())
    }

    @Test fun bundledRegionalDataAddsRealCityDetailAndPersistsProvenance()=withCache {file->
        val source=LocalAttractionDataSource(context)
        assertEquals(setOf("Q142","Q145"),source.regions().map {it.id}.toSet())
        val repository=CachedGlobalAttractionsRepository(source,file)
        val base=repository.load().size
        val london=repository.loadViewport(AttractionBounds(51.4,-.3,51.6,.1),14.0)
        assertTrue(london.size>base)
        assertTrue(london.any {it.id=="wikidata:Q83125"})
        val local=File(context.filesDir,"attraction-regions/Q145.json")
        assertTrue(local.exists())
        assertTrue(parseAttractions(local.readText()).entities.all {it.lastUpdated!=null && it.countryIds.isNotEmpty()})
    }

    @Test fun legacyCategoryMigrationPreservesExplicitEmptyAndLayerFlags() {
        val preferences=context.getSharedPreferences("attraction-migration-${UUID.randomUUID()}",Context.MODE_PRIVATE)
        try {
            val defaults=MapFiltersViewModel(preferences).state.placeCategories
            assertTrue(defaults.containsAll(attractionCategories))
            preferences.edit().putStringSet("place-categories",setOf("Food","Pharmacy"))
                .putBoolean("places-enabled",false).putBoolean("alerts-enabled",true).commit()
            val migrated=MapFiltersViewModel(preferences)
            assertTrue(migrated.state.placeCategories.containsAll(attractionCategories))
            assertFalse(migrated.state.placesEnabled);assertTrue(migrated.state.alertsEnabled)
            preferences.edit().putStringSet("place-categories",emptySet()).commit()
            assertTrue(MapFiltersViewModel(preferences).state.placeCategories.isEmpty())
            preferences.edit().putStringSet("attraction-categories",setOf("Museum"))
                .putStringSet("place-categories",setOf("Food")).commit()
            val selected=MapFiltersViewModel(preferences)
            assertEquals(setOf(PlaceCategory.Museum),selected.state.placeCategories)
            selected.toggleCategory(PlaceCategory.Historic)
            assertEquals(setOf(PlaceCategory.Museum,PlaceCategory.Historic),MapFiltersViewModel(preferences).state.placeCategories)
            preferences.edit().putStringSet("attraction-categories",emptySet()).commit()
            assertTrue(MapFiltersViewModel(preferences).state.placeCategories.isEmpty())
        } finally {preferences.edit().clear().commit()}
    }
}
