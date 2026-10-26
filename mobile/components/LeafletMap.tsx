import React, { useRef, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { StyleSheet, View, ActivityIndicator, Platform } from 'react-native';
import { WebView } from 'react-native-webview';

export interface LeafletCoordinate {
  latitude: number;
  longitude: number;
}

export interface LeafletRegion {
  latitude: number;
  longitude: number;
  latitudeDelta: number;
  longitudeDelta: number;
}

export interface LeafletSafeZone {
  id: string;
  name: string;
  zoneType: string;
  latitude: number;
  longitude: number;
  address?: string | null;
  phoneNumber?: string | null;
  operatingHours?: string | null;
  is24h?: boolean | null;
  verificationStatus?: string;
}

export interface LeafletHeatmapSegment {
  segmentId: number | string;
  coordinates: LeafletCoordinate[];
  colorHex: string;
  scoringStatus?: string;
  name?: string;
  safetyScore?: number | null;
  riskCategory?: string;
}

export interface LeafletRoute {
  mode: 'FASTEST' | 'BALANCED' | 'SAFEST';
  coordinates: LeafletCoordinate[];
  colorHex: string;
  isSelected?: boolean;
}

export interface LeafletMapRef {
  animateToRegion: (region: LeafletRegion, duration?: number) => void;
  fitBounds: (coords: LeafletCoordinate[]) => void;
}

export interface LeafletMapProps {
  initialRegion: LeafletRegion;
  onRegionChangeComplete?: (region: LeafletRegion) => void;
  userLocation?: LeafletCoordinate | null;
  safeZones?: LeafletSafeZone[];
  onSafeZoneSelect?: (zone: LeafletSafeZone) => void;
  heatmapSegments?: LeafletHeatmapSegment[];
  onSegmentSelect?: (segment: LeafletHeatmapSegment) => void;
  selectedSegmentId?: string | number | null;
  routes?: LeafletRoute[];
  selectedMode?: 'FASTEST' | 'BALANCED' | 'SAFEST';
  origin?: LeafletCoordinate | null;
  destination?: LeafletCoordinate | null;
  sampleRouteCoords?: LeafletCoordinate[] | null;
  style?: any;
}

const CATEGORY_ICONS: Record<string, { icon: string; bg: string; color: string }> = {
  POLICE_STATION: { icon: '👮', bg: '#1D68BD', color: '#FFFFFF' },
  HOSPITAL: { icon: '🏥', bg: '#DC2626', color: '#FFFFFF' },
  PHARMACY: { icon: '💊', bg: '#059669', color: '#FFFFFF' },
  PUBLIC_TRANSPORT: { icon: '🚇', bg: '#7C3AED', color: '#FFFFFF' },
  OTHER: { icon: '📍', bg: '#D97706', color: '#FFFFFF' },
};

export const LeafletMap = forwardRef<LeafletMapRef, LeafletMapProps>((props, ref) => {
  const {
    initialRegion,
    onRegionChangeComplete,
    userLocation,
    safeZones = [],
    onSafeZoneSelect,
    heatmapSegments = [],
    onSegmentSelect,
    selectedSegmentId,
    routes = [],
    origin,
    destination,
    sampleRouteCoords,
    style,
  } = props;

  const webViewRef = useRef<WebView | null>(null);
  const [isMapReady, setIsMapReady] = useState(false);

  // Expose animateToRegion to parent via ref
  useImperativeHandle(ref, () => ({
    animateToRegion: (region: LeafletRegion) => {
      const zoom = Math.round(Math.log2(360 / Math.max(region.latitudeDelta, region.longitudeDelta)));
      const safeZoom = Math.min(Math.max(zoom, 10), 18);
      const script = `
        if (window.panToRegion) {
          window.panToRegion(${region.latitude}, ${region.longitude}, ${safeZoom});
        }
        true;
      `;
      webViewRef.current?.injectJavaScript(script);
    },
    fitBounds: (coords: LeafletCoordinate[]) => {
      if (!coords || coords.length === 0) return;
      const script = `
        if (window.fitCoordinates) {
          window.fitCoordinates(${JSON.stringify(coords)});
        }
        true;
      `;
      webViewRef.current?.injectJavaScript(script);
    },
  }));

  // Send incremental updates whenever props change without reloading the WebView
  useEffect(() => {
    if (!isMapReady) return;

    const payload = {
      userLocation: userLocation || null,
      safeZones: safeZones.map((z) => ({
        id: z.id,
        name: z.name,
        zoneType: z.zoneType,
        latitude: z.latitude,
        longitude: z.longitude,
        cat: CATEGORY_ICONS[z.zoneType] || CATEGORY_ICONS.OTHER,
      })),
      heatmapSegments: heatmapSegments.map((s) => ({
        segmentId: s.segmentId,
        coordinates: s.coordinates,
        colorHex: s.colorHex,
        scoringStatus: s.scoringStatus,
        isSelected: selectedSegmentId !== null && selectedSegmentId !== undefined && String(selectedSegmentId) === String(s.segmentId),
      })),
      routes: routes.map((r) => ({
        mode: r.mode,
        coordinates: r.coordinates,
        colorHex: r.colorHex,
        isSelected: !!r.isSelected,
      })),
      origin: origin || null,
      destination: destination || null,
      sampleRouteCoords: sampleRouteCoords || null,
    };

    const script = `
      if (window.updateLayers) {
        window.updateLayers(${JSON.stringify(payload)});
      }
      true;
    `;
    webViewRef.current?.injectJavaScript(script);
  }, [
    isMapReady,
    userLocation,
    safeZones,
    heatmapSegments,
    selectedSegmentId,
    routes,
    origin,
    destination,
    sampleRouteCoords,
  ]);

  // Handle messages sent from Leaflet inside WebView
  const handleMessage = (event: any) => {
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      if (msg.type === 'MAP_READY') {
        setIsMapReady(true);
      } else if (msg.type === 'REGION_CHANGE') {
        if (onRegionChangeComplete) {
          onRegionChangeComplete(msg.region);
        }
      } else if (msg.type === 'SAFE_ZONE_SELECT') {
        const found = safeZones.find((z) => String(z.id) === String(msg.id));
        if (found && onSafeZoneSelect) {
          onSafeZoneSelect(found);
        }
      } else if (msg.type === 'SEGMENT_SELECT') {
        const found = heatmapSegments.find((s) => String(s.segmentId) === String(msg.id));
        if (found && onSegmentSelect) {
          onSegmentSelect(found);
        }
      }
    } catch {
      // Ignore JSON parse errors
    }
  };

  const initialZoom = Math.min(
    Math.max(
      Math.round(Math.log2(360 / Math.max(initialRegion.latitudeDelta, initialRegion.longitudeDelta))),
      11
    ),
    17
  );

  const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body, #map {
      width: 100%;
      height: 100%;
      background: #F2F2F7;
      overflow: hidden;
      touch-action: none;
      -webkit-touch-callout: none;
      -webkit-user-select: none;
      user-select: none;
    }
    
    /* User pulsing marker */
    .user-pulse-marker {
      width: 22px;
      height: 22px;
      position: relative;
    }
    .user-pulse-dot {
      width: 14px;
      height: 14px;
      background: #007AFF;
      border: 2px solid #FFFFFF;
      border-radius: 50%;
      position: absolute;
      top: 4px;
      left: 4px;
      box-shadow: 0 0 6px rgba(0,122,255,0.6);
      z-index: 2;
    }
    .user-pulse-wave {
      width: 22px;
      height: 22px;
      background: rgba(0, 122, 255, 0.35);
      border-radius: 50%;
      position: absolute;
      top: 0;
      left: 0;
      animation: pulseWave 1.8s infinite ease-out;
      z-index: 1;
    }
    @keyframes pulseWave {
      0% { transform: scale(0.6); opacity: 1; }
      100% { transform: scale(1.6); opacity: 0; }
    }

    /* Safe Zone Badges */
    .safe-zone-badge {
      width: 30px;
      height: 30px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      border: 2px solid #FFFFFF;
      cursor: pointer;
      transition: transform 0.15s ease;
    }
    .safe-zone-badge:active {
      transform: scale(1.2);
    }

    /* Origin & Destination Badges */
    .endpoint-badge {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 800;
      color: #FFFFFF;
      box-shadow: 0 2px 6px rgba(0,0,0,0.35);
      border: 2px solid #FFFFFF;
    }
    .origin-badge { background: #34C759; }
    .dest-badge { background: #FF3B30; }

    /* Custom Leaflet Controls */
    .leaflet-control-zoom {
      border: none !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.18) !important;
      border-radius: 8px !important;
      overflow: hidden;
    }
    .leaflet-control-zoom a {
      background: #FFFFFF !important;
      color: #1C1C1E !important;
      border: none !important;
      width: 34px !important;
      height: 34px !important;
      line-height: 34px !important;
      font-size: 16px !important;
    }
    .leaflet-control-attribution {
      background: rgba(255, 255, 255, 0.75) !important;
      font-size: 9px !important;
      padding: 2px 5px !important;
    }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    var map = L.map('map', {
      zoomControl: false,
      attributionControl: true,
      touchZoom: true,
      bounceAtZoomLimits: false,
      zoomAnimation: true,
      zoomSnap: 0.5,
      wheelPxPerZoomLevel: 60
    }).setView([${initialRegion.latitude}, ${initialRegion.longitude}], ${initialZoom});

    // Official OpenStreetMap tiles (100% free, community-funded, ZERO API key requirement, NO watermark)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Layer groups
    var userLayer = L.layerGroup().addTo(map);
    var heatmapLayer = L.layerGroup().addTo(map);
    var routesLayer = L.layerGroup().addTo(map);
    var safeZonesLayer = L.layerGroup().addTo(map);
    var endpointsLayer = L.layerGroup().addTo(map);

    // Post message helper
    function sendMsg(obj) {
      if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
        window.ReactNativeWebView.postMessage(JSON.stringify(obj));
      }
    }

    // Region change listener
    var regionDebounceTimer = null;
    map.on('moveend', function() {
      clearTimeout(regionDebounceTimer);
      regionDebounceTimer = setTimeout(function() {
        var center = map.getCenter();
        var bounds = map.getBounds();
        var latDelta = Math.abs(bounds.getNorth() - bounds.getSouth());
        var lonDelta = Math.abs(bounds.getEast() - bounds.getWest());
        sendMsg({
          type: 'REGION_CHANGE',
          region: {
            latitude: center.lat,
            longitude: center.lng,
            latitudeDelta: latDelta,
            longitudeDelta: lonDelta
          }
        });
      }, 250);
    });

    // Public bridge functions
    window.panToRegion = function(lat, lon, zoom) {
      map.flyTo([lat, lon], zoom || map.getZoom(), { duration: 0.8 });
    };

    window.fitCoordinates = function(coords) {
      if (!coords || !coords.length) return;
      var latlngs = coords.map(function(c) { return [c.latitude, c.longitude]; });
      map.fitBounds(L.latLngBounds(latlngs), { padding: [40, 40], maxZoom: 17 });
    };

    window.updateLayers = function(data) {
      // 1. User Location
      userLayer.clearLayers();
      if (data.userLocation) {
        var userIcon = L.divIcon({
          className: '',
          html: '<div class="user-pulse-marker"><div class="user-pulse-wave"></div><div class="user-pulse-dot"></div></div>',
          iconSize: [22, 22],
          iconAnchor: [11, 11]
        });
        L.marker([data.userLocation.latitude, data.userLocation.longitude], { icon: userIcon, zIndexOffset: 1000 }).addTo(userLayer);
      }

      // 2. Heatmap Segments
      heatmapLayer.clearLayers();
      if (data.heatmapSegments && data.heatmapSegments.length) {
        data.heatmapSegments.forEach(function(seg) {
          var pts = seg.coordinates.map(function(c) { return [c.latitude, c.longitude]; });
          var isSelected = seg.isSelected;
          var isUnscored = seg.scoringStatus !== 'SCORED';

          var poly = L.polyline(pts, {
            color: isSelected ? '#007AFF' : seg.colorHex,
            weight: isSelected ? 7 : 4,
            opacity: 0.9,
            dashArray: isUnscored ? '6, 8' : null
          });

          poly.on('click', function(e) {
            L.DomEvent.stopPropagation(e);
            sendMsg({ type: 'SEGMENT_SELECT', id: seg.segmentId });
          });

          poly.addTo(heatmapLayer);
        });
      }

      // 3. Safe Zones
      safeZonesLayer.clearLayers();
      if (data.safeZones && data.safeZones.length) {
        data.safeZones.forEach(function(zone) {
          var cat = zone.cat || { icon: '📍', bg: '#D97706' };
          var iconHtml = '<div class="safe-zone-badge" style="background-color: ' + cat.bg + ';">' + cat.icon + '</div>';
          var szIcon = L.divIcon({
            className: '',
            html: iconHtml,
            iconSize: [30, 30],
            iconAnchor: [15, 15]
          });

          var marker = L.marker([zone.latitude, zone.longitude], { icon: szIcon });
          marker.on('click', function(e) {
            L.DomEvent.stopPropagation(e);
            sendMsg({ type: 'SAFE_ZONE_SELECT', id: zone.id });
          });
          marker.addTo(safeZonesLayer);
        });
      }

      // 4. Routes
      routesLayer.clearLayers();
      endpointsLayer.clearLayers();

      if (data.routes && data.routes.length) {
        // Draw unselected first
        data.routes.filter(function(r) { return !r.isSelected; }).forEach(function(r) {
          if (!r.coordinates || r.coordinates.length < 2) return;
          var pts = r.coordinates.map(function(c) { return [c.latitude, c.longitude]; });
          L.polyline(pts, {
            color: '#8E8E93',
            weight: 4,
            opacity: 0.65
          }).addTo(routesLayer);
        });

        // Draw selected on top with highlight
        var sel = data.routes.find(function(r) { return r.isSelected; });
        if (sel && sel.coordinates && sel.coordinates.length >= 2) {
          var pts = sel.coordinates.map(function(c) { return [c.latitude, c.longitude]; });
          L.polyline(pts, {
            color: sel.colorHex || '#007AFF',
            weight: 6,
            opacity: 0.95
          }).addTo(routesLayer);
        }
      }

      // 5. Sample Route (for Map tab demo toggle)
      if (data.sampleRouteCoords && data.sampleRouteCoords.length >= 2) {
        var sPts = data.sampleRouteCoords.map(function(c) { return [c.latitude, c.longitude]; });
        L.polyline(sPts, {
          color: '#007AFF',
          weight: 6,
          opacity: 0.95
        }).addTo(routesLayer);

        var startIco = L.divIcon({
          className: '',
          html: '<div class="endpoint-badge origin-badge">A</div>',
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        });
        L.marker(sPts[0], { icon: startIco }).addTo(endpointsLayer);

        var endIco = L.divIcon({
          className: '',
          html: '<div class="endpoint-badge dest-badge">B</div>',
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        });
        L.marker(sPts[sPts.length - 1], { icon: endIco }).addTo(endpointsLayer);
      }

      // 6. Origin / Destination Endpoints (for Routes tab)
      if (data.origin) {
        var oIco = L.divIcon({
          className: '',
          html: '<div class="endpoint-badge origin-badge">A</div>',
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        });
        L.marker([data.origin.latitude, data.origin.longitude], { icon: oIco }).addTo(endpointsLayer);
      }
      if (data.destination) {
        var dIco = L.divIcon({
          className: '',
          html: '<div class="endpoint-badge dest-badge">B</div>',
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        });
        L.marker([data.destination.latitude, data.destination.longitude], { icon: dIco }).addTo(endpointsLayer);
      }
    };

    // Notify React Native that map is mounted and ready
    sendMsg({ type: 'MAP_READY' });
  </script>
</body>
</html>
  `;

  return (
    <View style={[styles.container, style]}>
      <WebView
        ref={webViewRef}
        style={styles.webview}
        originWhitelist={['*']}
        source={{ html: htmlContent }}
        onMessage={handleMessage}
        javaScriptEnabled={true}
        domStorageEnabled={true}
        scrollEnabled={false}
        bounces={false}
        overScrollMode="never"
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        scalesPageToFit={false}
        nestedScrollEnabled={false}
        startInLoadingState={true}
        renderLoading={() => (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
          </View>
        )}
      />
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  webview: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  loadingContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
  },
});
