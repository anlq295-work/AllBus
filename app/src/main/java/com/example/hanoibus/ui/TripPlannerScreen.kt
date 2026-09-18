package com.example.hanoibus.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hanoibus.data.*
import kotlinx.coroutines.launch

enum class SearchTarget { NONE, ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    allStations: List<BusStation>,
    userLocation: GeoPoint?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val phoneHeading by rememberCompassHeading()
    var mapBearing by remember { mutableFloatStateOf(0f) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var resetBearingTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    var origin by remember { mutableStateOf(userLocation ?: GeoPoint(20.9573, 105.8458)) }
    var originName by remember { mutableStateOf("Vị trí của bạn") }

    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationName by remember { mutableStateOf("") }

    var searchTarget by remember { mutableStateOf(SearchTarget.NONE) }
    var originQuery by remember { mutableStateOf("") }
    var destQuery by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<List<TripPlace>>(emptyList()) }
    var tripOptions by remember { mutableStateOf<List<TripOption>>(emptyList()) }
    var selectedOption by remember { mutableStateOf<TripOption?>(null) }
    var selectedFilterChip by remember { mutableStateOf("Tất cả") }
    var showStepDetails by remember { mutableStateOf(false) }

    // Selected Station & Live Nearest Bus State
    var selectedBusStation by remember { mutableStateOf<BusStation?>(null) }
    var selectedRouteForStation by remember { mutableStateOf<String>("") }
    var nearestBusEta by remember { mutableStateOf<BusEta?>(null) }
    var approachingBuses by remember { mutableStateOf<List<BusEta>>(emptyList()) }
    var isLoadingNearestBus by remember { mutableStateOf(false) }
    var nearestBusError by remember { mutableStateOf<String?>(null) }
    var selectedStationFocus by remember { mutableStateOf<GeoPoint?>(null) }
    var isRouteSheetExpanded by remember { mutableStateOf(true) }

    // Live Approaching Bus Tracking
    var trackingBusEta by remember { mutableStateOf<BusEta?>(null) }
    var trackingBusPath by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var liveRemainingDistance by remember { mutableStateOf<Int?>(null) }
    var liveRemainingSeconds by remember { mutableStateOf<Int?>(null) }

    // Function to toggle live tracking for a bus
    fun toggleTrackBus(bus: BusEta) {
        val isSameBus = trackingBusEta != null && (
            (!trackingBusEta?.licensePlate.isNullOrEmpty() && trackingBusEta?.licensePlate == bus.licensePlate) ||
            (trackingBusEta?.fleet == bus.fleet && trackingBusEta?.distanceMeters == bus.distanceMeters)
        )
        if (isSameBus) {
            // Stop tracking
            trackingBusEta = null
            trackingBusPath = emptyList()
            liveRemainingDistance = null
            liveRemainingSeconds = null
        } else {
            // Start tracking
            trackingBusEta = bus
            liveRemainingDistance = bus.distanceMeters ?: 1000
            liveRemainingSeconds = bus.timeSeconds ?: 180
            showStepDetails = false

            val st = selectedBusStation ?: return
            val rCode = if (selectedRouteForStation.isNotEmpty()) selectedRouteForStation else (bus.fleet ?: "")

            val candidateSegment = selectedOption?.segments?.find {
                it.type == TripSegmentType.BUS &&
                (it.routeCode.equals(rCode, ignoreCase = true) || it.stations.any { s -> s.objectId == st.objectId })
            }
            val knownPts = candidateSegment?.pathPoints

            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val path = TransitRoutingEngine.getApproachingBusPath(
                    targetStation = st,
                    routeCode = rCode,
                    distanceMeters = bus.distanceMeters ?: 1000,
                    knownSegmentPoints = knownPts
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (trackingBusEta?.licensePlate == bus.licensePlate || trackingBusEta?.fleet == bus.fleet) {
                        trackingBusPath = path
                    }
                }
            }
        }
    }

    // Function to load nearest and approaching buses for a specific stop
    fun loadNearestBus(station: BusStation, routeCode: String) {
        selectedBusStation = station
        selectedRouteForStation = routeCode
        isLoadingNearestBus = true
        nearestBusEta = null
        approachingBuses = emptyList()
        nearestBusError = null
        trackingBusEta = null
        trackingBusPath = emptyList()
        liveRemainingDistance = null
        liveRemainingSeconds = null
        if (station.geo != null && station.geo.lat != 0.0) {
            selectedStationFocus = station.geo
        }

        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = TransitRoutingEngine.fetchApproachingBusesEta(
                stationId = station.objectId,
                fleetOver = station.fleetOver ?: "",
                routeCode = routeCode
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                approachingBuses = list
                nearestBusEta = list.firstOrNull()
                isLoadingNearestBus = false
                if (list.isEmpty()) {
                    nearestBusError = "Chưa có xe nào thuộc tuyến $routeCode đang di chuyển tới trạm này. Tần suất: 10-15 phút/chuyến."
                }
            }
        }
    }

    // Function to calculate and update routes
    fun calculateRoutes(fromPt: GeoPoint = origin, toPt: GeoPoint, toTitle: String? = null) {
        if (toTitle != null) {
            destination = toPt
            destinationName = toTitle
        }
        searchTarget = SearchTarget.NONE
        selectedBusStation = null
        trackingBusEta = null
        trackingBusPath = emptyList()
        liveRemainingDistance = null
        liveRemainingSeconds = null
        focusManager.clearFocus()
        keyboardController?.hide()

        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val options = TransitRoutingEngine.planTrip(fromPt, toPt, allStations)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                tripOptions = options
                selectedOption = options.firstOrNull()
                selectedFilterChip = "Tất cả"
            }
        }
    }

    // When userLocation updates and origin was default, update origin
    LaunchedEffect(userLocation) {
        if (userLocation != null && originName == "Vị trí của bạn") {
            origin = userLocation
            val dest = destination
            if (dest != null) {
                calculateRoutes(origin, dest)
            }
        }
    }

    val activeQuery = when (searchTarget) {
        SearchTarget.ORIGIN -> originQuery
        SearchTarget.DESTINATION -> destQuery
        SearchTarget.NONE -> ""
    }

    // Local search whenever activeQuery or searchTarget changes (debounced & on Default dispatcher)
    LaunchedEffect(activeQuery, searchTarget) {
        if (searchTarget == SearchTarget.NONE) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        if (activeQuery.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            val localResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                TransitRoutingEngine.searchPlaces(activeQuery, allStations)
            }
            searchResults = localResults
        } else {
            searchResults = TransitRoutingEngine.popularLandmarks.take(6)
        }
    }

    // Auto enrich selectedOption with road geometry and all intermediate bus stations
    LaunchedEffect(selectedOption?.id) {
        val opt = selectedOption ?: return@LaunchedEffect
        if (opt.segments.any { it.type == TripSegmentType.BUS && it.stations.isEmpty() }) {
            val enriched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TransitRoutingEngine.enrichTripOption(opt)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                selectedOption = enriched
                tripOptions = tripOptions.map { if (it.id == opt.id) enriched else it }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Fullscreen Trip Map View
        TripPlannerMapView(
            origin = origin,
            destination = destination,
            selectedOption = selectedOption,
            phoneHeading = phoneHeading,
            trackedBus = trackingBusEta,
            busApproachingPath = trackingBusPath,
            onTrackingStep = { dist, time ->
                liveRemainingDistance = dist
                liveRemainingSeconds = time
            },
            onMapClick = { lat, lng ->
                if (selectedBusStation != null) {
                    selectedBusStation = null
                    trackingBusEta = null
                    trackingBusPath = emptyList()
                    liveRemainingDistance = null
                    liveRemainingSeconds = null
                }
                if (searchTarget != SearchTarget.NONE) {
                    searchTarget = SearchTarget.NONE
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
            onMapBearingChange = { b -> mapBearing = b },
            onUserLocationFound = { lat, lng ->
                if (originName == "Vị trí của bạn") {
                    origin = GeoPoint(lat, lng)
                }
            },
            onBusStationSelect = { sId, sName, rCode, lat, lng, fo, street ->
                val st = BusStation(
                    objectId = sId,
                    name = sName,
                    street = street,
                    fleetOver = fo,
                    geo = GeoPoint(lat, lng)
                )
                loadNearestBus(st, rCode)
            },
            selectedStationFocus = selectedStationFocus,
            recenterTrigger = recenterTrigger,
            resetBearingTrigger = resetBearingTrigger,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Compass & Recenter Controls (Top Right under search)
        MapFloatingControls(
            mapBearing = mapBearing,
            onRecenterClick = { recenterTrigger++ },
            onResetBearing = { resetBearingTrigger++ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp)
        )

        // 3. Compact Search Bar (Top) with Origin & Destination
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Origin & Destination rows
                    Column(modifier = Modifier.weight(1f)) {
                        // Origin Row (Searchable)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Điểm xuất phát",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = if (searchTarget == SearchTarget.ORIGIN) originQuery else originName,
                                onValueChange = {
                                    originQuery = it
                                    searchTarget = SearchTarget.ORIGIN
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (searchTarget == SearchTarget.ORIGIN) FontWeight.Normal else FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            searchTarget = SearchTarget.ORIGIN
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    if (searchTarget == SearchTarget.ORIGIN && originQuery.isEmpty()) {
                                        Text(
                                            text = "Nhập điểm xuất phát...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (originName != "Vị trí của bạn" || originQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        origin = userLocation ?: GeoPoint(20.9573, 105.8458)
                                        originName = "Vị trí của bạn"
                                        originQuery = ""
                                        searchTarget = SearchTarget.NONE
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        destination?.let { dest ->
                                            calculateRoutes(origin, dest)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Đặt lại vị trí của bạn", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Destination Row (Searchable)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Điểm đến",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = if (searchTarget == SearchTarget.DESTINATION) destQuery else destinationName,
                                onValueChange = {
                                    destQuery = it
                                    searchTarget = SearchTarget.DESTINATION
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (searchTarget == SearchTarget.DESTINATION) FontWeight.Normal else FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            searchTarget = SearchTarget.DESTINATION
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    if (destinationName.isEmpty() && (searchTarget != SearchTarget.DESTINATION || destQuery.isEmpty())) {
                                        Text(
                                            text = "Tìm điểm đến, tên đường, trường...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (destination != null || destQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        destination = null
                                        destinationName = ""
                                        destQuery = ""
                                        tripOptions = emptyList()
                                        selectedOption = null
                                        selectedBusStation = null
                                        trackingBusEta = null
                                        trackingBusPath = emptyList()
                                        liveRemainingDistance = null
                                        liveRemainingSeconds = null
                                        searchTarget = SearchTarget.NONE
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa điểm đến", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Swap Button (Right side)
                    IconButton(
                        onClick = {
                            val dest = destination
                            if (dest != null) {
                                val prevOrigin = origin
                                val prevOriginName = originName
                                origin = dest
                                originName = destinationName
                                destination = prevOrigin
                                destinationName = prevOriginName
                                originQuery = ""
                                destQuery = ""
                                searchTarget = SearchTarget.NONE
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                calculateRoutes(origin, prevOrigin)
                            }
                        },
                        enabled = destination != null,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Đổi chiều đi",
                            tint = if (destination != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Quick Landmark suggestions chips (shown when no destination is picked and not actively searching)
            if (destination == null && searchTarget == SearchTarget.NONE) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TransitRoutingEngine.popularLandmarks.take(6)) { place ->
                        SuggestionChip(
                            onClick = {
                                val dest = GeoPoint(place.lat, place.lng)
                                destination = dest
                                destinationName = place.name
                                searchTarget = SearchTarget.NONE
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                calculateRoutes(origin, dest)
                            },
                            label = { Text(place.name, fontSize = 12.sp, maxLines = 1) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        )
                    }
                }
            }

            // Search Results Dropdown List (For both Origin and Destination)
            if (searchTarget != SearchTarget.NONE) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 290.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        // If searching ORIGIN, show "Vị trí của bạn" as the first choice
                        if (searchTarget == SearchTarget.ORIGIN) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            origin = userLocation ?: GeoPoint(20.9573, 105.8458)
                                            originName = "Vị trí của bạn"
                                            originQuery = ""
                                            searchTarget = SearchTarget.NONE
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            destination?.let { dest ->
                                                calculateRoutes(origin, dest)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.MyLocation,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Vị trí hiện tại của bạn",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Sử dụng định vị GPS hiện tại",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        items(searchResults) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (searchTarget == SearchTarget.ORIGIN) {
                                            val orig = GeoPoint(place.lat, place.lng)
                                            origin = orig
                                            originName = place.name
                                            originQuery = ""
                                            searchTarget = SearchTarget.NONE
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            destination?.let { dest ->
                                                calculateRoutes(orig, dest)
                                            }
                                        } else {
                                            val dest = GeoPoint(place.lat, place.lng)
                                            destination = dest
                                            destinationName = place.name
                                            destQuery = ""
                                            searchTarget = SearchTarget.NONE
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            calculateRoutes(origin, dest)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (place.isBusStation) Icons.Default.DirectionsBus else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (place.isBusStation) MaterialTheme.colorScheme.primary else Color(0xFFE53935),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (place.address.isNotEmpty()) {
                                        Text(
                                            text = place.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // 4. Floating Approaching Bus Card (When a station is clicked on map or list)
        AnimatedVisibility(
            visible = selectedBusStation != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (selectedBusStation != null) {
                StationNearestBusCard(
                    station = selectedBusStation!!,
                    routeCode = selectedRouteForStation,
                    eta = nearestBusEta,
                    approachingBuses = approachingBuses,
                    isLoading = isLoadingNearestBus,
                    error = nearestBusError,
                    trackingBus = trackingBusEta,
                    liveDistance = liveRemainingDistance,
                    liveSeconds = liveRemainingSeconds,
                    onBusClick = { bus -> toggleTrackBus(bus) },
                    onRefresh = { 
                        trackingBusEta = null
                        trackingBusPath = emptyList()
                        liveRemainingDistance = null
                        liveRemainingSeconds = null
                        loadNearestBus(selectedBusStation!!, selectedRouteForStation) 
                    },
                    onClose = { 
                        selectedBusStation = null
                        trackingBusEta = null
                        trackingBusPath = emptyList()
                        liveRemainingDistance = null
                        liveRemainingSeconds = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // 5. Trip Options Sliding Bottom Sheet (Hidden when a bus station is selected)
        AnimatedVisibility(
            visible = destination != null && tripOptions.isNotEmpty() && selectedBusStation == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val filteredOptions = remember(tripOptions, selectedFilterChip) {
                when (selectedFilterChip) {
                    "Đi 1 tuyến" -> tripOptions.filter { it.busTransferCount == 0 }
                    "Nhanh nhất" -> tripOptions.sortedBy { it.totalDurationMinutes }
                    "Đi bộ ít" -> tripOptions.sortedBy { it.walkDistanceMeters }
                    else -> tripOptions
                }.ifEmpty { tripOptions }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    // Top drag handle & toggle bar (interactive slide up / down)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isRouteSheetExpanded = !isRouteSheetExpanded }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 15) {
                                        isRouteSheetExpanded = false
                                    } else if (dragAmount < -15) {
                                        isRouteSheetExpanded = true
                                    }
                                }
                            }
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lộ trình xe buýt (${filteredOptions.size} phương án)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isRouteSheetExpanded && selectedOption != null) {
                                    Text(
                                        text = "${selectedOption!!.totalDurationMinutes} phút • Tuyến ${selectedOption!!.busRoutes.joinToString(", ")} • Vuốt lên để mở rộng",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isRouteSheetExpanded = !isRouteSheetExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRouteSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isRouteSheetExpanded) "Thu gọn" else "Mở rộng",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Expanded Content (Filter Chips + Carousel)
                    if (isRouteSheetExpanded) {
                        // Filter Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            val filterList = listOf("Tất cả", "Đi 1 tuyến", "Nhanh nhất", "Đi bộ ít")
                            items(filterList) { chip ->
                                FilterChip(
                                    selected = selectedFilterChip == chip,
                                    onClick = { selectedFilterChip = chip },
                                    label = { Text(chip, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Options Horizontal Cards Carousel / Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            items(filteredOptions) { option ->
                                val isSelected = selectedOption?.id == option.id
                                Surface(
                                    modifier = Modifier
                                        .width(285.dp)
                                        .clickable { selectedOption = option },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${option.totalDurationMinutes} phút",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${String.format("%,d", option.totalFareVnd)} đ",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Sequence of pills: Walk -> Bus -> Walk
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            option.segments.forEachIndexed { idx, seg ->
                                                if (seg.type == TripSegmentType.WALK) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF1E88E5).copy(alpha = 0.15f))
                                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("🚶${seg.distanceMeters}m", fontSize = 10.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Medium)
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFFE65100))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("🚌 ${seg.routeCode}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                if (idx < option.segments.size - 1) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Gray)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (option.busTransferCount == 0) "Trực tiếp (0 đổi xe)" else "1 lần chuyển tuyến",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            TextButton(
                                                onClick = {
                                                    selectedOption = option
                                                    showStepDetails = true
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Xem chi tiết >", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        if (destination != null && tripOptions.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Không tìm thấy tuyến xe buýt kết nối trực tiếp", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Bạn hãy thử chọn địa điểm khác gần trục đường chính hơn.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 6. Step-by-Step Guidance Bottom Sheet (Enriched with segment labels and intermediate stops)
        if (showStepDetails && selectedOption != null) {
            ModalBottomSheet(
                onDismissRequest = { showStepDetails = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Hướng dẫn chi tiết lộ trình",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedOption!!.totalDurationMinutes} phút • Tổng cước ${String.format("%,d", selectedOption!!.totalFareVnd)} đ • Đi bộ ~${selectedOption!!.walkDistanceMeters}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(selectedOption!!.segments) { seg ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (seg.type == TripSegmentType.WALK)
                                        Color(0xFF1E88E5).copy(alpha = 0.06f)
                                    else
                                        Color(0xFFE65100).copy(alpha = 0.06f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // 1. Clear Segment Label Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (seg.type == TripSegmentType.WALK) Color(0xFF1976D2) else Color(0xFFE65100),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (seg.type == TripSegmentType.WALK)
                                                    "🚶 ĐOẠN ĐI BỘ • ${seg.distanceMeters}m"
                                                else
                                                    "🚌 ĐOẠN XE BUÝT: TUYẾN ${seg.routeCode} • ${seg.stopsCount.coerceAtLeast(seg.stations.size)} TRẠM",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "~${seg.durationMinutes} phút",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 2. Segment Main Instruction
                                    Text(
                                        text = seg.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = seg.instruction,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // 3. Intermediate Bus Stops List (Expandable / clickable to view nearest bus)
                                    if (seg.type == TripSegmentType.BUS && seg.stations.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Danh sách ${seg.stations.size} điểm dừng (Chạm để xem xe gần nhất):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            seg.stations.forEachIndexed { sIdx, st ->
                                                val isThisStationSelected = selectedBusStation?.objectId == st.objectId

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isThisStationSelected)
                                                        Color(0xFFE65100).copy(alpha = 0.15f)
                                                    else
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                    border = if (isThisStationSelected)
                                                        androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE65100))
                                                    else null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            loadNearestBus(st, seg.routeCode ?: "")
                                                        }
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            if (sIdx == 0) Color(0xFF2E7D32)
                                                                            else if (sIdx == seg.stations.size - 1) Color(0xFFC62828)
                                                                            else Color(0xFF757575)
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "${sIdx + 1}",
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                                    Text(
                                                                        text = st.name,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = if (isThisStationSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    if (!st.street.isNullOrEmpty()) {
                                                                        Text(
                                                                            text = st.street,
                                                                            fontSize = 10.sp,
                                                                            color = MaterialTheme.colorScheme.outline,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Text(
                                                                text = if (isThisStationSelected) "Đang xem" else "Xem xe >",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFFE65100),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        // Inline live ETA display when clicked
                                                        if (isThisStationSelected) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            if (isLoadingNearestBus) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("Đang tra cứu xe gần nhất...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                                }
                                                            } else if (nearestBusEta != null) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFFE8F5E9),
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clickable { showStepDetails = false }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Column {
                                                                            Text(
                                                                                text = "🚌 ${nearestBusEta!!.licensePlate ?: "Xe buýt"} • ${nearestBusEta!!.formattedDistance()}",
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.SemiBold,
                                                                                color = Color(0xFF1B5E20)
                                                                            )
                                                                            Text(
                                                                                text = "Đến trong ~${nearestBusEta!!.formattedTime()}",
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = Color(0xFF2E7D32)
                                                                            )
                                                                        }
                                                                        Surface(
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            color = Color(0xFF2E7D32)
                                                                        ) {
                                                                            Text(
                                                                                text = "Xem bản đồ >",
                                                                                color = Color.White,
                                                                                fontSize = 10.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = nearestBusError ?: "Chưa có xe đang tới trạm này",
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.outline
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
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
}

private fun formatRemainingDistance(meters: Int?): String {
    if (meters == null) return "Đang tính..."
    return if (meters >= 1000) {
        String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "$meters m"
    }
}

private fun formatRemainingTime(seconds: Int?): String {
    if (seconds == null) return "Đang tính..."
    if (seconds <= 0) return "Đang đến trạm!"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) {
        "${mins}p ${secs}s"
    } else {
        "${secs}s"
    }
}

@Composable
fun StationNearestBusCard(
    station: BusStation,
    routeCode: String,
    eta: BusEta?,
    approachingBuses: List<BusEta> = emptyList(),
    isLoading: Boolean,
    error: String?,
    trackingBus: BusEta? = null,
    liveDistance: Int? = null,
    liveSeconds: Int? = null,
    onBusClick: (BusEta) -> Unit = {},
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember(station.objectId, trackingBus != null) { mutableStateOf(trackingBus == null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Drag handle pill at the top: vertical dragging & tap to toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 12) {
                                isExpanded = false
                            } else if (dragAmount < -12) {
                                isExpanded = true
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

            if (!isExpanded) {
                // Collapsed slim bar
                if (trackingBus != null) {
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
                                .clickable { isExpanded = true }
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
                                            text = "Tuyến $routeCode",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = trackingBus.licensePlate ?: "Xe buýt",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Cách: ${formatRemainingDistance(liveDistance ?: trackingBus.distanceMeters)} • ${formatRemainingTime(liveSeconds ?: trackingBus.timeSeconds)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onBusClick(trackingBus) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Dừng", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { isExpanded = true },
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
                                .clickable { isExpanded = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🟢", fontSize = 12.sp)
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
                                    text = "Tuyến $routeCode • Chạm để xem xe sắp đến",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Mở rộng",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onClose,
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
                // Expanded View
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🟢", fontSize = 16.sp)
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
                                    text = station.street ?: "Trạm xe buýt Hà Nội",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Thu gọn", modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE65100),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "Tuyến $routeCode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang tìm xe sắp đến...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (TicketPreferences.isQuickTicketEnabled(context)) {
                                    Surface(
                                        onClick = { TicketAppHelper.openTicketApp(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1B5E20).copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Quét vé QR",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!isLoading) {
                        val busesToShow = if (approachingBuses.isNotEmpty()) approachingBuses else listOfNotNull(eta)
                        if (busesToShow.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                busesToShow.take(3).forEachIndexed { idx, item ->
                                    val isTracking = trackingBus != null && (
                                        (trackingBus.licensePlate != null && trackingBus.licensePlate == item.licensePlate) ||
                                        (trackingBus.licensePlate == null && trackingBus == item)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isTracking) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        border = if (isTracking) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00C853)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBusClick(item) }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            if (isTracking) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF00C853))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Đang theo dõi trực tiếp (cập nhật 1s) • Bấm để tắt",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "🚌 Biển số: ${item.licensePlate ?: "Xe Transerco"}" +
                                                            if (busesToShow.size > 1) " (Xe ${idx + 1})" else "",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (isTracking && liveDistance != null) {
                                                            "Khoảng cách: ${formatRemainingDistance(liveDistance)}"
                                                        } else {
                                                            "Khoảng cách: ${item.formattedDistance()}"
                                                        } + if (item.speed != null && item.speed > 0) " • ${item.speed.toInt()} km/h" else "",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                    if (!isTracking) {
                                                        Text(
                                                            text = "Chạm để xem đường đi xe chạy đến trạm 📍",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = if (isTracking && liveSeconds != null) {
                                                            formatRemainingTime(liveSeconds)
                                                        } else {
                                                            item.formattedTime()
                                                        },
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                    Text(
                                                        text = "Đến bến",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error ?: "Chưa có xe nào thuộc tuyến $routeCode đang chạy tới trạm này.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                }
            }
        }
    }
}
}
