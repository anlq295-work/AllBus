package com.example.hanoibus.data

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale

object RoadRoutingService {
    private const val TAG = "RoadRoutingService"
    private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving/"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-memory cache for road geometries
    private val routeCache = ConcurrentHashMap<String, List<List<Double>>>()

    suspend fun fetchRoadRoute(stations: List<BusStation>): List<List<Double>>? = withContext(Dispatchers.IO) {
        if (stations.size < 2) return@withContext null

        val validStations = stations.filter { it.geo != null && it.geo.lat != 0.0 && it.geo.lng != 0.0 }
        if (validStations.size < 2) return@withContext null

        val cacheKey = "${validStations.size}_${validStations.first().objectId}_${validStations.last().objectId}"
        routeCache[cacheKey]?.let { return@withContext it }

        try {
            val allRoadCoords = mutableListOf<List<Double>>()
            val chunkSize = 40

            var startIdx = 0
            while (startIdx < validStations.size - 1) {
                val endIdx = (startIdx + chunkSize).coerceAtMost(validStations.size)
                val chunk = validStations.subList(startIdx, endIdx)
                if (chunk.size < 2) break

                val coordsParam = chunk.joinToString(";") { s ->
                    String.format(Locale.US, "%.6f,%.6f", s.geo!!.lng, s.geo.lat)
                }
                val url = "$OSRM_BASE_URL$coordsParam?overview=full&geometries=geojson"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "HanoiBusApp/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JsonParser.parseString(bodyString).asJsonObject
                        if (json.has("code") && json.get("code").asString == "Ok") {
                            val routes = json.getAsJsonArray("routes")
                            if (routes.size() > 0) {
                                val geometry = routes[0].asJsonObject.getAsJsonObject("geometry")
                                val coordinates = geometry.getAsJsonArray("coordinates")
                                for (i in 0 until coordinates.size()) {
                                    val pt = coordinates[i].asJsonArray
                                    val lng = pt[0].asDouble
                                    val lat = pt[1].asDouble
                                    allRoadCoords.add(listOf(lat, lng))
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "OSRM chunk request returned code ${response.code}")
                }

                startIdx = endIdx - 1 // 1 overlapping waypoint to maintain continuity
            }

            if (allRoadCoords.isNotEmpty()) {
                routeCache[cacheKey] = allRoadCoords
                Log.d(TAG, "Fetched road path with ${allRoadCoords.size} points for key $cacheKey")
                return@withContext allRoadCoords
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching road route from OSRM: ${e.message}", e)
        }

        null
    }
}
