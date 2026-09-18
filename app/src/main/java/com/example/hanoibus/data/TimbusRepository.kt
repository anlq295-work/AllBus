package com.example.hanoibus.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class TimbusRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    private var cachedStations: List<BusStation>? = null

    companion object {
        private const val BASE_SEARCH_URL = "http://timbus.vn/Engine/Business/Search/action.ashx"
        private const val BASE_VEHICLE_URL = "http://timbus.vn/Engine/Business/Vehicle/action.ashx"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    suspend fun getRoutes(): List<BusRoute> = withContext(Dispatchers.IO) {
        DefaultRoutes.allRoutes
    }

    suspend fun getAllStations(context: Context): List<BusStation> = withContext(Dispatchers.IO) {
        cachedStations?.let { return@withContext it }
        try {
            context.assets.open("hanoi_stations.json").use { stream ->
                getAllStationsFromStream(stream)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllStationsFromStream(stream: InputStream): List<BusStation> {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val type = object : TypeToken<List<BusStation>>() {}.type
        val stations: List<BusStation> = gson.fromJson(reader, type) ?: emptyList()
        cachedStations = stations
        return stations
    }

    suspend fun getRouteDetail(route: BusRoute): Result<RouteDetail> = withContext(Dispatchers.IO) {
        if (route.code.equals("125", ignoreCase = true)) {
            return@withContext Result.success(Route125Detail.getRoute125Detail())
        }
        val fid = if (route.fleetId > 0) route.fleetId.toString() else route.code
        val res = getRouteDetailByFid(fid)
        if (res.isSuccess) {
            return@withContext res
        }
        buildFallbackRouteDetail(route.code, route.name)
    }

    suspend fun getRouteDetail(routeCode: String): Result<RouteDetail> = withContext(Dispatchers.IO) {
        if (routeCode.equals("125", ignoreCase = true)) {
            return@withContext Result.success(Route125Detail.getRoute125Detail())
        }
        val matched = DefaultRoutes.allRoutes.find { it.code.equals(routeCode, ignoreCase = true) }
        val fid = if (matched != null && matched.fleetId > 0) matched.fleetId.toString() else routeCode
        val res = getRouteDetailByFid(fid)
        if (res.isSuccess) {
            return@withContext res
        }
        buildFallbackRouteDetail(routeCode, matched?.name ?: "Tuyến $routeCode")
    }

    private fun buildFallbackRouteDetail(routeCode: String, routeName: String): Result<RouteDetail> {
        val clean = routeCode.trim().uppercase()
        val stations = cachedStations?.filter { s ->
            s.fleetOver?.split(",")?.any { it.trim().equals(clean, ignoreCase = true) } == true
        } ?: emptyList()

        if (stations.isEmpty()) {
            return Result.failure(Exception("Không tìm thấy thông tin lộ trình của tuyến $clean."))
        }

        val first = stations.firstOrNull()?.name ?: ""
        val last = stations.lastOrNull()?.name ?: ""
        val electricInfo = ElectricBusCatalog.getInfo(clean)
        val enterprise = electricInfo?.enterprise ?: "Tổng công ty Vận tải Hà Nội (Transerco)"
        val opsTime = electricInfo?.operationsTime ?: "05:00 - 21:00"
        val freq = electricInfo?.frequency ?: "10 - 15 phút/chuyến"

        val detail = RouteDetail(
            fleetId = clean.filter { it.isDigit() }.toIntOrNull() ?: 0,
            enterprise = enterprise,
            code = clean,
            name = routeName,
            operationsTime = opsTime,
            frequency = freq,
            busCount = "${(stations.size / 4).coerceAtLeast(8)} xe",
            cost = "3.000đ mở cửa + 450đ/km (Vé ĐT/QR) • Suốt tuyến: 8.000đ - 10.000đ",
            costInt = 9000,
            firstStation = first,
            lastStation = last,
            go = DirectionDetail(
                anomaly = 0,
                routeDescription = "$first ➔ $last",
                stations = stations
            ),
            re = DirectionDetail(
                anomaly = 0,
                routeDescription = "$last ➔ $first",
                stations = stations.reversed()
            )
        )
        return Result.success(detail)
    }

    private suspend fun getRouteDetailByFid(fid: String): Result<RouteDetail> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("act", "fleetdetail")
                .add("fid", fid)
                .build()

            val request = Request.Builder()
                .url(BASE_SEARCH_URL)
                .post(body)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "http://timbus.vn/")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi kết nối máy chủ (${response.code})"))
            }

            val jsonStr = response.body?.string() ?: return@withContext Result.failure(Exception("Phản hồi rỗng từ máy chủ"))
            
            val jsonObj = try {
                JsonParser.parseString(jsonStr).asJsonObject
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Lỗi giải mã JSON từ máy chủ"))
            }

            val st = jsonObj.get("st")?.asBoolean ?: false
            if (st && jsonObj.has("dt") && jsonObj.get("dt").isJsonObject) {
                val detail = gson.fromJson(jsonObj.get("dt"), RouteDetail::class.java)
                Result.success(detail)
            } else {
                val msg = if (jsonObj.has("msg") && !jsonObj.get("msg").isJsonNull) {
                    val rawMsg = jsonObj.get("msg").asString
                    if (rawMsg.contains("Construct", ignoreCase = true)) {
                        "Tuyến này hiện đang cập nhật lộ trình mới hoặc điều chỉnh tuyến từ Transerco."
                    } else {
                        rawMsg
                    }
                } else {
                    "Không tìm thấy thông tin lộ trình của tuyến xe này."
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBusEta(stationId: Long, fleetOver: String = ""): Result<List<BusEta>> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("act", "partremained")
                .add("State", "true")
                .add("StationID", stationId.toString())
                .add("FleetOver", fleetOver)
                .build()

            val request = Request.Builder()
                .url(BASE_VEHICLE_URL)
                .post(body)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "http://timbus.vn/")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi kết nối xe (${response.code})"))
            }

            val jsonStr = response.body?.string() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
            
            val jsonObj = try {
                JsonParser.parseString(jsonStr).asJsonObject
            } catch (e: Exception) {
                return@withContext Result.success(emptyList())
            }

            val st = jsonObj.get("st")?.asBoolean ?: false
            if (st && jsonObj.has("dt") && jsonObj.get("dt").isJsonArray) {
                val type = object : TypeToken<List<BusEta>>() {}.type
                val list: List<BusEta> = gson.fromJson(jsonObj.get("dt"), type)
                Result.success(list)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
