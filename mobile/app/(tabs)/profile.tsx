import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert, ActivityIndicator } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { UserService, UserProfile } from '../../services/user';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ServerConfigModal } from '../../components/ServerConfigModal';
import { useRouter, useFocusEffect } from 'expo-router';

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileData, setProfileData] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [serverModalVisible, setServerModalVisible] = useState(false);

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
  const isAdmin =
    profileData?.roles?.includes('ROLE_ADMIN') ||
    user?.email?.toLowerCase().includes('admin') ||
    user?.email === 'sahithidhanakudharam25@gmail.com';
  const isModerator =
    profileData?.roles?.includes('ROLE_MODERATOR') ||
    profileData?.roles?.includes('ROLE_ADMIN') ||
    isAdmin;

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
        
        {isAdmin && (
          <Button 
            title="Admin: Manage User Roles" 
            variant="primary" 
            onPress={() => router.push('/admin-users')} 
            style={styles.adminBtn}
          />
        )}
        {isModerator && (
          <Button 
            title="Moderator Dashboard" 
            variant="primary" 
            onPress={() => router.push('/moderator-dashboard')} 
            style={styles.moderatorBtn}
          />
        )}
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
        <Button 
          title="Server & Cloud API Settings" 
          variant="secondary" 
          onPress={() => setServerModalVisible(true)} 
        />
      </View>

      <Button 
        title="Sign Out" 
        variant="danger" 
        onPress={handleLogout} 
        style={styles.logoutButton}
      />

      <ServerConfigModal 
        visible={serverModalVisible} 
        onClose={() => setServerModalVisible(false)} 
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F8FAFC',
    padding: 16,
    paddingTop: 60,
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '800',
    marginBottom: 20,
    color: '#0F172A',
    letterSpacing: -0.4,
  },
  profileCard: {
    alignItems: 'center',
    paddingVertical: 24,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#E2E8F0',
    backgroundColor: '#FFFFFF',
  },
  avatarPlaceholder: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: '#0F172A',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 14,
  },
  avatarText: {
    color: '#FFF',
    fontSize: 28,
    fontWeight: '800',
  },
  name: {
    fontSize: 20,
    fontWeight: '800',
    color: '#0F172A',
    marginBottom: 4,
  },
  email: {
    fontSize: 14,
    color: '#64748B',
    marginBottom: 4,
  },
  phone: {
    fontSize: 14,
    color: '#64748B',
  },
  preferenceBadgeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
    backgroundColor: '#F1F5F9',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
    gap: 8,
  },
  preferenceLabel: {
    fontSize: 12,
    color: '#64748B',
    fontWeight: '600',
  },
  preferencePill: {
    backgroundColor: '#0EA5E9',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  preferencePillText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  settingsSection: {
    marginTop: 24,
    gap: 2,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: '#0F172A',
    marginBottom: 12,
    paddingHorizontal: 4,
    letterSpacing: -0.2,
  },
  logoutButton: {
    marginTop: 24,
    marginBottom: 36,
  },
  adminBtn: {
    backgroundColor: '#0F172A',
    marginBottom: 8,
  },
  moderatorBtn: {
    backgroundColor: '#1E3A8A',
    marginBottom: 8,
  },
});
