package com.example.hanoibus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hanoibus.data.BusEta
import com.example.hanoibus.data.BusRoute
import com.example.hanoibus.data.BusStation
import com.example.hanoibus.data.ElectricBusCatalog
import com.example.hanoibus.data.ElectricBusInfo
import com.example.hanoibus.data.HomeTab
import com.example.hanoibus.data.RouteDetail
import com.example.hanoibus.data.TimbusRepository
import com.example.hanoibus.data.TransitRoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DirectionType {
    GO, RE
}

data class BusUiState(
    // Home navigation
    val currentHomeTab: HomeTab = HomeTab.ROUTES,

    // Stations tab state
    val allStations: List<BusStation> = emptyList(),
    val stationSearchQuery: String = "",
    val suggestedStations: List<BusStation> = emptyList(),
    val selectedMapStation: BusStation? = null,
    val isLoadingStations: Boolean = false,

    // Routes tab state
    val allRoutes: List<BusRoute> = emptyList(),
    val searchQuery: String = "",
    val filteredRoutes: List<BusRoute> = emptyList(),
    val selectedFilterChip: String = "Tất cả",
    
    // Route detail
    val selectedRoute: BusRoute? = null,
    val selectedRouteIsElectric: Boolean = false,
    val electricBusInfo: ElectricBusInfo? = null,
    val isLoadingDetail: Boolean = false,
    val routeDetail: RouteDetail? = null,
    val detailError: String? = null,
    val currentDirection: DirectionType = DirectionType.GO,
    val isMapView: Boolean = true, // Default to Map View
    
    // Stop & ETA
    val selectedStation: BusStation? = null,
    val isLoadingEta: Boolean = false,
    val etaList: List<BusEta> = emptyList(),
    val etaError: String? = null,
    val lastEtaUpdated: Long = 0L
)

class BusViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TimbusRepository = TimbusRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BusUiState())
    val uiState: StateFlow<BusUiState> = _uiState.asStateFlow()

    private var etaPollingJob: Job? = null
    private var etaCountdownJob: Job? = null

    companion object {
        val routeComparator = Comparator<BusRoute> { r1, r2 ->
            compareFleetCodes(r1.code, r2.code)
        }
    }

    init {
        loadRoutes()
        loadStations()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            val routes = repository.getRoutes().sortedWith(routeComparator)
            _uiState.update {
                it.copy(
                    allRoutes = routes,
                    filteredRoutes = routes
                )
            }
        }
    }

    private fun loadStations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStations = true) }
            val stations = repository.getAllStations(getApplication())
            _uiState.update {
                it.copy(
                    allStations = stations,
                    isLoadingStations = false
                )
            }
            launch(Dispatchers.Default) {
                TransitRoutingEngine.warmStationCache(stations)
            }
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        _uiState.update { it.copy(currentHomeTab = tab) }
    }

    private var stationSearchJob: Job? = null

    fun onStationSearchQueryChanged(query: String) {
        _uiState.update { it.copy(stationSearchQuery = query) }
        stationSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(suggestedStations = emptyList()) }
            return
        }
        stationSearchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            val stations = _uiState.value.allStations
            val suggestions = TransitRoutingEngine.searchStations(trimmed, stations, limit = 25)
            android.util.Log.d("BusViewModel", "searchStations query='$trimmed', allStations=${stations.size}, results=${suggestions.size}")
            _uiState.update {
                it.copy(suggestedStations = suggestions)
            }
        }
    }

    fun selectStationOnMap(station: BusStation) {
        android.util.Log.d("BusViewModel", "selectStationOnMap: ${station.name} (${station.objectId})")
        _uiState.update {
            it.copy(
                selectedMapStation = station,
                stationSearchQuery = station.name,
                suggestedStations = emptyList(),
                selectedStation = station,
                isLoadingEta = true,
                etaList = emptyList(),
                etaError = null
            )
        }
        startEtaPolling(station.objectId)
    }

    fun clearSelectedStationOnMap() {
        cancelEtaPolling()
        _uiState.update {
            it.copy(
                selectedMapStation = null,
                selectedStation = null,
                stationSearchQuery = "",
                suggestedStations = emptyList(),
                etaList = emptyList(),
                etaError = null
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = filterRoutes(state.allRoutes, query, state.selectedFilterChip)
            state.copy(searchQuery = query, filteredRoutes = filtered)
        }
    }

    fun onFilterChipSelected(chip: String) {
        _uiState.update { state ->
            val filtered = filterRoutes(state.allRoutes, state.searchQuery, chip)
            state.copy(selectedFilterChip = chip, filteredRoutes = filtered)
        }
    }

    private fun filterRoutes(routes: List<BusRoute>, query: String, chip: String): List<BusRoute> {
        val rawQuery = query.trim().lowercase()
        val normalizedQuery = rawQuery.removeAccents()
        return routes.filter { route ->
            val matchesQuery = if (rawQuery.isEmpty()) true else {
                route.code.lowercase().contains(rawQuery) ||
                route.name.lowercase().contains(rawQuery) ||
                route.name.lowercase().removeAccents().contains(normalizedQuery)
            }
            val matchesChip = when (chip) {
                "Tất cả" -> true
                "Tuyến Xe Điện" -> ElectricBusCatalog.isElectricBus(route.code)
                "Tuyến Thường" -> !ElectricBusCatalog.isElectricBus(route.code)
                else -> true
            }
            matchesQuery && matchesChip
        }.sortedWith(routeComparator)
    }

    fun selectRoute(route: BusRoute) {
        cancelEtaPolling()
        val isElectric = ElectricBusCatalog.isElectricBus(route.code)
        val electricInfo = ElectricBusCatalog.getElectricBusInfo(route.code)
        _uiState.update {
            it.copy(
                selectedRoute = route,
                selectedRouteIsElectric = isElectric,
                electricBusInfo = electricInfo,
                isLoadingDetail = true,
                routeDetail = null,
                detailError = null,
                currentDirection = DirectionType.GO,
                selectedStation = null,
                isMapView = true // Default to Map View
            )
        }

        viewModelScope.launch {
            val result = repository.getRouteDetail(route)
            result.onSuccess { detail ->
                val firstStation = detail.go?.stations?.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        routeDetail = detail,
                        detailError = null,
                        selectedStation = firstStation
                    )
                }
                if (firstStation != null) {
                    startEtaPolling(firstStation.objectId)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailError = error.message ?: "Không thể tải chi tiết tuyến"
                    )
                }
            }
        }
    }

    fun setDirection(direction: DirectionType) {
        val detail = _uiState.value.routeDetail
        val stations = if (direction == DirectionType.GO) detail?.go?.stations else detail?.re?.stations
        val firstStation = stations?.firstOrNull()

        _uiState.update {
            it.copy(
                currentDirection = direction,
                selectedStation = firstStation
            )
        }
        if (firstStation != null) {
            startEtaPolling(firstStation.objectId)
        } else {
            cancelEtaPolling()
        }
    }

    fun setViewMode(isMap: Boolean) {
        _uiState.update { it.copy(isMapView = isMap) }
    }

    fun clearSelectedRoute() {
        cancelEtaPolling()
        _uiState.update {
            it.copy(
                selectedRoute = null,
                routeDetail = null,
                selectedStation = null,
                etaList = emptyList()
            )
        }
    }

    fun selectStation(station: BusStation) {
        _uiState.update {
            it.copy(
                selectedStation = station,
                isLoadingEta = true,
                etaList = emptyList(),
                etaError = null
            )
        }
        startEtaPolling(station.objectId)
    }

    fun clearSelectedStation() {
        cancelEtaPolling()
        _uiState.update {
            it.copy(
                selectedStation = null,
                etaList = emptyList(),
                etaError = null
            )
        }
    }

    fun refreshEta() {
        val station = _uiState.value.selectedStation ?: return
        viewModelScope.launch {
            loadEta(station.objectId)
        }
    }

    private fun startEtaPolling(stationId: Long) {
        cancelEtaPolling()
        // 1. Đồng bộ dữ liệu vệ tinh GPS từ máy chủ mỗi 5s
        etaPollingJob = viewModelScope.launch {
            while (isActive) {
                loadEta(stationId)
                delay(5_000L)
            }
        }

        // 2. Bộ đếm ngược thời gian thực từng giây (1-second live countdown ticker)
        // Giúp con số ETA giảm mượt mà từng giây một trên màn hình (4p 44s -> 4p 43s -> 4p 42s...)
        etaCountdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { state ->
                    if (state.etaList.isEmpty()) state
                    else {
                        val updated = state.etaList.map { bus ->
                            val sec = bus.timeSeconds
                            if (sec != null && sec > 0) {
                                bus.copy(timeSeconds = sec - 1)
                            } else bus
                        }
                        state.copy(etaList = updated)
                    }
                }
            }
        }
    }

    private fun cancelEtaPolling() {
        etaPollingJob?.cancel()
        etaPollingJob = null
        etaCountdownJob?.cancel()
        etaCountdownJob = null
    }

    private fun processEtas(rawEtas: List<BusEta>, stationId: Long): List<BusEta> {
        val finalEtas = rawEtas.toMutableList()
        val selectedRoute = _uiState.value.selectedRoute
        val selectedStation = _uiState.value.selectedStation ?: _uiState.value.selectedMapStation
        val currentDirection = _uiState.value.currentDirection

        // 1. Gather all passing fleets for this station
        val passingFleets = (selectedStation?.fleetOver ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (selectedRoute != null && !passingFleets.any { it.equals(selectedRoute.code, ignoreCase = true) }) {
            passingFleets.add(0, selectedRoute.code)
        }

        // 2. Add simulated ETA for electric buses if not already present
        for (fleet in passingFleets) {
            if (ElectricBusCatalog.isElectricBus(fleet)) {
                val hasThisRoute = finalEtas.any { it.fleet?.equals(fleet, ignoreCase = true) == true }
                if (!hasThisRoute) {
                    val sec = ElectricBusCatalog.computeScheduledEtaSeconds(fleet, stationId)
                    val plate = ElectricBusCatalog.getSimulatedLicensePlate(fleet, stationId)
                    finalEtas.add(
                        BusEta(
                            licensePlate = plate,
                            fleetCode = if (fleet.equals(selectedRoute?.code, ignoreCase = true) && currentDirection == DirectionType.RE) "1" else "0",
                            fleet = fleet,
                            distanceMeters = (sec * 5.5).toInt().coerceAtLeast(300),
                            timeSeconds = sec,
                            speed = 22.0
                        )
                    )
                }
            }
        }

        // 3. For all other fleets passing this station without incoming bus, add placeholder entry
        for (fleet in passingFleets) {
            val hasThisRoute = finalEtas.any { it.fleet?.equals(fleet, ignoreCase = true) == true }
            if (!hasThisRoute) {
                finalEtas.add(
                    BusEta(
                        licensePlate = null,
                        fleetCode = null,
                        fleet = fleet,
                        distanceMeters = null,
                        timeSeconds = null,
                        speed = null
                    )
                )
            }
        }

        // 4. Sort: active incoming buses first (by time remaining), then inactive passing fleets naturally by fleet code
        finalEtas.sortWith { e1, e2 ->
            val t1 = e1.timeSeconds
            val t2 = e2.timeSeconds
            val has1 = (t1 != null)
            val has2 = (t2 != null)
            if (has1 != has2) {
                return@sortWith if (has1) -1 else 1
            }
            if (t1 != null && t2 != null && t1 != t2) {
                return@sortWith t1.compareTo(t2)
            }
            compareFleetCodes(e1.fleet ?: "", e2.fleet ?: "")
        }

        return finalEtas
    }

    private suspend fun loadEta(stationId: Long) {
        val result = repository.getBusEta(stationId)

        result.onSuccess { rawEtas ->
            val finalEtas = processEtas(rawEtas, stationId)
            _uiState.update {
                it.copy(
                    isLoadingEta = false,
                    etaList = finalEtas,
                    etaError = null,
                    lastEtaUpdated = System.currentTimeMillis()
                )
            }
        }.onFailure { error ->
            val fallbackList = processEtas(emptyList(), stationId)
            _uiState.update {
                it.copy(
                    isLoadingEta = false,
                    etaList = fallbackList,
                    etaError = if (fallbackList.isEmpty()) (error.message ?: "Không thể lấy ước tính giờ đến") else null,
                    lastEtaUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelEtaPolling()
    }
}

fun compareFleetCodes(c1: String, c2: String): Int {
    val regex = Regex("""^([A-Za-z]*)(\d+)([A-Za-z0-9]*)$""")
    val m1 = regex.find(c1.trim())
    val m2 = regex.find(c2.trim())

    if (m1 != null && m2 != null) {
        val prefix1 = m1.groupValues[1].uppercase()
        val prefix2 = m2.groupValues[1].uppercase()
        val p1 = if (prefix1.isEmpty()) 0 else 1
        val p2 = if (prefix2.isEmpty()) 0 else 1

        if (p1 != p2) return p1.compareTo(p2)
        if (prefix1 != prefix2) return prefix1.compareTo(prefix2)

        val num1 = m1.groupValues[2].toIntOrNull() ?: 0
        val num2 = m2.groupValues[2].toIntOrNull() ?: 0
        if (num1 != num2) return num1.compareTo(num2)

        val suffix1 = m1.groupValues[3].uppercase()
        val suffix2 = m2.groupValues[3].uppercase()
        return suffix1.compareTo(suffix2)
    }

    if (m1 != null) return -1
    if (m2 != null) return 1
    return c1.compareTo(c2, ignoreCase = true)
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
