import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Dimensions,
} from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { LeafletMap, LeafletRoute } from '../../components/LeafletMap';
import { Input } from '../../components/Input';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { api } from '../../services/api';
import { fetchHeatmap, HeatmapSegment } from '../../services/heatmap';
import { WeatherWidget } from '../../components/WeatherWidget';
import { useAuth } from '../../context/AuthContext';

export interface Coordinate {
  latitude: number;
  longitude: number;
}

export interface RouteResult {
  routeMode: 'FASTEST' | 'BALANCED' | 'SAFEST';
  geometry?: any;
  coordinates: Coordinate[];
  distanceMeters: number | null;
  durationSeconds: number | null;
  safetyScore: number | null;
  riskLevel: 'VERY_LOW' | 'LOW' | 'MODERATE' | 'HIGH' | 'VERY_HIGH' | 'UNSCORED';
  safetyLabel: string;
  importantFactors?: string[];
  explanation?: any;
  demo?: boolean;
  isDistinct?: boolean;
  duplicateOf?: string | null;
  isMlScored?: boolean;
  modelVersion?: string;
  litSegmentsPercentage?: number;
  notes?: string;
  disclaimer?: string;
}

export interface RouteSearchResponse {
  requestId: string;
  fastestRoute: RouteResult;
  balancedRoute: RouteResult;
  safestRoute: RouteResult;
  tradeOffExplanation: string;
  hasDistinctAlternatives: boolean;
  distinctRouteCount: number;
  computedAt: string;
  mlServiceAvailable: boolean;
  weatherContextAvailable: boolean;
  notes?: string;
}

// Preset test coordinates for Bangalore road network
const PRESET_LOCATIONS = [
  {
    name: 'MG Road to Brigade Rd',
    startLat: 12.9756,
    startLon: 77.6066,
    endLat: 12.9716,
    endLon: 77.6074,
  },
  {
    name: 'Cubbon Park to Church St',
    startLat: 12.9763,
    startLon: 77.5929,
    endLat: 12.9748,
    endLon: 77.6042,
  },
];

export default function RoutesScreen() {
  const router = useRouter();
  const { user } = useAuth();
  const params = useLocalSearchParams<{
    endLat?: string;
    endLon?: string;
    endName?: string;
    startLat?: string;
    startLon?: string;
  }>();

  const [startLat, setStartLat] = useState('12.9756');
  const [startLon, setStartLon] = useState('77.6066');
  const [endLat, setEndLat] = useState('12.9716');
  const [endLon, setEndLon] = useState('77.6074');

  useEffect(() => {
    if (params.endLat && params.endLon) {
      setEndLat(params.endLat);
      setEndLon(params.endLon);
    }
    if (params.startLat && params.startLon) {
      setStartLat(params.startLat);
      setStartLon(params.startLon);
    }
  }, [params.endLat, params.endLon, params.startLat, params.startLon]);

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [searchResponse, setSearchResponse] = useState<RouteSearchResponse | null>(null);
  const [selectedMode, setSelectedMode] = useState<'FASTEST' | 'BALANCED' | 'SAFEST'>('SAFEST');
  const [showHeatmap, setShowHeatmap] = useState(false);
  const [heatmapSegments, setHeatmapSegments] = useState<HeatmapSegment[]>([]);
  const [loadingHeatmap, setLoadingHeatmap] = useState(false);

  const handleToggleHeatmap = async () => {
    const next = !showHeatmap;
    setShowHeatmap(next);
    if (next && heatmapSegments.length === 0) {
      const sLat = parseFloat(startLat) || 12.9756;
      const sLon = parseFloat(startLon) || 77.6066;
      const eLat = parseFloat(endLat) || 12.9716;
      const eLon = parseFloat(endLon) || 77.6074;
      const minLat = Math.max(-90, Math.min(sLat, eLat) - 0.015);
      const maxLat = Math.min(90, Math.max(sLat, eLat) + 0.015);
      const minLon = Math.max(-180, Math.min(sLon, eLon) - 0.015);
      const maxLon = Math.min(180, Math.max(sLon, eLon) + 0.015);
      setLoadingHeatmap(true);
      try {
        const hm = await fetchHeatmap({ minLat, minLon, maxLat, maxLon, limit: 300 });
        setHeatmapSegments(hm.segments);
      } catch (e) {
        // Fallback gracefully if offline
      } finally {
        setLoadingHeatmap(false);
      }
    }
  };

  const handleSearchRoutes = async () => {
    const sLat = parseFloat(startLat);
    const sLon = parseFloat(startLon);
    const eLat = parseFloat(endLat);
    const eLon = parseFloat(endLon);

    if (isNaN(sLat) || isNaN(sLon) || isNaN(eLat) || isNaN(eLon)) {
      setErrorMsg('Please enter valid numeric latitude and longitude coordinates.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const payload = {
        startLat: sLat,
        startLon: sLon,
        endLat: eLat,
        endLon: eLon,
        travelMode: 'WALKING',
        routePreference: user?.routePreference || 'STANDARD',
      };

      const response = await api.post('/routes/search', payload);
      setSearchResponse(response.data);

      // Prioritize mode based on user's route preference
      if (user?.routePreference === 'FASTEST') {
        setSelectedMode('FASTEST');
      } else if (
        user?.routePreference === 'BALANCED' ||
        user?.routePreference === 'SOLO_TRAVELLER' ||
        user?.routePreference === 'TOURIST' ||
        user?.routePreference === 'ELDERLY'
      ) {
        setSelectedMode('BALANCED');
      } else if (response.data.safestRoute && response.data.safestRoute.safetyScore !== null) {
        setSelectedMode('SAFEST');
      } else {
        setSelectedMode('FASTEST');
      }
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to connect to route service. Please check network and retry.';
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  };

  const applyPreset = (preset: (typeof PRESET_LOCATIONS)[0]) => {
    setStartLat(preset.startLat.toString());
    setStartLon(preset.startLon.toString());
    setEndLat(preset.endLat.toString());
    setEndLon(preset.endLon.toString());
    setErrorMsg(null);
  };

  const getSelectedRoute = (): RouteResult | null => {
    if (!searchResponse) return null;
    if (selectedMode === 'FASTEST') return searchResponse.fastestRoute;
    if (selectedMode === 'BALANCED') return searchResponse.balancedRoute;
    return searchResponse.safestRoute;
  };

  const selectedRoute = getSelectedRoute();

  const getRiskBadgeColor = (riskLevel: string) => {
    switch (riskLevel) {
      case 'VERY_LOW':
      case 'LOW':
        return '#34C759'; // Green
      case 'MODERATE':
        return '#FF9500'; // Amber
      case 'HIGH':
      case 'VERY_HIGH':
        return '#FF3B30'; // Red
      case 'UNSCORED':
      default:
        return '#8E8E93'; // Neutral gray
    }
  };

  const getRouteColor = (mode: string) => {
    switch (mode) {
      case 'SAFEST':
        return '#34C759';
      case 'BALANCED':
        return '#5856D6';
      case 'FASTEST':
      default:
        return '#007AFF';
    }
  };

  const formatDistance = (meters: number | null) => {
    if (meters === null || meters === undefined) return 'N/A';
    if (meters < 1000) return `${Math.round(meters)} m`;
    return `${(meters / 1000).toFixed(1)} km`;
  };

  const formatDuration = (seconds: number | null) => {
    if (seconds === null || seconds === undefined) return 'N/A';
    const minutes = Math.round(seconds / 60);
    return `${minutes} min`;
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle} accessibilityRole="header">
          Route Comparison
        </Text>
        <Text style={styles.headerSubtitle}>
          Compare travel time, distance, and predicted safety
        </Text>
      </View>

      {/* Inputs */}
      <Card style={styles.formCard}>
        <Text style={styles.formTitle}>Coordinates</Text>

        <View style={styles.coordRow}>
          <View style={styles.coordInput}>
            <Input
              label="Start Latitude"
              value={startLat}
              onChangeText={setStartLat}
              keyboardType="numeric"
              accessibilityLabel="Start latitude input"
            />
          </View>
          <View style={styles.coordInput}>
            <Input
              label="Start Longitude"
              value={startLon}
              onChangeText={setStartLon}
              keyboardType="numeric"
              accessibilityLabel="Start longitude input"
            />
          </View>
        </View>

        <View style={styles.coordRow}>
          <View style={styles.coordInput}>
            <Input
              label="Destination Latitude"
              value={endLat}
              onChangeText={setEndLat}
              keyboardType="numeric"
              accessibilityLabel="Destination latitude input"
            />
          </View>
          <View style={styles.coordInput}>
            <Input
              label="Destination Longitude"
              value={endLon}
              onChangeText={setEndLon}
              keyboardType="numeric"
              accessibilityLabel="Destination longitude input"
            />
          </View>
        </View>

        {/* Presets */}
        <View style={styles.presetContainer}>
          <Text style={styles.presetLabel}>Quick Presets:</Text>
          <View style={styles.presetButtonsRow}>
            {PRESET_LOCATIONS.map((preset, index) => (
              <TouchableOpacity
                key={index}
                style={styles.presetChip}
                onPress={() => applyPreset(preset)}
                accessibilityLabel={`Preset: ${preset.name}`}
                accessibilityRole="button"
              >
                <Text style={styles.presetChipText}>{preset.name}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Active Route Preference Pill */}
        <TouchableOpacity
          style={styles.prefBanner}
          onPress={() => router.push('/settings')}
          accessibilityRole="button"
          accessibilityLabel="View and adjust route preference"
        >
          <View style={styles.prefBannerLeft}>
            <Text style={styles.prefBannerLabel}>Route Preference:</Text>
            <View style={styles.prefBannerPill}>
              <Text style={styles.prefBannerValue}>{user?.routePreference || 'STANDARD'}</Text>
            </View>
          </View>
          <Text style={styles.prefBannerAction}>Customize ›</Text>
        </TouchableOpacity>

        <Button
          title={loading ? 'Comparing Routes...' : 'Compare Routes'}
          onPress={handleSearchRoutes}
          disabled={loading}
          accessibilityLabel="Compare routes button"
          accessibilityRole="button"
        />
      </Card>

      {/* Loading State */}
      {loading && (
        <View style={styles.loadingContainer} accessibilityLiveRegion="polite">
          <ActivityIndicator size="large" color="#007AFF" />
          <Text style={styles.loadingText}>Searching & comparing route alternatives...</Text>
        </View>
      )}

      {/* Error State */}
      {errorMsg && !loading && (
        <Card style={styles.errorCard} accessibilityRole="alert">
          <Text style={styles.errorTitle}>Search Failed</Text>
          <Text style={styles.errorText}>{errorMsg}</Text>
          <Button
            title="Retry Search"
            variant="secondary"
            onPress={handleSearchRoutes}
            accessibilityLabel="Retry search button"
            accessibilityRole="button"
          />
        </Card>
      )}

      {/* Results View */}
      {searchResponse && !loading && (
        <View style={styles.resultsContainer}>
          {/* ML Status Banner */}
          {!searchResponse.mlServiceAvailable && (
            <View style={styles.mlWarningBanner} accessibilityRole="alert">
              <Text style={styles.mlWarningText}>
                ⚠️ ML Risk Service Offline: Displaying non-ML topological routes. Safety scores are
                UNSCORED.
              </Text>
            </View>
          )}

          {/* Weather Status Banner */}
          <View
            style={searchResponse.weatherContextAvailable ? styles.weatherActiveBanner : styles.weatherInactiveBanner}
            accessibilityRole="summary"
          >
            <Text style={searchResponse.weatherContextAvailable ? styles.weatherActiveText : styles.weatherInactiveText}>
              {searchResponse.weatherContextAvailable
                ? '🌦️ Weather Context Active: Environmental parameters (precipitation, visibility) integrated into risk inference.'
                : '☁️ Weather Context Inactive: Standard environmental defaults applied (does not block routing).'}
            </Text>
          </View>

          {/* Ambient Departure Weather Context */}
          <View style={{ marginHorizontal: 16, marginBottom: 8 }}>
            <WeatherWidget
              latitude={parseFloat(startLat) || 12.9756}
              longitude={parseFloat(startLon) || 77.6066}
              compact={true}
            />
          </View>

          {/* Trade-off Explanation Card */}
          <Card style={styles.tradeOffCard} accessibilityRole="summary">
            <Text style={styles.tradeOffTitle}>Route Comparison Summary</Text>
            <Text style={styles.tradeOffText}>{searchResponse.tradeOffExplanation}</Text>
            <Text style={styles.disclaimerText}>
              Note: Relative risk is derived from street-level features, not empirical crime
              probability.
            </Text>
          </Card>

          {/* 3-Way Route Selector Cards */}
          <Text style={styles.sectionTitle}>Select Route Option</Text>
          <View style={styles.optionsRow}>
            {(['FASTEST', 'BALANCED', 'SAFEST'] as const).map((mode) => {
              const r =
                mode === 'FASTEST'
                  ? searchResponse.fastestRoute
                  : mode === 'BALANCED'
                  ? searchResponse.balancedRoute
                  : searchResponse.safestRoute;

              if (!r) return null;

              const isSelected = selectedMode === mode;
              const isDuplicate = r.isDistinct === false;

              return (
                <TouchableOpacity
                  key={mode}
                  style={[
                    styles.optionCard,
                    isSelected && { borderColor: getRouteColor(mode), borderWidth: 2 },
                  ]}
                  onPress={() => setSelectedMode(mode)}
                  accessibilityRole="button"
                  accessibilityLabel={`${mode} route option, ${formatDuration(
                    r.durationSeconds
                  )}, ${formatDistance(r.distanceMeters)}`}
                  accessibilityState={{ selected: isSelected }}
                >
                  <View
                    style={[
                      styles.modeBadge,
                      { backgroundColor: isSelected ? getRouteColor(mode) : '#E5E5EA' },
                    ]}
                  >
                    <Text
                      style={[
                        styles.modeBadgeText,
                        { color: isSelected ? '#FFFFFF' : '#1C1C1E' },
                      ]}
                    >
                      {mode}
                    </Text>
                  </View>

                  {mode === 'BALANCED' &&
                    user?.routePreference &&
                    ['SOLO_TRAVELLER', 'TOURIST', 'ELDERLY'].includes(user.routePreference) && (
                      <Text style={styles.personalizedSubText}>
                        {user.routePreference === 'SOLO_TRAVELLER'
                          ? 'Solo'
                          : user.routePreference === 'TOURIST'
                          ? 'Tourist'
                          : 'Elderly'}
                      </Text>
                    )}

                  <Text style={styles.optionDuration}>{formatDuration(r.durationSeconds)}</Text>
                  <Text style={styles.optionDistance}>{formatDistance(r.distanceMeters)}</Text>

                  {/* Safety Score / Unscored Badge */}
                  {r.safetyScore !== null && r.safetyScore !== undefined ? (
                    <View
                      style={[
                        styles.safetyBadge,
                        { backgroundColor: getRiskBadgeColor(r.riskLevel) },
                      ]}
                    >
                      <Text style={styles.safetyBadgeText}>{Math.round(r.safetyScore)}%</Text>
                    </View>
                  ) : (
                    <View style={[styles.safetyBadge, { backgroundColor: '#8E8E93' }]}>
                      <Text style={styles.safetyBadgeText}>UNSCORED</Text>
                    </View>
                  )}

                  {isDuplicate && (
                    <Text style={styles.duplicateTag}>
                      {r.duplicateOf ? `Same as ${r.duplicateOf}` : 'Duplicate'}
                    </Text>
                  )}
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Map Layer Controls */}
          <View style={styles.mapControlsHeader}>
            <TouchableOpacity
              style={[
                styles.heatmapToggleBtn,
                showHeatmap ? styles.heatmapToggleBtnActive : null,
              ]}
              onPress={handleToggleHeatmap}
              accessibilityRole="switch"
              accessibilityLabel="Toggle safety heatmap overlay under routes"
              accessibilityState={{ checked: showHeatmap }}
            >
              <Text
                style={[
                  styles.heatmapToggleBtnText,
                  showHeatmap ? styles.heatmapToggleBtnTextActive : null,
                ]}
              >
                {loadingHeatmap
                  ? 'Loading Heatmap...'
                  : showHeatmap
                  ? '🔥 Heatmap Overlay: ON'
                  : '⚪ Heatmap Overlay: OFF'}
              </Text>
            </TouchableOpacity>
            {showHeatmap && (
              <Text style={styles.demoWatermarkText}>DEMO ML MODEL</Text>
            )}
          </View>

          {/* Interactive Map (OpenStreetMap / CartoDB Voyager, Zero API Key Dependency) */}
          <View style={styles.mapWrapper}>
            <LeafletMap
              style={styles.map}
              initialRegion={{
                latitude: (parseFloat(startLat) + parseFloat(endLat)) / 2 || 12.9736,
                longitude: (parseFloat(startLon) + parseFloat(endLon)) / 2 || 77.607,
                latitudeDelta: 0.02,
                longitudeDelta: 0.02,
              }}
              heatmapSegments={showHeatmap ? heatmapSegments : []}
              routes={[
                ...(searchResponse.fastestRoute?.coordinates && searchResponse.fastestRoute.coordinates.length >= 2
                  ? [
                      {
                        mode: 'FASTEST' as const,
                        coordinates: searchResponse.fastestRoute.coordinates,
                        colorHex: getRouteColor('FASTEST'),
                        isSelected: selectedMode === 'FASTEST',
                      },
                    ]
                  : []),
                ...(searchResponse.balancedRoute?.coordinates && searchResponse.balancedRoute.coordinates.length >= 2
                  ? [
                      {
                        mode: 'BALANCED' as const,
                        coordinates: searchResponse.balancedRoute.coordinates,
                        colorHex: getRouteColor('BALANCED'),
                        isSelected: selectedMode === 'BALANCED',
                      },
                    ]
                  : []),
                ...(searchResponse.safestRoute?.coordinates && searchResponse.safestRoute.coordinates.length >= 2
                  ? [
                      {
                        mode: 'SAFEST' as const,
                        coordinates: searchResponse.safestRoute.coordinates,
                        colorHex: getRouteColor('SAFEST'),
                        isSelected: selectedMode === 'SAFEST',
                      },
                    ]
                  : []),
              ]}
              origin={
                selectedRoute?.coordinates && selectedRoute.coordinates.length > 0
                  ? selectedRoute.coordinates[0]
                  : { latitude: parseFloat(startLat) || 12.9756, longitude: parseFloat(startLon) || 77.6066 }
              }
              destination={
                selectedRoute?.coordinates && selectedRoute.coordinates.length > 0
                  ? selectedRoute.coordinates[selectedRoute.coordinates.length - 1]
                  : { latitude: parseFloat(endLat) || 12.9716, longitude: parseFloat(endLon) || 77.6074 }
              }
            />
          </View>

          {/* Selected Route Detailed Card */}
          {selectedRoute && (
            <Card style={styles.detailsCard}>
              <View style={styles.detailsHeader}>
                <Text style={styles.detailsTitle}>{selectedMode} Route Details</Text>
                <View
                  style={[
                    styles.riskPill,
                    { backgroundColor: getRiskBadgeColor(selectedRoute.riskLevel) },
                  ]}
                >
                  <Text style={styles.riskPillText}>{selectedRoute.safetyLabel}</Text>
                </View>
              </View>

              <View style={styles.metricsRow}>
                <View style={styles.metricItem}>
                  <Text style={styles.metricLabel}>Distance</Text>
                  <Text style={styles.metricValue}>
                    {formatDistance(selectedRoute.distanceMeters)}
                  </Text>
                </View>
                <View style={styles.metricItem}>
                  <Text style={styles.metricLabel}>Walking Time</Text>
                  <Text style={styles.metricValue}>
                    {formatDuration(selectedRoute.durationSeconds)}
                  </Text>
                </View>
                <View style={styles.metricItem}>
                  <Text style={styles.metricLabel}>Safety Score</Text>
                  <Text
                    style={[
                      styles.metricValue,
                      { color: getRiskBadgeColor(selectedRoute.riskLevel) },
                    ]}
                  >
                    {selectedRoute.safetyScore !== null && selectedRoute.safetyScore !== undefined
                      ? `${selectedRoute.safetyScore}/100`
                      : 'UNSCORED'}
                  </Text>
                </View>
              </View>

              {/* Additional Key Attributes */}
              {selectedRoute.litSegmentsPercentage !== undefined && (
                <View style={styles.attributeRow}>
                  <Text style={styles.attributeLabel}>Verified Lit Streets:</Text>
                  <Text style={styles.attributeValue}>
                    {selectedRoute.litSegmentsPercentage}%
                  </Text>
                </View>
              )}

              {selectedRoute.importantFactors && selectedRoute.importantFactors.length > 0 && (
                <View style={styles.factorsContainer}>
                  <Text style={styles.factorsTitle}>Key Factors:</Text>
                  {selectedRoute.importantFactors.map((factor, idx) => (
                    <Text key={idx} style={styles.factorItem}>
                      • {factor}
                    </Text>
                  ))}
                </View>
              )}

              {selectedRoute.notes && (
                <Text style={styles.routeNotesText}>{selectedRoute.notes}</Text>
              )}

              {selectedRoute.demo && (
                <View style={styles.demoBadge}>
                  <Text style={styles.demoBadgeText}>SYNTHETIC DEMO DATA</Text>
                </View>
              )}

              <Button
                title="Start Navigation"
                onPress={() => alert('Navigation view will guide you along the selected route.')}
                accessibilityLabel="Start navigation button"
                accessibilityRole="button"
              />
            </Card>
          )}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  contentContainer: {
    paddingBottom: 40,
  },
  header: {
    paddingHorizontal: 16,
    paddingTop: 54,
    paddingBottom: 12,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#C6C6C8',
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#8E8E93',
    marginTop: 4,
  },
  formCard: {
    margin: 16,
    marginBottom: 8,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  coordRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  coordInput: {
    flex: 1,
  },
  presetContainer: {
    marginVertical: 8,
  },
  presetLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: '#8E8E93',
    marginBottom: 6,
  },
  presetButtonsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  presetChip: {
    backgroundColor: '#E5E5EA',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
  },
  presetChipText: {
    fontSize: 12,
    color: '#007AFF',
    fontWeight: '500',
  },
  loadingContainer: {
    padding: 24,
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 10,
    fontSize: 14,
    color: '#8E8E93',
  },
  errorCard: {
    margin: 16,
    borderColor: '#FF3B30',
    borderWidth: 1,
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FF3B30',
    marginBottom: 4,
  },
  errorText: {
    fontSize: 14,
    color: '#3A3A3C',
    marginBottom: 12,
  },
  resultsContainer: {
    marginTop: 8,
  },
  mlWarningBanner: {
    backgroundColor: '#FFF2F2',
    borderColor: '#FF3B30',
    borderWidth: 1,
    marginHorizontal: 16,
    marginBottom: 12,
    padding: 10,
    borderRadius: 8,
  },
  mlWarningText: {
    fontSize: 13,
    color: '#D70015',
    fontWeight: '500',
  },
  weatherActiveBanner: {
    backgroundColor: '#EFF6FF',
    borderColor: '#93C5FD',
    borderWidth: 1,
    marginHorizontal: 16,
    marginBottom: 8,
    padding: 10,
    borderRadius: 8,
  },
  weatherActiveText: {
    fontSize: 12,
    color: '#1E40AF',
    fontWeight: '500',
  },
  weatherInactiveBanner: {
    backgroundColor: '#F3F4F6',
    borderColor: '#E5E7EB',
    borderWidth: 1,
    marginHorizontal: 16,
    marginBottom: 8,
    padding: 10,
    borderRadius: 8,
  },
  weatherInactiveText: {
    fontSize: 12,
    color: '#6B7280',
    fontWeight: '500',
  },
  tradeOffCard: {
    marginHorizontal: 16,
    marginBottom: 12,
    backgroundColor: '#F8F9FA',
  },
  tradeOffTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  tradeOffText: {
    fontSize: 14,
    color: '#3A3A3C',
    lineHeight: 20,
    marginBottom: 8,
  },
  disclaimerText: {
    fontSize: 11,
    color: '#8E8E93',
    fontStyle: 'italic',
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginHorizontal: 16,
    marginBottom: 8,
  },
  optionsRow: {
    flexDirection: 'row',
    marginHorizontal: 16,
    gap: 8,
    marginBottom: 16,
  },
  optionCard: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 10,
    alignItems: 'center',
    borderColor: '#E5E5EA',
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  modeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    marginBottom: 6,
  },
  modeBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  optionDuration: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  optionDistance: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 6,
  },
  safetyBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  safetyBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  duplicateTag: {
    fontSize: 9,
    color: '#FF9500',
    marginTop: 4,
    textAlign: 'center',
  },
  mapWrapper: {
    height: 240,
    marginHorizontal: 16,
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#C6C6C8',
    backgroundColor: '#F2F2F7',
  },
  map: {
    width: '100%',
    height: '100%',
  },
  detailsCard: {
    marginHorizontal: 16,
  },
  detailsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  detailsTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  riskPill: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  riskPillText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  metricsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E5EA',
    marginBottom: 12,
  },
  metricItem: {
    alignItems: 'center',
  },
  metricLabel: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 2,
  },
  metricValue: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  attributeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  attributeLabel: {
    fontSize: 13,
    color: '#3A3A3C',
  },
  attributeValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  factorsContainer: {
    marginTop: 4,
    marginBottom: 8,
  },
  factorsTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  factorItem: {
    fontSize: 12,
    color: '#3A3A3C',
    lineHeight: 18,
  },
  routeNotesText: {
    fontSize: 11,
    color: '#8E8E93',
    marginVertical: 4,
  },
  demoBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#FFE58F',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginVertical: 6,
  },
  demoBadgeText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#D48800',
  },
  mapControlsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginHorizontal: 16,
    marginBottom: 8,
  },
  heatmapToggleBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: '#F2F2F7',
    borderWidth: 1,
    borderColor: '#D1D1D6',
  },
  heatmapToggleBtnActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  heatmapToggleBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  heatmapToggleBtnTextActive: {
    color: '#FFFFFF',
  },
  demoWatermarkText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#FF9500',
    backgroundColor: '#FFF2D6',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  prefBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    marginVertical: 10,
  },
  prefBannerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  prefBannerLabel: {
    fontSize: 13,
    color: '#636366',
    fontWeight: '500',
  },
  prefBannerPill: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  prefBannerValue: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  prefBannerAction: {
    fontSize: 13,
    color: '#007AFF',
    fontWeight: '600',
  },
  personalizedSubText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#5856D6',
    backgroundColor: '#EBF4FF',
    paddingHorizontal: 4,
    paddingVertical: 1,
    borderRadius: 4,
    alignSelf: 'center',
    marginTop: 2,
  },
});
