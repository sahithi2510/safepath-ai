import React, { useState } from 'react';
import { View, Text, StyleSheet, KeyboardAvoidingView, Platform, Alert, ScrollView, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { Input } from '../../components/Input';
import { Button } from '../../components/Button';
import { ServerConfigModal } from '../../components/ServerConfigModal';
import { AuthService } from '../../services/auth';
import { useAuth } from '../../context/AuthContext';

export default function RegisterScreen() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [serverModalVisible, setServerModalVisible] = useState(false);
  
  const router = useRouter();
  const { login } = useAuth();

  const handleRegister = async () => {
    if (!email || !password || !fullName) {
      Alert.alert('Error', 'Full Name, Email and Password are required');
      return;
    }
    
    try {
      setLoading(true);
      const data = await AuthService.register(email, password, fullName, phoneNumber || undefined);
      await login(data);
      router.replace('/(tabs)');
    } catch (error: any) {
      const msg = error.response?.data?.message || (error.response ? 'Registration failed on server' : (error.message || 'Network error: could not connect to server'));
      Alert.alert('Registration Failed', msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Create Account</Text>
        <Text style={styles.subtitle}>Join SafePath AI today</Text>
        
        <Input
          label="Full Name *"
          placeholder="Enter your full name"
          value={fullName}
          onChangeText={setFullName}
        />
        
        <Input
          label="Email *"
          placeholder="Enter your email"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        
        <Input
          label="Phone Number"
          placeholder="Enter your phone number (optional)"
          value={phoneNumber}
          onChangeText={setPhoneNumber}
          keyboardType="phone-pad"
        />
        
        <Input
          label="Password *"
          placeholder="Create a strong password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
        />
        
        <Button 
          title={loading ? "Creating Account..." : "Create Account"} 
          onPress={handleRegister} 
          disabled={loading}
          style={styles.button}
        />
        
        <Button 
          title="Back to Login" 
          variant="secondary"
          onPress={() => router.back()} 
          disabled={loading}
        />

        <TouchableOpacity 
          style={styles.serverSettingsLink}
          onPress={() => setServerModalVisible(true)}
        >
          <Text style={styles.serverSettingsText}>⚙️ Server Settings / Change API URL</Text>
        </TouchableOpacity>
      </ScrollView>

      <ServerConfigModal 
        visible={serverModalVisible} 
        onClose={() => setServerModalVisible(false)} 
      />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#8E8E93',
    marginBottom: 32,
  },
  button: {
    marginTop: 16,
  },
  serverSettingsLink: {
    marginTop: 16,
    alignItems: 'center',
    padding: 8,
  },
  serverSettingsText: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '500',
  },
});
