import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  TouchableOpacity,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../context/AuthContext';
import {
  UserService,
  UserProfile,
  RoutePreference,
  ROUTE_PREFERENCE_OPTIONS,
} from '../services/user';
import { Button } from '../components/Button';
import { Card } from '../components/Card';

export default function SettingsScreen() {
  const router = useRouter();
  const { user, updateUser } = useAuth();

  const [selectedPreference, setSelectedPreference] = useState<RoutePreference>(
    user?.routePreference || 'STANDARD'
  );
  const [savedPreference, setSavedPreference] = useState<RoutePreference>(
    user?.routePreference || 'STANDARD'
  );

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const loadProfile = async () => {
      try {
        setLoading(true);
        const profile: UserProfile = await UserService.getProfile();
        if (isMounted) {
          const pref = profile.routePreference || 'STANDARD';
          setSelectedPreference(pref);
          setSavedPreference(pref);
        }
      } catch (err: any) {
        if (isMounted) {
          const fallback = user?.routePreference || 'STANDARD';
          setSelectedPreference(fallback);
          setSavedPreference(fallback);
          const msg =
            err.response?.data?.message ||
            err.response?.data?.error ||
            'Could not load latest preferences from server. Showing cached setting.';
          setErrorMessage(msg);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    loadProfile();

    return () => {
      isMounted = false;
    };
  }, []);

  const handleSavePreference = async () => {
    setErrorMessage(null);
    setSuccessMessage(null);

    setSaving(true);
    try {
      const updatedProfile = await UserService.updateProfile({
        routePreference: selectedPreference,
      });

      setSavedPreference(updatedProfile.routePreference);
      await updateUser({
        routePreference: updatedProfile.routePreference,
      });

      setSuccessMessage('Routing preference saved successfully.');
      setTimeout(() => {
        router.back();
      }, 1000);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to save routing preference. Please check connection and retry.';
      setErrorMessage(msg);
    } finally {
      setSaving(false);
    }
  };

  const hasUnsavedChanges = selectedPreference !== savedPreference;

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>Loading routing preferences...</Text>
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      {/* Header */}
      <View style={styles.headerRow}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          accessibilityLabel="Back"
        >
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Routing Preferences</Text>
        <View style={{ width: 60 }} />
      </View>

      <Text style={styles.subtitle}>
        Choose your default routing profile. SafePath AI adjusts the A* cost function weights based
        on your personal safety needs and travel style.
      </Text>

      {/* Notifications / Alerts */}
      {errorMessage && (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>{errorMessage}</Text>
        </View>
      )}

      {successMessage && (
        <View style={styles.successBanner}>
          <Text style={styles.successBannerText}>{successMessage}</Text>
        </View>
      )}

      {/* Options List */}
      <View style={styles.optionsList}>
        {ROUTE_PREFERENCE_OPTIONS.map((option) => {
          const isSelected = selectedPreference === option.key;
          const isSaved = savedPreference === option.key;

          return (
            <TouchableOpacity
              key={option.key}
              activeOpacity={0.8}
              onPress={() => {
                setSelectedPreference(option.key);
                if (errorMessage) setErrorMessage(null);
                if (successMessage) setSuccessMessage(null);
              }}
            >
              <Card
                style={[
                  styles.optionCard,
                  isSelected && styles.optionCardSelected,
                ]}
              >
                <View style={styles.optionHeader}>
                  <View style={styles.radioContainer}>
                    <View
                      style={[
                        styles.radioOuter,
                        isSelected && styles.radioOuterSelected,
                      ]}
                    >
                      {isSelected && <View style={styles.radioInner} />}
                    </View>
                    <View style={styles.titleContainer}>
                      <Text style={[styles.optionTitle, isSelected && styles.optionTitleSelected]}>
                        {option.label}
                      </Text>
                      {isSaved && (
                        <View style={styles.currentBadge}>
                          <Text style={styles.currentBadgeText}>CURRENT</Text>
                        </View>
                      )}
                    </View>
                  </View>

                  <View
                    style={[
                      styles.categoryBadge,
                      option.badge === 'Personalized'
                        ? styles.personalizedBadge
                        : option.badge === 'Safety'
                        ? styles.safetyBadge
                        : styles.defaultBadge,
                    ]}
                  >
                    <Text
                      style={[
                        styles.categoryBadgeText,
                        option.badge === 'Personalized'
                          ? styles.personalizedBadgeText
                          : option.badge === 'Safety'
                          ? styles.safetyBadgeText
                          : styles.defaultBadgeText,
                      ]}
                    >
                      {option.badge}
                    </Text>
                  </View>
                </View>

                <Text style={styles.optionDescription}>{option.description}</Text>

                <View style={styles.weightsRow}>
                  <View style={styles.weightChip}>
                    <Text style={styles.weightLabel}>Safety: </Text>
                    <Text style={styles.weightValue}>{option.weights.safety}</Text>
                  </View>
                  <View style={styles.weightChip}>
                    <Text style={styles.weightLabel}>Time: </Text>
                    <Text style={styles.weightValue}>{option.weights.time}</Text>
                  </View>
                  <View style={styles.weightChip}>
                    <Text style={styles.weightLabel}>Distance: </Text>
                    <Text style={styles.weightValue}>{option.weights.distance}</Text>
                  </View>
                </View>
              </Card>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Action Buttons */}
      <View style={styles.actionsContainer}>
        <Button
          title={
            saving
              ? 'Saving Preference...'
              : hasUnsavedChanges
              ? 'Save Preference Changes'
              : 'Save Preference'
          }
          onPress={handleSavePreference}
          disabled={saving}
        />

        <Button
          title="Back to Profile"
          variant="secondary"
          onPress={() => router.back()}
          disabled={saving}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F2F2F7',
    padding: 16,
    paddingTop: 50,
    paddingBottom: 40,
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 15,
    color: '#8E8E93',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  backButton: {
    paddingVertical: 8,
    paddingHorizontal: 4,
    width: 60,
  },
  backButtonText: {
    fontSize: 17,
    color: '#007AFF',
    fontWeight: '600',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#636366',
    lineHeight: 20,
    marginBottom: 16,
    paddingHorizontal: 4,
  },
  errorBanner: {
    backgroundColor: '#FFE5E5',
    borderColor: '#FF3B30',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  errorBannerText: {
    color: '#D70015',
    fontSize: 14,
    lineHeight: 18,
  },
  successBanner: {
    backgroundColor: '#E8F8EE',
    borderColor: '#34C759',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  successBannerText: {
    color: '#1E7B34',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  optionsList: {
    marginBottom: 16,
  },
  optionCard: {
    padding: 16,
    marginBottom: 12,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: 'transparent',
    backgroundColor: '#FFFFFF',
  },
  optionCardSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#F5F9FF',
  },
  optionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  radioContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: '#C7C7CC',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
  },
  radioOuterSelected: {
    borderColor: '#007AFF',
  },
  radioInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#007AFF',
  },
  titleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    flex: 1,
  },
  optionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginRight: 8,
  },
  optionTitleSelected: {
    color: '#007AFF',
  },
  currentBadge: {
    backgroundColor: '#34C759',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  currentBadgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '700',
  },
  categoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  categoryBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  personalizedBadge: {
    backgroundColor: '#EBF4FF',
  },
  personalizedBadgeText: {
    color: '#5856D6',
  },
  safetyBadge: {
    backgroundColor: '#E8F8EE',
  },
  safetyBadgeText: {
    color: '#34C759',
  },
  defaultBadge: {
    backgroundColor: '#F2F2F7',
  },
  defaultBadgeText: {
    color: '#8E8E93',
  },
  optionDescription: {
    fontSize: 13,
    color: '#636366',
    lineHeight: 18,
    marginBottom: 10,
    paddingLeft: 32,
  },
  weightsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingLeft: 32,
  },
  weightChip: {
    flexDirection: 'row',
    backgroundColor: '#EFEFF4',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  weightLabel: {
    fontSize: 11,
    color: '#8E8E93',
  },
  weightValue: {
    fontSize: 11,
    fontWeight: '600',
    color: '#3A3A3C',
  },
  actionsContainer: {
    marginTop: 8,
  },
});
