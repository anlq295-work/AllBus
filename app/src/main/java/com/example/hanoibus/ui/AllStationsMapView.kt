package com.example.hanoibus.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hanoibus.data.BusStation
import com.google.gson.Gson

class AllStationsWebInterface(
    private val onStationClickCallback: (Long) -> Unit,
    private val onMapRotateCallback: (Float) -> Unit = {},
    private val onBusTrackingStepCallback: (Int, Int) -> Unit = { _, _ -> }
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onStationClick(stationIdStr: String) {
        android.util.Log.d("AllStationsWebMap", "WebInterface received: $stationIdStr")
        val id = stationIdStr.toLongOrNull()
        if (id == null) {
            android.util.Log.e("AllStationsWebMap", "WebInterface parse Long failed for: $stationIdStr")
            return
        }
        mainHandler.post {
            onStationClickCallback(id)
        }
    }

    @JavascriptInterface
    fun onMapRotate(bearing: Float) {
        mainHandler.post {
            onMapRotateCallback(bearing)
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
fun AllStationsMapView(
    stations: List<BusStation>,
    selectedStation: BusStation?,
    onStationSelect: (BusStation) -> Unit,
    phoneHeading: Float = 0f,
    onMapBearingChange: ((Float) -> Unit)? = null,
    trackedBus: com.example.hanoibus.data.BusEta? = null,
    busApproachingPath: List<com.example.hanoibus.data.GeoPoint>? = null,
    onTrackingStep: ((Int, Int) -> Unit)? = null,
    recenterTrigger: Int = 0,
    resetBearingTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val validStations = remember(stations) {
        stations.filter { it.geo != null && it.geo.lat != 0.0 && it.geo.lng != 0.0 }
    }

    val currentOnStationSelect by rememberUpdatedState(onStationSelect)
    val currentOnMapBearingChange by rememberUpdatedState(onMapBearingChange)
    val currentOnTrackingStep by rememberUpdatedState(onTrackingStep)
    val currentValidStations by rememberUpdatedState(validStations)
    val webInterface = remember {
        AllStationsWebInterface(
            onStationClickCallback = { stationId ->
                android.util.Log.d("AllStationsWebMap", "onStationClickCallback for id=$stationId, stations size=${currentValidStations.size}")
                val station = currentValidStations.find { it.objectId == stationId }
                if (station != null) {
                    android.util.Log.d("AllStationsWebMap", "Found station ${station.name}, invoking currentOnStationSelect")
                    currentOnStationSelect(station)
                } else {
                    android.util.Log.e("AllStationsWebMap", "Station NOT found for id=$stationId among ${currentValidStations.size} stations")
                }
            },
            onMapRotateCallback = { bearing ->
                currentOnMapBearingChange?.invoke(bearing)
            },
            onBusTrackingStepCallback = { dist, time ->
                currentOnTrackingStep?.invoke(dist, time)
            }
        )
    }

    val stationsJson = remember(validStations) {
        val mapped = validStations.map { s ->
            mapOf(
                "id" to s.objectId,
                "code" to (s.code ?: ""),
                "name" to s.name,
                "lat" to (s.geo?.lat ?: 0.0),
                "lng" to (s.geo?.lng ?: 0.0),
                "fleetOver" to (s.fleetOver ?: "")
            )
        }
        Gson().toJson(mapped)
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

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("AllStationsWebMap", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (selectedStation != null && selectedStation.geo != null) {
                            val lat = selectedStation.geo.lat
                            val lng = selectedStation.geo.lng
                            view?.evaluateJavascript("focusStation($lat, $lng, ${selectedStation.objectId});", null)
                        }
                    }
                }

                val html = generateAllStationsMapHtml(stationsJson)
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webViewRef.value = webView
        }
    )

    LaunchedEffect(selectedStation) {
        if (selectedStation != null && selectedStation.geo != null) {
            val lat = selectedStation.geo.lat
            val lng = selectedStation.geo.lng
            webViewRef.value?.evaluateJavascript("focusStation($lat, $lng, ${selectedStation.objectId});", null)
        } else if (selectedStation == null) {
            webViewRef.value?.evaluateJavascript("clearSelection();", null)
        }
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

    LaunchedEffect(trackedBus, busApproachingPath) {
        android.util.Log.d("AllStationsWebMap", "LaunchedEffect: trackedBus=${trackedBus?.licensePlate}, pathCount=${busApproachingPath?.size}")
        if (trackedBus != null && !busApproachingPath.isNullOrEmpty()) {
            val busJson = Gson().toJson(trackedBus)
            val pathJson = Gson().toJson(busApproachingPath)
            android.util.Log.d("AllStationsWebMap", "Calling startBusTracking in JS with ${busApproachingPath.size} pts")
            webViewRef.value?.evaluateJavascript("if (typeof startBusTracking === 'function') { startBusTracking($busJson, $pathJson); } else { console.error('startBusTracking function not found'); }", null)
        } else {
            webViewRef.value?.evaluateJavascript("if (typeof stopBusTracking === 'function') { stopBusTracking(); }", null)
        }
    }
}

private fun generateAllStationsMapHtml(stationsJson: String): String {
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
        }
        .recenter-btn {
            background: #ffffff;
            border: 2px solid rgba(0,0,0,0.2);
            border-radius: 4px;
            padding: 6px 10px;
            font-size: 12px;
            font-weight: bold;
            cursor: pointer;
            box-shadow: 0 1px 5px rgba(0,0,0,0.3);
            color: #1565C0;
            display: block;
            width: 100%;
            text-align: center;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var rawStations = $stationsJson;
        var markersMap = {};
        var selectedMarker = null;

        var map = L.map('map', {
            zoomControl: false,
            preferCanvas: true,
            rotate: true,
            touchRotate: true,
            rotateControl: false,
            bearing: 0,
            zoomSnap: 0.1
        }).setView([21.0285, 105.8542], 14.3);

        map.on('rotate', function() {
            var b = (map.getBearing ? map.getBearing() : 0);
            if (window.AndroidBridge && window.AndroidBridge.onMapRotate) {
                window.AndroidBridge.onMapRotate(b);
            }
            updateConeAngle();
        });

        window.resetBearing = function() {
            if (map.setBearing) {
                map.setBearing(0);
            }
            if (window.AndroidBridge && window.AndroidBridge.onMapRotate) {
                window.AndroidBridge.onMapRotate(0);
            }
            updateConeAngle();
        };

        L.control.zoom({ position: 'bottomright' }).addTo(map);

        // Google Maps Tiles with OSM fallback
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
            if (!map.hasLayer(osmLayer)) {
                osmLayer.addTo(map);
            }
        });

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

        // High-performance canvas renderer for 5000+ stations
        var canvasRenderer = L.canvas({ padding: 0.5, tolerance: 24 });

        // Override canvasRenderer._updateCircle to draw a bus stop pictogram inside every circle marker
        canvasRenderer._updateCircle = function(layer) {
            if (!this._drawing || layer._empty()) return;
            var p = layer._point,
                ctx = this._ctx,
                r = Math.max(Math.round(layer._radius), 1),
                s = r / 8.0,
                isSel = layer.options.isSelected;

            ctx.save();
            ctx.translate(p.x, p.y);

            // Circle background badge
            ctx.beginPath();
            ctx.arc(0, 0, r, 0, Math.PI * 2);
            ctx.fillStyle = isSel ? '#D32F2F' : '#1976D2';
            ctx.fill();
            ctx.lineWidth = isSel ? 2.5 : 1.5;
            ctx.strokeStyle = '#FFFFFF';
            ctx.stroke();

            // White Bus Pictogram inside
            ctx.fillStyle = '#FFFFFF';
            var bw = 7.5 * s;
            var bh = 8.5 * s;
            var bx = -bw / 2;
            var by = -bh / 2 - 0.5 * s;
            var rad = 1.2 * s;

            ctx.beginPath();
            ctx.moveTo(bx + rad, by);
            ctx.lineTo(bx + bw - rad, by);
            ctx.quadraticCurveTo(bx + bw, by, bx + bw, by + rad);
            ctx.lineTo(bx + bw, by + bh - rad);
            ctx.quadraticCurveTo(bx + bw, by + bh, bx + bw - rad, by + bh);
            ctx.lineTo(bx + rad, by + bh);
            ctx.quadraticCurveTo(bx, by + bh, bx, by + bh - rad);
            ctx.lineTo(bx, by + rad);
            ctx.quadraticCurveTo(bx, by, bx + rad, by);
            ctx.closePath();
            ctx.fill();

            // Front window cutout
            ctx.fillStyle = isSel ? '#D32F2F' : '#1976D2';
            ctx.fillRect(bx + 1.0 * s, by + 1.8 * s, bw - 2.0 * s, 2.6 * s);

            // Headlights
            ctx.beginPath();
            ctx.arc(bx + 1.6 * s, by + bh - 1.8 * s, 0.6 * s, 0, Math.PI * 2);
            ctx.arc(bx + bw - 1.6 * s, by + bh - 1.8 * s, 0.6 * s, 0, Math.PI * 2);
            ctx.fill();

            // Wheels
            ctx.fillStyle = '#212121';
            ctx.fillRect(bx + 1.0 * s, by + bh - 0.2 * s, 1.6 * s, 1.2 * s);
            ctx.fillRect(bx + bw - 2.6 * s, by + bh - 0.2 * s, 1.6 * s, 1.2 * s);

            ctx.restore();
        };

        // Add circle markers with bus pictogram renderer
        rawStations.forEach(function(s) {
            if (!s.lat || !s.lng) return;

            var marker = L.circleMarker([s.lat, s.lng], {
                renderer: canvasRenderer,
                radius: 8,
                isSelected: false
            });

            marker.stationData = s;

            marker.on('click', function(e) {
                if (e && e.originalEvent) {
                    L.DomEvent.stopPropagation(e);
                }
                selectStationById(s.id);
            });

            markersMap[s.id] = marker;
            markersMap[String(s.id)] = marker;
            marker.addTo(map);
        });

        function highlightMarker(marker) {
            if (selectedMarker && selectedMarker !== marker) {
                selectedMarker.options.isSelected = false;
                selectedMarker.setRadius(8);
                selectedMarker.redraw();
            }
            selectedMarker = marker;
            marker.options.isSelected = true;
            marker.setRadius(13);
            marker.redraw();
        }

        window.clearSelection = function() {
            if (selectedMarker) {
                selectedMarker.options.isSelected = false;
                selectedMarker.setRadius(8);
                selectedMarker.redraw();
                selectedMarker = null;
            }
        };

        function selectStationById(id) {
            console.log("selectStationById: " + id);
            var m = markersMap[id] || markersMap[String(id)];
            if (m) {
                highlightMarker(m);
            }
            if (window.AndroidBridge && window.AndroidBridge.onStationClick) {
                window.AndroidBridge.onStationClick("" + id);
            }
        }

        // Tap anywhere on map: robust hit detection with 48px touch radius
        map.on('click', function(e) {
            var clickPt = map.latLngToContainerPoint(e.latlng);
            var clickLat = e.latlng.lat;
            var clickLng = e.latlng.lng;
            var bestStation = null;
            var minDistancePx = 48; // Generous 48px touch radius for effortless tapping

            for (var i = 0; i < rawStations.length; i++) {
                var s = rawStations[i];
                if (!s.lat || !s.lng) continue;
                if (Math.abs(s.lat - clickLat) > 0.007 || Math.abs(s.lng - clickLng) > 0.007) continue;

                var pt = map.latLngToContainerPoint([s.lat, s.lng]);
                var dist = Math.hypot(pt.x - clickPt.x, pt.y - clickPt.y);
                if (dist < minDistancePx) {
                    minDistancePx = dist;
                    bestStation = s;
                }
            }

            if (bestStation) {
                selectStationById(bestStation.id);
            }
        });

        window.focusStation = function(lat, lng, id) {
            console.log("focusStation: " + lat + ", " + lng + ", " + id);
            map.setView([lat, lng], 17, { animate: true });
            setTimeout(function() {
                map.panBy([0, 100], { animate: true });
            }, 200);
            var m = markersMap[id] || markersMap[String(id)];
            if (m) {
                highlightMarker(m);
            }
        };

        // Geolocation: Default zoom to user location with live heading cone
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
            });

            map.on('locationerror', function(e) {
                console.log("Location not available:", e.message);
            });
        } catch (e) {
            console.error("Locate error:", e);
        }

        // Global recenter functions callable from Compose
        window.recenterToUser = function() {
            console.log("recenterToUser called, userLatLng: " + (userLatLng ? JSON.stringify(userLatLng) : "null"));
            if (userLatLng) {
                map.setView(userLatLng, 16.3, { animate: true });
                if (userMarker) userMarker.openPopup();
            } else {
                try {
                    map.locate({ setView: true, maxZoom: 16.3, enableHighAccuracy: true });
                } catch(e) {
                    console.error("Locate error in recenterToUser:", e);
                }
            }
        };

        window.recenterToHanoi = function() {
            map.setView([21.0285, 105.8542], 14.3, { animate: true });
        };
    </script>
</body>
</html>
    """.trimIndent()
}
