package com.example.hanoibus.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hanoibus.data.GeoPoint
import com.example.hanoibus.data.TripOption
import com.example.hanoibus.data.BusEta
import com.google.gson.Gson

class TripPlannerWebInterface(
    private val onMapClickCallback: (Double, Double) -> Unit,
    private val onMapRotateCallback: (Float) -> Unit,
    private val onUserLocationFoundCallback: (Double, Double) -> Unit,
    private val onBusStationSelectCallback: (Long, String, String, Double, Double, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    private val onBusTrackingStepCallback: (Int, Int) -> Unit = { _, _ -> }
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapClick(lat: Double, lng: Double) {
        mainHandler.post {
            onMapClickCallback(lat, lng)
        }
    }

    @JavascriptInterface
    fun onMapRotate(bearing: Float) {
        mainHandler.post {
            onMapRotateCallback(bearing)
        }
    }

    @JavascriptInterface
    fun onUserLocation(lat: Double, lng: Double) {
        mainHandler.post {
            onUserLocationFoundCallback(lat, lng)
        }
    }

    @JavascriptInterface
    fun onBusStationSelect(
        stationId: Long,
        stationName: String,
        routeCode: String,
        lat: Double,
        lng: Double,
        fleetOver: String,
        street: String
    ) {
        mainHandler.post {
            onBusStationSelectCallback(stationId, stationName, routeCode, lat, lng, fleetOver, street)
        }
    }

    @JavascriptInterface
    fun onBusTrackingStep(distanceMeters: Int, timeSeconds: Int) {
        mainHandler.post {
            onBusTrackingStepCallback(distanceMeters, timeSeconds)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TripPlannerMapView(
    origin: GeoPoint?,
    destination: GeoPoint?,
    selectedOption: TripOption?,
    phoneHeading: Float = 0f,
    onMapClick: (Double, Double) -> Unit,
    onMapBearingChange: (Float) -> Unit,
    onUserLocationFound: (Double, Double) -> Unit,
    onBusStationSelect: (Long, String, String, Double, Double, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    selectedStationFocus: GeoPoint? = null,
    trackedBus: BusEta? = null,
    busApproachingPath: List<GeoPoint>? = null,
    onTrackingStep: (Int, Int) -> Unit = { _, _ -> },
    recenterTrigger: Int = 0,
    resetBearingTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapBearingChange by rememberUpdatedState(onMapBearingChange)
    val currentOnUserLocationFound by rememberUpdatedState(onUserLocationFound)
    val currentOnBusStationSelect by rememberUpdatedState(onBusStationSelect)
    val currentOnTrackingStep by rememberUpdatedState(onTrackingStep)

    val webInterface = remember {
        TripPlannerWebInterface(
            onMapClickCallback = { lat, lng -> currentOnMapClick(lat, lng) },
            onMapRotateCallback = { bearing -> currentOnMapBearingChange(bearing) },
            onUserLocationFoundCallback = { lat, lng -> currentOnUserLocationFound(lat, lng) },
            onBusStationSelectCallback = { sId, sName, rCode, lat, lng, fo, street ->
                currentOnBusStationSelect(sId, sName, rCode, lat, lng, fo, street)
            },
            onBusTrackingStepCallback = { dist, time ->
                currentOnTrackingStep(dist, time)
            }
        )
    }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef.value = this
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                settings.allowFileAccess = true
                settings.allowContentAccess = true
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
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        updateRouteOnWeb(view, origin, destination, selectedOption)
                    }
                }

                val html = generateTripPlannerHtml()
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webViewRef.value = webView
        }
    )

    LaunchedEffect(origin, destination, selectedOption) {
        updateRouteOnWeb(webViewRef.value, origin, destination, selectedOption)
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            webViewRef.value?.evaluateJavascript("if (typeof recenterToUser === 'function') { recenterToUser(); }", null)
        }
    }

    LaunchedEffect(phoneHeading) {
        webViewRef.value?.evaluateJavascript("if (typeof updateHeading === 'function') { updateHeading($phoneHeading); }", null)
    }

    LaunchedEffect(resetBearingTrigger) {
        if (resetBearingTrigger > 0) {
            webViewRef.value?.evaluateJavascript("if (typeof resetBearing === 'function') { resetBearing(); }", null)
        }
    }

    LaunchedEffect(selectedStationFocus) {
        if (selectedStationFocus != null) {
            webViewRef.value?.evaluateJavascript("if (typeof focusStation === 'function') { focusStation(${selectedStationFocus.lat}, ${selectedStationFocus.lng}); }", null)
        }
    }

    LaunchedEffect(trackedBus, busApproachingPath) {
        if (trackedBus != null && !busApproachingPath.isNullOrEmpty()) {
            val busJson = Gson().toJson(trackedBus)
            val pathJson = Gson().toJson(busApproachingPath)
            webViewRef.value?.evaluateJavascript("if (typeof startBusTracking === 'function') { startBusTracking($busJson, $pathJson); }", null)
        } else {
            webViewRef.value?.evaluateJavascript("if (typeof stopBusTracking === 'function') { stopBusTracking(); }", null)
        }
    }
}

private fun updateRouteOnWeb(
    webView: WebView?,
    origin: GeoPoint?,
    destination: GeoPoint?,
    selectedOption: TripOption?
) {
    webView ?: return
    val origJson = if (origin != null) Gson().toJson(origin) else "null"
    val destJson = if (destination != null) Gson().toJson(destination) else "null"
    val optionJson = if (selectedOption != null) Gson().toJson(selectedOption) else "null"
    val js = "if (typeof renderTripRoute === 'function') { renderTripRoute($origJson, $destJson, $optionJson); }"
    webView.evaluateJavascript(js, null)
}

private fun generateTripPlannerHtml(): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="leaflet.css" />
    <script src="leaflet.js"></script>
    <script src="leaflet-rotate.js"></script>
    <style>
        html, body, #map {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            background: #E8ECEF;
        }
        @keyframes busGlow {
            0% { transform: scale(0.85); opacity: 0.85; }
            50% { transform: scale(1.4); opacity: 0.2; }
            100% { transform: scale(0.85); opacity: 0.85; }
        }
        .tracked-bus-icon {
            background: transparent !important;
            border: none !important;
            transition: transform 0.9s linear;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = L.map('map', {
            zoomControl: false,
            rotate: true,
            touchRotate: true,
            rotateControl: false,
            bearing: 0,
            zoomSnap: 0.1
        }).setView([21.0285, 105.8542], 14.3);

        L.control.zoom({ position: 'bottomright' }).addTo(map);

        var googleLayer = L.tileLayer('http://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
            maxZoom: 19,
            attribution: 'Google Maps'
        });
        var osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: 'OpenStreetMap'
        });

        googleLayer.addTo(map);
        googleLayer.on('tileerror', function() {
            if (!map.hasLayer(osmLayer)) osmLayer.addTo(map);
        });

        map.on('rotate', function() {
            var b = (map.getBearing ? map.getBearing() : 0);
            if (window.AndroidBridge && window.AndroidBridge.onMapRotate) {
                window.AndroidBridge.onMapRotate(b);
            }
            updateConeAngle();
        });

        window.resetBearing = function() {
            if (map.setBearing) map.setBearing(0);
            if (window.AndroidBridge && window.AndroidBridge.onMapRotate) {
                window.AndroidBridge.onMapRotate(0);
            }
            updateConeAngle();
        };

        map.on('click', function(e) {
            if (window.AndroidBridge && window.AndroidBridge.onMapClick) {
                window.AndroidBridge.onMapClick(e.latlng.lat, e.latlng.lng);
            }
        });

        var currentPhoneHeading = 0;
        var userMarker = null;
        var userLatLng = null;

        function updateConeAngle() {
            var cone = document.getElementById('user-heading-cone');
            if (cone) {
                var mapB = (map.getBearing ? map.getBearing() : 0);
                var effAngle = currentPhoneHeading - mapB;
                cone.style.transform = 'rotate(' + effAngle + 'deg)';
            }
        }

        window.updateHeading = function(h) {
            currentPhoneHeading = h;
            updateConeAngle();
        };

        var userIconHtml = '<div style="position:relative; width:80px; height:80px; margin-left:-40px; margin-top:-40px; pointer-events:none;">' +
            '<div id="user-heading-cone" style="position:absolute; left:0; top:0; width:80px; height:80px; transform-origin:40px 40px; transform:rotate(0deg); transition: transform 0.08s linear;">' +
            '<svg width="80" height="80" viewBox="0 0 80 80">' +
            '<defs>' +
            '<radialGradient id="coneGrad" cx="50%" cy="50%" r="50%">' +
            '<stop offset="0%" stop-color="#2979FF" stop-opacity="0.6"/>' +
            '<stop offset="60%" stop-color="#2979FF" stop-opacity="0.2"/>' +
            '<stop offset="100%" stop-color="#2979FF" stop-opacity="0"/>' +
            '</defs>' +
            '<path d="M 40 40 L 16 0 A 40 40 0 0 1 64 0 Z" fill="url(#coneGrad)" />' +
            '</svg>' +
            '</div>' +
            '<div style="position:absolute; left:31px; top:31px; width:18px; height:18px; border-radius:50%; background:#2979FF; border:3px solid #FFFFFF; box-shadow:0 0 8px rgba(41,121,255,0.9);"></div>' +
            '</div>';

        var userDivIcon = L.divIcon({
            html: userIconHtml,
            className: 'user-location-custom-icon',
            iconSize: [0, 0]
        });

        try {
            map.locate({ setView: true, maxZoom: 16.3, enableHighAccuracy: true });

            map.on('locationfound', function(e) {
                userLatLng = e.latlng;
                if (!userMarker) {
                    userMarker = L.marker(e.latlng, {
                        icon: userDivIcon,
                        zIndexOffset: 1000
                    }).addTo(map);
                    userMarker.bindPopup('<b>Vị trí của bạn</b>');
                } else {
                    userMarker.setLatLng(e.latlng);
                }
                updateConeAngle();
                if (window.AndroidBridge && window.AndroidBridge.onUserLocation) {
                    window.AndroidBridge.onUserLocation(e.latlng.lat, e.latlng.lng);
                }
            });
        } catch (e) {}

        window.recenterToUser = function() {
            if (userLatLng) {
                map.setView(userLatLng, 16.3, { animate: true });
            } else {
                try { map.locate({ setView: true, maxZoom: 16.3, enableHighAccuracy: true }); } catch(e) {}
            }
        };

        window.focusStation = function(lat, lng) {
            if (lat && lng) {
                map.setView([lat, lng], 17, { animate: true });
            }
        };

        // Bus tracking overlay
        var trackedBusMarker = null;
        var trackedBusPathLine = null;
        var trackedBusInterval = null;
        var trackedBusLayerGroup = L.layerGroup().addTo(map);

        window.stopBusTracking = function() {
            if (trackedBusInterval) {
                clearInterval(trackedBusInterval);
                trackedBusInterval = null;
            }
            trackedBusLayerGroup.clearLayers();
            trackedBusMarker = null;
            trackedBusPathLine = null;
        };

        window.startBusTracking = function(busData, rawPath) {
            window.stopBusTracking();
            if (!busData || !rawPath || rawPath.length < 2) return;

            var getLat = function(pt) { return pt ? (pt.Lat !== undefined ? pt.Lat : (pt.lat !== undefined ? pt.lat : pt[0])) : 0; };
            var getLng = function(pt) { return pt ? (pt.Lng !== undefined ? pt.Lng : (pt.lng !== undefined ? pt.lng : pt[1])) : 0; };

            var fullPath = rawPath.map(function(p) { return [getLat(p), getLng(p)]; }).filter(function(pt) { return pt[0] > 10 && pt[1] > 100; });
            if (fullPath.length < 2) return;

            var totalPoints = fullPath.length;
            var plate = busData.licensePlate || busData.BienKiemSoat || (busData.fleet ? 'Tuyến ' + busData.fleet : 'Xe buýt');
            var distRemaining = busData.distanceMeters || busData.PartRemained || 1000;
            var timeRemaining = busData.timeSeconds || busData.TimeRemained || 180;
            var speed = (busData.speed || busData.Speed || 22.0);
            if (speed <= 0) speed = 22.0;

            var stepSpeedMps = Math.max(speed / 3.6, 12.0);

            var segmentDists = [];
            var totalPathDist = 0;
            for (var i = 0; i < totalPoints - 1; i++) {
                var d = map.distance(fullPath[i], fullPath[i+1]);
                segmentDists.push(d);
                totalPathDist += d;
            }

            var currentTraveledMeters = 0;

            trackedBusPathLine = L.polyline(fullPath, {
                color: '#00E676',
                weight: 8,
                opacity: 0.95,
                lineJoin: 'round',
                dashArray: '10, 8'
            }).addTo(trackedBusLayerGroup);

            function createBusIconHtml(pText) {
                return '<div style="position:relative; width:0; height:0; pointer-events:auto; cursor:pointer;">' +
                    '<div style="position:absolute; left:-22px; top:-22px; width:44px; height:44px; border-radius:50%; background:rgba(0, 230, 118, 0.4); animation: busGlow 1.4s infinite;"></div>' +
                    '<div style="position:absolute; left:-18px; top:-18px; width:36px; height:36px; border-radius:50%; background:#1B5E20; border:2.5px solid #FFFFFF; display:flex; align-items:center; justify-content:center; box-shadow:0 3px 10px rgba(0,0,0,0.5); font-size:18px;">🚌</div>' +
                    '<div style="position:absolute; left:22px; top:-13px; background:#1B5E20; color:#FFFFFF; border:1.5px solid #FFFFFF; border-radius:12px; padding:3px 9px; font-size:11px; font-weight:bold; white-space:nowrap; box-shadow:0 2px 6px rgba(0,0,0,0.35);">' +
                    pText +
                    '</div>' +
                    '</div>';
            }

            var initialTimeText = (timeRemaining > 60 ? Math.round(timeRemaining / 60) + 'p' : timeRemaining + 's');
            var busIcon = L.divIcon({
                html: createBusIconHtml(plate + ' • ' + initialTimeText),
                className: 'tracked-bus-icon',
                iconSize: [0, 0],
                iconAnchor: [0, 0]
            });

            trackedBusMarker = L.marker(fullPath[0], {
                icon: busIcon,
                zIndexOffset: 1500
            }).addTo(trackedBusLayerGroup);

            map.fitBounds(fullPath, { paddingBottomRight: [40, 90], paddingTopLeft: [40, 90], maxZoom: 16, animate: true });

            function getPointAtDistance(meters) {
                if (meters <= 0) return { pt: fullPath[0], remainingCoords: fullPath };
                if (meters >= totalPathDist) return { pt: fullPath[totalPoints - 1], remainingCoords: [fullPath[totalPoints - 1]] };

                var acc = 0;
                for (var j = 0; j < segmentDists.length; j++) {
                    var segD = segmentDists[j];
                    if (acc + segD >= meters) {
                        var remainSeg = meters - acc;
                        var ratio = (segD > 0) ? (remainSeg / segD) : 0;
                        var p1 = fullPath[j];
                        var p2 = fullPath[j+1];
                        var curLat = p1[0] + (p2[0] - p1[0]) * ratio;
                        var curLng = p1[1] + (p2[1] - p1[1]) * ratio;
                        var curPt = [curLat, curLng];
                        var remaining = [curPt].concat(fullPath.slice(j + 1));
                        return { pt: curPt, remainingCoords: remaining };
                    }
                    acc += segD;
                }
                return { pt: fullPath[totalPoints - 1], remainingCoords: [fullPath[totalPoints - 1]] };
            }

            trackedBusInterval = setInterval(function() {
                currentTraveledMeters += stepSpeedMps;
                distRemaining = Math.max(0, Math.round(distRemaining - stepSpeedMps));
                timeRemaining = Math.max(0, timeRemaining - 1);

                var posData = getPointAtDistance(currentTraveledMeters);

                if (trackedBusMarker) {
                    trackedBusMarker.setLatLng(posData.pt);

                    var tText = (timeRemaining > 60 ? Math.round(timeRemaining / 60) + 'p' : timeRemaining + 's');
                    if (distRemaining <= 25 || timeRemaining <= 0) {
                        tText = 'Đã tới bến!';
                    }
                    trackedBusMarker.setIcon(L.divIcon({
                        html: createBusIconHtml(plate + ' • ' + tText),
                        className: 'tracked-bus-icon',
                        iconSize: [0, 0],
                        iconAnchor: [0, 0]
                    }));
                }

                if (trackedBusPathLine && posData.remainingCoords.length >= 2) {
                    trackedBusPathLine.setLatLngs(posData.remainingCoords);
                }

                if (window.AndroidBridge && window.AndroidBridge.onBusTrackingStep) {
                    window.AndroidBridge.onBusTrackingStep(distRemaining, timeRemaining);
                }

                if (currentTraveledMeters >= totalPathDist || distRemaining <= 0) {
                    clearInterval(trackedBusInterval);
                    trackedBusInterval = null;
                }
            }, 1000);
        };

        // Layers for trip rendering
        var routeLayerGroup = L.layerGroup().addTo(map);
        var activeRoutePolylines = [];
        var activeRouteMarkers = [];

        function getBusRouteWeight(z) {
            if (z <= 11) return 3;
            if (z === 12) return 4;
            if (z === 13) return 5;
            if (z === 14) return 6;
            if (z === 15) return 8;
            if (z === 16) return 11;
            if (z === 17) return 14;
            if (z === 18) return 17;
            return 21;
        }

        function getWalkRouteWeight(z) {
            if (z <= 11) return 2;
            if (z === 12) return 3;
            if (z === 13) return 4;
            if (z === 14) return 5;
            if (z === 15) return 7;
            if (z === 16) return 9;
            if (z === 17) return 11;
            if (z === 18) return 13;
            return 16;
        }

        function getWalkDash(z) {
            var w = getWalkRouteWeight(z);
            return Math.round(w * 1.2) + ', ' + Math.round(w * 1.8);
        }

        function applyRouteZoomStyling() {
            var z = map.getZoom();
            var busW = getBusRouteWeight(z);
            var walkW = getWalkRouteWeight(z);
            var walkDash = getWalkDash(z);

            for (var i = 0; i < activeRoutePolylines.length; i++) {
                var pItem = activeRoutePolylines[i];
                if (pItem.type === 'BUS') {
                    pItem.polyline.setStyle({ weight: busW });
                } else if (pItem.type === 'WALK') {
                    pItem.polyline.setStyle({ weight: walkW, dashArray: walkDash });
                }
            }

            for (var j = 0; j < activeRouteMarkers.length; j++) {
                var mItem = activeRouteMarkers[j];
                var el = document.getElementById(mItem.id);
                if (!el) continue;

                if (mItem.type === 'POINT_A' || mItem.type === 'POINT_B') {
                    var sz = (z <= 12 ? 22 : (z <= 15 ? 28 : 34));
                    var fsz = (z <= 12 ? 11 : (z <= 15 ? 13 : 15));
                    el.style.width = sz + 'px';
                    el.style.height = sz + 'px';
                    el.style.fontSize = fsz + 'px';
                    el.style.lineHeight = sz + 'px';
                } else if (mItem.type === 'BOARDING') {
                    var maxW = (z <= 11 ? 90 : (z <= 13 ? 160 : (z <= 15 ? 240 : 360)));
                    var fsz = (z <= 12 ? 10 : (z <= 15 ? 11 : 12));
                    el.style.maxWidth = maxW + 'px';
                    el.style.fontSize = fsz + 'px';
                    if (z <= 11) {
                        el.innerText = '🟢 ' + mItem.routeCode;
                    } else if (z <= 13) {
                        el.innerText = '🟢 ' + mItem.routeCode + ': ' + mItem.stationName;
                    } else {
                        el.innerText = '🟢 Lên xe ' + mItem.routeCode + ': ' + mItem.stationName;
                    }
                } else if (mItem.type === 'WALK_LABEL') {
                    if (z <= 12) {
                        el.style.display = 'none';
                    } else {
                        el.style.display = 'inline-flex';
                        el.style.fontSize = (z <= 15 ? '10px' : '11px');
                        el.style.maxWidth = (z <= 15 ? '140px' : '220px');
                    }
                } else if (mItem.type === 'BUS_LABEL') {
                    if (z <= 12) {
                        el.style.display = 'none';
                    } else {
                        el.style.display = 'inline-flex';
                        el.style.fontSize = (z <= 15 ? '10px' : '11px');
                        el.style.maxWidth = (z <= 15 ? '160px' : '250px');
                    }
                }
            }
        }

        map.on('zoom', applyRouteZoomStyling);
        map.on('zoomend', applyRouteZoomStyling);

        var trackedBusMarker = null;
        var trackedBusPathLine = null;
        var trackedBusInterval = null;
        var trackedBusLayerGroup = L.layerGroup().addTo(map);

        window.stopBusTracking = function() {
            if (trackedBusInterval) {
                clearInterval(trackedBusInterval);
                trackedBusInterval = null;
            }
            trackedBusLayerGroup.clearLayers();
            trackedBusMarker = null;
            trackedBusPathLine = null;
        };

        window.startBusTracking = function(busData, rawPath) {
            window.stopBusTracking();
            if (!busData || !rawPath || rawPath.length < 2) return;

            var getLat = function(pt) { return pt ? (pt.Lat !== undefined ? pt.Lat : pt.lat) : 0; };
            var getLng = function(pt) { return pt ? (pt.Lng !== undefined ? pt.Lng : pt.lng) : 0; };

            var fullPath = rawPath.map(function(p) { return [getLat(p), getLng(p)]; }).filter(function(pt) { return pt[0] > 10 && pt[1] > 100; });
            if (fullPath.length < 2) return;
            var totalPoints = fullPath.length;

            var plate = busData.licensePlate || busData.BienKiemSoat || (busData.fleet ? 'Tuyến ' + busData.fleet : 'Xe buýt');
            var distRemaining = busData.distanceMeters || busData.PartRemained || 1000;
            var timeRemaining = busData.timeSeconds || busData.TimeRemained || 180;
            var speed = (busData.speed || busData.Speed || 22.0);
            if (speed <= 0) speed = 22.0;

            var stepSpeedMps = Math.max(speed / 3.6, 22.0);

            var segmentDists = [];
            var totalPathDist = 0;
            for (var i = 0; i < totalPoints - 1; i++) {
                var d = map.distance(fullPath[i], fullPath[i+1]);
                segmentDists.push(d);
                totalPathDist += d;
            }

            var currentTraveledMeters = 0;

            trackedBusPathLine = L.polyline(fullPath, {
                color: '#00E676',
                weight: 8,
                opacity: 0.95,
                lineJoin: 'round',
                dashArray: '10, 8'
            }).addTo(trackedBusLayerGroup);

            function createBusIconHtml(pText) {
                return '<div style="position:relative; width:0; height:0; pointer-events:auto; cursor:pointer;">' +
                    '<div style="position:absolute; left:-22px; top:-22px; width:44px; height:44px; border-radius:50%; background:rgba(0, 230, 118, 0.4); animation: busGlow 1.4s infinite;"></div>' +
                    '<div style="position:absolute; left:-18px; top:-18px; width:36px; height:36px; border-radius:50%; background:#1B5E20; border:2.5px solid #FFFFFF; display:flex; align-items:center; justify-content:center; box-shadow:0 3px 10px rgba(0,0,0,0.5); font-size:18px;">🚌</div>' +
                    '<div style="position:absolute; left:22px; top:-13px; background:#1B5E20; color:#FFFFFF; border:1.5px solid #FFFFFF; border-radius:12px; padding:3px 9px; font-size:11px; font-weight:bold; white-space:nowrap; box-shadow:0 2px 6px rgba(0,0,0,0.35);">' +
                    pText +
                    '</div>' +
                    '</div>';
            }

            var initialTimeText = (timeRemaining > 60 ? Math.round(timeRemaining / 60) + 'p' : timeRemaining + 's');
            var busIcon = L.divIcon({
                html: createBusIconHtml(plate + ' • ' + initialTimeText),
                className: 'tracked-bus-icon',
                iconSize: [0, 0],
                iconAnchor: [0, 0]
            });

            trackedBusMarker = L.marker(fullPath[0], {
                icon: busIcon,
                zIndexOffset: 1500
            }).addTo(trackedBusLayerGroup);

            map.fitBounds(fullPath, { paddingBottomRight: [40, 90], paddingTopLeft: [40, 90], maxZoom: 16, animate: true });

            function getPointAtDistance(meters) {
                if (meters <= 0) return { pt: fullPath[0], remainingCoords: fullPath };
                if (meters >= totalPathDist) return { pt: fullPath[totalPoints - 1], remainingCoords: [fullPath[totalPoints - 1]] };

                var acc = 0;
                for (var j = 0; j < segmentDists.length; j++) {
                    var segD = segmentDists[j];
                    if (acc + segD >= meters) {
                        var remainSeg = meters - acc;
                        var ratio = (segD > 0) ? (remainSeg / segD) : 0;
                        var p1 = fullPath[j];
                        var p2 = fullPath[j+1];
                        var curLat = p1[0] + (p2[0] - p1[0]) * ratio;
                        var curLng = p1[1] + (p2[1] - p1[1]) * ratio;
                        var curPt = [curLat, curLng];
                        var remaining = [curPt].concat(fullPath.slice(j + 1));
                        return { pt: curPt, remainingCoords: remaining };
                    }
                    acc += segD;
                }
                return { pt: fullPath[totalPoints - 1], remainingCoords: [fullPath[totalPoints - 1]] };
            }

            trackedBusInterval = setInterval(function() {
                currentTraveledMeters += stepSpeedMps;
                distRemaining = Math.max(0, Math.round(distRemaining - stepSpeedMps));
                timeRemaining = Math.max(0, timeRemaining - 1);

                var posData = getPointAtDistance(currentTraveledMeters);

                if (trackedBusMarker) {
                    trackedBusMarker.setLatLng(posData.pt);

                    var tText = (timeRemaining > 60 ? Math.round(timeRemaining / 60) + 'p' : timeRemaining + 's');
                    if (distRemaining <= 25 || timeRemaining <= 0) {
                        tText = 'Đã tới bến!';
                    }
                    trackedBusMarker.setIcon(L.divIcon({
                        html: createBusIconHtml(plate + ' • ' + tText),
                        className: 'tracked-bus-icon',
                        iconSize: [0, 0],
                        iconAnchor: [0, 0]
                    }));
                }

                if (trackedBusPathLine && posData.remainingCoords.length >= 2) {
                    trackedBusPathLine.setLatLngs(posData.remainingCoords);
                }

                if (window.AndroidBridge && window.AndroidBridge.onBusTrackingStep) {
                    window.AndroidBridge.onBusTrackingStep(distRemaining, timeRemaining);
                }

                if (currentTraveledMeters >= totalPathDist || distRemaining <= 0) {
                    clearInterval(trackedBusInterval);
                    trackedBusInterval = null;
                }
            }, 1000);
        };

        window.renderTripRoute = function(orig, dest, option) {
            window.stopBusTracking();
            routeLayerGroup.clearLayers();
            activeRoutePolylines = [];
            activeRouteMarkers = [];
            var bounds = [];

            var getLat = function(pt) { return pt ? (pt.Lat !== undefined ? pt.Lat : pt.lat) : 0; };
            var getLng = function(pt) { return pt ? (pt.Lng !== undefined ? pt.Lng : pt.lng) : 0; };
            var curZoom = map.getZoom();

            if (orig && (getLat(orig) !== 0 || getLng(orig) !== 0)) {
                var oLat = getLat(orig);
                var oLng = getLng(orig);
                var origIcon = L.divIcon({
                    html: '<div id="point-marker-a" style="background:#2E7D32; color:white; border:2.5px solid white; border-radius:50%; width:28px; height:28px; display:flex; align-items:center; justify-content:center; font-weight:bold; font-size:13px; box-shadow:0 2px 6px rgba(0,0,0,0.4); transform:translate(-50%, -50%);">A</div>',
                    className: 'trip-point-origin',
                    iconSize: [0, 0],
                    iconAnchor: [0, 0]
                });
                L.marker([oLat, oLng], { icon: origIcon, zIndexOffset: 800 }).bindPopup('<b>Điểm xuất phát</b>').addTo(routeLayerGroup);
                activeRouteMarkers.push({ id: 'point-marker-a', type: 'POINT_A' });
                bounds.push([oLat, oLng]);
            }

            if (dest && (getLat(dest) !== 0 || getLng(dest) !== 0)) {
                var dLat = getLat(dest);
                var dLng = getLng(dest);
                var destIcon = L.divIcon({
                    html: '<div id="point-marker-b" style="background:#C62828; color:white; border:2.5px solid white; border-radius:50%; width:28px; height:28px; display:flex; align-items:center; justify-content:center; font-weight:bold; font-size:13px; box-shadow:0 2px 6px rgba(0,0,0,0.4); transform:translate(-50%, -50%);">B</div>',
                    className: 'trip-point-dest',
                    iconSize: [0, 0],
                    iconAnchor: [0, 0]
                });
                L.marker([dLat, dLng], { icon: destIcon, zIndexOffset: 800 }).bindPopup('<b>Điểm đến</b>').addTo(routeLayerGroup);
                activeRouteMarkers.push({ id: 'point-marker-b', type: 'POINT_B' });
                bounds.push([dLat, dLng]);
            }

            if (option && option.segments && option.segments.length > 0) {
                var busColors = ['#E65100', '#6A1B9A', '#00695C', '#1565C0', '#D84315'];
                var busColorIdx = 0;

                option.segments.forEach(function(seg, segIdx) {
                    var rawPts = seg.pathPoints || [];
                    if (rawPts.length < 2) return;
                    var pts = rawPts.map(function(p) { return [getLat(p), getLng(p)]; });
                    pts.forEach(function(pt) { bounds.push(pt); });

                    var midIdx = Math.floor(pts.length / 2);
                    var midPt = pts[midIdx];

                    if (seg.type === 'WALK') {
                        // 1. Draw walking polyline with dynamic zoom weight
                        var walkPoly = L.polyline(pts, {
                            color: '#1E88E5',
                            weight: getWalkRouteWeight(curZoom),
                            opacity: 0.9,
                            dashArray: getWalkDash(curZoom)
                        }).addTo(routeLayerGroup);
                        activeRoutePolylines.push({ polyline: walkPoly, type: 'WALK' });

                        // 2. Segment label for walking with width handling
                        var walkLabelId = 'walk-label-' + segIdx + '-' + Math.floor(Math.random() * 1000);
                        var walkDistStr = (seg.distanceMeters ? seg.distanceMeters + 'm' : '');
                        var walkMinStr = (seg.durationMinutes ? ' • ' + seg.durationMinutes + 'p' : '');
                        var walkBadge = L.divIcon({
                            html: '<div id="' + walkLabelId + '" style="background:#1565C0; color:white; border:1.5px solid white; border-radius:12px; padding:2px 8px; font-size:11px; font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:200px; box-shadow:0 2px 5px rgba(0,0,0,0.35); pointer-events:none; transform:translate(-50%, -50%); display:inline-flex; align-items:center;">🚶 Đi bộ ~' + walkDistStr + walkMinStr + '</div>',
                            className: 'segment-label-walk',
                            iconSize: [0, 0],
                            iconAnchor: [0, 0]
                        });
                        L.marker(midPt, { icon: walkBadge, interactive: false, zIndexOffset: 300 }).addTo(routeLayerGroup);
                        activeRouteMarkers.push({ id: walkLabelId, type: 'WALK_LABEL' });
                    } else {
                        // Bus Segment
                        var col = busColors[busColorIdx % busColors.length];
                        busColorIdx++;

                        // 1. Draw bus polyline with dynamic zoom weight
                        var busPoly = L.polyline(pts, {
                            color: col,
                            weight: getBusRouteWeight(curZoom),
                            opacity: 0.95
                        }).addTo(routeLayerGroup);
                        activeRoutePolylines.push({ polyline: busPoly, type: 'BUS' });

                        // 2. Segment label for bus with width handling
                        var busLabelId = 'bus-label-' + segIdx + '-' + Math.floor(Math.random() * 1000);
                        var stationList = seg.stations || [];
                        var stopsText = (stationList.length > 0) ? (stationList.length + ' trạm') : ((seg.stopsCount || 0) + ' trạm');
                        var busMinStr = (seg.durationMinutes ? ' • ' + seg.durationMinutes + 'p' : '');
                        var busBadge = L.divIcon({
                            html: '<div id="' + busLabelId + '" style="background:' + col + '; color:white; border:1.5px solid white; border-radius:12px; padding:2px 8px; font-size:11px; font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:240px; box-shadow:0 2px 5px rgba(0,0,0,0.35); pointer-events:none; transform:translate(-50%, -50%); display:inline-flex; align-items:center;">🚌 Tuyến ' + (seg.routeCode || '') + ' (' + stopsText + busMinStr + ')</div>',
                            className: 'segment-label-bus',
                            iconSize: [0, 0],
                            iconAnchor: [0, 0]
                        });
                        L.marker(midPt, { icon: busBadge, interactive: false, zIndexOffset: 350 }).addTo(routeLayerGroup);
                        activeRouteMarkers.push({ id: busLabelId, type: 'BUS_LABEL' });

                        // 3. Render ONLY the starting/boarding station for this bus segment
                        var boardingStation = (stationList.length > 0) ? stationList[0] : null;
                        var rCode = seg.routeCode || '';
                        if (boardingStation) {
                            var sGeo = boardingStation.Geo || boardingStation.geo;
                            if (sGeo) {
                                var sLat = getLat(sGeo);
                                var sLng = getLng(sGeo);
                                if (sLat !== 0 || sLng !== 0) {
                                    bounds.push([sLat, sLng]);
                                    var sName = boardingStation.Name || boardingStation.name || '';
                                    var sStreet = boardingStation.Street || boardingStation.street || 'Trạm xe buýt Hà Nội';
                                    var sFleetOver = boardingStation.FleetOver || boardingStation.fleetOver || '';
                                    var sId = boardingStation.ObjectID || boardingStation.objectId || 0;

                                    var markerId = 'boarding-badge-' + segIdx + '-' + Math.floor(Math.random() * 1000);
                                    var initialMaxW = (curZoom <= 11 ? 90 : (curZoom <= 13 ? 160 : (curZoom <= 15 ? 240 : 360)));
                                    var initialFsz = (curZoom <= 12 ? 10 : (curZoom <= 15 ? 11 : 12));
                                    var initialText = (curZoom <= 11 ? '🟢 ' + rCode : (curZoom <= 13 ? '🟢 ' + rCode + ': ' + sName : '🟢 Lên xe ' + rCode + ': ' + sName));

                                    var bHtml = '<div id="' + markerId + '" style="background:#2E7D32; color:white; border:2.5px solid white; border-radius:16px; padding:4px 10px; font-size:' + initialFsz + 'px; font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:' + initialMaxW + 'px; box-shadow:0 3px 8px rgba(0,0,0,0.4); cursor:pointer; transform:translate(-50%, -100%); margin-top:-6px; display:inline-flex; align-items:center;">' + initialText + '</div>';
                                    var mIcon = L.divIcon({ html: bHtml, className: 'trip-boarding-icon', iconSize: [0, 0], iconAnchor: [0, 0] });

                                    var stMarker = L.marker([sLat, sLng], { icon: mIcon, zIndexOffset: 700 }).addTo(routeLayerGroup);
                                    activeRouteMarkers.push({
                                        id: markerId,
                                        type: 'BOARDING',
                                        routeCode: rCode,
                                        stationName: sName
                                    });

                                    stMarker.on('click', function() {
                                        if (window.AndroidBridge && window.AndroidBridge.onBusStationSelect) {
                                            window.AndroidBridge.onBusStationSelect(
                                                sId,
                                                sName,
                                                rCode,
                                                sLat,
                                                sLng,
                                                sFleetOver,
                                                sStreet
                                            );
                                        }
                                    });
                                }
                            }
                        } else {
                            var startPt = pts[0];
                            var sLat = startPt[0];
                            var sLng = startPt[1];
                            var fPlaceName = seg.fromPlaceName || 'Điểm đón';
                            var markerId = 'boarding-fallback-' + segIdx + '-' + Math.floor(Math.random() * 1000);
                            var initialMaxW = (curZoom <= 11 ? 90 : (curZoom <= 13 ? 160 : (curZoom <= 15 ? 240 : 360)));
                            var initialFsz = (curZoom <= 12 ? 10 : (curZoom <= 15 ? 11 : 12));
                            var initialText = (curZoom <= 11 ? '🟢 ' + (rCode || 'Bus') : (curZoom <= 13 ? '🟢 ' + (rCode || 'Bus') + ': ' + fPlaceName : '🟢 Lên xe ' + (rCode || 'Bus') + ': ' + fPlaceName));

                            var busBadgeHtml = '<div id="' + markerId + '" style="background:#2E7D32; color:white; border:2.5px solid white; border-radius:16px; padding:4px 10px; font-size:' + initialFsz + 'px; font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:' + initialMaxW + 'px; box-shadow:0 3px 8px rgba(0,0,0,0.4); cursor:pointer; transform:translate(-50%, -100%); margin-top:-6px; display:inline-flex; align-items:center;">' + initialText + '</div>';
                            var busBadgeIcon = L.divIcon({ html: busBadgeHtml, className: 'trip-boarding-icon', iconSize: [0, 0], iconAnchor: [0, 0] });
                            var stMarker = L.marker(startPt, { icon: busBadgeIcon, zIndexOffset: 700 }).addTo(routeLayerGroup);
                            activeRouteMarkers.push({
                                id: markerId,
                                type: 'BOARDING',
                                routeCode: rCode || 'Bus',
                                stationName: fPlaceName
                            });

                            stMarker.on('click', function() {
                                if (window.AndroidBridge && window.AndroidBridge.onBusStationSelect) {
                                    window.AndroidBridge.onBusStationSelect(
                                        seg.fromStationId || 0,
                                        fPlaceName,
                                        rCode,
                                        sLat,
                                        sLng,
                                        '',
                                        ''
                                    );
                                }
                            });
                        }
                    }
                });
            }

            if (bounds.length > 0) {
                map.fitBounds(bounds, { padding: [70, 70], animate: true });
            }
            applyRouteZoomStyling();
        };
    </script>
</body>
</html>
    """.trimIndent()
}
