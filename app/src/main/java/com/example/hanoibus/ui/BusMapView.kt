package com.example.hanoibus.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hanoibus.data.BusStation
import com.google.gson.Gson

class MapWebInterface(
    private val onStationClickCallback: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onStationClick(stationId: Long) {
        mainHandler.post {
            onStationClickCallback(stationId)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusMapView(
    stations: List<BusStation>,
    selectedStation: BusStation?,
    onStationSelect: (BusStation) -> Unit,
    routeGeo: List<com.example.hanoibus.data.GeoPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val validStations = remember(stations) {
        stations.filter { it.geo != null && it.geo.lat != 0.0 && it.geo.lng != 0.0 }
    }

    val currentOnStationSelect by rememberUpdatedState(onStationSelect)
    val currentValidStations by rememberUpdatedState(validStations)
    val webInterface = remember {
        MapWebInterface { id ->
            val station = currentValidStations.find { it.objectId == id }
            if (station != null) {
                currentOnStationSelect(station)
            }
        }
    }

    val stationsJson = remember(validStations) {
        val mapped = validStations.mapIndexed { index, s ->
            mapOf(
                "id" to s.objectId,
                "name" to s.name,
                "lat" to (s.geo?.lat ?: 0.0),
                "lng" to (s.geo?.lng ?: 0.0),
                "index" to (index + 1),
                "fleetOver" to (s.fleetOver ?: "")
            )
        }
        Gson().toJson(mapped)
    }

    val officialRouteCoords = remember(routeGeo) {
        if (routeGeo.isNotEmpty()) {
            routeGeo.map { listOf(it.lat, it.lng) }
        } else {
            null
        }
    }
    val routeCoordsJson = remember(officialRouteCoords) {
        if (officialRouteCoords != null) Gson().toJson(officialRouteCoords) else "null"
    }

    val selectedId = selectedStation?.objectId ?: (validStations.firstOrNull()?.objectId ?: 0L)

    var webViewRef: WebView? = null
    var cachedRoadPath by remember(validStations, routeGeo) { 
        mutableStateOf<List<List<Double>>?>(officialRouteCoords) 
    }

    LaunchedEffect(validStations, routeGeo) {
        if (officialRouteCoords != null && officialRouteCoords.isNotEmpty()) {
            cachedRoadPath = officialRouteCoords
            val json = Gson().toJson(officialRouteCoords)
            webViewRef?.evaluateJavascript("if (typeof updateRoadPath === 'function') { updateRoadPath($json); }", null)
        } else if (validStations.size >= 2) {
            val roadCoords = com.example.hanoibus.data.RoadRoutingService.fetchRoadRoute(validStations)
            if (roadCoords != null && roadCoords.isNotEmpty()) {
                cachedRoadPath = roadCoords
                val json = Gson().toJson(roadCoords)
                webViewRef?.evaluateJavascript("if (typeof updateRoadPath === 'function') { updateRoadPath($json); }", null)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef = this
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                addJavascriptInterface(webInterface, "AndroidBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("WebMap", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.postDelayed({
                            view.evaluateJavascript("renderStations($stationsJson, $selectedId, $routeCoordsJson);", null)
                            cachedRoadPath?.let { path ->
                                if (officialRouteCoords == null) {
                                    val json = Gson().toJson(path)
                                    view.evaluateJavascript("if (typeof updateRoadPath === 'function') { updateRoadPath($json); }", null)
                                }
                            }
                        }, 150)
                    }
                }

                loadDataWithBaseURL(
                    "file:///android_asset/",
                    generateMapHtml(),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            webViewRef = webView
            webView.evaluateJavascript("if (typeof renderStations === 'function') { renderStations($stationsJson, $selectedId, $routeCoordsJson); }", null)
            cachedRoadPath?.let { path ->
                if (officialRouteCoords == null) {
                    val json = Gson().toJson(path)
                    webView.evaluateJavascript("if (typeof updateRoadPath === 'function') { updateRoadPath($json); }", null)
                }
            }
        }
    )

    LaunchedEffect(selectedId, stationsJson, routeCoordsJson) {
        webViewRef?.evaluateJavascript("if (typeof renderStations === 'function') { renderStations($stationsJson, $selectedId, $routeCoordsJson); }", null)
        cachedRoadPath?.let { path ->
            if (officialRouteCoords == null) {
                val json = Gson().toJson(path)
                webViewRef?.evaluateJavascript("if (typeof updateRoadPath === 'function') { updateRoadPath($json); }", null)
            }
        }
    }
}

private fun generateMapHtml(): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="leaflet.css" />
    <script src="leaflet.js"></script>
    <style>
        * { box-sizing: border-box; }
        html, body {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            overflow: hidden;
            background-color: #E8ECEF;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        }
        #map {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            width: 100%;
            height: 100%;
            background-color: #E8ECEF;
        }
        .station-badge {
            background-color: #1976D2;
            color: #FFFFFF;
            width: 30px;
            height: 30px;
            border-radius: 50%;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            border: 2px solid #FFFFFF;
            box-shadow: 0 2px 5px rgba(0,0,0,0.35);
            transition: transform 0.2s ease;
        }
        .station-badge svg {
            width: 12px;
            height: 12px;
            fill: #FFFFFF;
            margin-bottom: -1px;
        }
        .station-badge .station-idx {
            font-weight: 800;
            font-size: 9px;
            line-height: 9px;
        }
        .station-badge.active {
            background-color: #E53935 !important;
            transform: scale(1.35);
            border: 2.5px solid #FFCDD2;
            box-shadow: 0 0 10px rgba(229,57,53,0.8);
            z-index: 9999 !important;
        }
        .first-station {
            background-color: #2E7D32 !important;
        }
        .last-station {
            background-color: #D32F2F !important;
        }
        .leaflet-popup-content-wrapper {
            border-radius: 12px;
            box-shadow: 0 3px 14px rgba(0,0,0,0.25);
            padding: 4px;
        }
        .popup-title {
            font-weight: bold;
            font-size: 13px;
            color: #1A1A1A;
            margin-bottom: 2px;
        }
        .popup-subtitle {
            font-size: 11px;
            color: #666666;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = null;
        var markersLayer = null;
        var polylineLayer = null;
        var currentStations = [];
        var markersById = {};
        var currentSelectedId = 0;

        function initMap() {
            if (map) return;
            try {
                map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([21.0285, 105.8542], 12);

                L.control.zoom({ position: 'topright' }).addTo(map);

                // Cleartext HTTP tiles compatible with Android network_security_config
                L.tileLayer('http://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                    maxZoom: 19
                }).addTo(map);

                markersLayer = L.layerGroup().addTo(map);

                // Locate user
                try {
                    map.locate({ setView: false, maxZoom: 15, enableHighAccuracy: true });
                    var userMarker = null;
                    map.on('locationfound', function(e) {
                        if (!userMarker) {
                            userMarker = L.circleMarker(e.latlng, {
                                radius: 8,
                                fillColor: '#2979FF',
                                color: '#FFFFFF',
                                weight: 3,
                                opacity: 1,
                                fillOpacity: 0.95
                            }).addTo(map);
                            userMarker.bindPopup('<b>Vị trí của bạn</b>');
                        } else {
                            userMarker.setLatLng(e.latlng);
                        }
                    });
                } catch(err) {}

                map.on('zoom', updatePolylineWeight);
                map.on('zoomend', updatePolylineWeight);

                console.log("Map initialized!");
            } catch (e) {
                console.error("Error initMap:", e);
            }
        }

        function renderStations(stations, selectedId, roadCoords) {
            initMap();
            if (!map) return;

            currentStations = stations || [];
            currentSelectedId = selectedId || 0;
            markersLayer.clearLayers();
            markersById = {};

            if (polylineLayer) {
                map.removeLayer(polylineLayer);
                polylineLayer = null;
            }

            if (currentStations.length === 0) return;

            var latlngs = [];
            var total = currentStations.length;

            var busSvg = '<svg viewBox="0 0 24 24"><path d="M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6H6V6h12v5z"/></svg>';

            currentStations.forEach(function(s, idx) {
                var pos = [s.lat, s.lng];
                latlngs.push(pos);

                var isSelected = (s.id === currentSelectedId);
                var isFirst = (idx === 0);
                var isLast = (idx === total - 1);

                var badgeClass = 'station-badge';
                if (isSelected) badgeClass += ' active';
                else if (isFirst) badgeClass += ' first-station';
                else if (isLast) badgeClass += ' last-station';

                var icon = L.divIcon({
                    className: 'custom-pin',
                    html: '<div id="pin-' + s.id + '" class="' + badgeClass + '">' + busSvg + '<span class="station-idx">' + s.index + '</span></div>',
                    iconSize: [30, 30],
                    iconAnchor: [15, 15]
                });

                var marker = L.marker(pos, { icon: icon });
                marker.on('click', function() {
                    selectStation(s.id);
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onStationClick(s.id);
                    }
                });

                marker.bindPopup(
                    '<div class="popup-title">' + s.index + '. ' + s.name + '</div>' +
                    '<div class="popup-subtitle">Tuyến qua trạm: ' + (s.fleetOver || 'Không có') + '</div>'
                );

                markersLayer.addLayer(marker);
                markersById[s.id] = { marker: marker, lat: s.lat, lng: s.lng, index: s.index, isFirst: isFirst, isLast: isLast };
            });

            var polyCoords = (roadCoords && roadCoords.length > 0) ? roadCoords : latlngs;

            if (polyCoords.length > 0) {
                polylineLayer = L.polyline(polyCoords, {
                    color: '#1E88E5',
                    weight: 5,
                    opacity: 0.85,
                    lineJoin: 'round'
                }).addTo(map);

                function fit() {
                    map.invalidateSize();
                    var sz = map.getSize();
                    console.log("Size in fit:", sz.x, sz.y);
                    if (sz.x > 0 && sz.y > 0) {
                        map.fitBounds(polylineLayer.getBounds(), { padding: [40, 40] });
                    }
                }
                setTimeout(fit, 100);
                setTimeout(fit, 400);
            }

            if (currentSelectedId && markersById[currentSelectedId]) {
                selectStation(currentSelectedId);
            }
        }

        function selectStation(id) {
            currentSelectedId = id;
            for (var stationId in markersById) {
                var el = document.getElementById('pin-' + stationId);
                if (el) {
                    var data = markersById[stationId];
                    var cls = 'station-badge';
                    if (parseInt(stationId) === id) {
                        cls += ' active';
                    } else if (data.isFirst) {
                        cls += ' first-station';
                    } else if (data.isLast) {
                        cls += ' last-station';
                    }
                    el.className = cls;
                }
            }

            if (markersById[id] && map) {
                map.panTo([markersById[id].lat, markersById[id].lng], { animate: true });
                markersById[id].marker.openPopup();
            }
        }

        function getBusLineWeight(z) {
            if (z <= 11) return 3;
            if (z <= 13) return 5;
            if (z <= 15) return 8;
            if (z <= 17) return 12;
            return 16;
        }

        function updatePolylineWeight() {
            if (polylineLayer && map) {
                var z = map.getZoom();
                polylineLayer.setStyle({ weight: getBusLineWeight(z) });
            }
        }

        function updateRoadPath(roadCoords) {
            if (roadCoords && roadCoords.length > 0) {
                if (polylineLayer) {
                    polylineLayer.setLatLngs(roadCoords);
                    updatePolylineWeight();
                } else if (map) {
                    var curW = getBusLineWeight(map.getZoom());
                    polylineLayer = L.polyline(roadCoords, {
                        color: '#1E88E5',
                        weight: curW,
                        opacity: 0.85,
                        lineJoin: 'round'
                    }).addTo(map);
                }
            }
        }

        window.addEventListener('load', function() {
            initMap();
        });
    </script>
</body>
</html>
    """.trimIndent()
}
