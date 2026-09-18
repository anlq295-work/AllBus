package com.example.hanoibus

import com.example.hanoibus.data.BusEta
import com.example.hanoibus.data.DefaultRoutes
import com.example.hanoibus.data.TimbusRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BusRepositoryTest {

    @Test
    fun testDefaultRoutesLoaded() {
        val routes = DefaultRoutes.allRoutes
        assertTrue("Routes count should be > 100", routes.size > 100)
        
        // Test route 32 and 01 exist
        val route32 = routes.find { it.code == "32" }
        assertNotNull("Route 32 should exist", route32)
        assertEquals("Bến xe Giáp Bát - Nhổn", route32?.name)

        val vinbusE01 = routes.find { it.code == "E01" }
        assertNotNull("VinBus E01 should exist", vinbusE01)
    }

    @Test
    fun testBusEtaFormatting() {
        val etaShort = BusEta(distanceMeters = 250, timeSeconds = 55)
        assertEquals("<1 phút", etaShort.formattedTime())
        assertEquals("250 m", etaShort.formattedDistance())

        val etaLong = BusEta(distanceMeters = 2400, timeSeconds = 185)
        assertEquals("3 phút", etaLong.formattedTime())
        assertEquals("2.4 km", etaLong.formattedDistance())

        val etaArrived = BusEta(distanceMeters = 10, timeSeconds = 0)
        assertEquals("Sắp tới bến", etaArrived.formattedTime())
    }

    @Test
    fun testLiveRouteDetailFetch() = runBlocking {
        val repository = TimbusRepository()
        val result = repository.getRouteDetail("32")
        
        assertTrue("Live fetch route 32 should succeed", result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals("32", detail?.code)
        assertTrue("Should have stations in Go direction", (detail?.go?.stations?.size ?: 0) > 20)
        
        val firstStop = detail?.go?.stations?.first()
        assertNotNull(firstStop)
        assertTrue("First stop should have an objectId", (firstStop?.objectId ?: 0) > 0)
    }

    @Test
    fun testLiveEtaFetch() = runBlocking {
        val repository = TimbusRepository()
        // Station 166 (Toyota Giải Phóng) has high bus traffic
        val result = repository.getBusEta(166L)
        assertTrue("Live ETA call should not throw error", result.isSuccess)
    }

    @Test
    fun testRoute08BFetch() = runBlocking {
        val repository = TimbusRepository()
        val route08B = DefaultRoutes.allRoutes.find { it.code == "08B" }
        assertNotNull("Route 08B should exist in catalog", route08B)
        assertEquals(708, route08B?.fleetId)

        val result = repository.getRouteDetail(route08B!!)
        assertTrue("Live fetch route 08B should succeed without crash", result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals("08B", detail?.code)
        assertTrue("Route 08B should have stations in Go direction", (detail?.go?.stations?.size ?: 0) > 10)
    }

    @Test
    fun testRoute21BFetch() = runBlocking {
        val repository = TimbusRepository()
        val route21B = DefaultRoutes.allRoutes.find { it.code == "21B" }
        assertNotNull("Route 21B should exist in catalog", route21B)
        assertEquals(821, route21B?.fleetId)
        assertTrue("Route 21B name should mention Duyên Thái", route21B?.name?.contains("Duyên Thái") == true)

        val result = repository.getRouteDetail(route21B!!)
        assertTrue("Live fetch route 21B should succeed", result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals("21B", detail?.code)
        assertTrue("Route 21B should have stations in Go direction", (detail?.go?.stations?.size ?: 0) > 10)
    }

    @Test
    fun testNaturalRouteSorting() {
        val routes = DefaultRoutes.allRoutes
        val idx08A = routes.indexOfFirst { it.code == "08A" }
        val idx08ACT = routes.indexOfFirst { it.code == "08ACT" }
        val idx08B = routes.indexOfFirst { it.code == "08B" }
        val idx08BCT = routes.indexOfFirst { it.code == "08BCT" }
        val idx21A = routes.indexOfFirst { it.code == "21A" }
        val idx21B = routes.indexOfFirst { it.code == "21B" }

        assertTrue("08A should come before 08ACT", idx08A < idx08ACT)
        assertTrue("08ACT should come before 08B", idx08ACT < idx08B)
        assertTrue("08B should come before 08BCT", idx08B < idx08BCT)
        assertTrue("08BCT should come before 21A", idx08BCT < idx21A)
        assertTrue("21A should come before 21B", idx21A < idx21B)
    }

    @Test
    fun testAllStationsExtraction() {
        val repository = TimbusRepository()
        val file = java.io.File("src/main/assets/hanoi_stations.json")
        assertTrue("hanoi_stations.json asset file must exist", file.exists())
        
        file.inputStream().use { stream ->
            val stations = repository.getAllStationsFromStream(stream)
            assertTrue("Should load more than 5,000 stations", stations.size >= 5000)
            
            // Check that coordinates are present
            val validCoords = stations.filter { it.geo != null && it.geo.lat > 20.0 && it.geo.lng > 105.0 }
            assertEquals("All stations must have valid Hanoi coordinates", stations.size, validCoords.size)

            // Verify station 5910 (Cổng tòa chung cư IEC) contains 125 in fleetOver
            val iecStation = stations.find { it.objectId == 5910L }
            assertNotNull("Station 5910 (Cổng tòa chung cư IEC) should exist", iecStation)
            assertTrue("Station 5910 should have route 125 in fleetOver", iecStation?.fleetOver?.contains("125") == true)
        }
    }

    @Test
    fun testRoute125DetailAndCatalog() = runBlocking {
        val repository = TimbusRepository()
        val route125 = DefaultRoutes.allRoutes.find { it.code == "125" }
        assertNotNull("Route 125 should exist in catalog", route125)
        assertTrue("Route 125 name should mention Giáp Bát", route125?.name?.contains("Giáp Bát") == true)
        assertEquals("Bến xe Giáp Bát - Vân Đình", route125?.name)

        val result = repository.getRouteDetail(route125!!)
        assertTrue("Fetching Route 125 should succeed", result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals("125", detail?.code)

        val goStations = detail?.go?.stations ?: emptyList()
        val reStations = detail?.re?.stations ?: emptyList()
        assertTrue("Route 125 Go direction should have >= 45 stations", goStations.size >= 45)
        assertTrue("Route 125 Re direction should have >= 45 stations", reStations.size >= 45)
        
        val iecStationGo = goStations.find { it.objectId == 5910L }
        val iecStationRe = reStations.find { it.objectId == 5910L }
        assertNotNull("Station 5910 (Cổng tòa chung cư IEC) must be in Route 125 Go stops", iecStationGo)
        assertNotNull("Station 5910 (Cổng tòa chung cư IEC) must be in Route 125 Re stops", iecStationRe)
        assertTrue("Station name should mention Cổng tòa chung cư IEC", iecStationGo?.name?.contains("Cổng tòa chung cư IEC") == true)

        // Verify end station is Vân Đình
        val vanDinhGo = goStations.lastOrNull()
        val vanDinhRe = reStations.firstOrNull()
        assertTrue("Go terminal should be Vân Đình", vanDinhGo?.name?.contains("Vân Đình") == true)
        assertTrue("Re origin should be Vân Đình", vanDinhRe?.name?.contains("Vân Đình") == true)
    }

    @Test
    fun testElectricBusCatalog() {
        val convertedLines = listOf("08A", "08B", "09A", "09B", "19", "21A", "21B", "37", "125")
        for (code in convertedLines) {
            assertTrue("Route $code should be identified as electric bus", com.example.hanoibus.data.ElectricBusCatalog.isElectricBus(code))
            val info = com.example.hanoibus.data.ElectricBusCatalog.getInfo(code)
            assertNotNull("Info for route $code should not be null", info)
            assertTrue("Should mention Liên Ninh or VinFast", info?.enterprise?.contains("Liên Ninh") == true || info?.model?.contains("VinFast") == true)
        }

        val vinbusLines = listOf("E01", "E02", "E03", "E04", "E05", "E06", "E07", "E08", "E09", "E10")
        for (code in vinbusLines) {
            assertTrue("Route $code should be identified as electric bus", com.example.hanoibus.data.ElectricBusCatalog.isElectricBus(code))
        }

        // Regular diesel route should be false
        assertFalse("Route 32 should not be an electric bus", com.example.hanoibus.data.ElectricBusCatalog.isElectricBus("32"))
        assertFalse("Route 01 should not be an electric bus", com.example.hanoibus.data.ElectricBusCatalog.isElectricBus("01"))

        // Scheduled ETA test
        val etaSec = com.example.hanoibus.data.ElectricBusCatalog.computeScheduledEtaSeconds("21B", 5910L)
        assertTrue("Scheduled ETA seconds should be between 120 and 960", etaSec in 120..960)
    }
}
