package com.example.hanoibus.data

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.*

object TransitRoutingEngine {
    private const val TAG = "TransitRoutingEngine"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Pre-defined key landmarks across Hanoi
    val popularLandmarks = listOf(
        TripPlace(name = "Hồ Hoàn Kiếm (Bờ Hồ)", address = "Đinh Tiên Hoàng, Hoàn Kiếm", lat = 21.0285, lng = 105.8542),
        TripPlace(name = "Bến xe Mỹ Đình", address = "Phạm Hùng, Nam Từ Liêm", lat = 21.0281, lng = 105.7777),
        TripPlace(name = "Bến xe Giáp Bát", address = "Giải Phóng, Hoàng Mai", lat = 20.9806, lng = 105.8415),
        TripPlace(name = "Bến xe Nước Ngầm", address = "Ngọc Hồi, Hoàng Mai", lat = 20.9632, lng = 105.8431),
        TripPlace(name = "Ga Hà Nội", address = "120 Lê Duẩn, Hoàn Kiếm", lat = 21.0245, lng = 105.8412),
        TripPlace(name = "Sân bay Quốc tế Nội Bài", address = "Phú Minh, Sóc Sơn", lat = 21.2212, lng = 105.8072),
        TripPlace(name = "Bệnh viện Bạch Mai", address = "78 Giải Phóng, Đống Đa", lat = 21.0003, lng = 105.8398),
        TripPlace(name = "Đại học Bách Khoa Hà Nội", address = "1 Đại Cồ Việt, Hai Bà Trưng", lat = 21.0055, lng = 105.8432),
        TripPlace(name = "Đại học Quốc Gia Hà Nội", address = "144 Xuân Thủy, Cầu Giấy", lat = 21.0372, lng = 105.7820),
        TripPlace(name = "Lăng Chủ tịch Hồ Chí Minh", address = "2 Hùng Vương, Ba Đình", lat = 21.0368, lng = 105.8347),
        TripPlace(name = "Trung tâm Hội nghị Quốc gia", address = "Đại lộ Thăng Long, Nam Từ Liêm", lat = 21.0068, lng = 105.7876),
        TripPlace(name = "Vincom Center Bà Triệu", address = "191 Bà Triệu, Hai Bà Trưng", lat = 21.0118, lng = 105.8499)
    )

    data class CachedStationSearch(
        val station: BusStation,
        val nameLower: String,
        val nameNorm: String,
        val streetLower: String,
        val streetNorm: String,
        val codeLower: String,
        val fleetOverLower: String,
        val fleetOverNorm: String
    )

    private var cachedSearchStations: List<CachedStationSearch>? = null

    fun warmStationCache(allStations: List<BusStation>) {
        getCachedStations(allStations)
    }

    private fun getCachedStations(allStations: List<BusStation>): List<CachedStationSearch> {
        val existing = cachedSearchStations
        if (existing != null && existing.size == allStations.size) return existing

        val list = allStations.map { s ->
            val nl = s.name.lowercase()
            val sl = s.street?.lowercase() ?: ""
            val fl = s.fleetOver?.lowercase() ?: ""
            CachedStationSearch(
                station = s,
                nameLower = nl,
                nameNorm = nl.removeAccents(),
                streetLower = sl,
                streetNorm = sl.removeAccents(),
                codeLower = s.code?.lowercase() ?: "",
                fleetOverLower = fl,
                fleetOverNorm = fl.removeAccents()
            )
        }
        cachedSearchStations = list
        return list
    }

    fun searchStations(query: String, allStations: List<BusStation>, limit: Int = 25): List<BusStation> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val qNorm = q.removeAccents()
        val cached = getCachedStations(allStations)
        return cached.filter { c ->
            c.nameLower.contains(q) || c.nameNorm.contains(qNorm) ||
            c.streetLower.contains(q) || c.streetNorm.contains(qNorm) ||
            c.codeLower == q ||
            c.fleetOverLower.contains(q) || c.fleetOverNorm.contains(qNorm)
        }.take(limit).map { it.station }
    }

    fun searchPlaces(
        query: String,
        allStations: List<BusStation>
    ): List<TripPlace> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return popularLandmarks.take(6)
        val qNorm = q.removeAccents()

        val results = mutableListOf<TripPlace>()

        // 1. Search in landmarks
        popularLandmarks.filter {
            val nameRaw = it.name.lowercase()
            val addrRaw = it.address.lowercase()
            nameRaw.contains(q) || nameRaw.removeAccents().contains(qNorm) ||
            addrRaw.contains(q) || addrRaw.removeAccents().contains(qNorm)
        }.forEach { results.add(it) }

        // 2. Search in pre-cached stations (instant!)
        val cached = getCachedStations(allStations)
        cached.filter { c ->
            c.nameLower.contains(q) || c.nameNorm.contains(qNorm) ||
            c.streetLower.contains(q) || c.streetNorm.contains(qNorm) ||
            c.codeLower == q
        }.take(10).forEach { c ->
            val s = c.station
            results.add(
                TripPlace(
                    id = "station_${s.objectId}",
                    name = s.name,
                    address = s.street ?: "Trạm xe buýt Hà Nội (Mã: ${s.code ?: ""})",
                    lat = s.geo!!.lat,
                    lng = s.geo.lng,
                    isBusStation = true,
                    stationId = s.objectId
                )
            )
        }

        return results.distinctBy { "${it.lat}_${it.lng}" }.take(10)
    }

    suspend fun searchOnlinePlaces(query: String): List<TripPlace> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode("$q, Hà Nội", "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&viewbox=105.3,21.5,106.2,20.6&bounded=0&countrycodes=vn&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HanoiBusApp/1.0 (transit-planner)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrEmpty()) {
                    val jsonArray = JsonParser.parseString(bodyStr).asJsonArray
                    val list = mutableListOf<TripPlace>()
                    for (i in 0 until jsonArray.size()) {
                        val obj = jsonArray[i].asJsonObject
                        val lat = obj.get("lat")?.asDouble ?: continue
                        val lng = obj.get("lon")?.asDouble ?: continue
                        val displayName = obj.get("display_name")?.asString ?: ""
                        val parts = displayName.split(",")
                        val name = parts.firstOrNull()?.trim() ?: displayName
                        val address = parts.drop(1).take(3).joinToString(", ") { it.trim() }

                        list.add(
                            TripPlace(
                                name = name,
                                address = address,
                                lat = lat,
                                lng = lng
                            )
                        )
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim search error: ${e.message}")
        }
        emptyList()
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun parseFleetOver(fleetOver: String?): Set<String> {
        if (fleetOver.isNullOrBlank()) return emptySet()
        return fleetOver.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun planTrip(
        origin: GeoPoint,
        destination: GeoPoint,
        allStations: List<BusStation>
    ): List<TripOption> {
        val directDistance = calculateDistanceMeters(origin.lat, origin.lng, destination.lat, destination.lng)

        // Case 0: Walking distance only (< 600m)
        if (directDistance <= 600) {
            val walkTimeMin = max(1, (directDistance / 70.0).roundToInt())
            return listOf(
                TripOption(
                    summary = "Đi bộ trực tiếp (${directDistance.roundToInt()}m)",
                    totalDurationMinutes = walkTimeMin,
                    totalDistanceMeters = directDistance.roundToInt(),
                    totalFareVnd = 0,
                    walkDistanceMeters = directDistance.roundToInt(),
                    busTransferCount = 0,
                    segments = listOf(
                        TripSegment(
                            type = TripSegmentType.WALK,
                            title = "Đi bộ đến điểm đến",
                            instruction = "Đi bộ thẳng khoảng ${directDistance.roundToInt()}m (khoảng $walkTimeMin phút) đến đích.",
                            fromPlaceName = "Điểm xuất phát",
                            toPlaceName = "Điểm đến",
                            distanceMeters = directDistance.roundToInt(),
                            durationMinutes = walkTimeMin,
                            pathPoints = listOf(origin, destination)
                        )
                    ),
                    busRoutes = emptyList()
                )
            )
        }

        val validStations = allStations.filter { it.geo != null && it.geo.lat != 0.0 && it.geo.lng != 0.0 }

        // Find candidate origin stations (within 1200m)
        val originCandidates = validStations.map { station ->
            val d = calculateDistanceMeters(origin.lat, origin.lng, station.geo!!.lat, station.geo.lng)
            station to d
        }.filter { it.second <= 1200.0 }
         .sortedBy { it.second }
         .take(15)

        // Find candidate destination stations (within 1200m)
        val destCandidates = validStations.map { station ->
            val d = calculateDistanceMeters(destination.lat, destination.lng, station.geo!!.lat, station.geo.lng)
            station to d
        }.filter { it.second <= 1200.0 }
         .sortedBy { it.second }
         .take(15)

        if (originCandidates.isEmpty() || destCandidates.isEmpty()) {
            return emptyList()
        }

        val tripOptions = mutableListOf<TripOption>()

        // 1. Check Direct Routes (0 transfer)
        val directFoundRoutes = mutableSetOf<String>()
        for ((origStation, origDist) in originCandidates) {
            val origRoutes = parseFleetOver(origStation.fleetOver)
            if (origRoutes.isEmpty()) continue

            for ((destStation, destDist) in destCandidates) {
                if (origStation.objectId == destStation.objectId) continue

                val destRoutes = parseFleetOver(destStation.fleetOver)
                val commonRoutes = origRoutes.intersect(destRoutes)

                for (routeCode in commonRoutes) {
                    if (directFoundRoutes.contains(routeCode)) continue
                    directFoundRoutes.add(routeCode)

                    val busRideDist = calculateDistanceMeters(
                        origStation.geo!!.lat, origStation.geo.lng,
                        destStation.geo!!.lat, destStation.geo.lng
                    )

                    // Skip if the bus stop is backwards or total ride distance is unreasonably small
                    if (busRideDist < 400) continue

                    val walk1Min = max(1, (origDist / 70.0).roundToInt())
                    val busMin = max(4, (busRideDist / 350.0).roundToInt() + 3) // ~21 km/h + 3 min wait
                    val walk2Min = max(1, (destDist / 70.0).roundToInt())
                    val totalMin = walk1Min + busMin + walk2Min
                    val totalDist = (origDist + busRideDist + destDist).roundToInt()
                    val totalWalkDist = (origDist + destDist).roundToInt()

                    tripOptions.add(
                        TripOption(
                            summary = "Tuyến $routeCode (${totalMin} phút, 0 lần đổi xe)",
                            totalDurationMinutes = totalMin,
                            totalDistanceMeters = totalDist,
                            totalFareVnd = 8000,
                            walkDistanceMeters = totalWalkDist,
                            busTransferCount = 0,
                            segments = listOf(
                                TripSegment(
                                    type = TripSegmentType.WALK,
                                    title = "Đi bộ ra trạm xe",
                                    instruction = "Đi bộ ${origDist.roundToInt()}m (khoảng $walk1Min phút) đến trạm ${origStation.name}.",
                                    fromPlaceName = "Vị trí của bạn",
                                    toPlaceName = origStation.name,
                                    distanceMeters = origDist.roundToInt(),
                                    durationMinutes = walk1Min,
                                    pathPoints = listOf(origin, origStation.geo)
                                ),
                                TripSegment(
                                    type = TripSegmentType.BUS,
                                    title = "Lên xe buýt $routeCode",
                                    instruction = "Đón xe buýt $routeCode tại trạm ${origStation.name}, đi khoảng ${busMin - 3} phút đến trạm ${destStation.name}.",
                                    routeCode = routeCode,
                                    fromPlaceName = origStation.name,
                                    toPlaceName = destStation.name,
                                    fromStationId = origStation.objectId,
                                    toStationId = destStation.objectId,
                                    distanceMeters = busRideDist.roundToInt(),
                                    durationMinutes = busMin,
                                    pathPoints = listOf(origStation.geo, destStation.geo)
                                ),
                                TripSegment(
                                    type = TripSegmentType.WALK,
                                    title = "Đi bộ đến điểm đến",
                                    instruction = "Xuống trạm ${destStation.name}, đi bộ ${destDist.roundToInt()}m (khoảng $walk2Min phút) đến điểm đích.",
                                    fromPlaceName = destStation.name,
                                    toPlaceName = "Điểm đến",
                                    distanceMeters = destDist.roundToInt(),
                                    durationMinutes = walk2Min,
                                    pathPoints = listOf(destStation.geo, destination)
                                )
                            ),
                            busRoutes = listOf(routeCode)
                        )
                    )
                }
            }
        }

        // 2. Check 1-Transfer Routes (1 transfer / 2 buses)
        if (tripOptions.size < 6) {
            val transferPairsFound = mutableSetOf<String>()

            for ((origStation, origDist) in originCandidates.take(6)) {
                val origRoutes = parseFleetOver(origStation.fleetOver)
                if (origRoutes.isEmpty()) continue

                for ((destStation, destDist) in destCandidates.take(6)) {
                    val destRoutes = parseFleetOver(destStation.fleetOver)
                    if (destRoutes.isEmpty()) continue

                    // Find transfer stations that connect any origRoute with any destRoute
                    for (route1 in origRoutes.take(4)) {
                        for (route2 in destRoutes.take(4)) {
                            if (route1 == route2) continue // already covered in direct

                            val pairKey = "$route1->$route2"
                            if (transferPairsFound.contains(pairKey)) continue

                            // Find candidate transfer station
                            val transferStation = validStations.firstOrNull { station ->
                                val fo = parseFleetOver(station.fleetOver)
                                fo.contains(route1) && fo.contains(route2) &&
                                station.objectId != origStation.objectId &&
                                station.objectId != destStation.objectId
                            } ?: continue

                            val ride1Dist = calculateDistanceMeters(
                                origStation.geo!!.lat, origStation.geo.lng,
                                transferStation.geo!!.lat, transferStation.geo.lng
                            )
                            val ride2Dist = calculateDistanceMeters(
                                transferStation.geo.lat, transferStation.geo.lng,
                                destStation.geo!!.lat, destStation.geo.lng
                            )

                            // Sanity check: transfer shouldn't blow up the direct distance more than 2x
                            if (ride1Dist + ride2Dist > directDistance * 2.2 + 2000) continue
                            if (ride1Dist < 400 || ride2Dist < 400) continue

                            transferPairsFound.add(pairKey)

                            val walk1Min = max(1, (origDist / 70.0).roundToInt())
                            val bus1Min = max(4, (ride1Dist / 350.0).roundToInt() + 3)
                            val waitTransferMin = 4
                            val bus2Min = max(4, (ride2Dist / 350.0).roundToInt() + 2)
                            val walk2Min = max(1, (destDist / 70.0).roundToInt())

                            val totalMin = walk1Min + bus1Min + waitTransferMin + bus2Min + walk2Min
                            val totalDist = (origDist + ride1Dist + ride2Dist + destDist).roundToInt()
                            val totalWalkDist = (origDist + destDist).roundToInt()

                            tripOptions.add(
                                TripOption(
                                    summary = "Tuyến $route1 ➔ Tuyến $route2 (${totalMin} phút, 1 chuyển tuyến)",
                                    totalDurationMinutes = totalMin,
                                    totalDistanceMeters = totalDist,
                                    totalFareVnd = 16000,
                                    walkDistanceMeters = totalWalkDist,
                                    busTransferCount = 1,
                                    segments = listOf(
                                        TripSegment(
                                            type = TripSegmentType.WALK,
                                            title = "Đi bộ ra trạm đón",
                                            instruction = "Đi bộ ${origDist.roundToInt()}m đến trạm ${origStation.name}.",
                                            fromPlaceName = "Vị trí của bạn",
                                            toPlaceName = origStation.name,
                                            distanceMeters = origDist.roundToInt(),
                                            durationMinutes = walk1Min,
                                            pathPoints = listOf(origin, origStation.geo)
                                        ),
                                        TripSegment(
                                            type = TripSegmentType.BUS,
                                            title = "Lên xe buýt $route1",
                                            instruction = "Đi xe buýt $route1 từ ${origStation.name} đến điểm trung chuyển ${transferStation.name}.",
                                            routeCode = route1,
                                            fromPlaceName = origStation.name,
                                            toPlaceName = transferStation.name,
                                            fromStationId = origStation.objectId,
                                            toStationId = transferStation.objectId,
                                            distanceMeters = ride1Dist.roundToInt(),
                                            durationMinutes = bus1Min,
                                            pathPoints = listOf(origStation.geo, transferStation.geo)
                                        ),
                                        TripSegment(
                                            type = TripSegmentType.BUS,
                                            title = "Chuyển sang xe buýt $route2",
                                            instruction = "Tại trạm ${transferStation.name}, đón tiếp xe buýt $route2 đi đến trạm ${destStation.name}.",
                                            routeCode = route2,
                                            fromPlaceName = transferStation.name,
                                            toPlaceName = destStation.name,
                                            fromStationId = transferStation.objectId,
                                            toStationId = destStation.objectId,
                                            distanceMeters = ride2Dist.roundToInt(),
                                            durationMinutes = bus2Min + waitTransferMin,
                                            pathPoints = listOf(transferStation.geo, destStation.geo)
                                        ),
                                        TripSegment(
                                            type = TripSegmentType.WALK,
                                            title = "Đi bộ đến điểm đến",
                                            instruction = "Xuống trạm ${destStation.name}, đi bộ ${destDist.roundToInt()}m đến đích.",
                                            fromPlaceName = destStation.name,
                                            toPlaceName = "Điểm đến",
                                            distanceMeters = destDist.roundToInt(),
                                            durationMinutes = walk2Min,
                                            pathPoints = listOf(destStation.geo, destination)
                                        )
                                    ),
                                    busRoutes = listOf(route1, route2)
                                )
                            )

                            if (tripOptions.size >= 8) break
                        }
                        if (tripOptions.size >= 8) break
                    }
                }
            }
        }

        // Sort options: 0 transfers first, then lowest duration, then least walking
        return tripOptions.sortedWith(
            compareBy<TripOption> { it.busTransferCount }
                .thenBy { it.totalDurationMinutes }
                .thenBy { it.walkDistanceMeters }
        ).take(6)
    }

    // Cache for RouteDetail objects so subsequent requests for the same bus line are instant
    private val routeDetailCache = java.util.concurrent.ConcurrentHashMap<String, RouteDetail>()

    /**
     * Enriches a TripOption by fetching detailed route geometry and intermediate bus stops
     * for all bus segments, providing accurate road lines and the complete list of stops.
     */
    suspend fun enrichTripOption(
        option: TripOption,
        repository: TimbusRepository = TimbusRepository()
    ): TripOption = withContext(Dispatchers.IO) {
        val newSegments = option.segments.map { seg ->
            if (seg.type == TripSegmentType.BUS && seg.routeCode != null && seg.stations.isEmpty()) {
                enrichSegmentWithRouteDetail(seg, repository)
            } else {
                seg
            }
        }
        val totalWalk = newSegments.filter { it.type == TripSegmentType.WALK }.sumOf { it.distanceMeters }
        val totalDist = newSegments.sumOf { it.distanceMeters }
        option.copy(
            segments = newSegments,
            totalDistanceMeters = totalDist,
            walkDistanceMeters = totalWalk
        )
    }

    /**
     * Enriches a single bus segment with its intermediate bus stations and true polyline coordinates.
     */
    suspend fun enrichSegmentWithRouteDetail(
        segment: TripSegment,
        repository: TimbusRepository
    ): TripSegment = withContext(Dispatchers.IO) {
        val routeCode = segment.routeCode ?: return@withContext segment
        val cleanCode = routeCode.trim().uppercase()

        val detail = routeDetailCache.getOrPut(cleanCode) {
            val res = repository.getRouteDetail(cleanCode)
            res.getOrNull() ?: RouteDetail()
        }

        val fallbackStart = segment.pathPoints.firstOrNull()
        val fallbackEnd = segment.pathPoints.lastOrNull()

        val (extractedStations, extractedGeo) = extractRouteSegment(
            detail = detail,
            fromStationId = segment.fromStationId,
            fromName = segment.fromPlaceName,
            toStationId = segment.toStationId,
            toName = segment.toPlaceName,
            fallbackStartGeo = fallbackStart,
            fallbackEndGeo = fallbackEnd
        )

        if (extractedStations.isNotEmpty()) {
            val finalPath = if (extractedGeo.isNotEmpty()) extractedGeo else extractedStations.mapNotNull { it.geo }
            val stationNames = extractedStations.map { it.name }
            segment.copy(
                stations = extractedStations,
                stopsCount = extractedStations.size,
                intermediateStops = stationNames,
                pathPoints = finalPath
            )
        } else {
            segment
        }
    }

    /**
     * Extracts the sublist of stations and sliced road geometry between boarding and alighting stops.
     */
    fun extractRouteSegment(
        detail: RouteDetail,
        fromStationId: Long?,
        fromName: String,
        toStationId: Long?,
        toName: String,
        fallbackStartGeo: GeoPoint?,
        fallbackEndGeo: GeoPoint?
    ): Pair<List<BusStation>, List<GeoPoint>> {
        val goStations = detail.go?.stations ?: emptyList()
        val reStations = detail.re?.stations ?: emptyList()

        // 1. Try GO direction
        var match = findSublistByStations(goStations, fromStationId, fromName, toStationId, toName, fallbackStartGeo, fallbackEndGeo)
        if (match != null && match.isNotEmpty()) {
            val slicedGeo = sliceGeoPoints(detail.go?.geo ?: emptyList(), match)
            return Pair(match, slicedGeo)
        }

        // 2. Try RE direction
        match = findSublistByStations(reStations, fromStationId, fromName, toStationId, toName, fallbackStartGeo, fallbackEndGeo)
        if (match != null && match.isNotEmpty()) {
            val slicedGeo = sliceGeoPoints(detail.re?.geo ?: emptyList(), match)
            return Pair(match, slicedGeo)
        }

        return Pair(emptyList(), emptyList())
    }

    private fun findSublistByStations(
        stations: List<BusStation>,
        fromStationId: Long?,
        fromName: String,
        toStationId: Long?,
        toName: String,
        startGeo: GeoPoint?,
        endGeo: GeoPoint?
    ): List<BusStation>? {
        if (stations.isEmpty()) return null

        val fName = fromName.trim().lowercase().removeAccents()
        val tName = toName.trim().lowercase().removeAccents()

        var idx1 = stations.indexOfFirst {
            (fromStationId != null && it.objectId == fromStationId) ||
            (fName.isNotEmpty() && it.name.trim().lowercase().removeAccents().contains(fName))
        }
        var idx2 = stations.indexOfFirst {
            (toStationId != null && it.objectId == toStationId) ||
            (tName.isNotEmpty() && it.name.trim().lowercase().removeAccents().contains(tName))
        }

        // Proximity fallback if ID or Name didn't find direct index
        if (idx1 < 0 && startGeo != null) {
            idx1 = stations.indices.minByOrNull { i ->
                val g = stations[i].geo ?: return@minByOrNull Double.MAX_VALUE
                calculateDistanceMeters(startGeo.lat, startGeo.lng, g.lat, g.lng)
            } ?: -1
        }
        if (idx2 < 0 && endGeo != null) {
            idx2 = stations.indices.minByOrNull { i ->
                val g = stations[i].geo ?: return@minByOrNull Double.MAX_VALUE
                calculateDistanceMeters(endGeo.lat, endGeo.lng, g.lat, g.lng)
            } ?: -1
        }

        if (idx1 >= 0 && idx2 >= 0 && idx1 < idx2) {
            return stations.subList(idx1, idx2 + 1)
        }
        return null
    }

    private fun sliceGeoPoints(geo: List<GeoPoint>, stations: List<BusStation>): List<GeoPoint> {
        if (stations.isEmpty()) return emptyList()
        val stationPoints = stations.mapNotNull { it.geo }
        if (geo.isEmpty() || stationPoints.size < 2) return stationPoints

        val firstSt = stationPoints.first()
        val lastSt = stationPoints.last()

        var minD1 = Double.MAX_VALUE
        var bestIdx1 = -1
        var minD2 = Double.MAX_VALUE
        var bestIdx2 = -1

        for (i in geo.indices) {
            val p = geo[i]
            val d1 = calculateDistanceMeters(firstSt.lat, firstSt.lng, p.lat, p.lng)
            if (d1 < minD1) {
                minD1 = d1
                bestIdx1 = i
            }
            val d2 = calculateDistanceMeters(lastSt.lat, lastSt.lng, p.lat, p.lng)
            if (d2 < minD2) {
                minD2 = d2
                bestIdx2 = i
            }
        }

        if (bestIdx1 >= 0 && bestIdx2 >= 0 && bestIdx1 < bestIdx2 && minD1 < 600 && minD2 < 600) {
            val subGeo = mutableListOf<GeoPoint>()
            subGeo.add(firstSt)
            subGeo.addAll(geo.subList(bestIdx1, bestIdx2 + 1))
            subGeo.add(lastSt)
            return subGeo
        }

        return stationPoints
    }

    /**
     * Fetches all approaching buses for a specific bus station and route.
     * Supports real-time Timbus GPS tracking and fallback to electric bus schedules.
     */
    suspend fun fetchApproachingBusesEta(
        stationId: Long,
        fleetOver: String,
        routeCode: String,
        repository: TimbusRepository = TimbusRepository()
    ): List<BusEta> = withContext(Dispatchers.IO) {
        val cleanRoute = routeCode.trim().uppercase()
        val result = mutableListOf<BusEta>()
        try {
            val res = repository.getBusEta(stationId, fleetOver)
            val list = res.getOrNull() ?: emptyList()
            val matching = list.filter { it.fleet?.trim()?.equals(cleanRoute, ignoreCase = true) == true }
                .filter { (it.timeSeconds ?: 0) > 0 }
                .sortedBy { it.timeSeconds ?: Int.MAX_VALUE }

            result.addAll(matching)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching live bus ETA: ${e.message}")
        }

        if (result.isEmpty() && ElectricBusCatalog.isElectricBus(cleanRoute)) {
            val sec = ElectricBusCatalog.computeScheduledEtaSeconds(cleanRoute, stationId)
            val plate = ElectricBusCatalog.getSimulatedLicensePlate(cleanRoute, stationId)
            result.add(
                BusEta(
                    licensePlate = plate,
                    fleetCode = "0",
                    fleet = cleanRoute,
                    distanceMeters = (sec * 5.5).toInt().coerceAtLeast(300),
                    timeSeconds = sec,
                    speed = 22.0
                )
            )
        }

        result
    }

    /**
     * Fetches the nearest approaching bus for a specific bus station and route.
     */
    suspend fun fetchNearestBusEta(
        stationId: Long,
        fleetOver: String,
        routeCode: String,
        repository: TimbusRepository = TimbusRepository()
    ): BusEta? = withContext(Dispatchers.IO) {
        fetchApproachingBusesEta(stationId, fleetOver, routeCode, repository).firstOrNull()
    }

    /**
     * Calculates the true road polyline path from an approaching bus's position to a target bus station.
     * Traces backward along the route's road geometry by distanceMeters.
     */
    suspend fun getApproachingBusPath(
        routeCode: String,
        targetStation: BusStation,
        distanceMeters: Int,
        knownSegmentPoints: List<GeoPoint>? = null,
        repository: TimbusRepository = TimbusRepository()
    ): List<GeoPoint> = withContext(Dispatchers.IO) {
        val targetGeo = targetStation.geo ?: return@withContext emptyList()
        val distNeeded = distanceMeters.coerceIn(300, 15000).toDouble()

        var candidateGeo: List<GeoPoint> = emptyList()
        if (!knownSegmentPoints.isNullOrEmpty() && knownSegmentPoints.size > 2) {
            candidateGeo = knownSegmentPoints
        }

        if (candidateGeo.isEmpty()) {
            val cleanCode = routeCode.trim().uppercase()
            try {
                val detail = routeDetailCache.getOrPut(cleanCode) {
                    val res = repository.getRouteDetail(cleanCode)
                    res.getOrNull() ?: RouteDetail()
                }
                val goGeo = detail.go?.geo ?: emptyList()
                val reGeo = detail.re?.geo ?: emptyList()

                val distGo = goGeo.minOfOrNull { calculateDistanceMeters(it.lat, it.lng, targetGeo.lat, targetGeo.lng) } ?: Double.MAX_VALUE
                val distRe = reGeo.minOfOrNull { calculateDistanceMeters(it.lat, it.lng, targetGeo.lat, targetGeo.lng) } ?: Double.MAX_VALUE

                candidateGeo = if (distGo <= distRe && goGeo.isNotEmpty()) goGeo else reGeo
            } catch (e: Exception) {
                Log.w(TAG, "Error loading route geometry for $cleanCode: ${e.message}")
            }
        }

        if (candidateGeo.isEmpty()) {
            val deltaLat = 0.007 * (distNeeded / 1000.0)
            val deltaLng = 0.005 * (distNeeded / 1000.0)
            val steps = 8
            val synth = mutableListOf<GeoPoint>()
            for (step in steps downTo 0) {
                val ratio = step.toDouble() / steps
                val jitter = kotlin.math.sin(ratio * Math.PI) * 0.001
                synth.add(
                    GeoPoint(
                        targetGeo.lat - deltaLat * ratio + jitter,
                        targetGeo.lng - deltaLng * ratio - jitter * 0.5
                    )
                )
            }
            return@withContext synth
        }

        // Find closest point to targetGeo
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE
        for (i in candidateGeo.indices) {
            val d = calculateDistanceMeters(candidateGeo[i].lat, candidateGeo[i].lng, targetGeo.lat, targetGeo.lng)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        var accum = 0.0
        val subpath = mutableListOf<GeoPoint>()
        subpath.add(candidateGeo[bestIdx])

        if (bestIdx > 0) {
            for (i in (bestIdx - 1) downTo 0) {
                val d = calculateDistanceMeters(
                    candidateGeo[i].lat, candidateGeo[i].lng,
                    candidateGeo[i + 1].lat, candidateGeo[i + 1].lng
                )
                if (accum + d >= distNeeded) {
                    val rem = distNeeded - accum
                    val ratio = if (d > 0) (rem / d) else 0.0
                    val p1 = candidateGeo[i + 1]
                    val p0 = candidateGeo[i]
                    val interpLat = p1.lat + (p0.lat - p1.lat) * ratio
                    val interpLng = p1.lng + (p0.lng - p1.lng) * ratio
                    subpath.add(0, GeoPoint(interpLat, interpLng))
                    accum += rem
                    break
                } else {
                    accum += d
                    subpath.add(0, candidateGeo[i])
                }
            }
        }

        if (subpath.size < 3 && bestIdx < candidateGeo.size - 1) {
            subpath.clear()
            accum = 0.0
            subpath.add(candidateGeo[bestIdx])
            for (i in (bestIdx + 1) until candidateGeo.size) {
                val d = calculateDistanceMeters(
                    candidateGeo[i].lat, candidateGeo[i].lng,
                    candidateGeo[i - 1].lat, candidateGeo[i - 1].lng
                )
                if (accum + d >= distNeeded) {
                    val rem = distNeeded - accum
                    val ratio = if (d > 0) (rem / d) else 0.0
                    val p1 = candidateGeo[i - 1]
                    val p0 = candidateGeo[i]
                    val interpLat = p1.lat + (p0.lat - p1.lat) * ratio
                    val interpLng = p1.lng + (p0.lng - p1.lng) * ratio
                    subpath.add(0, GeoPoint(interpLat, interpLng))
                    accum += rem
                    break
                } else {
                    accum += d
                    subpath.add(0, candidateGeo[i])
                }
            }
            if (subpath.firstOrNull()?.let { calculateDistanceMeters(it.lat, it.lng, targetGeo.lat, targetGeo.lng) } ?: 0.0 < 50.0) {
                subpath.reverse()
            }
        }

        if (subpath.size < 2) {
            val deltaLat = 0.006 * (distNeeded / 1000.0)
            val deltaLng = 0.004 * (distNeeded / 1000.0)
            return@withContext listOf(
                GeoPoint(targetGeo.lat - deltaLat, targetGeo.lng - deltaLng),
                GeoPoint(targetGeo.lat - deltaLat * 0.66, targetGeo.lng - deltaLng * 0.66),
                GeoPoint(targetGeo.lat - deltaLat * 0.33, targetGeo.lng - deltaLng * 0.33),
                targetGeo
            )
        }

        subpath
    }

    fun String.removeAccents(): String {
        val sb = StringBuilder(this.length)
        for (i in indices) {
            val c = this[i]
            val replaced = when (c) {
                'à', 'á', 'ả', 'ã', 'ạ', 'ă', 'ằ', 'ắ', 'ẳ', 'ẵ', 'ặ', 'â', 'ầ', 'ấ', 'ẩ', 'ẫ', 'ậ' -> 'a'
                'À', 'Á', 'Ả', 'Ã', 'Ạ', 'Ă', 'Ằ', 'Ắ', 'Ẳ', 'Ẵ', 'Ặ', 'Â', 'Ầ', 'Ấ', 'Ẩ', 'Ẫ', 'Ậ' -> 'A'
                'è', 'é', 'ẻ', 'ẽ', 'ẹ', 'ê', 'ề', 'ế', 'ể', 'ễ', 'ệ' -> 'e'
                'È', 'É', 'Ẻ', 'Ẽ', 'Ẹ', 'Ê', 'Ề', 'Ế', 'Ể', 'Ễ', 'Ệ' -> 'E'
                'ì', 'í', 'ỉ', 'ĩ', 'ị' -> 'i'
                'Ì', 'Í', 'Ỉ', 'Ĩ', 'Ị' -> 'I'
                'ò', 'ó', 'ỏ', 'õ', 'ọ', 'ô', 'ồ', 'ố', 'ổ', 'ỗ', 'ộ', 'ơ', 'ờ', 'ớ', 'ở', 'ỡ', 'ợ' -> 'o'
                'Ò', 'Ó', 'Ỏ', 'Õ', 'Ọ', 'Ô', 'Ồ', 'Ố', 'Ổ', 'Ỗ', 'Ộ', 'Ơ', 'Ờ', 'Ớ', 'Ở', 'Ỡ', 'Ợ' -> 'O'
                'ù', 'ú', 'ủ', 'ũ', 'ụ', 'ư', 'ừ', 'ứ', 'ử', 'ữ', 'ự' -> 'u'
                'Ù', 'Ú', 'Ủ', 'Ũ', 'Ụ', 'Ư', 'Ừ', 'Ứ', 'Ử', 'Ữ', 'Ự' -> 'U'
                'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ' -> 'y'
                'Ỳ', 'Ý', 'Ỷ', 'Ỹ', 'Ỵ' -> 'Y'
                'đ' -> 'd'
                'Đ' -> 'D'
                else -> c
            }
            sb.append(replaced)
        }
        return sb.toString()
    }
}


