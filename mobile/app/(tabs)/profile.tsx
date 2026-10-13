import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert, ActivityIndicator } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { AuthService } from '../../services/auth';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { useRouter } from 'expo-router';

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileData, setProfileData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const data = await AuthService.me();
        setProfileData(data);
      } catch (error) {
        console.error('Failed to fetch profile', error);
        Alert.alert('Error', 'Could not load profile data');
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, []);

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.headerTitle}>My Profile</Text>

      <Card style={styles.profileCard}>
        <View style={styles.avatarPlaceholder}>
          <Text style={styles.avatarText}>
            {user?.fullName?.charAt(0).toUpperCase() || 'U'}
          </Text>
        </View>
        <Text style={styles.name}>{user?.fullName}</Text>
        <Text style={styles.email}>{user?.email}</Text>
        
        {profileData?.phoneNumber && (
          <Text style={styles.phone}>{profileData.phoneNumber}</Text>
        )}
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
