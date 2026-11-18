import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ActivityIndicator,
  TouchableOpacity,
  ScrollView,
  Dimensions,
  Platform,
  Modal,
  TextInput,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LeafletMap, LeafletMapRef, LeafletRegion } from '../../components/LeafletMap';
type Region = LeafletRegion;
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import { Card } from '../../components/Card';
import {
  fetchHeatmap,
  HeatmapSegment,
  HeatmapResponse,
  Coordinate,
} from '../../services/heatmap';
import {
  SafeZone,
  CATEGORY_CONFIG as SAFE_ZONE_CATEGORIES,
  VERIFICATION_CONFIG as SAFE_ZONE_VERIFICATION,
  fetchSafeZonesInViewport,
} from '../../services/safeZones';
import { WeatherWidget } from '../../components/WeatherWidget';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { searchPlaces, GeocodedPlace } from '../../services/geocoding';

const DEFAULT_REGION: Region = {
  latitude: 12.9756,
  longitude: 77.6066,
  latitudeDelta: 0.025,
  longitudeDelta: 0.025,
};

const BANGALORE_CENTER: Region = {
  latitude: 12.9756,
  longitude: 77.6066,
  latitudeDelta: 0.02,
  longitudeDelta: 0.02,
};

export interface MapRouteResult {
  routeMode: 'FASTEST' | 'BALANCED' | 'SAFEST';
  coordinates: Coordinate[];
  distanceMeters: number | null;
  durationSeconds: number | null;
  safetyScore: number | null;
  riskLevel: string;
  safetyLabel: string;
}

export interface MapRouteResponse {
  fastestRoute: MapRouteResult;
  balancedRoute: MapRouteResult;
  safestRoute: MapRouteResult;
}

export default function MapScreen() {
  const router = useRouter();
  const { user } = useAuth();
  const mapRef = useRef<LeafletMapRef | null>(null);

  const [userLocation, setUserLocation] = useState<Location.LocationObject | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [currentRegion, setCurrentRegion] = useState<Region>(DEFAULT_REGION);

  // Heatmap state
  const [heatmapEnabled, setHeatmapEnabled] = useState(true);
  const [loadingHeatmap, setLoadingHeatmap] = useState(false);
  const [heatmapError, setHeatmapError] = useState<string | null>(null);
  const [heatmapData, setHeatmapData] = useState<HeatmapResponse | null>(null);
  const [selectedSegment, setSelectedSegment] = useState<HeatmapSegment | null>(null);

  // Safe Zones state
  const [safeZonesEnabled, setSafeZonesEnabled] = useState(true);
  const [loadingSafeZones, setLoadingSafeZones] = useState(false);
  const [safeZonesError, setSafeZonesError] = useState<string | null>(null);
  const [safeZones, setSafeZones] = useState<SafeZone[]>([]);
  const [selectedSafeZone, setSelectedSafeZone] = useState<SafeZone | null>(null);

  // UI state
  const [showLegend, setShowLegend] = useState(false);
  const [viewportSpanWarning, setViewportSpanWarning] = useState(false);

  // Route state
  const [showRoute, setShowRoute] = useState(false);
  const [routeModalVisible, setRouteModalVisible] = useState(false);
  const [routeOriginName, setRouteOriginName] = useState('');
  const [routeDestName, setRouteDestName] = useState('');
  const [routeOriginLat, setRouteOriginLat] = useState('');
  const [routeOriginLon, setRouteOriginLon] = useState('');
  const [routeDestLat, setRouteDestLat] = useState('');
  const [routeDestLon, setRouteDestLon] = useState('');
  const [routeSearching, setRouteSearching] = useState(false);
  const [routeResponse, setRouteResponse] = useState<MapRouteResponse | null>(null);
  const [selectedRouteMode, setSelectedRouteMode] = useState<'FASTEST' | 'BALANCED' | 'SAFEST'>('SAFEST');
  const [routeError, setRouteError] = useState<string | null>(null);
  const [destSuggestions, setDestSuggestions] = useState<GeocodedPlace[]>([]);
  const [searchingPlaces, setSearchingPlaces] = useState(false);
  const destSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          setPermissionDenied(true);
          return;
        }
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        setUserLocation(loc);
        const userRegion: Region = {
          latitude: loc.coords.latitude,
          longitude: loc.coords.longitude,
          latitudeDelta: 0.02,
          longitudeDelta: 0.02,
        };
        setCurrentRegion(userRegion);
        mapRef.current?.animateToRegion(userRegion, 500);
        setRouteOriginLat(loc.coords.latitude.toString());
        setRouteOriginLon(loc.coords.longitude.toString());
        setRouteOriginName('My Location');
      } catch {
        setPermissionDenied(true);
      }
    })();
  }, []);

  const loadHeatmapForRegion = useCallback(async (region: Region) => {
    if (region.latitudeDelta > 0.14 || region.longitudeDelta > 0.14) {
      setViewportSpanWarning(true);
      return;
    }
    setViewportSpanWarning(false);
    setLoadingHeatmap(true);
    setHeatmapError(null);
    const minLat = Math.max(-90, region.latitude - region.latitudeDelta / 2);
    const maxLat = Math.min(90, region.latitude + region.latitudeDelta / 2);
    const minLon = Math.max(-180, region.longitude - region.longitudeDelta / 2);
    const maxLon = Math.min(180, region.longitude + region.longitudeDelta / 2);
    try {
      const data = await fetchHeatmap({ minLat, minLon, maxLat, maxLon, limit: 500 });
      setHeatmapData(data);
    } catch (err: any) {
      setHeatmapError(err.response?.data?.message || err.message || 'Failed to load heatmap.');
    } finally {
      setLoadingHeatmap(false);
    }
  }, []);

  const loadSafeZonesForRegion = useCallback(async (region: Region) => {
    if (region.latitudeDelta > 0.14 || region.longitudeDelta > 0.14) return;
    setLoadingSafeZones(true);
    setSafeZonesError(null);
    const minLat = Math.max(-90, region.latitude - region.latitudeDelta / 2);
    const maxLat = Math.min(90, region.latitude + region.latitudeDelta / 2);
    const minLon = Math.max(-180, region.longitude - region.longitudeDelta / 2);
    const maxLon = Math.min(180, region.longitude + region.longitudeDelta / 2);
    try {
      const data = await fetchSafeZonesInViewport({ minLat, minLon, maxLat, maxLon, limit: 50 });
      setSafeZones(data);
    } catch (err: any) {
      setSafeZonesError(err.response?.data?.message || err.message || 'Failed to load safe zones.');
    } finally {
      setLoadingSafeZones(false);
    }
  }, []);

  useEffect(() => { if (heatmapEnabled) loadHeatmapForRegion(currentRegion); }, [heatmapEnabled]);
  useEffect(() => { if (safeZonesEnabled) loadSafeZonesForRegion(currentRegion); }, [safeZonesEnabled]);

  const handleRegionChangeComplete = (region: Region) => {
    setCurrentRegion(region);
    if (fetchTimeoutRef.current) clearTimeout(fetchTimeoutRef.current);
    fetchTimeoutRef.current = setTimeout(() => {
      if (heatmapEnabled) loadHeatmapForRegion(region);
      if (safeZonesEnabled) loadSafeZonesForRegion(region);
    }, 600);
  };

  const jumpToBangalore = () => {
    setCurrentRegion(BANGALORE_CENTER);
    mapRef.current?.animateToRegion(BANGALORE_CENTER, 800);
    if (heatmapEnabled) loadHeatmapForRegion(BANGALORE_CENTER);
    if (safeZonesEnabled) loadSafeZonesForRegion(BANGALORE_CENTER);
  };

  const handleRecenterGPS = async () => {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setUserLocation(loc);
      const r = { latitude: loc.coords.latitude, longitude: loc.coords.longitude, latitudeDelta: 0.012, longitudeDelta: 0.012 };
      setCurrentRegion(r);
      mapRef.current?.animateToRegion(r, 500);
      if (heatmapEnabled) loadHeatmapForRegion(r);
      if (safeZonesEnabled) loadSafeZonesForRegion(r);
    } catch {
      if (userLocation) {
        const r = { latitude: userLocation.coords.latitude, longitude: userLocation.coords.longitude, latitudeDelta: 0.012, longitudeDelta: 0.012 };
        mapRef.current?.animateToRegion(r, 500);
      }
    }
  };

  const handleDestSearchChange = (text: string) => {
    setRouteDestName(text);
    if (destSearchTimer.current) clearTimeout(destSearchTimer.current);
    if (!text || text.trim().length < 2) { setDestSuggestions([]); return; }
    setSearchingPlaces(true);
    destSearchTimer.current = setTimeout(async () => {
      const results = await searchPlaces(text, parseFloat(routeOriginLat) || undefined, parseFloat(routeOriginLon) || undefined);
      setDestSuggestions(results);
      setSearchingPlaces(false);
    }, 350);
  };

  const handleDestSelect = (place: GeocodedPlace) => {
    setRouteDestName(place.displayName);
    setRouteDestLat(place.latitude.toString());
    setRouteDestLon(place.longitude.toString());
    setDestSuggestions([]);
  };

  const handleFindRoutes = async () => {
    const eLat = parseFloat(routeDestLat);
    const eLon = parseFloat(routeDestLon);
    const sLat = parseFloat(routeOriginLat) || currentRegion.latitude;
    const sLon = parseFloat(routeOriginLon) || currentRegion.longitude;
    if (isNaN(eLat) || isNaN(eLon)) { setRouteError('Please select a destination from the suggestions.'); return; }
    setRouteSearching(true);
    setRouteError(null);
    try {
      const response = await api.post('/routes/search', {
        startLat: sLat, startLon: sLon, endLat: eLat, endLon: eLon,
        travelMode: 'WALKING', routePreference: user?.routePreference || 'STANDARD',
      });
      setRouteResponse(response.data);
      if (user?.routePreference === 'FASTEST') setSelectedRouteMode('FASTEST');
      else if (['BALANCED', 'SOLO_TRAVELLER', 'TOURIST', 'ELDERLY'].includes(user?.routePreference || '')) setSelectedRouteMode('BALANCED');
      else setSelectedRouteMode('SAFEST');
      setRouteModalVisible(false);
      setShowRoute(true);
      const selRoute = response.data?.safestRoute || response.data?.fastestRoute;
      if (selRoute?.coordinates?.length > 0) mapRef.current?.fitBounds(selRoute.coordinates);
    } catch (err: any) {
      setRouteError(err.response?.data?.message || err.message || 'Failed to find routes. Please try again.');
    } finally {
      setRouteSearching(false);
    }
  };

  const getSelectedRoute = (): MapRouteResult | null => {
    if (!routeResponse) return null;
    if (selectedRouteMode === 'FASTEST') return routeResponse.fastestRoute;
    if (selectedRouteMode === 'BALANCED') return routeResponse.balancedRoute;
    return routeResponse.safestRoute;
  };

  const getRouteColor = (mode: string) => {
    switch (mode) {
      case 'SAFEST': return '#10B981';
      case 'BALANCED': return '#6366F1';
      case 'FASTEST': default: return '#0EA5E9';
    }
  };

  const formatDuration = (s: number | null) => s === null ? 'N/A' : `${Math.round(s / 60)} min`;
  const formatDistance = (m: number | null) => {
    if (m === null) return 'N/A';
    return m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`;
  };

  const selectedRoute = getSelectedRoute();
  const mapRoutes = routeResponse && showRoute ? [
    ...(routeResponse.fastestRoute?.coordinates?.length >= 2 ? [{ mode: 'FASTEST' as const, coordinates: routeResponse.fastestRoute.coordinates, colorHex: getRouteColor('FASTEST'), isSelected: selectedRouteMode === 'FASTEST' }] : []),
    ...(routeResponse.balancedRoute?.coordinates?.length >= 2 ? [{ mode: 'BALANCED' as const, coordinates: routeResponse.balancedRoute.coordinates, colorHex: getRouteColor('BALANCED'), isSelected: selectedRouteMode === 'BALANCED' }] : []),
    ...(routeResponse.safestRoute?.coordinates?.length >= 2 ? [{ mode: 'SAFEST' as const, coordinates: routeResponse.safestRoute.coordinates, colorHex: getRouteColor('SAFEST'), isSelected: selectedRouteMode === 'SAFEST' }] : []),
  ] : [];

  return (
    <View style={styles.container}>
      <LeafletMap
        ref={mapRef}
        style={styles.map}
        initialRegion={currentRegion}
        onRegionChangeComplete={handleRegionChangeComplete}
        userLocation={userLocation ? { latitude: userLocation.coords.latitude, longitude: userLocation.coords.longitude } : null}
        heatmapSegments={heatmapEnabled ? heatmapData?.segments : []}
        selectedSegmentId={selectedSegment?.segmentId}
        onSegmentSelect={(seg) => {
          setSelectedSafeZone(null);
          const full = heatmapData?.segments.find((s) => String(s.segmentId) === String(seg.segmentId));
          if (full) setSelectedSegment(full);
        }}
        safeZones={safeZonesEnabled ? safeZones : []}
        onSafeZoneSelect={(zone) => {
          setSelectedSegment(null);
          const full = safeZones.find((z) => String(z.id) === String(zone.id));
          if (full) setSelectedSafeZone(full);
        }}
        routes={mapRoutes}
        origin={selectedRoute?.coordinates?.[0] ?? null}
        destination={selectedRoute?.coordinates?.[selectedRoute.coordinates.length - 1] ?? null}
        sampleRouteCoords={null}
      />

      {/* Floating Top Header */}
      <View style={styles.topHeaderContainer}>
        <View style={styles.headerTitleRow}>
          <View style={styles.headerTitleBlock}>
            <Text style={styles.headerTitle}>SafePath AI</Text>
            {heatmapEnabled && (
              <View style={styles.aiStatusRow}>
                <View style={styles.aiStatusDot} />
                <Text style={styles.aiStatusText}>AI Risk Model Active</Text>
              </View>
            )}
          </View>
          <WeatherWidget latitude={currentRegion.latitude} longitude={currentRegion.longitude} compact={true} />
        </View>

        <View style={styles.controlsRow}>
          <TouchableOpacity
            style={[styles.controlBtn, heatmapEnabled ? styles.controlBtnActive : styles.controlBtnInactive]}
            onPress={() => { const n = !heatmapEnabled; setHeatmapEnabled(n); if (n) loadHeatmapForRegion(currentRegion); }}
            accessibilityRole="switch" accessibilityLabel="Toggle safety heatmap" accessibilityState={{ checked: heatmapEnabled }}
          >
            <Text style={[styles.controlBtnText, heatmapEnabled ? styles.controlBtnTextActive : styles.controlBtnTextInactive]}>
              {heatmapEnabled ? `Heatmap${heatmapData?.segments?.length ? ` (${heatmapData.segments.length})` : ''}` : 'Heatmap'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.controlBtn, safeZonesEnabled ? styles.safeZonesBtnActive : styles.controlBtnInactive]}
            onPress={() => { const n = !safeZonesEnabled; setSafeZonesEnabled(n); if (n) loadSafeZonesForRegion(currentRegion); }}
            accessibilityRole="switch" accessibilityLabel="Toggle safe zones" accessibilityState={{ checked: safeZonesEnabled }}
          >
            <Text style={[styles.controlBtnText, safeZonesEnabled ? styles.controlBtnTextActive : styles.controlBtnTextInactive]}>
              {safeZonesEnabled ? `Zones${safeZones?.length ? ` (${safeZones.length})` : ''}` : 'Zones'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.iconBtn} onPress={() => setShowLegend(!showLegend)} accessibilityRole="button" accessibilityLabel="Toggle legend">
            <Text style={styles.iconBtnText}>{showLegend ? 'Hide' : 'Legend'}</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.iconBtn, showRoute && styles.routeToggleActive]}
            onPress={() => { if (showRoute) { setShowRoute(false); setRouteResponse(null); } else { setRouteModalVisible(true); } }}
            accessibilityRole="button" accessibilityLabel="Toggle route"
          >
            <Text style={[styles.iconBtnText, showRoute && styles.iconBtnTextActive]}>
              {showRoute ? 'Route: ON' : 'Route'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Route Search Modal */}
      <Modal visible={routeModalVisible} animationType="slide" transparent onRequestClose={() => setRouteModalVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.routeModal}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Find a Route</Text>
              <TouchableOpacity onPress={() => setRouteModalVisible(false)}>
                <Text style={styles.modalCloseBtn}>✕</Text>
              </TouchableOpacity>
            </View>

            <Text style={styles.inputLabel}>FROM</Text>
            <View style={styles.inputBox}>
              <Text style={styles.originText} numberOfLines={1}>{routeOriginName || 'Current Location'}</Text>
            </View>

            <Text style={styles.inputLabel}>TO</Text>
            <View style={styles.inputBox}>
              <TextInput
                style={styles.destInput}
                placeholder="Search destination..."
                placeholderTextColor="#9CA3AF"
                value={routeDestName}
                onChangeText={handleDestSearchChange}
                autoFocus
                returnKeyType="search"
              />
              {routeDestName.length > 0 && (
                <TouchableOpacity onPress={() => { setRouteDestName(''); setRouteDestLat(''); setRouteDestLon(''); setDestSuggestions([]); }}>
                  <Text style={styles.clearText}>✕</Text>
                </TouchableOpacity>
              )}
            </View>

            {(searchingPlaces || destSuggestions.length > 0) && (
              <View style={styles.suggestionsBox}>
                {searchingPlaces && (
                  <View style={styles.searchingRow}><ActivityIndicator size="small" color="#0EA5E9" /><Text style={styles.searchingText}>Searching...</Text></View>
                )}
                {destSuggestions.map((item) => (
                  <TouchableOpacity key={item.id} style={styles.suggestionRow} onPress={() => handleDestSelect(item)}>
                    <Text style={styles.suggestionPin}>&#9679;</Text>
                    <View style={styles.suggestionTexts}>
                      <Text style={styles.suggestionName} numberOfLines={1}>{item.name}</Text>
                      <Text style={styles.suggestionAddr} numberOfLines={1}>{item.displayName}</Text>
                    </View>
                  </TouchableOpacity>
                ))}
              </View>
            )}

            {routeError && <Text style={styles.routeErrorText}>{routeError}</Text>}

            <TouchableOpacity style={[styles.findRouteBtn, routeSearching && { opacity: 0.6 }]} onPress={handleFindRoutes} disabled={routeSearching}>
              {routeSearching ? <ActivityIndicator color="#FFFFFF" size="small" /> : <Text style={styles.findRouteBtnText}>Find Routes</Text>}
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* Route Mode Selector bar */}
      {showRoute && routeResponse && (
        <View style={styles.routeModeBar}>
          {(['FASTEST', 'BALANCED', 'SAFEST'] as const).map((mode) => {
            const r = mode === 'FASTEST' ? routeResponse.fastestRoute : mode === 'BALANCED' ? routeResponse.balancedRoute : routeResponse.safestRoute;
            if (!r) return null;
            const isSelected = selectedRouteMode === mode;
            const isPreferred = user?.routePreference === mode ||
              (['SOLO_TRAVELLER', 'TOURIST', 'ELDERLY'].includes(user?.routePreference || '') && mode === 'BALANCED');
            return (
              <TouchableOpacity
                key={mode}
                style={[styles.routeModeChip, isSelected && { backgroundColor: getRouteColor(mode), borderColor: getRouteColor(mode) }, !isSelected && isPreferred && styles.routeModeChipPreferred]}
                onPress={() => { setSelectedRouteMode(mode); if (r.coordinates?.length > 0) mapRef.current?.fitBounds(r.coordinates); }}
              >
                {isPreferred && !isSelected && <View style={[styles.preferredDot, { backgroundColor: getRouteColor(mode) }]} />}
                <Text style={[styles.routeModeChipLabel, isSelected && { color: '#FFF' }]}>{mode}</Text>
                <Text style={[styles.routeModeChipTime, isSelected && { color: 'rgba(255,255,255,0.85)' }]}>{formatDuration(r.durationSeconds)}</Text>
                {r.safetyScore !== null && r.safetyScore !== undefined && (
                  <Text style={[styles.routeModeChipScore, isSelected && { color: 'rgba(255,255,255,0.75)' }]}>{Math.round(r.safetyScore)}/100</Text>
                )}
              </TouchableOpacity>
            );
          })}
        </View>
      )}

      {/* Banners */}
      {heatmapEnabled && viewportSpanWarning && (
        <View style={styles.warningBanner}>
          <Text style={styles.warningBannerText}>Zoom in to view the safety heatmap.</Text>
          <TouchableOpacity style={styles.jumpBtn} onPress={jumpToBangalore}><Text style={styles.jumpBtnText}>Zoom to Bangalore</Text></TouchableOpacity>
        </View>
      )}
      {heatmapEnabled && loadingHeatmap && (
        <View style={styles.loadingBanner}>
          <ActivityIndicator size="small" color="#0EA5E9" /><Text style={styles.loadingBannerText}>Updating heatmap...</Text>
        </View>
      )}
      {heatmapEnabled && heatmapError && !loadingHeatmap && (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>{heatmapError}</Text>
          <TouchableOpacity style={styles.retryBtn} onPress={() => loadHeatmapForRegion(currentRegion)}><Text style={styles.retryBtnText}>Retry</Text></TouchableOpacity>
        </View>
      )}
      {heatmapEnabled && !loadingHeatmap && !viewportSpanWarning && heatmapData && heatmapData.segments.length === 0 && (
        <View style={styles.emptyBanner}>
          <Text style={styles.emptyBannerText}>No road segments in this area.</Text>
          <TouchableOpacity style={styles.jumpBtn} onPress={jumpToBangalore}><Text style={styles.jumpBtnText}>Jump to Bangalore</Text></TouchableOpacity>
        </View>
      )}
      {permissionDenied && (
        <View style={styles.permissionPill}><Text style={styles.permissionPillText}>Location access denied — showing default view</Text></View>
      )}

      {/* Legend Card */}
      {showLegend && (
        <View style={styles.legendCard}>
          <View style={styles.legendHeaderRow}>
            <Text style={styles.legendTitle}>Safety Heatmap Legend</Text>
            <TouchableOpacity onPress={() => setShowLegend(false)}><Text style={styles.closeBtnText}>✕</Text></TouchableOpacity>
          </View>
          <ScrollView style={styles.legendScroll} showsVerticalScrollIndicator={false}>
            {[
              { color: '#10B981', title: 'Very Low / Low Risk', desc: 'Safety Score 60+ — Relatively safer area' },
              { color: '#F59E0B', title: 'Moderate Risk', desc: 'Safety Score 40–59 — Use caution' },
              { color: '#EF4444', title: 'High Risk', desc: 'Safety Score 20–39 — Avoid if possible' },
              { color: '#991B1B', title: 'Very High Risk', desc: 'Safety Score below 20 — High risk area' },
              { color: '#6B7280', title: 'Unscored', desc: 'Missing ML data — never assumed safe', dashed: true },
            ].map((item) => (
              <View key={item.title} style={styles.legendItem}>
                <View style={[styles.colorBox, { backgroundColor: item.color }, item.dashed ? { borderStyle: 'dashed', borderWidth: 1, borderColor: '#374151' } : {}]} />
                <View style={styles.legendTextCol}>
                  <Text style={styles.legendCategoryTitle}>{item.title}</Text>
                  <Text style={styles.legendCategoryDesc}>{item.desc}</Text>
                </View>
              </View>
            ))}

            <View style={styles.legendSectionDivider} />
            <Text style={styles.legendSectionHeader}>Verified Safe Zones</Text>
            {[
              { label: 'P', bg: '#1D68BD', name: 'Police Station', desc: 'Blue marker — Law enforcement' },
              { label: 'H', bg: '#DC2626', name: 'Hospital / Clinic', desc: 'Red marker — Emergency medical' },
              { label: 'Rx', bg: '#059669', name: 'Pharmacy', desc: 'Green marker — First aid' },
              { label: 'T', bg: '#7C3AED', name: 'Transit Hub', desc: 'Purple marker — Staffed transit' },
              { label: 'i', bg: '#D97706', name: 'Public Assistance', desc: 'Amber marker — Community help' },
            ].map((item) => (
              <View key={item.label} style={styles.legendItem}>
                <View style={[styles.legendIconCircle, { backgroundColor: item.bg }]}>
                  <Text style={styles.legendIconCircleText}>{item.label}</Text>
                </View>
                <View style={styles.legendTextCol}>
                  <Text style={styles.legendCategoryTitle}>{item.name}</Text>
                  <Text style={styles.legendCategoryDesc}>{item.desc}</Text>
                </View>
              </View>
            ))}

            <View style={styles.disclaimerContainer}>
              <Text style={styles.disclaimerHeading}>Academic & Data Integrity Notice</Text>
              <Text style={styles.disclaimerBody}>Scores derived from synthetic training features. Not for real-world life safety decisions.</Text>
              <Text style={styles.disclaimerBody}>Green = relatively lower predicted risk, NOT guaranteed absence of crime.</Text>
              <Text style={styles.disclaimerBody}>Segments without valid ML predictions are labelled UNSCORED — never defaulted safe.</Text>
            </View>
          </ScrollView>
        </View>
      )}

      {/* Selected Segment Detail Card */}
      {selectedSegment && (
        <Card style={styles.segmentDetailCard}>
          <View style={styles.segmentDetailHeader}>
            <View style={styles.segmentTitleCol}>
              <Text style={styles.segmentName} numberOfLines={1}>{selectedSegment.name || `Segment #${selectedSegment.segmentId}`}</Text>
              <Text style={styles.segmentSubtitle}>{selectedSegment.highwayType || 'unknown'}{selectedSegment.lengthMeters ? ` • ${Math.round(selectedSegment.lengthMeters)}m` : ''}</Text>
            </View>
            <TouchableOpacity onPress={() => setSelectedSegment(null)}><Text style={styles.closeBtnText}>✕</Text></TouchableOpacity>
          </View>
          <View style={styles.segmentStatsRow}>
            <View style={[styles.categoryBadge, { backgroundColor: selectedSegment.colorHex }]}><Text style={styles.categoryBadgeText}>{selectedSegment.riskCategory}</Text></View>
            <View style={styles.statBox}><Text style={styles.statLabel}>Safety</Text><Text style={styles.statValue}>{selectedSegment.safetyScore !== null ? `${Math.round(selectedSegment.safetyScore)}/100` : 'N/A'}</Text></View>
            <View style={styles.statBox}><Text style={styles.statLabel}>Risk</Text><Text style={styles.statValue}>{selectedSegment.relativeRiskScore !== null ? selectedSegment.relativeRiskScore.toFixed(2) : 'N/A'}</Text></View>
            <View style={styles.statBox}><Text style={styles.statLabel}>Status</Text><Text style={styles.statValue}>{selectedSegment.scoringStatus}</Text></View>
          </View>
          <View style={styles.attributesRow}>
            <Text style={styles.attributeText}>Lighting: {selectedSegment.isLit === null ? 'Unknown' : selectedSegment.isLit ? 'Lit' : 'Unlit'}</Text>
            <Text style={styles.attributeText}>Surface: {selectedSegment.surface || 'Unknown'}</Text>
            <Text style={styles.attributeText}>Model: {selectedSegment.modelVersion || 'None'}</Text>
          </View>
          <View style={styles.segmentDisclaimerBox}><Text style={styles.segmentDisclaimerText}>AI Relative Risk Assessment (XGBoost v1.0). Missing data is never defaulted safe.</Text></View>
        </Card>
      )}

      {/* Selected Safe Zone Detail Card */}
      {selectedSafeZone && (
        <Card style={styles.safeZoneDetailCard}>
          <View style={styles.safeZoneCardHeader}>
            <View style={styles.safeZoneTitleCol}>
              <Text style={styles.safeZoneName} numberOfLines={1}>{selectedSafeZone.name}</Text>
              <View style={styles.safeZoneBadgesRow}>
                <View style={[styles.safeZoneBadge, { backgroundColor: (SAFE_ZONE_CATEGORIES[selectedSafeZone.zoneType] || SAFE_ZONE_CATEGORIES.OTHER).bgHex }]}>
                  <Text style={[styles.safeZoneBadgeText, { color: (SAFE_ZONE_CATEGORIES[selectedSafeZone.zoneType] || SAFE_ZONE_CATEGORIES.OTHER).pinColor }]}>
                    {(SAFE_ZONE_CATEGORIES[selectedSafeZone.zoneType] || SAFE_ZONE_CATEGORIES.OTHER).icon}{' '}{(SAFE_ZONE_CATEGORIES[selectedSafeZone.zoneType] || SAFE_ZONE_CATEGORIES.OTHER).label}
                  </Text>
                </View>
                <View style={[styles.safeZoneBadge, { backgroundColor: (SAFE_ZONE_VERIFICATION[selectedSafeZone.verificationStatus] || SAFE_ZONE_VERIFICATION.UNVERIFIED).bgHex }]}>
                  <Text style={[styles.safeZoneBadgeText, { color: (SAFE_ZONE_VERIFICATION[selectedSafeZone.verificationStatus] || SAFE_ZONE_VERIFICATION.UNVERIFIED).badgeColor }]}>
                    {(SAFE_ZONE_VERIFICATION[selectedSafeZone.verificationStatus] || SAFE_ZONE_VERIFICATION.UNVERIFIED).label}
                  </Text>
                </View>
              </View>
            </View>
            <TouchableOpacity onPress={() => setSelectedSafeZone(null)}><Text style={styles.closeBtnText}>✕</Text></TouchableOpacity>
          </View>
          <View style={styles.safeZoneInfoRow}><Text style={styles.safeZoneInfoLabel}>Address:</Text><Text style={styles.safeZoneInfoValue}>{selectedSafeZone.address || 'Not documented'}</Text></View>
          <View style={styles.safeZoneInfoRow}><Text style={styles.safeZoneInfoLabel}>Hours:</Text><Text style={styles.safeZoneInfoValue}>{selectedSafeZone.operatingHours || (selectedSafeZone.is24h ? 'Open 24/7' : 'Not documented')}</Text></View>
          <View style={styles.safeZoneInfoRow}><Text style={styles.safeZoneInfoLabel}>Source:</Text><Text style={styles.safeZoneInfoSubtle}>{selectedSafeZone.source || 'OpenStreetMap'}{selectedSafeZone.osmNodeId ? ` (#${selectedSafeZone.osmNodeId})` : ''}</Text></View>
          <View style={styles.safeZoneDisclaimerBox}><Text style={styles.safeZoneDisclaimerText}>Existence in public spatial records does NOT guarantee personal safety, active staffing, or current operational status.</Text></View>
          <View style={styles.safeZoneActionRow}>
            <TouchableOpacity style={styles.navigateActionBtn} onPress={() => router.push({ pathname: '/(tabs)/routes', params: { endLat: selectedSafeZone.latitude.toString(), endLon: selectedSafeZone.longitude.toString(), endName: selectedSafeZone.name } })}>
              <Text style={styles.navigateActionBtnText}>Get Directions</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.directoryLinkBtn} onPress={() => router.push('/safe-zones')}>
              <Text style={styles.directoryLinkBtnText}>Directory</Text>
            </TouchableOpacity>
          </View>
        </Card>
      )}

      {/* GPS Recenter FAB */}
      <TouchableOpacity style={styles.recenterGpsFab} onPress={handleRecenterGPS} accessibilityRole="button" accessibilityLabel="Recenter map to GPS position">
        <Ionicons name="locate" size={22} color="#0EA5E9" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F1F5F9' },
  map: { width: '100%', height: '100%' },
  topHeaderContainer: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 52 : 36,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(255,255,255,0.97)',
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 6,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.05)',
  },
  headerTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  headerTitleBlock: { flexDirection: 'column' },
  headerTitle: { fontSize: 18, fontWeight: '800', color: '#0F172A', letterSpacing: -0.4 },
  aiStatusRow: { flexDirection: 'row', alignItems: 'center', marginTop: 4, gap: 5 },
  aiStatusDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#10B981' },
  aiStatusText: { fontSize: 10, fontWeight: '600', color: '#10B981', letterSpacing: 0.5, textTransform: 'uppercase' },
  controlsRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  controlBtn: {
    flex: 1,
    paddingVertical: 7,
    paddingHorizontal: 6,
    borderRadius: 10,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  controlBtnActive: { backgroundColor: '#0EA5E9', borderColor: '#0EA5E9' },
  controlBtnInactive: { backgroundColor: '#F1F5F9', borderColor: '#E2E8F0' },
  safeZonesBtnActive: { backgroundColor: '#1E40AF', borderColor: '#1E40AF' },
  controlBtnText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.1 },
  controlBtnTextActive: { color: '#FFFFFF' },
  controlBtnTextInactive: { color: '#475569' },
  iconBtn: {
    paddingVertical: 7,
    paddingHorizontal: 10,
    borderRadius: 10,
    backgroundColor: '#F1F5F9',
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  iconBtnText: { fontSize: 11, fontWeight: '700', color: '#475569' },
  routeToggleActive: { backgroundColor: '#10B981', borderColor: '#10B981' },
  iconBtnTextActive: { color: '#FFFFFF' },
  // Route mode bar
  routeModeBar: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 164 : 148,
    left: 12,
    right: 12,
    flexDirection: 'row',
    gap: 6,
  },
  routeModeChip: {
    flex: 1,
    backgroundColor: 'rgba(255,255,255,0.97)',
    borderRadius: 12,
    paddingVertical: 8,
    paddingHorizontal: 4,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#E2E8F0',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 3,
  },
  routeModeChipPreferred: { borderColor: '#CBD5E1' },
  preferredDot: { width: 5, height: 5, borderRadius: 2.5, marginBottom: 3 },
  routeModeChipLabel: { fontSize: 9, fontWeight: '800', color: '#0F172A', letterSpacing: 0.6, textTransform: 'uppercase' },
  routeModeChipTime: { fontSize: 13, fontWeight: '700', color: '#1E293B', marginTop: 2 },
  routeModeChipScore: { fontSize: 10, color: '#6B7280', marginTop: 1 },
  // Modal
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  routeModal: { backgroundColor: '#FFFFFF', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, paddingBottom: 36 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  modalTitle: { fontSize: 18, fontWeight: '800', color: '#0F172A', letterSpacing: -0.3 },
  modalCloseBtn: { fontSize: 18, color: '#94A3B8', fontWeight: '600', paddingHorizontal: 4 },
  inputLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', letterSpacing: 1.2, textTransform: 'uppercase', marginBottom: 6 },
  inputBox: {
    backgroundColor: '#F8FAFC', borderRadius: 12, borderWidth: 1, borderColor: '#E2E8F0',
    paddingHorizontal: 14, paddingVertical: 12, marginBottom: 14, flexDirection: 'row', alignItems: 'center',
  },
  originText: { fontSize: 14, color: '#64748B', flex: 1 },
  destInput: { flex: 1, fontSize: 14, color: '#0F172A', paddingVertical: 0 },
  clearText: { fontSize: 14, color: '#94A3B8', fontWeight: '600', paddingLeft: 8 },
  suggestionsBox: {
    backgroundColor: '#FFF', borderRadius: 12, borderWidth: 1, borderColor: '#E2E8F0',
    marginBottom: 14, maxHeight: 200, overflow: 'hidden',
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.08, shadowRadius: 6, elevation: 4,
  },
  searchingRow: { flexDirection: 'row', alignItems: 'center', padding: 12, gap: 8, backgroundColor: '#F8FAFC' },
  searchingText: { fontSize: 13, color: '#64748B' },
  suggestionRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#F1F5F9' },
  suggestionPin: { fontSize: 10, color: '#94A3B8', marginRight: 10 },
  suggestionTexts: { flex: 1 },
  suggestionName: { fontSize: 14, fontWeight: '600', color: '#0F172A' },
  suggestionAddr: { fontSize: 12, color: '#94A3B8', marginTop: 1 },
  routeErrorText: { fontSize: 13, color: '#EF4444', marginBottom: 10 },
  findRouteBtn: { backgroundColor: '#0F172A', borderRadius: 14, paddingVertical: 16, alignItems: 'center', marginTop: 4 },
  findRouteBtnText: { fontSize: 15, fontWeight: '800', color: '#FFFFFF', letterSpacing: 0.3 },
  // Banners
  warningBanner: { position: 'absolute', top: 152, left: 12, right: 12, backgroundColor: 'rgba(245,158,11,0.95)', borderRadius: 10, padding: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', elevation: 4 },
  warningBannerText: { color: '#FFF', fontSize: 12, fontWeight: '600', flex: 1, marginRight: 6 },
  loadingBanner: { position: 'absolute', top: 152, alignSelf: 'center', backgroundColor: 'rgba(255,255,255,0.97)', paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, flexDirection: 'row', alignItems: 'center', gap: 8, elevation: 3, shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.1, shadowRadius: 4 },
  loadingBannerText: { fontSize: 12, color: '#374151', fontWeight: '500' },
  errorBanner: { position: 'absolute', top: 152, left: 12, right: 12, backgroundColor: 'rgba(239,68,68,0.95)', borderRadius: 10, padding: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', elevation: 4 },
  errorBannerText: { color: '#FFF', fontSize: 12, flex: 1, marginRight: 8 },
  retryBtn: { backgroundColor: '#FFF', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6 },
  retryBtnText: { color: '#EF4444', fontWeight: '700', fontSize: 12 },
  emptyBanner: { position: 'absolute', top: 152, left: 12, right: 12, backgroundColor: 'rgba(15,23,42,0.88)', borderRadius: 10, padding: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', elevation: 4 },
  emptyBannerText: { color: '#FFF', fontSize: 12, flex: 1 },
  jumpBtn: { backgroundColor: '#0EA5E9', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 },
  jumpBtnText: { color: '#FFF', fontSize: 11, fontWeight: '700' },
  permissionPill: { position: 'absolute', bottom: 24, alignSelf: 'center', backgroundColor: 'rgba(15,23,42,0.78)', paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20 },
  permissionPillText: { color: '#FFF', fontSize: 11 },
  // Legend
  legendCard: { position: 'absolute', bottom: 20, left: 12, right: 12, maxHeight: Dimensions.get('window').height * 0.52, backgroundColor: 'rgba(255,255,255,0.98)', borderRadius: 20, padding: 16, shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.14, shadowRadius: 12, elevation: 8, borderWidth: 1, borderColor: 'rgba(0,0,0,0.05)' },
  legendHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  legendTitle: { fontSize: 15, fontWeight: '800', color: '#0F172A' },
  closeBtnText: { fontSize: 18, fontWeight: '600', color: '#94A3B8', paddingHorizontal: 4 },
  legendScroll: { maxHeight: Dimensions.get('window').height * 0.42 },
  legendItem: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  colorBox: { width: 20, height: 10, borderRadius: 3, marginRight: 10 },
  legendIconCircle: { width: 24, height: 24, borderRadius: 12, marginRight: 10, alignItems: 'center', justifyContent: 'center' },
  legendIconCircleText: { color: '#FFF', fontSize: 9, fontWeight: '800' },
  legendTextCol: { flex: 1 },
  legendCategoryTitle: { fontSize: 13, fontWeight: '600', color: '#0F172A' },
  legendCategoryDesc: { fontSize: 11, color: '#64748B', marginTop: 1 },
  disclaimerContainer: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: '#F1F5F9' },
  disclaimerHeading: { fontSize: 11, fontWeight: '800', color: '#D97706', marginBottom: 4 },
  disclaimerBody: { fontSize: 10, color: '#475569', lineHeight: 15, marginBottom: 3 },
  legendSectionDivider: { height: 1, backgroundColor: '#F1F5F9', marginVertical: 10 },
  legendSectionHeader: { fontSize: 13, fontWeight: '800', color: '#0F172A', marginBottom: 8 },
  // Segment Detail
  segmentDetailCard: { position: 'absolute', bottom: 20, left: 12, right: 12, backgroundColor: '#FFF', borderRadius: 18, padding: 14, elevation: 8, shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.12, shadowRadius: 10 },
  segmentDetailHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 },
  segmentTitleCol: { flex: 1, marginRight: 8 },
  segmentName: { fontSize: 15, fontWeight: '700', color: '#0F172A' },
  segmentSubtitle: { fontSize: 12, color: '#64748B', marginTop: 2 },
  segmentStatsRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10, backgroundColor: '#F8FAFC', padding: 10, borderRadius: 10 },
  categoryBadge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  categoryBadgeText: { color: '#FFF', fontSize: 11, fontWeight: '700' },
  statBox: { alignItems: 'center' },
  statLabel: { fontSize: 10, color: '#94A3B8', fontWeight: '500' },
  statValue: { fontSize: 13, fontWeight: '700', color: '#0F172A' },
  attributesRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  attributeText: { fontSize: 11, color: '#475569' },
  segmentDisclaimerBox: { backgroundColor: '#FFFBEB', borderRadius: 8, padding: 8, borderWidth: 1, borderColor: '#FDE68A' },
  segmentDisclaimerText: { fontSize: 10, color: '#92400E', lineHeight: 14 },
  // Safe Zone Detail
  safeZoneDetailCard: { position: 'absolute', bottom: 20, left: 12, right: 12, backgroundColor: '#FFF', borderRadius: 18, padding: 14, elevation: 8, shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.12, shadowRadius: 10 },
  safeZoneCardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 },
  safeZoneTitleCol: { flex: 1, marginRight: 8 },
  safeZoneName: { fontSize: 15, fontWeight: '700', color: '#0F172A', marginBottom: 4 },
  safeZoneBadgesRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  safeZoneBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  safeZoneBadgeText: { fontSize: 11, fontWeight: '600' },
  safeZoneInfoRow: { flexDirection: 'row', marginBottom: 4 },
  safeZoneInfoLabel: { fontSize: 12, color: '#94A3B8', width: 80, fontWeight: '500' },
  safeZoneInfoValue: { flex: 1, fontSize: 12, color: '#1F2937' },
  safeZoneInfoSubtle: { flex: 1, fontSize: 11, color: '#9CA3AF' },
  safeZoneDisclaimerBox: { backgroundColor: '#FFFBEB', borderRadius: 8, padding: 8, borderWidth: 1, borderColor: '#FDE68A', marginTop: 6, marginBottom: 10 },
  safeZoneDisclaimerText: { fontSize: 10, color: '#92400E', lineHeight: 14 },
  safeZoneActionRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  navigateActionBtn: { flex: 2, backgroundColor: '#0F172A', paddingVertical: 11, borderRadius: 10, alignItems: 'center' },
  navigateActionBtnText: { color: '#FFF', fontSize: 13, fontWeight: '700' },
  directoryLinkBtn: { flex: 1, backgroundColor: '#F8FAFC', borderWidth: 1, borderColor: '#E2E8F0', paddingVertical: 11, borderRadius: 10, alignItems: 'center' },
  directoryLinkBtnText: { color: '#374151', fontSize: 13, fontWeight: '600' },
  // GPS FAB
  recenterGpsFab: { position: 'absolute', right: 16, bottom: 36, backgroundColor: '#FFF', width: 48, height: 48, borderRadius: 24, justifyContent: 'center', alignItems: 'center', elevation: 6, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.16, shadowRadius: 6, borderWidth: 1, borderColor: '#E2E8F0', zIndex: 100 },
  recenterRing: { width: 18, height: 18, borderRadius: 9, borderWidth: 3, borderColor: '#0EA5E9', backgroundColor: 'transparent' },
});
