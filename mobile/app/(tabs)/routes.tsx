import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  TextInput,
  Dimensions,
  Platform,
  Linking,
  Alert,
} from 'react-native';
import * as Location from 'expo-location';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { LeafletMap, LeafletRoute } from '../../components/LeafletMap';
import { Input } from '../../components/Input';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { api } from '../../services/api';
import { fetchHeatmap, HeatmapSegment } from '../../services/heatmap';
import { WeatherWidget } from '../../components/WeatherWidget';
import { useAuth } from '../../context/AuthContext';
import { searchPlaces, reverseGeocode, GeocodedPlace } from '../../services/geocoding';

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

// Preset locations for quick testing (supports both Guntur and Bangalore)
const PRESET_PLACES = [
  {
    name: 'Guntur Central → Brodipet',
    startName: 'Guntur Railway Station',
    startLat: '16.3067',
    startLon: '80.4365',
    endName: 'Brodipet Main Rd, Guntur',
    endLat: '16.3120',
    endLon: '80.4420',
  },
  {
    name: 'Brodipet → Arundelpet (Guntur)',
    startName: 'Brodipet, Guntur',
    startLat: '16.3120',
    startLon: '80.4420',
    endName: 'Arundelpet, Guntur',
    endLat: '16.3025',
    endLon: '80.4485',
  },
  {
    name: 'MG Road → Brigade Rd (BLR)',
    startName: 'MG Road Metro, Bangalore',
    startLat: '12.9756',
    startLon: '77.6066',
    endName: 'Brigade Road, Bangalore',
    endLat: '12.9716',
    endLon: '77.6074',
  },
  {
    name: 'Cubbon Park → Church St (BLR)',
    startName: 'Cubbon Park, Bangalore',
    startLat: '12.9763',
    startLon: '77.5929',
    endName: 'Church Street, Bangalore',
    endLat: '12.9748',
    endLon: '77.6042',
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
    startName?: string;
  }>();

  // Places state (Name-based input with GPS autofill)
  const [startName, setStartName] = useState('My Current Location');
  const [startLat, setStartLat] = useState('16.3067');
  const [startLon, setStartLon] = useState('80.4365');
  const [endName, setEndName] = useState('Brodipet, Guntur');
  const [endLat, setEndLat] = useState('16.3120');
  const [endLon, setEndLon] = useState('80.4420');

  // Autocomplete and selection state
  const [activeField, setActiveField] = useState<'start' | 'end' | null>(null);
  const [suggestions, setSuggestions] = useState<GeocodedPlace[]>([]);
  const [isSearchingPlaces, setIsSearchingPlaces] = useState(false);
  const [locatingUser, setLocatingUser] = useState(false);
  const [showCoordinateDetails, setShowCoordinateDetails] = useState(false);
  const searchDebounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [searchResponse, setSearchResponse] = useState<RouteSearchResponse | null>(null);
  const [selectedMode, setSelectedMode] = useState<'FASTEST' | 'BALANCED' | 'SAFEST'>('SAFEST');
  const [showHeatmap, setShowHeatmap] = useState(false);
  const [heatmapSegments, setHeatmapSegments] = useState<HeatmapSegment[]>([]);
  const [loadingHeatmap, setLoadingHeatmap] = useState(false);

  // Active Live Navigation Mode state
  const [isNavigating, setIsNavigating] = useState(false);
  const [navUserLocation, setNavUserLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [navRemainingDistance, setNavRemainingDistance] = useState<number | null>(null);
  const [navRemainingTime, setNavRemainingTime] = useState<number | null>(null);
  const [navInstruction, setNavInstruction] = useState<string>('');
  const locationWatchSubscription = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    return () => {
      if (locationWatchSubscription.current) {
        locationWatchSubscription.current.remove();
        locationWatchSubscription.current = null;
      }
    };
  }, []);

  const calculateDistanceMeters = (lat1: number, lon1: number, lat2: number, lon2: number) => {
    const R = 6371000;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round(R * c);
  };

  const handleStartNavigation = async () => {
    const route = getSelectedRoute();
    if (!route || !route.coordinates || route.coordinates.length < 2) {
      Alert.alert('Route Required', 'Please select a route with valid coordinates before starting navigation.');
      return;
    }

    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('Permission Denied', 'Location permission is required for live route navigation.');
        return;
      }

      setIsNavigating(true);
      setNavRemainingDistance(route.distanceMeters);
      setNavRemainingTime(route.durationSeconds);
      const cleanDest = endName.replace(/^📍\s*/, '') || 'Destination';
      setNavInstruction(`Follow the ${selectedMode.toLowerCase()} path towards ${cleanDest}`);

      // Start live GPS tracking
      locationWatchSubscription.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          distanceInterval: 3,
          timeInterval: 2000,
        },
        (loc) => {
          const uLat = loc.coords.latitude;
          const uLon = loc.coords.longitude;
          setNavUserLocation({ latitude: uLat, longitude: uLon });

          const dest = route.coordinates[route.coordinates.length - 1];
          const dist = calculateDistanceMeters(uLat, uLon, dest.latitude, dest.longitude);
          setNavRemainingDistance(dist);
          // Walking speed ~1.3 m/s
          setNavRemainingTime(Math.round(dist / 1.3));

          if (dist < 25) {
            setNavInstruction('🎉 You have arrived at your destination!');
          } else {
            setNavInstruction(`Continue following ${selectedMode.toLowerCase()} corridor to ${cleanDest}`);
          }
        }
      );
    } catch (err: any) {
      Alert.alert('Navigation Error', err.message || 'Unable to start GPS navigation.');
    }
  };

  const handleStopNavigation = () => {
    if (locationWatchSubscription.current) {
      locationWatchSubscription.current.remove();
      locationWatchSubscription.current = null;
    }
    setIsNavigating(false);
    setNavUserLocation(null);
  };

  const handleOpenGoogleMaps = () => {
    const sLat = parseFloat(startLat);
    const sLon = parseFloat(startLon);
    const eLat = parseFloat(endLat);
    const eLon = parseFloat(endLon);

    if (isNaN(eLat) || isNaN(eLon)) {
      Alert.alert('Destination Missing', 'Please enter a valid destination to navigate.');
      return;
    }

    const originQuery = !isNaN(sLat) && !isNaN(sLon) ? `${sLat},${sLon}` : '';
    const webUrl = `https://www.google.com/maps/dir/?api=1&origin=${originQuery}&destination=${eLat},${eLon}&travelmode=walking`;
    const nativeUri = Platform.select({
      android: `google.navigation:q=${eLat},${eLon}&mode=w`,
      ios: `maps://app?saddr=${originQuery}&daddr=${eLat},${eLon}&dirflg=w`,
      default: webUrl,
    });

    Linking.canOpenURL(nativeUri).then((can) => {
      if (can) {
        Linking.openURL(nativeUri);
      } else {
        Linking.openURL(webUrl);
      }
    }).catch(() => {
      Linking.openURL(webUrl);
    });
  };

  // Automatically request GPS position and reverse-geocode address
  const handleUseCurrentLocation = async () => {
    setLocatingUser(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setErrorMsg('Location permission not granted. You can search places by name above.');
        setLocatingUser(false);
        return;
      }

      const loc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });

      const lat = loc.coords.latitude;
      const lon = loc.coords.longitude;
      setStartLat(lat.toString());
      setStartLon(lon.toString());

      const placeName = await reverseGeocode(lat, lon);
      setStartName(`📍 ${placeName}`);
      setErrorMsg(null);
    } catch (err: any) {
      console.warn('GPS location lookup failed:', err);
    } finally {
      setLocatingUser(false);
    }
  };

  useEffect(() => {
    // Process destination params if navigated from Safe Zones or Directory
    if (params.endLat && params.endLon) {
      setEndLat(params.endLat);
      setEndLon(params.endLon);
      if (params.endName) {
        setEndName(params.endName);
      } else {
        reverseGeocode(parseFloat(params.endLat), parseFloat(params.endLon)).then(name => setEndName(name));
      }
    }
    if (params.startLat && params.startLon) {
      setStartLat(params.startLat);
      setStartLon(params.startLon);
      if (params.startName) {
        setStartName(params.startName);
      }
    } else {
      // Auto-detect current location
      handleUseCurrentLocation();
    }
  }, [params.endLat, params.endLon, params.endName, params.startLat, params.startLon, params.startName]);

  const handlePlaceInputChange = (text: string, field: 'start' | 'end') => {
    if (field === 'start') {
      setStartName(text);
    } else {
      setEndName(text);
    }
    setActiveField(field);

    if (searchDebounceTimer.current) {
      clearTimeout(searchDebounceTimer.current);
    }

    if (!text || text.trim().length < 2) {
      setSuggestions([]);
      setIsSearchingPlaces(false);
      return;
    }

    setIsSearchingPlaces(true);
    searchDebounceTimer.current = setTimeout(async () => {
      const uLat = parseFloat(startLat) || undefined;
      const uLon = parseFloat(startLon) || undefined;
      const results = await searchPlaces(text, uLat, uLon);
      setSuggestions(results);
      setIsSearchingPlaces(false);
    }, 350);
  };

  const handleSelectSuggestion = (place: GeocodedPlace) => {
    if (activeField === 'start') {
      setStartName(place.displayName);
      setStartLat(place.latitude.toString());
      setStartLon(place.longitude.toString());
    } else if (activeField === 'end') {
      setEndName(place.displayName);
      setEndLat(place.latitude.toString());
      setEndLon(place.longitude.toString());
    }
    setSuggestions([]);
    setActiveField(null);
  };

  const handleSwapLocations = () => {
    const prevStartName = startName;
    const prevStartLat = startLat;
    const prevStartLon = startLon;

    setStartName(endName);
    setStartLat(endLat);
    setStartLon(endLon);

    setEndName(prevStartName);
    setEndLat(prevStartLat);
    setEndLon(prevStartLon);

    setSuggestions([]);
    setActiveField(null);
  };

  const applyPreset = (preset: (typeof PRESET_PLACES)[0]) => {
    setStartName(preset.startName);
    setStartLat(preset.startLat);
    setStartLon(preset.startLon);
    setEndName(preset.endName);
    setEndLat(preset.endLat);
    setEndLon(preset.endLon);
    setSuggestions([]);
    setActiveField(null);
    setErrorMsg(null);
  };

  const handleToggleHeatmap = async () => {
    const next = !showHeatmap;
    setShowHeatmap(next);
    if (next && heatmapSegments.length === 0) {
      const sLat = parseFloat(startLat) || 16.3067;
      const sLon = parseFloat(startLon) || 80.4365;
      const eLat = parseFloat(endLat) || 16.3120;
      const eLon = parseFloat(endLon) || 80.4420;
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
    setLoading(true);
    setErrorMsg(null);
    setSuggestions([]);
    setActiveField(null);

    let sLat = parseFloat(startLat);
    let sLon = parseFloat(startLon);
    let eLat = parseFloat(endLat);
    let eLon = parseFloat(endLon);

    try {
      // If user typed custom name without selecting a suggestion, geocode on the fly
      if (isNaN(sLat) || isNaN(sLon)) {
        const cleanName = startName.replace(/^📍\s*/, '');
        const places = await searchPlaces(cleanName);
        if (places.length > 0) {
          sLat = places[0].latitude;
          sLon = places[0].longitude;
          setStartLat(sLat.toString());
          setStartLon(sLon.toString());
        } else {
          setErrorMsg(`Could not find coordinates for origin: "${cleanName}". Please pick from suggestions.`);
          setLoading(false);
          return;
        }
      }

      if (isNaN(eLat) || isNaN(eLon)) {
        const cleanEnd = endName.replace(/^📍\s*/, '');
        const places = await searchPlaces(cleanEnd, sLat, sLon);
        if (places.length > 0) {
          eLat = places[0].latitude;
          eLon = places[0].longitude;
          setEndLat(eLat.toString());
          setEndLon(eLon.toString());
        } else {
          setErrorMsg(`Could not find coordinates for destination: "${cleanEnd}". Please pick from suggestions.`);
          setLoading(false);
          return;
        }
      }

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
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer} keyboardShouldPersistTaps="handled">
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle} accessibilityRole="header">
          Safe Route Planning
        </Text>
        <Text style={styles.headerSubtitle}>
          Enter places by name to compare time, distance, and predicted safety
        </Text>
      </View>

      {/* Place Search Card */}
      <Card style={styles.formCard}>
        <View style={styles.formCardHeader}>
          <Text style={styles.formTitle}>Where are you going?</Text>
          <Text style={styles.formSub}>Enter place names or tap GPS for current location</Text>
        </View>

        {/* Origin Place Input */}
        <View style={styles.placeInputGroup}>
          <Text style={styles.inputGroupLabel}>Start Location</Text>
          <View style={styles.placeInputRow}>
            <Text style={styles.placeDot}>🟢</Text>
            <TextInput
              style={styles.placeTextInput}
              placeholder="Origin address or place..."
              placeholderTextColor="#8E8E93"
              value={startName}
              onChangeText={(text) => handlePlaceInputChange(text, 'start')}
              onFocus={() => setActiveField('start')}
              returnKeyType="search"
            />
            {startName.length > 0 && (
              <TouchableOpacity
                onPress={() => {
                  setStartName('');
                  setSuggestions([]);
                }}
                style={styles.clearBtn}
                accessibilityLabel="Clear origin"
              >
                <Text style={styles.clearBtnText}>✕</Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity
              style={styles.currentLocBtn}
              onPress={handleUseCurrentLocation}
              disabled={locatingUser}
              accessibilityLabel="Use GPS current location"
            >
              {locatingUser ? (
                <ActivityIndicator size="small" color="#007AFF" />
              ) : (
                <Text style={styles.currentLocBtnText}>📍 GPS</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Swap Origin & Destination Button */}
        <View style={styles.swapRow}>
          <View style={styles.swapDivider} />
          <TouchableOpacity
            style={styles.swapButton}
            onPress={handleSwapLocations}
            accessibilityLabel="Swap start and destination"
          >
            <Text style={styles.swapButtonText}>⇅ Swap</Text>
          </TouchableOpacity>
          <View style={styles.swapDivider} />
        </View>

        {/* Destination Place Input */}
        <View style={styles.placeInputGroup}>
          <Text style={styles.inputGroupLabel}>Destination</Text>
          <View style={styles.placeInputRow}>
            <Text style={styles.placeDot}>🔴</Text>
            <TextInput
              style={styles.placeTextInput}
              placeholder="Search destination or place..."
              placeholderTextColor="#8E8E93"
              value={endName}
              onChangeText={(text) => handlePlaceInputChange(text, 'end')}
              onFocus={() => setActiveField('end')}
              returnKeyType="search"
            />
            {endName.length > 0 && (
              <TouchableOpacity
                onPress={() => {
                  setEndName('');
                  setSuggestions([]);
                }}
                style={styles.clearBtn}
                accessibilityLabel="Clear destination"
              >
                <Text style={styles.clearBtnText}>✕</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Live Autocomplete Suggestions Overlay */}
        {activeField !== null && (isSearchingPlaces || suggestions.length > 0) && (
          <View style={styles.suggestionsContainer}>
            {isSearchingPlaces && (
              <View style={styles.searchingRow}>
                <ActivityIndicator size="small" color="#007AFF" />
                <Text style={styles.searchingText}>Searching places worldwide...</Text>
              </View>
            )}
            {suggestions.map((item) => (
              <TouchableOpacity
                key={item.id}
                style={styles.suggestionItem}
                onPress={() => handleSelectSuggestion(item)}
              >
                <Text style={styles.suggestionIcon}>📍</Text>
                <View style={styles.suggestionTextContainer}>
                  <Text style={styles.suggestionTitle} numberOfLines={1}>
                    {item.name}
                  </Text>
                  <Text style={styles.suggestionSubtitle} numberOfLines={1}>
                    {item.displayName}
                  </Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Quick Presets */}
        <View style={styles.presetContainer}>
          <Text style={styles.presetLabel}>Quick Presets:</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.presetButtonsRow}>
            <TouchableOpacity
              style={[styles.presetChip, styles.presetChipGps]}
              onPress={handleUseCurrentLocation}
              accessibilityLabel="Quick GPS current location"
            >
              <Text style={styles.presetChipGpsText}>📍 Current Location</Text>
            </TouchableOpacity>
            {PRESET_PLACES.map((preset, index) => (
              <TouchableOpacity
                key={index}
                style={styles.presetChip}
                onPress={() => applyPreset(preset)}
                accessibilityLabel={`Preset: ${preset.name}`}
              >
                <Text style={styles.presetChipText}>{preset.name}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>

        {/* Collapsible Coordinates Toggle */}
        <TouchableOpacity
          style={styles.coordsToggle}
          onPress={() => setShowCoordinateDetails(!showCoordinateDetails)}
        >
          <Text style={styles.coordsToggleText}>
            {showCoordinateDetails ? '▾ Hide Exact Coordinates' : '▸ Show Exact Coordinates'}
          </Text>
          <Text style={styles.coordsSummaryText}>
            ({parseFloat(startLat).toFixed(4)}, {parseFloat(startLon).toFixed(4)} → {parseFloat(endLat).toFixed(4)}, {parseFloat(endLon).toFixed(4)})
          </Text>
        </TouchableOpacity>

        {showCoordinateDetails && (
          <View style={styles.coordsDetailBox}>
            <View style={styles.coordRow}>
              <View style={styles.coordInput}>
                <Input
                  label="Start Lat"
                  value={startLat}
                  onChangeText={setStartLat}
                  keyboardType="numeric"
                />
              </View>
              <View style={styles.coordInput}>
                <Input
                  label="Start Lon"
                  value={startLon}
                  onChangeText={setStartLon}
                  keyboardType="numeric"
                />
              </View>
            </View>
            <View style={styles.coordRow}>
              <View style={styles.coordInput}>
                <Input
                  label="End Lat"
                  value={endLat}
                  onChangeText={setEndLat}
                  keyboardType="numeric"
                />
              </View>
              <View style={styles.coordInput}>
                <Input
                  label="End Lon"
                  value={endLon}
                  onChangeText={setEndLon}
                  keyboardType="numeric"
                />
              </View>
            </View>
          </View>
        )}

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
          title={loading ? 'Calculating Safe Routes...' : 'Find & Compare Routes'}
          onPress={handleSearchRoutes}
          disabled={loading}
          accessibilityLabel="Find safe routes button"
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
                  ? `🔥 Heatmap Overlay: ON ${heatmapSegments.length ? `(${heatmapSegments.length})` : ''}`
                  : '⚪ Heatmap Overlay: OFF'}
              </Text>
            </TouchableOpacity>
            {showHeatmap && (
              <Text style={styles.demoWatermarkText}>DEMO ML MODEL</Text>
            )}
          </View>

          {/* Active Live Navigation HUD Card */}
          {isNavigating && (
            <Card style={styles.navHudCard}>
              <View style={styles.navHudHeader}>
                <View style={styles.navHudBadge}>
                  <Text style={styles.navHudBadgeDot}>🟢</Text>
                  <Text style={styles.navHudBadgeText}>LIVE SAFE GUIDANCE</Text>
                </View>
                <View style={[styles.navRiskPill, { backgroundColor: getRouteColor(selectedMode) }]}>
                  <Text style={styles.navRiskPillText}>{selectedMode} PATH</Text>
                </View>
              </View>

              <Text style={styles.navInstructionText}>
                {navInstruction || `Proceed along ${selectedMode.toLowerCase()} route towards destination`}
              </Text>

              <View style={styles.navMetricsRow}>
                <View style={styles.navMetricItem}>
                  <Text style={styles.navMetricVal}>{formatDistance(navRemainingDistance)}</Text>
                  <Text style={styles.navMetricLbl}>Remaining</Text>
                </View>
                <View style={styles.navMetricDivider} />
                <View style={styles.navMetricItem}>
                  <Text style={styles.navMetricVal}>{formatDuration(navRemainingTime)}</Text>
                  <Text style={styles.navMetricLbl}>Est. Walking</Text>
                </View>
                <View style={styles.navMetricDivider} />
                <View style={styles.navMetricItem}>
                  <Text style={[styles.navMetricVal, { color: getRouteColor(selectedMode) }]}>
                    {selectedRoute?.safetyScore !== null && selectedRoute?.safetyScore !== undefined
                      ? `${Math.round(selectedRoute.safetyScore)}%`
                      : 'Active'}
                  </Text>
                  <Text style={styles.navMetricLbl}>Safety Rating</Text>
                </View>
              </View>

              <View style={styles.navBtnRow}>
                <TouchableOpacity
                  style={styles.navGoogleBtn}
                  onPress={handleOpenGoogleMaps}
                  accessibilityLabel="Open voice turn-by-turn navigation in Google Maps"
                >
                  <Text style={styles.navGoogleBtnText}>🧭 Google Maps Voice</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.navStopBtn}
                  onPress={handleStopNavigation}
                  accessibilityLabel="End current navigation"
                >
                  <Text style={styles.navStopBtnText}>🛑 End Guidance</Text>
                </TouchableOpacity>
              </View>
            </Card>
          )}

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
              userLocation={navUserLocation}
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

              <View style={styles.actionButtonsContainer}>
                {!isNavigating ? (
                  <>
                    <TouchableOpacity
                      style={[styles.primaryNavBtn, { backgroundColor: getRouteColor(selectedMode) }]}
                      onPress={handleStartNavigation}
                      accessibilityLabel="Start live navigation"
                    >
                      <Text style={styles.primaryNavBtnText}>🚶 Start Live Safe Navigation</Text>
                    </TouchableOpacity>

                    <TouchableOpacity
                      style={styles.secondaryMapsBtn}
                      onPress={handleOpenGoogleMaps}
                      accessibilityLabel="Open turn-by-turn navigation in Google Maps"
                    >
                      <Text style={styles.secondaryMapsBtnText}>🧭 Open in Google Maps (Spoken Voice)</Text>
                    </TouchableOpacity>
                  </>
                ) : (
                  <TouchableOpacity
                    style={styles.stopNavBtn}
                    onPress={handleStopNavigation}
                    accessibilityLabel="Stop active navigation"
                  >
                    <Text style={styles.stopNavBtnText}>🛑 Stop Live Navigation</Text>
                  </TouchableOpacity>
                )}
              </View>
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
  formCardHeader: {
    marginBottom: 12,
  },
  formSub: {
    fontSize: 13,
    color: '#636366',
    marginTop: 2,
  },
  placeInputGroup: {
    marginBottom: 8,
  },
  inputGroupLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#48484A',
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  placeInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#E5E5EA',
    paddingHorizontal: 10,
    paddingVertical: Platform.OS === 'ios' ? 10 : 4,
  },
  placeDot: {
    fontSize: 14,
    marginRight: 8,
  },
  placeTextInput: {
    flex: 1,
    fontSize: 15,
    color: '#1C1C1E',
    paddingVertical: 6,
  },
  clearBtn: {
    paddingHorizontal: 6,
    paddingVertical: 4,
  },
  clearBtnText: {
    fontSize: 14,
    color: '#8E8E93',
    fontWeight: '600',
  },
  currentLocBtn: {
    backgroundColor: '#EBF4FF',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6,
    marginLeft: 6,
  },
  currentLocBtnText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#007AFF',
  },
  swapRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 4,
  },
  swapDivider: {
    flex: 1,
    height: 1,
    backgroundColor: '#E5E5EA',
  },
  swapButton: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#D1D1D6',
    borderRadius: 14,
    paddingHorizontal: 10,
    paddingVertical: 3,
    marginHorizontal: 10,
  },
  swapButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#007AFF',
  },
  suggestionsContainer: {
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#D1D1D6',
    marginTop: 4,
    marginBottom: 10,
    maxHeight: 220,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 4,
    overflow: 'hidden',
  },
  searchingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 10,
    gap: 8,
    backgroundColor: '#F8F9FA',
  },
  searchingText: {
    fontSize: 13,
    color: '#636366',
  },
  suggestionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E5EA',
  },
  suggestionIcon: {
    fontSize: 16,
    marginRight: 10,
  },
  suggestionTextContainer: {
    flex: 1,
  },
  suggestionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  suggestionSubtitle: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 2,
  },
  presetChipGps: {
    backgroundColor: '#EBF4FF',
    borderColor: '#007AFF',
  },
  presetChipGpsText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#007AFF',
  },
  coordsToggle: {
    flexDirection: 'column',
    alignItems: 'flex-start',
    marginVertical: 6,
    paddingVertical: 4,
  },
  coordsToggleText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#007AFF',
  },
  coordsSummaryText: {
    fontSize: 10,
    color: '#8E8E93',
    marginTop: 2,
  },
  coordsDetailBox: {
    backgroundColor: '#F8F9FA',
    padding: 8,
    borderRadius: 8,
    marginBottom: 8,
  },
  // Navigation HUD & Actions
  navHudCard: {
    marginHorizontal: 16,
    marginBottom: 10,
    backgroundColor: '#1C1C1E',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1.5,
    borderColor: '#34C759',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    elevation: 6,
  },
  navHudHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  navHudBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(52, 199, 89, 0.15)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  navHudBadgeDot: {
    fontSize: 10,
    marginRight: 5,
  },
  navHudBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#34C759',
    letterSpacing: 0.5,
  },
  navRiskPill: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  navRiskPillText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  navInstructionText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 12,
    lineHeight: 22,
  },
  navMetricsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    backgroundColor: '#2C2C2E',
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 8,
    marginBottom: 12,
  },
  navMetricItem: {
    alignItems: 'center',
    flex: 1,
  },
  navMetricDivider: {
    width: 1,
    height: 24,
    backgroundColor: '#48484A',
  },
  navMetricVal: {
    fontSize: 18,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  navMetricLbl: {
    fontSize: 11,
    color: '#AEAEB2',
    marginTop: 2,
  },
  navBtnRow: {
    flexDirection: 'row',
    gap: 8,
  },
  navGoogleBtn: {
    flex: 1,
    backgroundColor: '#007AFF',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  navGoogleBtnText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  navStopBtn: {
    flex: 1,
    backgroundColor: '#FF3B30',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  navStopBtnText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  actionButtonsContainer: {
    marginTop: 12,
    gap: 10,
  },
  primaryNavBtn: {
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 3,
  },
  primaryNavBtnText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  secondaryMapsBtn: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1.5,
    borderColor: '#007AFF',
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryMapsBtnText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#007AFF',
  },
  stopNavBtn: {
    backgroundColor: '#FF3B30',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stopNavBtnText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FFFFFF',
  },
});
