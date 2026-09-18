package com.example.hanoibus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hanoibus.data.BusEta
import com.example.hanoibus.data.BusRoute
import com.example.hanoibus.data.BusStation
import com.example.hanoibus.data.ElectricBusCatalog
import com.example.hanoibus.data.GeoPoint
import com.example.hanoibus.data.HomeTab
import com.example.hanoibus.data.RouteDetail
import com.example.hanoibus.data.TransitRoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusAppScreen(
    viewModel: BusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    var isQuickTicketEnabled by rememberSaveable { 
        mutableStateOf(TicketPreferences.isQuickTicketEnabled(context)) 
    }

    // Handle back button when inside Route Detail or Station selection
    BackHandler(enabled = state.selectedRoute != null || (state.currentHomeTab == HomeTab.STATIONS && state.selectedMapStation != null)) {
        if (state.selectedRoute != null) {
            if (!state.isMapView && state.selectedStation != null) {
                viewModel.clearSelectedStation()
            } else {
                viewModel.clearSelectedRoute()
            }
        } else if (state.currentHomeTab == HomeTab.STATIONS && state.selectedMapStation != null) {
            viewModel.clearSelectedStationOnMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val titleText = if (state.selectedRoute != null) {
                            "Tuyến ${state.selectedRoute?.code}"
                        } else {
                            when (state.currentHomeTab) {
                                HomeTab.ROUTES -> "Hà Nội Bus Tracker"
                                HomeTab.STATIONS -> "Bản đồ trạm xe buýt"
                                HomeTab.TRIP_PLANNER -> "Tìm đường xe buýt"
                                HomeTab.TICKETS -> "Thẻ vé Giao thông HN"
                            }
                        }
                        val subtitleText = if (state.selectedRoute != null) {
                            state.selectedRoute?.name ?: ""
                        } else {
                            when (state.currentHomeTab) {
                                HomeTab.ROUTES -> "Tra cứu tuyến, điểm đón & giờ xe đến"
                                HomeTab.STATIONS -> "5,051 trạm dừng đón trên toàn Hà Nội"
                                HomeTab.TRIP_PLANNER -> "Lộ trình & chuyển tuyến xe buýt thông minh"
                                HomeTab.TICKETS -> "Thẻ ảo, mã QR quét xe buýt & Metro Hà Nội"
                            }
                        }

                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (state.selectedRoute != null) {
                        IconButton(onClick = { viewModel.clearSelectedRoute() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại"
                            )
                        }
                    }
                },
                actions = {
                    // Quick 1-tap QR ticket button - only show when enabled in settings
                    if (isQuickTicketEnabled) {
                        IconButton(onClick = { TicketAppHelper.openTicketApp(context) }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Mở thẻ vé quét QR",
                                tint = Color(0xFF2E7D32)
                            )
                        }
                    }

                    if (state.selectedRoute != null && state.routeDetail != null) {
                        // Toggle between Map and List View
                        IconButton(onClick = { viewModel.setViewMode(!state.isMapView) }) {
                            Icon(
                                imageVector = if (state.isMapView) Icons.Default.FormatListNumbered else Icons.Default.Map,
                                contentDescription = if (state.isMapView) "Xem dạng danh sách" else "Xem trên bản đồ"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        },
        bottomBar = {
            if (state.selectedRoute == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.currentHomeTab == HomeTab.ROUTES,
                        onClick = { viewModel.selectHomeTab(HomeTab.ROUTES) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "Tuyến xe"
                            )
                        },
                        label = { Text("Tuyến xe", fontWeight = if (state.currentHomeTab == HomeTab.ROUTES) FontWeight.Bold else FontWeight.Normal) }
                    )
                    NavigationBarItem(
                        selected = state.currentHomeTab == HomeTab.STATIONS,
                        onClick = { viewModel.selectHomeTab(HomeTab.STATIONS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Trạm xe"
                            )
                        },
                        label = { Text("Trạm xe", fontWeight = if (state.currentHomeTab == HomeTab.STATIONS) FontWeight.Bold else FontWeight.Normal) }
                    )
                    NavigationBarItem(
                        selected = state.currentHomeTab == HomeTab.TRIP_PLANNER,
                        onClick = { viewModel.selectHomeTab(HomeTab.TRIP_PLANNER) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = "Tìm đường"
                            )
                        },
                        label = { Text("Tìm đường", fontWeight = if (state.currentHomeTab == HomeTab.TRIP_PLANNER) FontWeight.Bold else FontWeight.Normal) }
                    )
                    NavigationBarItem(
                        selected = state.currentHomeTab == HomeTab.TICKETS,
                        onClick = { viewModel.selectHomeTab(HomeTab.TICKETS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = "Thẻ vé"
                            )
                        },
                        label = { Text("Thẻ vé", fontWeight = if (state.currentHomeTab == HomeTab.TICKETS) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.selectedRoute == null) {
                when (state.currentHomeTab) {
                    HomeTab.ROUTES -> {
                        RouteSearchContent(
                            state = state,
                            onSearchChange = { viewModel.onSearchQueryChanged(it) },
                            onChipSelect = { viewModel.onFilterChipSelected(it) },
                            onRouteClick = { viewModel.selectRoute(it) }
                        )
                    }
                    HomeTab.STATIONS -> {
                        AllStationsContent(
                            state = state,
                            onSearchChange = { viewModel.onStationSearchQueryChanged(it) },
                            onStationSelect = { viewModel.selectStationOnMap(it) },
                            onClearSelectedStation = { viewModel.clearSelectedStationOnMap() },
                            onRefreshEta = { viewModel.refreshEta() }
                        )
                    }
                    HomeTab.TRIP_PLANNER -> {
                        TripPlannerScreen(
                            allStations = state.allStations,
                            userLocation = null
                        )
                    }
                    HomeTab.TICKETS -> {
                        TicketScreen(
                            isQuickTicketEnabled = isQuickTicketEnabled,
                            onToggleQuickTicket = { enabled ->
                                isQuickTicketEnabled = enabled
                                TicketPreferences.setQuickTicketEnabled(context, enabled)
                            }
                        )
                    }
                }
            } else {
                // Route Detail Screen (Defaults to Map View)
                RouteDetailContent(
                    state = state,
                    onDirectionChange = { viewModel.setDirection(it) },
                    onStationSelect = { viewModel.selectStation(it) },
                    onRefreshEta = { viewModel.refreshEta() },
                    onToggleViewMode = { viewModel.setViewMode(!state.isMapView) },
                    onRetry = { viewModel.selectRoute(state.selectedRoute!!) }
                )
            }

            // Floating 1-tap ticket scan button - only show when enabled in settings
            if (isQuickTicketEnabled && state.currentHomeTab != HomeTab.TICKETS) {
                FloatingTicketButton(
                    onClick = { TicketAppHelper.openTicketApp(context) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 20.dp)
                )
            }

            // Bottom sheet for List mode when user taps ETA on a specific stop
            if (!state.isMapView && state.selectedStation != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.clearSelectedStation() }
                ) {
                    LiveEtaSheetContent(
                        station = state.selectedStation!!,
                        isLoading = state.isLoadingEta,
                        etaList = state.etaList,
                        errorMessage = state.etaError,
                        lastUpdated = state.lastEtaUpdated,
                        onRefresh = { viewModel.refreshEta() },
                        onClose = { viewModel.clearSelectedStation() }
                    )
                }
            }
        }
    }
}

@Composable
fun RouteSearchContent(
    state: BusUiState,
    onSearchChange: (String) -> Unit,
    onChipSelect: (String) -> Unit,
    onRouteClick: (BusRoute) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Compact 1-line Search Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "Tìm số hiệu, tuyến xe, tên đường...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Xóa",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Filter Chips Row
        val filterChips = listOf("Tất cả", "Tuyến Thường", "Tuyến Xe Điện")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterChips) { chip ->
                FilterChip(
                    selected = state.selectedFilterChip == chip,
                    onClick = { onChipSelect(chip) },
                    label = { Text(chip) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Total count banner
        Text(
            text = "Tìm thấy ${state.filteredRoutes.size} tuyến xe buýt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Route list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.filteredRoutes, key = { "${it.fleetId}_${it.code}" }) { route ->
                BusRouteCard(route = route, onClick = { onRouteClick(route) })
            }
        }
    }
}

@Composable
fun BusRouteCard(
    route: BusRoute,
    onClick: () -> Unit
) {
    val isElectric = ElectricBusCatalog.isElectricBus(route.code)
    val electricInfo = ElectricBusCatalog.getInfo(route.code)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge for route code
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .defaultMinSize(minWidth = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isElectric) Color(0xFF00897B) else MaterialTheme.colorScheme.primary
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = route.code,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (route.code.length >= 5) 11.sp else if (route.code.length > 3) 12.sp else 15.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Route Name & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isElectric) {
                    Text(
                        text = "⚡ Xe điện ${electricInfo?.model ?: "VinFast"} • ${electricInfo?.enterprise ?: "VinBus"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00796B),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Xe buýt Transerco Hà Nội",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Xem chi tiết",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RouteDetailContent(
    state: BusUiState,
    onDirectionChange: (DirectionType) -> Unit,
    onStationSelect: (BusStation) -> Unit,
    onRefreshEta: () -> Unit,
    onToggleViewMode: () -> Unit,
    onRetry: () -> Unit
) {
    if (state.isLoadingDetail) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Đang tải lộ trình & điểm đón...")
            }
        }
        return
    }

    if (state.detailError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Warning, contentDescription = "Lỗi", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.detailError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Thử lại")
                }
            }
        }
        return
    }

    val detail = state.routeDetail ?: return
    val directionData = if (state.currentDirection == DirectionType.GO) detail.go else detail.re
    val stations = directionData?.stations ?: emptyList()
    val routeGeo = directionData?.geo ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoItem(label = "Thời gian chạy", value = detail.operationsTime?.split(";")?.firstOrNull()?.replace("0|", "") ?: "05:00 - 22:30")
                InfoItem(label = "Tần suất", value = detail.frequency ?: "10 - 15p")
                InfoItem(label = "Giá vé", value = detail.cost ?: "10.000đ")
            }
        }

        // Electric Bus Notice Banner if electric
        if (state.selectedRouteIsElectric && state.electricBusInfo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = "Tuyến xe buýt điện thông minh",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "${state.electricBusInfo?.model ?: "VinFast EV"} • Đơn vị: ${state.electricBusInfo?.enterprise ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "Tần suất ${state.electricBusInfo?.frequency ?: "10-15p"} • Cập nhật đếm ngược mỗi giây",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF388E3C)
                        )
                    }
                }
            }
        }

        // Direction Selector & View Mode Toggle Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.currentDirection == DirectionType.GO,
                    onClick = { onDirectionChange(DirectionType.GO) },
                    label = { Text("Chiều đi (${detail.go?.stations?.size ?: 0})", fontSize = 12.sp) },
                    leadingIcon = {
                        if (state.currentDirection == DirectionType.GO) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                )
                FilterChip(
                    selected = state.currentDirection == DirectionType.RE,
                    onClick = { onDirectionChange(DirectionType.RE) },
                    label = { Text("Chiều về (${detail.re?.stations?.size ?: 0})", fontSize = 12.sp) },
                    leadingIcon = {
                        if (state.currentDirection == DirectionType.RE) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                )
            }

            // Mode indicator / switch
            FilledTonalButton(
                onClick = onToggleViewMode,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = if (state.isMapView) Icons.Default.FormatListNumbered else Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (state.isMapView) "Danh sách" else "Bản đồ", fontSize = 12.sp)
            }
        }

        // Main Content: Default is Map View
        if (state.isMapView) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Interactive OpenStreetMap with Leaflet
                BusMapView(
                    stations = stations,
                    selectedStation = state.selectedStation,
                    onStationSelect = onStationSelect,
                    routeGeo = routeGeo,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Live ETA Card at the bottom of the map
                val selected = state.selectedStation
                if (selected != null) {
                    val selectedIndex = stations.indexOfFirst { it.objectId == selected.objectId } + 1
                    StationMapEtaCard(
                        index = if (selectedIndex > 0) selectedIndex else 1,
                        station = selected,
                        state = state,
                        onRefresh = onRefreshEta,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            // Traditional List View
            if (stations.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Không có dữ liệu điểm đón cho chiều này")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(stations, key = { index, st -> "${st.objectId}_$index" }) { index, station ->
                        StationTimelineItem(
                            index = index + 1,
                            isFirst = index == 0,
                            isLast = index == stations.size - 1,
                            station = station,
                            onClick = { onStationSelect(station) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StationMapEtaCard(
    index: Int,
    station: BusStation,
    state: BusUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Station Name & Sequence
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mã trạm: ${station.code ?: station.objectId} • Tuyến qua: ${station.fleetOver ?: "Đang cập nhật"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Làm mới ETA",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // ETA Status Section
            if (state.isLoadingEta && state.etaList.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang tính thời gian xe tới trạm...", fontSize = 13.sp)
                }
            } else if (state.etaList.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chưa có xe đang tiến về trạm này (Đếm giây & đồng bộ GPS 5s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val activeBusesCount = state.etaList.count { it.timeSeconds != null }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeBusesCount > 0) "Xe sắp tới điểm đón ($activeBusesCount xe đang tới):" else "Tuyến xe qua điểm đón (${state.etaList.size} tuyến):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (activeBusesCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Trực tiếp 1s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Scroll list if >2 buses (fits ~2 items, scrolls smoothly for more)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.etaList.forEach { eta ->
                            BusEtaCompactRow(eta = eta)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusEtaCompactRow(
    eta: BusEta,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isElectric = ElectricBusCatalog.isElectricBus(eta.fleet) || eta.licensePlate?.contains("⚡") == true
    val electricInfo = if (isElectric) ElectricBusCatalog.getInfo(eta.fleet) else null
    val hasActiveBus = eta.timeSeconds != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(1.5.dp, Color(0xFF00C853), RoundedCornerShape(8.dp))
                else Modifier
            )
            .background(
                if (isSelected) Color(0xFFE8F5E9)
                else if (hasActiveBus) {
                    if (isElectric) Color(0xFFE8F5E9).copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                }
            )
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) Color(0xFF00C853)
                        else if (hasActiveBus) {
                            if (isElectric) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFF78909C)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = eta.fleet ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if ((eta.fleet?.length ?: 0) >= 5) 10.sp else 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                if (hasActiveBus) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isElectric) {
                            Text(
                                text = "⚡ ",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Text(
                            text = "BS: ${eta.licensePlate ?: "---"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isElectric) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "🟢 Đang bám theo xe (1s) • Chạm để tắt",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    } else if (isElectric) {
                        Text(
                            text = "${electricInfo?.model ?: "VinFast EV"} • Chạm để bám theo xe 📍",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF388E3C)
                        )
                    } else {
                        Text(
                            text = "Chạm để xem đường đi xe chạy tới trạm 📍",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                } else {
                    Text(
                        text = "Chưa có xe đang tới",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tuyến qua trạm này",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (hasActiveBus) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFFC8E6C9) else Color(0xFFE8F5E9))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = eta.formattedTime(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
                Text(
                    text = "Còn ${eta.formattedDistance()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Chưa có xe",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Chờ xuất bến",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StationTimelineItem(
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    station: BusStation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Timeline indicator column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isFirst -> Color(0xFF2E7D32)
                            isLast -> Color(0xFFD32F2F)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Station Details Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (!station.fleetOver.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tuyến qua trạm: ${station.fleetOver}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xe sắp đến (ETA)", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveEtaSheetContent(
    station: BusStation,
    isLoading: Boolean,
    etaList: List<BusEta>,
    errorMessage: String?,
    lastUpdated: Long,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Mã trạm: ${station.code ?: station.objectId} • Cập nhật tự động",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (lastUpdated > 0) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            Text(
                text = "Cập nhật lúc: ${sdf.format(Date(lastUpdated))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading && etaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && etaList.isEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else if (etaList.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hiện chưa có xe buýt nào đang di chuyển tới trạm này.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hệ thống đếm giây trực tiếp và tự động đồng bộ GPS mỗi 5s.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            val activeCount = etaList.count { it.timeSeconds != null }
            Text(
                text = if (activeCount > 0) "Có $activeCount xe đang tiến về trạm:" else "Tuyến xe qua trạm (${etaList.size} tuyến):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(etaList) { eta ->
                    BusEtaCard(eta = eta)
                }
            }
        }
    }
}

@Composable
fun BusEtaCard(eta: BusEta) {
    val isElectric = ElectricBusCatalog.isElectricBus(eta.fleet) || eta.licensePlate?.contains("⚡") == true
    val electricInfo = if (isElectric) ElectricBusCatalog.getInfo(eta.fleet) else null
    val hasActiveBus = eta.timeSeconds != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasActiveBus) {
                if (isElectric) Color(0xFFE8F5E9).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasActiveBus) {
                            if (isElectric) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFF78909C)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = eta.fleet ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if ((eta.fleet?.length ?: 0) >= 5) 12.sp else 15.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (eta.fleetCode != null && eta.fleetCode.isNotEmpty()) {
                        "${eta.fleet} (${if (eta.fleetCode == "1") "Chiều về" else "Chiều đi"})"
                    } else {
                        eta.fleet ?: ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (hasActiveBus) {
                    Text(
                        text = if (isElectric) "⚡ BS: ${eta.licensePlate ?: "Chưa rõ"} • ${electricInfo?.model ?: "VinFast EV"}"
                               else "Biển số: ${eta.licensePlate ?: "Chưa rõ"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isElectric) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Chưa có xe đang tới • Tuyến qua trạm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (hasActiveBus) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = eta.formattedTime(),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Còn ${eta.formattedDistance()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Chưa có xe",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chờ xuất bến",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun AllStationsContent(
    state: BusUiState,
    onSearchChange: (String) -> Unit,
    onStationSelect: (BusStation) -> Unit,
    onClearSelectedStation: () -> Unit,
    onRefreshEta: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val heading by rememberCompassHeading()
    var mapBearing by remember { mutableFloatStateOf(0f) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var resetBearingTrigger by remember { mutableIntStateOf(0) }

    // Live Approaching Bus Tracking state for Tab 2
    var trackingBusEta by remember { mutableStateOf<BusEta?>(null) }
    var trackingBusPath by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var liveRemainingDistance by remember { mutableStateOf<Int?>(null) }
    var liveRemainingSeconds by remember { mutableStateOf<Int?>(null) }
    var isStationCardExpanded by remember(state.selectedMapStation?.objectId) { mutableStateOf(true) }

    LaunchedEffect(state.selectedMapStation) {
        trackingBusEta = null
        trackingBusPath = emptyList()
        liveRemainingDistance = null
        liveRemainingSeconds = null
        isStationCardExpanded = true
    }

    fun toggleTrackBus(bus: BusEta) {
        val isSameBus = trackingBusEta != null && (
            (!trackingBusEta?.licensePlate.isNullOrEmpty() && trackingBusEta?.licensePlate == bus.licensePlate) ||
            (trackingBusEta?.fleet == bus.fleet && trackingBusEta?.distanceMeters == bus.distanceMeters)
        )
        if (isSameBus) {
            trackingBusEta = null
            trackingBusPath = emptyList()
            liveRemainingDistance = null
            liveRemainingSeconds = null
            isStationCardExpanded = true
            return
        }

        val st = state.selectedMapStation ?: return
        val rCode = bus.fleet ?: ""
        trackingBusEta = bus
        liveRemainingDistance = bus.distanceMeters ?: 1000
        liveRemainingSeconds = bus.timeSeconds ?: 180
        isStationCardExpanded = false

        coroutineScope.launch(Dispatchers.IO) {
            val path = TransitRoutingEngine.getApproachingBusPath(
                routeCode = rCode,
                targetStation = st,
                distanceMeters = bus.distanceMeters ?: 1000
            )
            withContext(Dispatchers.Main) {
                if (trackingBusEta?.licensePlate == bus.licensePlate || trackingBusEta?.fleet == bus.fleet) {
                    trackingBusPath = path
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoadingStations && state.allStations.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Đang nạp 5,051 trạm xe buýt Hà Nội...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // Fullscreen Leaflet Canvas Map of all 5,051 stations
            AllStationsMapView(
                stations = state.allStations,
                selectedStation = state.selectedMapStation,
                onStationSelect = { station ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    trackingBusEta = null
                    trackingBusPath = emptyList()
                    liveRemainingDistance = null
                    liveRemainingSeconds = null
                    onStationSelect(station)
                },
                phoneHeading = heading,
                onMapBearingChange = { mapBearing = it },
                trackedBus = trackingBusEta,
                busApproachingPath = trackingBusPath,
                onTrackingStep = { dist, time ->
                    liveRemainingDistance = dist
                    liveRemainingSeconds = time
                },
                recenterTrigger = recenterTrigger,
                resetBearingTrigger = resetBearingTrigger,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Search Bar & Auto-complete Suggestions Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 5.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm trạm",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = state.stationSearchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (state.stationSearchQuery.isEmpty()) {
                                Text(
                                    text = "Tìm trạm dừng, tên đường, tuyến xe...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (state.stationSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onSearchChange("")
                                onClearSelectedStation()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Suggestions dropdown
            AnimatedVisibility(
                visible = state.suggestedStations.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn {
                        items(state.suggestedStations) { station ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onStationSelect(station)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = buildString {
                                        if (!station.street.isNullOrEmpty()) append(station.street)
                                        if (!station.fleetOver.isNullOrEmpty()) {
                                            if (isNotEmpty()) append(" • Tuyến: ") else append("Tuyến: ")
                                            append(station.fleetOver)
                                        }
                                    }
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            text = subtitle,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // Bottom Selected Station Floating Live ETA Card
        AnimatedVisibility(
            visible = state.selectedMapStation != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            state.selectedMapStation?.let { station ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top drag handle bar: supports vertical dragging and tapping to collapse/expand
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isStationCardExpanded = !isStationCardExpanded }
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount > 12) {
                                            isStationCardExpanded = false
                                        } else if (dragAmount < -12) {
                                            isStationCardExpanded = true
                                        }
                                    }
                                }
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                        }

                        if (!isStationCardExpanded) {
                            // Collapsed slim bar: leaves the map completely unobstructed
                            if (trackingBusEta != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { isStationCardExpanded = true }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00C853))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFE65100),
                                                    contentColor = Color.White
                                                ) {
                                                    Text(
                                                        text = "Tuyến ${trackingBusEta?.fleet ?: ""}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = trackingBusEta?.licensePlate ?: "Xe buýt",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Cách: ${liveRemainingDistance ?: trackingBusEta?.distanceMeters ?: "---"}m • ${if ((liveRemainingSeconds ?: 0) > 60) "${(liveRemainingSeconds ?: 0)/60} phút" else "${liveRemainingSeconds ?: 0} giây"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = {
                                                trackingBusEta = null
                                                trackingBusPath = emptyList()
                                                liveRemainingDistance = null
                                                liveRemainingSeconds = null
                                                isStationCardExpanded = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Dừng", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(
                                            onClick = { isStationCardExpanded = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Mở rộng",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Collapsed non-tracking station summary
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { isStationCardExpanded = true }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = station.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (state.etaList.isNotEmpty()) "${state.etaList.size} xe/tuyến • Chạm để xem" else "Chạm để xem xe sắp tới",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { isStationCardExpanded = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Mở rộng",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = onClearSelectedStation,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Đóng",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Expanded view
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = station.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    if (!station.code.isNullOrEmpty()) append("Mã: ${station.code} • ")
                                                    if (!station.street.isNullOrEmpty()) append(station.street)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { isStationCardExpanded = false },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Thu gọn",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = onRefreshEta,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Làm mới ETA",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = onClearSelectedStation,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Đóng",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (!station.fleetOver.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tuyến xe qua trạm: ${station.fleetOver}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Live ETA status & approaching buses
                                if (state.isLoadingEta && state.etaList.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Đang dò tìm xe buýt tới điểm đón...", fontSize = 13.sp)
                                    }
                                } else if (state.etaList.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Hiện chưa có xe buýt nào đang tiến về điểm đón này",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val activeBusesCount = state.etaList.count { it.timeSeconds != null }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (activeBusesCount > 0) "Xe buýt sắp tới ($activeBusesCount xe đang tới):" else "Tuyến xe qua trạm (${state.etaList.size} tuyến):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (activeBusesCount > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF4CAF50))
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Trực tiếp 1s",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Active Tracking Status Banner if bus is selected
                                    if (trackingBusEta != null) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFE8F5E9),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C853)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF00C853))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "Đang bám theo: ${trackingBusEta?.licensePlate ?: "Xe buýt"} (Tuyến ${trackingBusEta?.fleet})",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1B5E20)
                                                        )
                                                        Text(
                                                            text = "Khoảng cách: ${liveRemainingDistance ?: trackingBusEta?.distanceMeters ?: "---"}m • ${if ((liveRemainingSeconds ?: 0) > 60) "${(liveRemainingSeconds ?: 0)/60} phút" else "${liveRemainingSeconds ?: 0} giây"}",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF2E7D32)
                                                        )
                                                    }
                                                }
                                                TextButton(
                                                    onClick = {
                                                        trackingBusEta = null
                                                        trackingBusPath = emptyList()
                                                        liveRemainingDistance = null
                                                        liveRemainingSeconds = null
                                                        isStationCardExpanded = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Dừng", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Scroll list if >2 buses (fits ~2 items, scrolls smoothly for more)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 130.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            state.etaList.forEach { eta ->
                                                val isTracking = trackingBusEta != null && (
                                                    (!trackingBusEta?.licensePlate.isNullOrEmpty() && trackingBusEta?.licensePlate == eta.licensePlate) ||
                                                    (trackingBusEta?.fleet == eta.fleet && trackingBusEta?.distanceMeters == eta.distanceMeters)
                                                )
                                                val displayEta = if (isTracking && liveRemainingDistance != null) {
                                                    eta.copy(
                                                        distanceMeters = liveRemainingDistance,
                                                        timeSeconds = liveRemainingSeconds
                                                    )
                                                } else eta

                                                BusEtaCompactRow(
                                                    eta = displayEta,
                                                    isSelected = isTracking,
                                                    onClick = {
                                                        toggleTrackBus(eta)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Controls: Compass (resets map bearing) & My Location Button
        MapFloatingControls(
            mapBearing = mapBearing,
            onRecenterClick = {
                recenterTrigger++
            },
            onResetBearing = {
                resetBearingTrigger++
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = 16.dp)
        )
    }
}
