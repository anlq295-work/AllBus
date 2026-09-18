package com.example.hanoibus.data

import com.google.gson.annotations.SerializedName

enum class HomeTab {
    ROUTES,        // Tuyến xe
    STATIONS,      // Trạm xe
    TRIP_PLANNER,  // Tìm đường
    TICKETS        // Thẻ vé
}

data class BusRoute(
    val fleetId: Int = 0,
    val code: String,
    val name: String,
    val fullName: String
)

data class TimbusResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("st") val st: Boolean,
    @SerializedName("dt") val data: T? = null,
    @SerializedName("msg") val msg: String? = null
)

data class RouteDetail(
    @SerializedName("FleetID") val fleetId: Int = 0,
    @SerializedName("Enterprise") val enterprise: String? = "",
    @SerializedName("Code") val code: String = "",
    @SerializedName("Name") val name: String = "",
    @SerializedName("OperationsTime") val operationsTime: String? = "",
    @SerializedName("Frequency") val frequency: String? = "",
    @SerializedName("BusCount") val busCount: String? = "",
    @SerializedName("Cost") val cost: String? = "",
    @SerializedName("CostInt") val costInt: Int? = null,
    @SerializedName("FirstStation") val firstStation: String? = "",
    @SerializedName("LastStation") val lastStation: String? = "",
    @SerializedName("Go") val go: DirectionDetail? = null,
    @SerializedName("Re") val re: DirectionDetail? = null
)

data class DirectionDetail(
    @SerializedName("Anomaly") val anomaly: Int? = 0,
    @SerializedName("Route") val routeDescription: String? = "",
    @SerializedName("Geo") val geo: List<GeoPoint> = emptyList(),
    @SerializedName("Station") val stations: List<BusStation> = emptyList()
)

data class BusStation(
    @SerializedName("ObjectID") val objectId: Long = 0,
    @SerializedName("Code") val code: String? = "",
    @SerializedName("Name") val name: String = "",
    @SerializedName("Street") val street: String? = "",
    @SerializedName("FleetOver") val fleetOver: String? = "",
    @SerializedName("Geo") val geo: GeoPoint? = null
)

data class GeoPoint(
    @SerializedName("Lat") val lat: Double = 0.0,
    @SerializedName("Lng") val lng: Double = 0.0
)

data class BusEta(
    @SerializedName("BienKiemSoat") val licensePlate: String? = "",
    @SerializedName("FleetCode") val fleetCode: String? = "",
    @SerializedName("Fleet") val fleet: String? = "",
    @SerializedName("PartRemained") val distanceMeters: Int? = 0,
    @SerializedName("TimeRemained") val timeSeconds: Int? = 0,
    @SerializedName("Speed") val speed: Double? = 0.0
) {
    fun formattedTime(): String {
        val s = timeSeconds ?: return "Chưa có xe"
        if (s <= 0) return "Sắp tới bến"
        if (s < 60) return "<1 phút"
        val min = kotlin.math.round(s / 60.0).toInt()
        return if (min < 1) "<1 phút" else "$min phút"
    }

    fun formattedDistance(): String {
        val d = distanceMeters ?: return "Chờ xuất bến"
        return if (d >= 1000) {
            String.format("%.1f km", d / 1000.0)
        } else {
            "${d} m"
        }
    }
}

data class TripPlace(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val lat: Double,
    val lng: Double,
    val isBusStation: Boolean = false,
    val stationId: Long? = null
)

enum class TripSegmentType {
    WALK,
    BUS
}

data class TripSegment(
    val type: TripSegmentType,
    val title: String,
    val instruction: String,
    val routeCode: String? = null,
    val routeName: String? = null,
    val fromPlaceName: String = "",
    val toPlaceName: String = "",
    val fromStationId: Long? = null,
    val toStationId: Long? = null,
    val stopsCount: Int = 0,
    val intermediateStops: List<String> = emptyList(),
    val stations: List<BusStation> = emptyList(),
    val distanceMeters: Int = 0,
    val durationMinutes: Int = 0,
    val pathPoints: List<GeoPoint> = emptyList()
)

data class TripOption(
    val id: String = java.util.UUID.randomUUID().toString(),
    val summary: String,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Int,
    val totalFareVnd: Int,
    val walkDistanceMeters: Int,
    val busTransferCount: Int,
    val segments: List<TripSegment>,
    val busRoutes: List<String> = emptyList()
)
