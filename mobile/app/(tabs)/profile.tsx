import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert, ActivityIndicator } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { UserService, UserProfile } from '../../services/user';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { useRouter, useFocusEffect } from 'expo-router';

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileData, setProfileData] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchProfile = async () => {
    try {
      const data = await UserService.getProfile();
      setProfileData(data);
    } catch (error) {
      console.error('Failed to fetch profile', error);
    } finally {
      setLoading(false);
    }
  };

  useFocusEffect(
    useCallback(() => {
      fetchProfile();
    }, [])
  );

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  if (loading && !profileData && !user) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  const currentPref = profileData?.routePreference || user?.routePreference || 'STANDARD';

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.headerTitle}>My Profile</Text>

      <Card style={styles.profileCard}>
        <View style={styles.avatarPlaceholder}>
          <Text style={styles.avatarText}>
            {user?.fullName?.charAt(0).toUpperCase() || 'U'}
          </Text>
        </View>
        <Text style={styles.name}>{profileData?.fullName || user?.fullName}</Text>
        <Text style={styles.email}>{profileData?.email || user?.email}</Text>
        
        {profileData?.phoneNumber && (
          <Text style={styles.phone}>{profileData.phoneNumber}</Text>
        )}

        <View style={styles.preferenceBadgeContainer}>
          <Text style={styles.preferenceLabel}>Route Preference:</Text>
          <View style={styles.preferencePill}>
            <Text style={styles.preferencePillText}>{currentPref}</Text>
          </View>
        </View>
      </Card>

      <View style={styles.settingsSection}>
        <Text style={styles.sectionTitle}>Account</Text>
        
        <Button 
          title="Edit Profile" 
          variant="secondary" 
          onPress={() => router.push('/edit-profile')} 
        />
        <Button 
          title="Emergency Contacts" 
          variant="secondary" 
          onPress={() => router.push('/emergency')} 
        />
        <Button 
          title="Routing Preferences" 
          variant="secondary" 
          onPress={() => router.push('/settings')} 
        />
      </View>

      <Button 
        title="Sign Out" 
        variant="danger" 
        onPress={handleLogout} 
        style={styles.logoutButton}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F2F2F7',
    padding: 16,
    paddingTop: 60,
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 20,
    color: '#1C1C1E',
  },
  profileCard: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  avatarPlaceholder: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#007AFF',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  avatarText: {
    color: '#FFF',
    fontSize: 32,
    fontWeight: 'bold',
  },
  name: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  email: {
    fontSize: 16,
    color: '#8E8E93',
    marginBottom: 4,
  },
  phone: {
    fontSize: 16,
    color: '#8E8E93',
  },
  preferenceBadgeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    gap: 8,
  },
  preferenceLabel: {
    fontSize: 13,
    color: '#636366',
    fontWeight: '500',
  },
  preferencePill: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
  },
  preferencePillText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  settingsSection: {
    marginTop: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 12,
    paddingHorizontal: 4,
  },
  logoutButton: {
    marginTop: 'auto',
    marginBottom: 32,
  }
});
