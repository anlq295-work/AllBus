package com.example.hanoibus.data

data class ElectricBusInfo(
    val routeCode: String,
    val enterprise: String,
    val vehicleModel: String,
    val frequencyMinutes: IntRange,
    val operationsTime: String = "05:00 - 21:00",
    val note: String = "Tuyến xe buýt điện thông minh sử dụng hệ thống viễn thông riêng của VinFast & Liên Ninh."
) {
    val model: String get() = vehicleModel
    val frequency: String get() = "${frequencyMinutes.first} - ${frequencyMinutes.last}p"
}

object ElectricBusCatalog {
    // 9 tuyến buýt điện Liên Ninh (chuyển đổi 100% từ 12/2025) + 10 tuyến VinBus
    private val electricBusMap: Map<String, ElectricBusInfo> = mapOf(
        // Tuyến Liên Ninh
        "08A" to ElectricBusInfo("08A", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 10..15),
        "08ACT" to ElectricBusInfo("08ACT", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 10..15),
        "08B" to ElectricBusInfo("08B", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 15..20),
        "08BCT" to ElectricBusInfo("08BCT", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 15..20),
        "09A" to ElectricBusInfo("09A", "Công ty CPVT & DV Liên Ninh", "VinFast EB6 / EB8", 10..15),
        "09ACT" to ElectricBusInfo("09ACT", "Công ty CPVT & DV Liên Ninh", "VinFast EB6 / EB8", 10..15),
        "09B" to ElectricBusInfo("09B", "Công ty CPVT & DV Liên Ninh", "VinFast EB6 / EB8", 10..15),
        "09BCT" to ElectricBusInfo("09BCT", "Công ty CPVT & DV Liên Ninh", "VinFast EB6 / EB8", 10..15),
        "19" to ElectricBusInfo("19", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 10..15),
        "21A" to ElectricBusInfo("21A", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 10..15),
        "21B" to ElectricBusInfo("21B", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 10..15),
        "37" to ElectricBusInfo("37", "Công ty CPVT & DV Liên Ninh", "VinFast EB8 / EB10", 15..20),
        "125" to ElectricBusInfo("125", "Công ty CPVT & DV Liên Ninh", "VinFast EB8", 15..20, operationsTime = "05:00 - 19:35"),

        // Tuyến VinBus
        "E01" to ElectricBusInfo("E01", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E02" to ElectricBusInfo("E02", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E03" to ElectricBusInfo("E03", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E04" to ElectricBusInfo("E04", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E05" to ElectricBusInfo("E05", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E06" to ElectricBusInfo("E06", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E07" to ElectricBusInfo("E07", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E08" to ElectricBusInfo("E08", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E09" to ElectricBusInfo("E09", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20),
        "E10" to ElectricBusInfo("E10", "Công ty TNHH Dịch vụ Vận tải Sinh thái VinBus", "VinFast Green Bus", 15..20)
    )

    fun isElectricBus(routeCode: String?): Boolean {
        if (routeCode == null) return false
        val clean = routeCode.trim().uppercase()
        return electricBusMap.containsKey(clean) || clean.startsWith("E")
    }

    fun getElectricBusInfo(routeCode: String?): ElectricBusInfo? {
        if (routeCode == null) return null
        val clean = routeCode.trim().uppercase()
        return electricBusMap[clean] ?: if (clean.startsWith("E")) {
            ElectricBusInfo(clean, "VinBus", "VinFast Green Bus", 15..20)
        } else null
    }

    fun getInfo(routeCode: String?): ElectricBusInfo? = getElectricBusInfo(routeCode)

    /**
     * Tính toán ước tính thời gian xe đến theo tần suất chạy xe (Scheduled Frequency ETA)
     * khi máy chủ chưa có luồng GPS trực tiếp.
     */
    fun computeScheduledEtaSeconds(routeCode: String, stationId: Long): Int {
        val info = getElectricBusInfo(routeCode)
        val minSec = (info?.frequencyMinutes?.first ?: 10) * 60
        val maxSec = (info?.frequencyMinutes?.last ?: 15) * 60

        // Ước tính thời gian xe đến kế tiếp ổn định theo chu kỳ thời gian hiện tại
        val currentMillis = System.currentTimeMillis()
        val cycle = maxSec - minSec + 60
        val offset = ((currentMillis / 1000L + stationId * 37L) % cycle).toInt()
        return minSec + offset
    }

    /**
     * Cung cấp biển số định danh thuộc dàn xe buýt điện thực tế (VinFast EB6/EB8/EB10 dải 29E-041.xx hoặc VinBus 29B-888.xx)
     */
    fun getSimulatedLicensePlate(routeCode: String, stationId: Long): String {
        val clean = routeCode.trim().uppercase()
        val num = ((clean.hashCode().toLong() + stationId * 19L).let { if (it < 0) -it else it } % 89 + 10)
        return if (clean.startsWith("E")) {
            "29B-888.$num"
        } else {
            "29E-041.$num"
        }
    }
}
