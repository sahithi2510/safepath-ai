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
} from 'react-native';
import MapView, { Marker, Polyline, Region } from 'react-native-maps';
import * as Location from 'expo-location';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import {
  fetchHeatmap,
  HeatmapSegment,
  HeatmapResponse,
  Coordinate,
} from '../../services/heatmap';

const DEFAULT_REGION: Region = {
  latitude: 12.9756,
  longitude: 77.6066,
  latitudeDelta: 0.025,
  longitudeDelta: 0.025,
};

// Preset Bangalore demo coordinates where road network data is loaded
const BANGALORE_CENTER: Region = {
  latitude: 12.9756,
  longitude: 77.6066,
  latitudeDelta: 0.02,
  longitudeDelta: 0.02,
};

// Demo route coordinates for testing route-heatmap coexistence
const SAMPLE_ROUTE_COORDS: Coordinate[] = [
  { latitude: 12.9756, longitude: 77.6066 },
  { latitude: 12.9740, longitude: 77.6070 },
  { latitude: 12.9725, longitude: 77.6072 },
  { latitude: 12.9716, longitude: 77.6074 },
];

export default function MapScreen() {
  const mapRef = useRef<MapView | null>(null);

  // User location and permissions
  const [userLocation, setUserLocation] = useState<Location.LocationObject | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [currentRegion, setCurrentRegion] = useState<Region>(DEFAULT_REGION);

  // Heatmap state
  const [heatmapEnabled, setHeatmapEnabled] = useState(true);
  const [loadingHeatmap, setLoadingHeatmap] = useState(false);
  const [heatmapError, setHeatmapError] = useState<string | null>(null);
  const [heatmapData, setHeatmapData] = useState<HeatmapResponse | null>(null);
  const [selectedSegment, setSelectedSegment] = useState<HeatmapSegment | null>(null);

  // UI state
  const [showLegend, setShowLegend] = useState(false);
  const [showSampleRoute, setShowSampleRoute] = useState(false);
  const [viewportSpanWarning, setViewportSpanWarning] = useState(false);

  // Debounce timer for viewport changes
  const fetchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Request location on mount
  useEffect(() => {
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          setPermissionDenied(true);
          return;
        }

        const loc = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        setUserLocation(loc);

        // Center on user location if available
        const userRegion: Region = {
          latitude: loc.coords.latitude,
          longitude: loc.coords.longitude,
          latitudeDelta: 0.02,
          longitudeDelta: 0.02,
        };
        setCurrentRegion(userRegion);
        mapRef.current?.animateToRegion(userRegion, 500);
      } catch (err) {
        // Fall back gracefully to Bangalore default coordinates
        setPermissionDenied(true);
      }
    })();
  }, []);

  // Fetch heatmap for a specific geographic bounding box
  const loadHeatmapForRegion = useCallback(async (region: Region) => {
    // Max span enforcement: Backend enforces 0.15 degrees
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
      const data = await fetchHeatmap({
        minLat,
        minLon,
        maxLat,
        maxLon,
        limit: 500,
      });
      setHeatmapData(data);
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to connect to safety heatmap service.';
      setHeatmapError(message);
    } finally {
      setLoadingHeatmap(false);
    }
  }, []);

  // Initial fetch when map is mounted and heatmap is enabled
  useEffect(() => {
    if (heatmapEnabled) {
      loadHeatmapForRegion(currentRegion);
    }
  }, [heatmapEnabled]);

  // Handle region change with debounce
  const handleRegionChangeComplete = (region: Region) => {
    setCurrentRegion(region);
    if (!heatmapEnabled) return;

    if (fetchTimeoutRef.current) {
      clearTimeout(fetchTimeoutRef.current);
    }

    fetchTimeoutRef.current = setTimeout(() => {
      loadHeatmapForRegion(region);
    }, 600);
  };

  const jumpToBangalore = () => {
    setCurrentRegion(BANGALORE_CENTER);
    mapRef.current?.animateToRegion(BANGALORE_CENTER, 800);
    if (heatmapEnabled) {
      loadHeatmapForRegion(BANGALORE_CENTER);
    }
  };

  return (
    <View style={styles.container}>
      {/* Interactive Map */}
      <MapView
        ref={mapRef}
        style={styles.map}
        initialRegion={currentRegion}
        onRegionChangeComplete={handleRegionChangeComplete}
        showsUserLocation={!permissionDenied && !!userLocation}
        showsMyLocationButton={!permissionDenied}
      >
        {/* User location marker if available */}
        {userLocation && (
          <Marker
            coordinate={{
              latitude: userLocation.coords.latitude,
              longitude: userLocation.coords.longitude,
            }}
            title="You are here"
            accessibilityLabel="Current user location"
          />
        )}

        {/* Heatmap Road Segment Overlays */}
        {heatmapEnabled &&
          heatmapData?.segments.map((segment) => {
            const isSelected = selectedSegment?.segmentId === segment.segmentId;
            const isUnscored = segment.scoringStatus !== 'SCORED';

            return (
              <Polyline
                key={`segment-${segment.segmentId}`}
                coordinates={segment.coordinates}
                strokeColor={isSelected ? '#007AFF' : segment.colorHex}
                strokeWidth={isSelected ? 6 : 4}
                lineDashPattern={isUnscored ? [6, 4] : undefined}
                tappable={true}
                onPress={() => setSelectedSegment(segment)}
              />
            );
          })}

        {/* Coexisting Route Polyline (Demonstration of route-heatmap coexistence) */}
        {showSampleRoute && (
          <>
            <Polyline
              coordinates={SAMPLE_ROUTE_COORDS}
              strokeColor="#007AFF"
              strokeWidth={7}
            />
            <Marker
              coordinate={SAMPLE_ROUTE_COORDS[0]}
              title="Route Start"
              pinColor="green"
            />
            <Marker
              coordinate={SAMPLE_ROUTE_COORDS[SAMPLE_ROUTE_COORDS.length - 1]}
              title="Route Destination"
              pinColor="red"
            />
          </>
        )}
      </MapView>

      {/* Floating Top Header Bar */}
      <View style={styles.topHeaderContainer}>
        <View style={styles.headerTitleRow}>
          <Text style={styles.headerTitle}>SafePath AI</Text>
          <View style={styles.headerBadgesRow}>
            {heatmapEnabled && (
              <View style={styles.demoBadge}>
                <Text style={styles.demoBadgeText}>DEMO ML MODEL</Text>
              </View>
            )}
          </View>
        </View>

        {/* Control Buttons */}
        <View style={styles.controlsRow}>
          <TouchableOpacity
            style={[
              styles.controlBtn,
              heatmapEnabled ? styles.controlBtnActive : styles.controlBtnInactive,
            ]}
            onPress={() => {
              const nextState = !heatmapEnabled;
              setHeatmapEnabled(nextState);
              if (nextState) {
                loadHeatmapForRegion(currentRegion);
              }
            }}
            accessibilityRole="switch"
            accessibilityLabel="Toggle safety heatmap layer"
            accessibilityState={{ checked: heatmapEnabled }}
          >
            <Text
              style={[
                styles.controlBtnText,
                heatmapEnabled ? styles.controlBtnTextActive : styles.controlBtnTextInactive,
              ]}
            >
              {heatmapEnabled ? '🔥 Heatmap: ON' : '⚪ Heatmap: OFF'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.legendToggleBtn}
            onPress={() => setShowLegend(!showLegend)}
            accessibilityRole="button"
            accessibilityLabel="Toggle safety heatmap legend and disclaimers"
          >
            <Text style={styles.legendToggleBtnText}>
              {showLegend ? 'Hide Legend' : '📖 Legend'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.routeToggleBtn,
              showSampleRoute ? styles.routeToggleActive : null,
            ]}
            onPress={() => setShowSampleRoute(!showSampleRoute)}
            accessibilityRole="button"
            accessibilityLabel="Toggle route coexistence demo"
          >
            <Text style={styles.routeToggleBtnText}>
              {showSampleRoute ? 'Hide Route' : '🧭 Route Test'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Viewport Span Warning (When zoomed out beyond 0.15 deg) */}
      {heatmapEnabled && viewportSpanWarning && (
        <View style={styles.warningBanner} accessibilityRole="alert">
          <Text style={styles.warningBannerText}>
            🔍 Viewport too wide for segment safety. Zoom in closer to view heatmap.
          </Text>
          <TouchableOpacity
            style={styles.jumpBtn}
            onPress={jumpToBangalore}
            accessibilityRole="button"
            accessibilityLabel="Zoom to central Bangalore"
          >
            <Text style={styles.jumpBtnText}>Zoom to Bangalore</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Loading Indicator */}
      {heatmapEnabled && loadingHeatmap && (
        <View style={styles.loadingBanner} accessibilityLiveRegion="polite">
          <ActivityIndicator size="small" color="#007AFF" />
          <Text style={styles.loadingBannerText}>Updating safety heatmap...</Text>
        </View>
      )}

      {/* Network Error Card */}
      {heatmapEnabled && heatmapError && !loadingHeatmap && (
        <View style={styles.errorBanner} accessibilityRole="alert">
          <Text style={styles.errorBannerText}>{heatmapError}</Text>
          <TouchableOpacity
            style={styles.retryBtn}
            onPress={() => loadHeatmapForRegion(currentRegion)}
            accessibilityRole="button"
            accessibilityLabel="Retry loading heatmap"
          >
            <Text style={styles.retryBtnText}>Retry</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Empty State Banner */}
      {heatmapEnabled &&
        !loadingHeatmap &&
        !viewportSpanWarning &&
        heatmapData &&
        heatmapData.segments.length === 0 && (
          <View style={styles.emptyBanner}>
            <Text style={styles.emptyBannerText}>
              No road network segments in this area.
            </Text>
            <TouchableOpacity
              style={styles.jumpBtn}
              onPress={jumpToBangalore}
              accessibilityRole="button"
              accessibilityLabel="Jump to central Bangalore"
            >
              <Text style={styles.jumpBtnText}>Jump to Bangalore MG Rd</Text>
            </TouchableOpacity>
          </View>
        )}

      {/* Permission Denied Info Pill */}
      {permissionDenied && (
        <View style={styles.permissionPill}>
          <Text style={styles.permissionPillText}>
            📍 Location permission denied — showing map viewport
          </Text>
        </View>
      )}

      {/* Collapsible Legend & Transparency Modal / Card */}
      {showLegend && (
        <View style={styles.legendCard}>
          <View style={styles.legendHeaderRow}>
            <Text style={styles.legendTitle}>Safety Heatmap Legend</Text>
            <TouchableOpacity
              onPress={() => setShowLegend(false)}
              accessibilityLabel="Close legend"
            >
              <Text style={styles.closeBtnText}>✕</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.legendScroll} showsVerticalScrollIndicator={false}>
            {/* Color Categories */}
            <View style={styles.legendItem}>
              <View style={[styles.colorBox, { backgroundColor: '#34C759' }]} />
              <View style={styles.legendTextCol}>
                <Text style={styles.legendCategoryTitle}>Very Low / Low Risk</Text>
                <Text style={styles.legendCategoryDesc}>Safety Score ≥ 60 (Relative Risk &lt; 0.40)</Text>
              </View>
            </View>

            <View style={styles.legendItem}>
              <View style={[styles.colorBox, { backgroundColor: '#FF9500' }]} />
              <View style={styles.legendTextCol}>
                <Text style={styles.legendCategoryTitle}>Moderate Risk</Text>
                <Text style={styles.legendCategoryDesc}>Safety Score 40–59 (Relative Risk 0.40–0.59)</Text>
              </View>
            </View>

            <View style={styles.legendItem}>
              <View style={[styles.colorBox, { backgroundColor: '#FF3B30' }]} />
              <View style={styles.legendTextCol}>
                <Text style={styles.legendCategoryTitle}>High Risk</Text>
                <Text style={styles.legendCategoryDesc}>Safety Score 20–39 (Relative Risk 0.60–0.79)</Text>
              </View>
            </View>

            <View style={styles.legendItem}>
              <View style={[styles.colorBox, { backgroundColor: '#D70015' }]} />
              <View style={styles.legendTextCol}>
                <Text style={styles.legendCategoryTitle}>Very High Risk</Text>
                <Text style={styles.legendCategoryDesc}>Safety Score &lt; 20 (Relative Risk ≥ 0.80)</Text>
              </View>
            </View>

            <View style={styles.legendItem}>
              <View
                style={[
                  styles.colorBox,
                  { backgroundColor: '#8E8E93', borderStyle: 'dashed', borderWidth: 1, borderColor: '#3A3A3C' },
                ]}
              />
              <View style={styles.legendTextCol}>
                <Text style={styles.legendCategoryTitle}>Unscored / Unavailable</Text>
                <Text style={styles.legendCategoryDesc}>
                  Dashed gray — Missing ML or data. Never assumed safe.
                </Text>
              </View>
            </View>

            {/* Scientific Transparency Disclaimers */}
            <View style={styles.disclaimerContainer}>
              <Text style={styles.disclaimerHeading}>Academic & Data Integrity Notice</Text>
              <Text style={styles.disclaimerBody}>
                • Synthetic Demonstration Model: Predicted scores are derived from synthetic training fixtures and demonstration features. Do not use for real-world life safety.
              </Text>
              <Text style={styles.disclaimerBody}>
                • Relative Estimates: Green indicates relatively lower predicted risk in the demonstration model, NOT guaranteed safety or absence of crime.
              </Text>
              <Text style={styles.disclaimerBody}>
                • Not Calibrated Crime Probability: Scores represent feature-weighted relative risk, not actuarial crime probabilities.
              </Text>
              <Text style={styles.disclaimerBody}>
                • Missing Data Policy: Road segments without valid predictions are strictly rendered as UNSCORED.
              </Text>
            </View>
          </ScrollView>
        </View>
      )}

      {/* Selected Segment Detail Card */}
      {selectedSegment && (
        <Card style={styles.segmentDetailCard}>
          <View style={styles.segmentDetailHeader}>
            <View style={styles.segmentTitleCol}>
              <Text style={styles.segmentName} numberOfLines={1}>
                {selectedSegment.name || `Road Segment #${selectedSegment.segmentId}`}
              </Text>
              <Text style={styles.segmentSubtitle}>
                OSM Highway: {selectedSegment.highwayType || 'unknown'} • {selectedSegment.lengthMeters ? `${Math.round(selectedSegment.lengthMeters)}m` : ''}
              </Text>
            </View>
            <TouchableOpacity
              onPress={() => setSelectedSegment(null)}
              accessibilityLabel="Dismiss segment details"
            >
              <Text style={styles.closeBtnText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.segmentStatsRow}>
            {/* Risk Category Badge */}
            <View
              style={[
                styles.categoryBadge,
                { backgroundColor: selectedSegment.colorHex },
              ]}
            >
              <Text style={styles.categoryBadgeText}>
                {selectedSegment.riskCategory}
              </Text>
            </View>

            {/* Safety Score */}
            <View style={styles.statBox}>
              <Text style={styles.statLabel}>Safety Score</Text>
              <Text style={styles.statValue}>
                {selectedSegment.safetyScore !== null
                  ? `${Math.round(selectedSegment.safetyScore)}/100`
                  : 'N/A'}
              </Text>
            </View>

            {/* Relative Risk Score */}
            <View style={styles.statBox}>
              <Text style={styles.statLabel}>Relative Risk</Text>
              <Text style={styles.statValue}>
                {selectedSegment.relativeRiskScore !== null
                  ? selectedSegment.relativeRiskScore.toFixed(2)
                  : 'N/A'}
              </Text>
            </View>

            {/* Status */}
            <View style={styles.statBox}>
              <Text style={styles.statLabel}>Status</Text>
              <Text style={styles.statValue}>{selectedSegment.scoringStatus}</Text>
            </View>
          </View>

          {/* Road Attributes */}
          <View style={styles.attributesRow}>
            <Text style={styles.attributeText}>
              Lighting: {selectedSegment.isLit === null ? 'Unknown' : selectedSegment.isLit ? 'Lit' : 'Unlit'}
            </Text>
            <Text style={styles.attributeText}>
              Surface: {selectedSegment.surface || 'Unknown'}
            </Text>
            <Text style={styles.attributeText}>
              Model: {selectedSegment.modelVersion || 'None'}
            </Text>
          </View>

          {/* Synthetic Demo Label */}
          <View style={styles.segmentDisclaimerBox}>
            <Text style={styles.segmentDisclaimerText}>
              ⚠️ Demo Estimate: Not calibrated crime probability. Missing data is never defaulted safe.
            </Text>
          </View>
        </Card>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  map: {
    width: '100%',
    height: '100%',
  },
  topHeaderContainer: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 52 : 36,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.94)',
    borderRadius: 14,
    padding: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 4,
  },
  headerTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  headerBadgesRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  demoBadge: {
    backgroundColor: '#FF9500',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  demoBadgeText: {
    color: '#FFF',
    fontSize: 10,
    fontWeight: '700',
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  controlBtn: {
    flex: 1.2,
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  controlBtnActive: {
    backgroundColor: '#007AFF',
  },
  controlBtnInactive: {
    backgroundColor: '#E5E5EA',
  },
  controlBtnText: {
    fontSize: 13,
    fontWeight: '600',
  },
  controlBtnTextActive: {
    color: '#FFFFFF',
  },
  controlBtnTextInactive: {
    color: '#3A3A3C',
  },
  legendToggleBtn: {
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 8,
    backgroundColor: '#F2F2F7',
    borderWidth: 1,
    borderColor: '#D1D1D6',
  },
  legendToggleBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  routeToggleBtn: {
    paddingVertical: 8,
    paddingHorizontal: 8,
    borderRadius: 8,
    backgroundColor: '#F2F2F7',
    borderWidth: 1,
    borderColor: '#D1D1D6',
  },
  routeToggleActive: {
    backgroundColor: '#34C759',
    borderColor: '#34C759',
  },
  routeToggleBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  warningBanner: {
    position: 'absolute',
    top: 130,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(255, 149, 0, 0.95)',
    borderRadius: 8,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 4,
  },
  warningBannerText: {
    color: '#FFF',
    fontSize: 12,
    fontWeight: '600',
    flex: 1,
    marginRight: 6,
  },
  loadingBanner: {
    position: 'absolute',
    top: 130,
    alignSelf: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    elevation: 3,
  },
  loadingBannerText: {
    fontSize: 12,
    color: '#1C1C1E',
    fontWeight: '500',
  },
  errorBanner: {
    position: 'absolute',
    top: 130,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(255, 59, 48, 0.95)',
    borderRadius: 8,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 4,
  },
  errorBannerText: {
    color: '#FFF',
    fontSize: 12,
    flex: 1,
    marginRight: 8,
  },
  retryBtn: {
    backgroundColor: '#FFF',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
  },
  retryBtnText: {
    color: '#FF3B30',
    fontWeight: '700',
    fontSize: 12,
  },
  emptyBanner: {
    position: 'absolute',
    top: 130,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(28, 28, 30, 0.88)',
    borderRadius: 8,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 4,
  },
  emptyBannerText: {
    color: '#FFF',
    fontSize: 12,
    flex: 1,
  },
  jumpBtn: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 8,
    paddingVertical: 5,
    borderRadius: 6,
  },
  jumpBtnText: {
    color: '#FFF',
    fontSize: 11,
    fontWeight: '600',
  },
  permissionPill: {
    position: 'absolute',
    bottom: 24,
    alignSelf: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  permissionPillText: {
    color: '#FFF',
    fontSize: 11,
  },
  legendCard: {
    position: 'absolute',
    bottom: 20,
    left: 12,
    right: 12,
    maxHeight: Dimensions.get('window').height * 0.52,
    backgroundColor: 'rgba(255, 255, 255, 0.97)',
    borderRadius: 16,
    padding: 14,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 6,
  },
  legendHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  legendTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  closeBtnText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#8E8E93',
    paddingHorizontal: 4,
  },
  legendScroll: {
    maxHeight: Dimensions.get('window').height * 0.42,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  colorBox: {
    width: 22,
    height: 12,
    borderRadius: 3,
    marginRight: 10,
  },
  legendTextCol: {
    flex: 1,
  },
  legendCategoryTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  legendCategoryDesc: {
    fontSize: 11,
    color: '#636366',
  },
  disclaimerContainer: {
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
  },
  disclaimerHeading: {
    fontSize: 12,
    fontWeight: '700',
    color: '#FF9500',
    marginBottom: 4,
  },
  disclaimerBody: {
    fontSize: 10,
    color: '#3A3A3C',
    lineHeight: 14,
    marginBottom: 4,
  },
  segmentDetailCard: {
    position: 'absolute',
    bottom: 20,
    left: 12,
    right: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 14,
    elevation: 6,
  },
  segmentDetailHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  segmentTitleCol: {
    flex: 1,
    marginRight: 8,
  },
  segmentName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  segmentSubtitle: {
    fontSize: 12,
    color: '#636366',
    marginTop: 2,
  },
  segmentStatsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
    backgroundColor: '#F2F2F7',
    padding: 8,
    borderRadius: 8,
  },
  categoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  categoryBadgeText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  statBox: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 10,
    color: '#8E8E93',
    fontWeight: '500',
  },
  statValue: {
    fontSize: 13,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  attributesRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  attributeText: {
    fontSize: 11,
    color: '#3A3A3C',
  },
  segmentDisclaimerBox: {
    backgroundColor: '#FFF9E6',
    borderRadius: 6,
    padding: 6,
    borderWidth: 1,
    borderColor: '#FFE082',
  },
  segmentDisclaimerText: {
    fontSize: 10,
    color: '#8D6E63',
    lineHeight: 13,
  },
});
