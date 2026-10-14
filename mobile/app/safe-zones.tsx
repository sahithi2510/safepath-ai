import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  TextInput,
  Alert,
} from 'react-native';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import {
  SafeZone,
  SafeZoneCategory,
  CATEGORY_CONFIG,
  VERIFICATION_CONFIG,
  fetchNearbySafeZones,
} from '../services/safeZones';

export default function SafeZonesScreen() {
  const router = useRouter();

  // Search parameters
  const [lat, setLat] = useState('12.9756');
  const [lon, setLon] = useState('77.6066');
  const [radiusMeters, setRadiusMeters] = useState(2000);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  // Query state
  const [safeZones, setSafeZones] = useState<SafeZone[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [locatingUser, setLocatingUser] = useState(false);

  useEffect(() => {
    loadSafeZones();
  }, [radiusMeters, selectedCategory]);

  const loadSafeZones = async () => {
    const latNum = parseFloat(lat);
    const lonNum = parseFloat(lon);
    if (isNaN(latNum) || isNaN(lonNum)) {
      setErrorMsg('Please enter valid numeric latitude and longitude coordinates.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const types = selectedCategory === 'ALL' ? undefined : [selectedCategory];
      const data = await fetchNearbySafeZones({
        lat: latNum,
        lon: lonNum,
        radiusMeters,
        types,
        limit: 30,
      });
      setSafeZones(data);
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to query safe zones. Ensure backend service is reachable.';
      setErrorMsg(message);
    } finally {
      setLoading(false);
    }
  };

  const handleGetCurrentLocation = async () => {
    setLocatingUser(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert(
          'Location Permission Denied',
          'Using default Bangalore coordinates. You can also manually enter coordinates.'
        );
        return;
      }
      const loc = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setLat(loc.coords.latitude.toFixed(4));
      setLon(loc.coords.longitude.toFixed(4));
    } catch (err) {
      Alert.alert('Location Error', 'Unable to fetch current GPS location.');
    } finally {
      setLocatingUser(false);
    }
  };

  const handlePresetBangalore = () => {
    setLat('12.9756');
    setLon('77.6066');
  };

  const handleNavigateToSafeZone = (zone: SafeZone) => {
    router.push({
      pathname: '/(tabs)/routes',
      params: {
        endLat: zone.latitude.toString(),
        endLon: zone.longitude.toString(),
        endName: zone.name,
      },
    });
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Title & Description */}
      <View style={styles.header}>
        <Text style={styles.title}>Safe Zones & Assistance</Text>
        <Text style={styles.subtitle}>
          Verified public facilities (police stations, hospitals, transit hubs) sourced from authoritative registries.
        </Text>
      </View>

      {/* Prominent Academic Disclaimer */}
      <View style={styles.disclaimerBox}>
        <Text style={styles.disclaimerIcon}>⚠️</Text>
        <Text style={styles.disclaimerText}>
          <Text style={styles.disclaimerBold}>Academic & Safety Notice: </Text>
          Listing a facility confirms physical existence in public records, but does NOT guarantee real-time staffing, operational availability, or personal safety. In an active emergency, immediately dial 112 / 100 / 911.
        </Text>
      </View>

      {/* Coordinate & Search Card */}
      <Card style={styles.searchCard}>
        <Text style={styles.cardHeader}>Search Near Location</Text>

        <View style={styles.coordsRow}>
          <View style={styles.coordInputGroup}>
            <Text style={styles.inputLabel}>Latitude</Text>
            <TextInput
              style={styles.input}
              value={lat}
              onChangeText={setLat}
              keyboardType="numeric"
              placeholder="12.9756"
            />
          </View>
          <View style={styles.coordInputGroup}>
            <Text style={styles.inputLabel}>Longitude</Text>
            <TextInput
              style={styles.input}
              value={lon}
              onChangeText={setLon}
              keyboardType="numeric"
              placeholder="77.6066"
            />
          </View>
        </View>

        <View style={styles.presetButtonsRow}>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={handleGetCurrentLocation}
            disabled={locatingUser}
          >
            {locatingUser ? (
              <ActivityIndicator size="small" color="#007AFF" />
            ) : (
              <Text style={styles.secondaryButtonText}>📍 My Location</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity style={styles.secondaryButton} onPress={handlePresetBangalore}>
            <Text style={styles.secondaryButtonText}>🏙️ Bangalore Demo</Text>
          </TouchableOpacity>
        </View>

        {/* Radius Chips */}
        <Text style={styles.sectionLabel}>Search Radius</Text>
        <View style={styles.chipsRow}>
          {[1000, 2000, 5000, 10000].map((radius) => (
            <TouchableOpacity
              key={`radius-${radius}`}
              style={[
                styles.radiusChip,
                radiusMeters === radius && styles.radiusChipActive,
              ]}
              onPress={() => setRadiusMeters(radius)}
            >
              <Text
                style={[
                  styles.radiusChipText,
                  radiusMeters === radius && styles.radiusChipTextActive,
                ]}
              >
                {radius >= 1000 ? `${radius / 1000} km` : `${radius} m`}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Category Filters */}
        <Text style={styles.sectionLabel}>Filter Category</Text>
        <View style={styles.chipsRow}>
          {[
            { key: 'ALL', label: 'All', icon: '🌐' },
            { key: 'POLICE_STATION', label: 'Police', icon: '👮' },
            { key: 'HOSPITAL', label: 'Hospitals', icon: '🏥' },
            { key: 'PHARMACY', label: 'Pharmacies', icon: '💊' },
            { key: 'PUBLIC_TRANSPORT', label: 'Transit', icon: '🚏' },
          ].map((cat) => (
            <TouchableOpacity
              key={`cat-${cat.key}`}
              style={[
                styles.categoryChip,
                selectedCategory === cat.key && styles.categoryChipActive,
              ]}
              onPress={() => setSelectedCategory(cat.key)}
            >
              <Text
                style={[
                  styles.categoryChipText,
                  selectedCategory === cat.key && styles.categoryChipTextActive,
                ]}
              >
                {cat.icon} {cat.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <Button
          title={loading ? 'Searching Facilities...' : 'Search Safe Zones'}
          onPress={loadSafeZones}
          disabled={loading}
          style={styles.searchButton}
        />
      </Card>

      {/* Results Header */}
      <View style={styles.resultsHeaderRow}>
        <Text style={styles.resultsHeading}>
          Available Facilities ({safeZones.length})
        </Text>
        {loading && <ActivityIndicator size="small" color="#007AFF" />}
      </View>

      {/* Error State */}
      {errorMsg && (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>⚠️ {errorMsg}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={loadSafeZones}>
            <Text style={styles.retryButtonText}>Retry Search</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Empty State */}
      {!loading && !errorMsg && safeZones.length === 0 && (
        <View style={styles.emptyBox}>
          <Text style={styles.emptyIcon}>🏢</Text>
          <Text style={styles.emptyTitle}>No Safe Zones Recorded in Area</Text>
          <Text style={styles.emptyDesc}>
            SafePath AI strictly enforces scientific honesty: we do NOT fabricate facilities or coordinates. Facilities appear only when verified records exist in the database for this search radius.
          </Text>
        </View>
      )}

      {/* Facility Cards */}
      {safeZones.map((zone) => {
        const catConfig = CATEGORY_CONFIG[zone.zoneType] || CATEGORY_CONFIG.OTHER;
        const verifConfig =
          VERIFICATION_CONFIG[zone.verificationStatus] || VERIFICATION_CONFIG.UNVERIFIED;

        return (
          <Card key={zone.id} style={styles.zoneCard}>
            <View style={styles.zoneCardHeader}>
              <View style={[styles.zoneIconCircle, { backgroundColor: catConfig.bgHex }]}>
                <Text style={styles.zoneIconText}>{catConfig.icon}</Text>
              </View>

              <View style={styles.zoneTitleCol}>
                <Text style={styles.zoneName}>{zone.name}</Text>
                <View style={styles.zoneBadgesRow}>
                  <View style={[styles.catBadge, { backgroundColor: catConfig.bgHex }]}>
                    <Text style={[styles.catBadgeText, { color: catConfig.pinColor }]}>
                      {catConfig.label}
                    </Text>
                  </View>

                  <View style={[styles.verifBadge, { backgroundColor: verifConfig.bgHex }]}>
                    <Text style={[styles.verifBadgeText, { color: verifConfig.badgeColor }]}>
                      {verifConfig.label}
                    </Text>
                  </View>
                </View>
              </View>
            </View>

            {/* Details */}
            <View style={styles.zoneDetails}>
              {zone.distanceMeters != null && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Distance:</Text>
                  <Text style={styles.detailValueBold}>
                    {zone.distanceMeters >= 1000
                      ? `${(zone.distanceMeters / 1000).toFixed(1)} km away`
                      : `${Math.round(zone.distanceMeters)} m away`}
                  </Text>
                </View>
              )}

              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Address:</Text>
                <Text style={styles.detailValue}>
                  {zone.address || 'Address not documented'}
                </Text>
              </View>

              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Operating Hours:</Text>
                <Text style={styles.detailValue}>
                  {zone.operatingHours
                    ? zone.operatingHours
                    : zone.is24h
                    ? 'Open 24/7'
                    : 'Operating hours not documented'}
                </Text>
              </View>

              {zone.phoneNumber && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Phone:</Text>
                  <Text style={styles.detailValue}>{zone.phoneNumber}</Text>
                </View>
              )}

              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Source / Provenance:</Text>
                <Text style={styles.detailValueSubtle}>
                  {zone.source ? `Registry: ${zone.source}` : 'OpenStreetMap Node'}
                  {zone.osmNodeId ? ` (#${zone.osmNodeId})` : ''}
                </Text>
              </View>
            </View>

            {/* Card Action */}
            <TouchableOpacity
              style={styles.navigateButton}
              onPress={() => handleNavigateToSafeZone(zone)}
            >
              <Text style={styles.navigateButtonText}>🧭 Directions to this Facility</Text>
            </TouchableOpacity>
          </Card>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  header: {
    marginBottom: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#111827',
  },
  subtitle: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  disclaimerBox: {
    flexDirection: 'row',
    backgroundColor: '#FEF3C7',
    borderWidth: 1,
    borderColor: '#F59E0B',
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
    alignItems: 'flex-start',
  },
  disclaimerIcon: {
    fontSize: 18,
    marginRight: 8,
    marginTop: 2,
  },
  disclaimerText: {
    flex: 1,
    fontSize: 12,
    color: '#92400E',
    lineHeight: 18,
  },
  disclaimerBold: {
    fontWeight: 'bold',
  },
  searchCard: {
    padding: 16,
    marginBottom: 16,
  },
  cardHeader: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 12,
  },
  coordsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  coordInputGroup: {
    flex: 1,
    marginRight: 8,
  },
  inputLabel: {
    fontSize: 12,
    color: '#4B5563',
    marginBottom: 4,
  },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 8,
    padding: 8,
    backgroundColor: '#FFFFFF',
    fontSize: 14,
  },
  presetButtonsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 14,
  },
  secondaryButton: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 10,
    backgroundColor: '#EFF6FF',
    borderRadius: 6,
    alignItems: 'center',
    marginRight: 6,
    borderWidth: 1,
    borderColor: '#BFDBFE',
  },
  secondaryButtonText: {
    color: '#1D4ED8',
    fontSize: 12,
    fontWeight: '600',
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#374151',
    marginTop: 8,
    marginBottom: 6,
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 10,
  },
  radiusChip: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 16,
    backgroundColor: '#E5E7EB',
    marginRight: 8,
    marginBottom: 6,
  },
  radiusChipActive: {
    backgroundColor: '#007AFF',
  },
  radiusChipText: {
    fontSize: 12,
    color: '#374151',
    fontWeight: '500',
  },
  radiusChipTextActive: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  categoryChip: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 16,
    backgroundColor: '#F3F4F6',
    borderWidth: 1,
    borderColor: '#E5E7EB',
    marginRight: 6,
    marginBottom: 6,
  },
  categoryChipActive: {
    backgroundColor: '#1E3A8A',
    borderColor: '#1E3A8A',
  },
  categoryChipText: {
    fontSize: 12,
    color: '#4B5563',
  },
  categoryChipTextActive: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  searchButton: {
    marginTop: 10,
  },
  resultsHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  resultsHeading: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1F2937',
  },
  errorBox: {
    backgroundColor: '#FEE2E2',
    borderWidth: 1,
    borderColor: '#F87171',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  errorText: {
    color: '#B91C1C',
    fontSize: 13,
    marginBottom: 8,
  },
  retryButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#DC2626',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 6,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  emptyBox: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
    marginTop: 8,
  },
  emptyIcon: {
    fontSize: 40,
    marginBottom: 8,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 6,
    textAlign: 'center',
  },
  emptyDesc: {
    fontSize: 13,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 18,
  },
  zoneCard: {
    padding: 16,
    marginBottom: 12,
  },
  zoneCardHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  zoneIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  zoneIconText: {
    fontSize: 22,
  },
  zoneTitleCol: {
    flex: 1,
  },
  zoneName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#111827',
    marginBottom: 4,
  },
  zoneBadgesRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  catBadge: {
    paddingVertical: 2,
    paddingHorizontal: 8,
    borderRadius: 4,
    marginRight: 6,
    marginBottom: 2,
  },
  catBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  verifBadge: {
    paddingVertical: 2,
    paddingHorizontal: 8,
    borderRadius: 4,
    marginBottom: 2,
  },
  verifBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  zoneDetails: {
    borderTopWidth: 1,
    borderTopColor: '#F3F4F6',
    paddingTop: 10,
    marginBottom: 12,
  },
  detailRow: {
    flexDirection: 'row',
    marginBottom: 5,
  },
  detailLabel: {
    fontSize: 12,
    color: '#6B7280',
    width: 130,
    fontWeight: '500',
  },
  detailValue: {
    flex: 1,
    fontSize: 12,
    color: '#1F2937',
  },
  detailValueBold: {
    flex: 1,
    fontSize: 12,
    color: '#057A55',
    fontWeight: '700',
  },
  detailValueSubtle: {
    flex: 1,
    fontSize: 11,
    color: '#9CA3AF',
  },
  navigateButton: {
    backgroundColor: '#007AFF',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  navigateButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '600',
  },
});
