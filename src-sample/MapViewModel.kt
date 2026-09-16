package com.meuboi.fazendamaracatiara.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meuboi.fazendamaracatiara.data.SupabaseManager
import com.meuboi.fazendamaracatiara.model.geo.GeoJsonFeatureResponse
import com.meuboi.fazendamaracatiara.model.geo.Marco
import com.meuboi.fazendamaracatiara.model.geo.PointLikeStructure
import com.meuboi.fazendamaracatiara.model.geo.WaterStream
import com.meuboi.fazendamaracatiara.farmdata.FarmLocation
import com.meuboi.fazendamaracatiara.model.geo.POI
import com.meuboi.fazendamaracatiara.model.geo.PastureField
import com.meuboi.fazendamaracatiara.model.geo.Perimeter
import com.google.android.gms.maps.model.LatLng
import com.meuboi.fazendamaracatiara.data.MapCacheManager
import com.meuboi.fazendamaracatiara.data.MapDataSnapshot
import com.meuboi.fazendamaracatiara.model.geo.Contour
import com.meuboi.fazendamaracatiara.model.geo.Path
import com.meuboi.fazendamaracatiara.model.geo.Pick
import com.meuboi.fazendamaracatiara.model.geo.Street
import com.meuboi.fazendamaracatiara.model.geo.Vegetation
import com.meuboi.fazendamaracatiara.model.geo.WaterBody
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import android.content.Context
import kotlinx.serialization.json.boolean

class MapViewModel : ViewModel() {
    
    var lastUpdated by mutableStateOf<Long?>(null)
    var marcos by mutableStateOf<List<Marco>>(emptyList())
    var picks by mutableStateOf<List<Pick>>(emptyList())
    var structures by mutableStateOf<List<PointLikeStructure>>(emptyList())
    var pois by mutableStateOf<List<POI>>(emptyList())
    var poisLowScale by mutableStateOf<List<POI>>(emptyList())
    var contours by mutableStateOf<List<Contour>>(emptyList())
    var streams by mutableStateOf<List<WaterStream>>(emptyList())

    var waterBodies by mutableStateOf<List<WaterBody>>(emptyList())
    var paths by mutableStateOf<List<Path>>(emptyList())
    var streets by mutableStateOf<List<Street>>(emptyList())

    var vegetations by mutableStateOf<List<Vegetation>>(emptyList())
    var pastures by mutableStateOf<List<PastureField>>(emptyList())
    var perimeters by mutableStateOf<List<Perimeter>>(emptyList())
    
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var requestedFocusLocation by mutableStateOf<LatLng?>(null)

    fun focusOnLocation(location: LatLng) {
        requestedFocusLocation = location
    }

    fun clearFocusRequest() {
        requestedFocusLocation = null
    }

    /**
     * Loads all geographic features. Tries cache first.
     * Downloads only if cache is empty or forceRefresh is true.
     */
    fun loadMapFeatures(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoading = true
            error = null

            // 1. Try to load from cache
            if (!forceRefresh) {
                val cachedData = MapCacheManager.loadSnapshot(context)
                if (cachedData != null) {
                    applySnapshot(cachedData)
                    Log.d("MapViewModel", "Loaded from cache. Skipping download.")
                    isLoading = false
                    return@launch // Exit if data was found in cache
                }
            }

            // 2. Proceed to download if forced or cache was empty
            try {
                withContext(Dispatchers.Default) {
                    // Fetch from Supabase
                    // ... (rest of the fetching logic)
                    // 1. Load Marcos (Standard format)
                    val marcosResult = SupabaseManager.client.from("marcos_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedMarcos = marcosResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            val coords = feature.geometry.coordinates.jsonArray
                            val lng = coords[0].jsonPrimitive.double
                            val lat = coords[1].jsonPrimitive.double

                            val props = feature.properties
                            Marco(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                point = LatLng(lat, lng)
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing marco ${response.id}", e)
                            null
                        }
                    }

                    // 1. Load Elevation Picks (Standard format)
                    val picksResult = SupabaseManager.client.from("elevation_pick_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedPicks = picksResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            val coords = feature.geometry.coordinates.jsonArray
                            val lng = coords[0].jsonPrimitive.double
                            val lat = coords[1].jsonPrimitive.double

                            val props = feature.properties
                            Pick(
                                id = response.id,
                                height = props?.get("height")?.jsonPrimitive?.int,
                                point = LatLng(lat, lng)
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing marco ${response.id}", e)
                            null
                        }
                    }


                    // 2. Load Point Like Structures (GeoJSON format)
                    val structuresResult = SupabaseManager.client.from("pointlike_structures_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedStructures = structuresResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            val coords = feature.geometry.coordinates.jsonArray
                            val lng = coords[0].jsonPrimitive.double
                            val lat = coords[1].jsonPrimitive.double

                            val props = feature.properties
                            PointLikeStructure(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                type = props?.get("type")?.jsonPrimitive?.content,
                                farm = props?.get("farm")?.jsonPrimitive?.content,
                                point = LatLng(lat, lng)
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing structure ${response.id}", e)
                            null
                        }
                    }

                    // 2. Load POIs (GeoJSON format from view_poi)
                    val poiResult = SupabaseManager.client.from("poi_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedPOIs = poiResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            val coords = feature.geometry.coordinates.jsonArray
                            val lng = coords[0].jsonPrimitive.double
                            val lat = coords[1].jsonPrimitive.double

                            val props = feature.properties
                            POI(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content ?: "Ponto",
                                description = props?.get("description")?.jsonPrimitive?.content,
                                category = props?.get("category")?.jsonPrimitive?.content ?: "other",
                                createdAt = props?.get("created_at")?.jsonPrimitive?.content ?: "",
                                photoUrl = props?.get("photo_url")?.jsonPrimitive?.content,
                                photoHeading = props?.get("photo_heading")?.jsonPrimitive?.doubleOrNull?.toFloat(),
                                isCameraPOI = props?.get("is_camera_poi")?.jsonPrimitive?.boolean == false,
                                location = LatLng(lat, lng)
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing POI ${response.id}", e)
                            null
                        }
                    }

                    // 3. Load Contours (GeoJSON LineString)
                    val contourResult = SupabaseManager.client.from("contour_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedContours = contourResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // ST_LineString coordinates: [[lng, lat], [lng, lat], ...]
                            val lineCoords = feature.geometry.coordinates.jsonArray
                            if (lineCoords.isEmpty()) return@mapNotNull null

                            // Mapeia diretamente o array de pontos da LineString
                            val linePoints = lineCoords.map { pointElement ->
                                val p = pointElement.jsonArray
                                // No GeoJSON: p[0] é Longitude, p[1] é Latitude
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            Contour(
                                id = response.id,
                                elevation = props?.get("elevation")?.jsonPrimitive?.doubleOrNull?.toInt(),
                                points = linePoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing contours ${response.id}", e)
                            null
                        }
                    }

                     // 3. Load Paths (GeoJSON MultiLineString)
                    val pathsResult = SupabaseManager.client.from("path_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedPaths = pathsResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // ST_MultiLineString coordinates: [[[lng, lat], ...], ...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // We take the first line of the MultiLineString
                            val linePoints = multiCoords[0].jsonArray.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            Path(
                                id = response.id,
                                name = props?.get("nome")?.jsonPrimitive?.content,
                                type = props?.get("type")?.jsonPrimitive?.content,
                                points = linePoints)
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing paths ${response.id}", e)
                            null
                        }
                    }

                    // 3. Load Streets (GeoJSON MultiLineString)
                    val streetsResult = SupabaseManager.client.from("street_network_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedStreets = streetsResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // ST_MultiLineString coordinates: [[[lng, lat], ...], ...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // We take the first line of the MultiLineString
                            val linePoints = multiCoords[0].jsonArray.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            Street(
                                id = response.id,
                                name = props?.get("nome")?.jsonPrimitive?.content,
                                type = props?.get("type")?.jsonPrimitive?.content,
                                bridge = props?.get("flow_level")?.jsonPrimitive?.int ?: 0,
                                points = linePoints)
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing street network ${response.id}", e)
                            null
                        }
                    }


                    // 3. Load Water Streams (GeoJSON MultiLineString)
                    val streamsResult = SupabaseManager.client.from("water_stream_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedStreams = streamsResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // ST_MultiLineString coordinates: [[[lng, lat], ...], ...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // We take the first line of the MultiLineString
                            val linePoints = multiCoords[0].jsonArray.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            WaterStream(
                                id = response.id,
                                name = props?.get("nome")?.jsonPrimitive?.content,
                                flowLevel = props?.get("flow_level")?.jsonPrimitive?.int ?: 1,
                                points = linePoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing stream ${response.id}", e)
                            null
                        }
                    }

                    // 4. Load Vegetation (Polygon)
                    val vegetationResult = SupabaseManager.client.from("vegetation_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedVegetations = vegetationResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                              // MultiPolygon coordinates: [[[[lng, lat], ...], inner rings...], next polygon...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null


                            // polygon
                            val firstPolygon = multiCoords[0].jsonArray
                            //val externalRing = firstPolygon[0].jsonArray

                            val ringPoints = firstPolygon.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }
                            Log.d("MapViewMode", "circulo vegetacao ${ringPoints.size}")
                            val props = feature.properties
                            Vegetation(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                type = props?.get("type")?.jsonPrimitive?.content,
                                points = ringPoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing vegetation ${response.id}", e)
                            null
                        }
                    }

                    // 4. Load Water Bodies (MultiPolygon)
                    val waterBodiesResult = SupabaseManager.client.from("water_body_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedWaterBodies = waterBodiesResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // MultiPolygon coordinates: [[[[lng, lat], ...], inner rings...], next polygon...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // External ring of the first polygon
                            val firstPolygon = multiCoords[0].jsonArray
                            //val externalRing = firstPolygon[0].jsonArray

                            val ringPoints = firstPolygon.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            WaterBody(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                points = ringPoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing water bodies ${response.id}", e)
                            null
                        }
                    }

                    // 4. Load Pasture Fields (MultiPolygon)
                    val pasturesResult = SupabaseManager.client.from("pastures_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedPastures = pasturesResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            // MultiPolygon coordinates: [[[[lng, lat], ...], inner rings...], next polygon...]
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // External ring of the first polygon
                            val firstPolygon = multiCoords[0].jsonArray
                            val externalRing = firstPolygon[0].jsonArray

                            val ringPoints = externalRing.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            PastureField(
                                id = response.id,
                                hectares = props?.get("hectares")?.jsonPrimitive?.doubleOrNull,
                                alqueires = props?.get("alqueires")?.jsonPrimitive?.doubleOrNull,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                points = ringPoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing pasture ${response.id}", e)
                            null
                        }
                    }

                    // 5. Load Perimeter (MultiPolygon)
                    val perimeterResult = SupabaseManager.client.from("perimeter_view")
                        .select().decodeList<GeoJsonFeatureResponse>()

                    val loadedPerimeters = perimeterResult.mapNotNull { response ->
                        try {
                            val feature = response.anyFeature!!
                            val multiCoords = feature.geometry.coordinates.jsonArray
                            if (multiCoords.isEmpty()) return@mapNotNull null

                            // External ring of the first polygon
                            val firstPolygon = multiCoords[0].jsonArray
                            val externalRing = firstPolygon[0].jsonArray

                            val ringPoints = externalRing.map {
                                val p = it.jsonArray
                                LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)
                            }

                            val props = feature.properties
                            Perimeter(
                                id = response.id,
                                name = props?.get("name")?.jsonPrimitive?.content,
                                hectares = props?.get("hectares")?.jsonPrimitive?.doubleOrNull,
                                alqueires = props?.get("alqueires")?.jsonPrimitive?.doubleOrNull,
                                perimeterKm = props?.get("perimeter_km")?.jsonPrimitive?.doubleOrNull,
                                points = ringPoints
                            )
                        } catch (e: Exception) {
                            Log.e("MapViewModel", "Error parsing perimeter ${response.id}", e)
                            null
                        }
                    }

                    // Update states on the main thread
                    withContext(Dispatchers.Main) {
                        marcos = loadedMarcos
                        picks = loadedPicks
                        structures = loadedStructures
                        pois = loadedPOIs
                        poisLowScale = computePoisLowScale(loadedPOIs)
                        contours = loadedContours
                        vegetations = loadedVegetations
                        streams = loadedStreams
                        waterBodies = loadedWaterBodies
                        paths = loadedPaths
                        streets = loadedStreets
                        pastures = loadedPastures
                        perimeters = loadedPerimeters
                        lastUpdated = System.currentTimeMillis()
                        
                        // Save to cache
                        val snapshot = MapDataSnapshot(
                            marcos = loadedMarcos,
                            picks = loadedPicks,
                            structures = loadedStructures,
                            pois = loadedPOIs,
                            poisLowScale = poisLowScale,
                            contours = loadedContours,
                            streams = loadedStreams,
                            waterBodies = loadedWaterBodies,
                            paths = loadedPaths,
                            streets = loadedStreets,
                            vegetations = loadedVegetations,
                            pastures = loadedPastures,
                            perimeters = loadedPerimeters,
                            lastUpdated = lastUpdated!!
                        )
                        MapCacheManager.saveSnapshot(context, snapshot)
                        
                        Log.d("MapViewModel", """
                            Loaded and cached features:
                            - Marcos: ${marcos.size}
                            - Picks: ${picks.size}
                            - Structures: ${structures.size}
                            - POIs: ${pois.size}
                            - Contours: ${contours.size}
                            - Streams: ${streams.size}
                            - Water Bodies: ${waterBodies.size}
                            - Paths: ${paths.size}
                            - Streets: ${streets.size}
                            - Vegetations: ${vegetations.size}
                            - Pastures: ${pastures.size}
                            - Perimeters: ${perimeters.size}
                        """.trimIndent())
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error loading map features", e)
                // If we already have cached data, don't show error to user
                if (marcos.isEmpty()) {
                    error = "Erro ao carregar dados: ${e.localizedMessage}"
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun applySnapshot(snapshot: MapDataSnapshot) {
        marcos = snapshot.marcos
        picks = snapshot.picks
        structures = snapshot.structures
        pois = snapshot.pois
        poisLowScale = if (snapshot.poisLowScale.isEmpty() && snapshot.pois.isNotEmpty()) {
            computePoisLowScale(snapshot.pois)
        } else {
            snapshot.poisLowScale
        }
        contours = snapshot.contours
        streams = snapshot.streams
        waterBodies = snapshot.waterBodies
        paths = snapshot.paths
        streets = snapshot.streets
        vegetations = snapshot.vegetations
        pastures = snapshot.pastures
        perimeters = snapshot.perimeters
        lastUpdated = snapshot.lastUpdated
    }

    private fun computePoisLowScale(allPois: List<POI>): List<POI> {
        // Modo Resumo: Apenas incidentes ou pontos distantes (>7km)
        val filtered = allPois.filter { poi ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                poi.location.latitude, poi.location.longitude,
                FarmLocation.LATITUDE, FarmLocation.LONGITUDE,
                results
            )
            poi.category.lowercase() == "incidente" || results[0] > 7000
        }

        // Evita sobreposição (50m) no modo resumo
        val resultList = mutableListOf<POI>()
        filtered.forEach { poi ->
            val tooClose = resultList.any { existing ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    poi.location.latitude, poi.location.longitude,
                    existing.location.latitude, existing.location.longitude,
                    results
                )
                results[0] < 50 // 50 metros
            }
            if (!tooClose) {
                resultList.add(poi)
            }
        }
        return resultList
    }
}
