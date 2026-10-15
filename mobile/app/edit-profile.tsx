import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../context/AuthContext';
import { UserService, UserProfile } from '../services/user';
import { Input } from '../components/Input';
import { Button } from '../components/Button';
import { Card } from '../components/Card';

export default function EditProfileScreen() {
  const router = useRouter();
  const { user, updateUser } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber || '');
  const [email, setEmail] = useState(user?.email || '');

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [nameError, setNameError] = useState<string | undefined>();
  const [phoneError, setPhoneError] = useState<string | undefined>();

  useEffect(() => {
    let isMounted = true;

    const loadProfile = async () => {
      try {
        setLoading(true);
        const profile: UserProfile = await UserService.getProfile();
        if (isMounted) {
          setFullName(profile.fullName || '');
          setPhoneNumber(profile.phoneNumber || '');
          setEmail(profile.email || '');
        }
      } catch (err: any) {
        if (isMounted) {
          // Fall back to AuthContext user if offline or network error
          if (user) {
            setFullName(user.fullName || '');
            setPhoneNumber(user.phoneNumber || '');
            setEmail(user.email || '');
          }
          const msg =
            err.response?.data?.message ||
            err.response?.data?.error ||
            'Could not load latest profile from server. Showing cached details.';
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

  const validate = (): boolean => {
    let isValid = true;
    setNameError(undefined);
    setPhoneError(undefined);

    const trimmedName = fullName.trim();
    if (!trimmedName) {
      setNameError('Full name is required.');
      isValid = false;
    } else if (trimmedName.length < 2) {
      setNameError('Full name must be at least 2 characters.');
      isValid = false;
    } else if (trimmedName.length > 100) {
      setNameError('Full name cannot exceed 100 characters.');
      isValid = false;
    }

    const trimmedPhone = phoneNumber.trim();
    if (trimmedPhone.length > 0) {
      // Validate phone format: allow digits, plus, hyphens, spaces, parentheses
      const phoneRegex = /^[+]?[(]?[0-9]{1,4}[)]?[-\s./0-9]{5,20}$/;
      if (!phoneRegex.test(trimmedPhone)) {
        setPhoneError('Please enter a valid phone number (e.g. +91 9876543210).');
        isValid = false;
      }
    }

    return isValid;
  };

  const handleSave = async () => {
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      const updatedProfile = await UserService.updateProfile({
        fullName: fullName.trim(),
        phoneNumber: phoneNumber.trim() || undefined,
      });

      // Update local auth context & secure storage
      await updateUser({
        fullName: updatedProfile.fullName,
        phoneNumber: updatedProfile.phoneNumber,
        routePreference: updatedProfile.routePreference,
      });

      setSuccessMessage('Profile updated successfully.');
      setTimeout(() => {
        router.back();
      }, 1000);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to save profile changes. Please try again.';
      setErrorMessage(msg);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>Loading profile details...</Text>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={{ flex: 1 }}
    >
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        {/* Navigation / Header */}
        <View style={styles.headerRow}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.back()}
            accessibilityLabel="Back"
          >
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Edit Profile</Text>
          <View style={{ width: 60 }} />
        </View>

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

        {/* Form Card */}
        <Card style={styles.formCard}>
          <Text style={styles.sectionHeader}>Personal Information</Text>

          <Input
            label="Full Name *"
            placeholder="Your full name"
            value={fullName}
            onChangeText={(text) => {
              setFullName(text);
              if (nameError) setNameError(undefined);
              if (errorMessage) setErrorMessage(null);
            }}
            error={nameError}
            autoCapitalize="words"
            autoCorrect={false}
          />

          <Input
            label="Phone Number"
            placeholder="+91 9876543210 (optional)"
            value={phoneNumber}
            onChangeText={(text) => {
              setPhoneNumber(text);
              if (phoneError) setPhoneError(undefined);
              if (errorMessage) setErrorMessage(null);
            }}
            error={phoneError}
            keyboardType="phone-pad"
            autoCorrect={false}
          />

          {/* Email (Read Only) */}
          <View style={styles.readOnlyContainer}>
            <Text style={styles.readOnlyLabel}>Email Address</Text>
            <View style={styles.readOnlyBox}>
              <Text style={styles.readOnlyText}>{email}</Text>
              <Text style={styles.readOnlyBadge}>Verified</Text>
            </View>
            <Text style={styles.readOnlyHelp}>Email address is tied to your account and cannot be modified.</Text>
          </View>
        </Card>

        {/* Action Buttons */}
        <View style={styles.actionsContainer}>
          <Button
            title={saving ? 'Saving Changes...' : 'Save Profile'}
            onPress={handleSave}
            disabled={saving}
          />

          <Button
            title="Cancel"
            variant="secondary"
            onPress={() => router.back()}
            disabled={saving}
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
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
    marginBottom: 20,
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
  formCard: {
    padding: 20,
    marginBottom: 16,
  },
  sectionHeader: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 12,
  },
  readOnlyContainer: {
    marginVertical: 10,
  },
  readOnlyLabel: {
    fontSize: 14,
    color: '#3A3A3C',
    marginBottom: 4,
    fontWeight: '500',
  },
  readOnlyBox: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 8,
    padding: 12,
    backgroundColor: '#EFEFF4',
  },
  readOnlyText: {
    fontSize: 15,
    color: '#636366',
  },
  readOnlyBadge: {
    fontSize: 11,
    fontWeight: '600',
    color: '#34C759',
    backgroundColor: '#E8F8EE',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
    overflow: 'hidden',
  },
  readOnlyHelp: {
    fontSize: 12,
    color: '#8E8E93',
    marginTop: 4,
  },
  actionsContainer: {
    marginTop: 8,
  },
});
