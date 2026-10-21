import React, { useState } from 'react';
import { View, Text, StyleSheet, KeyboardAvoidingView, Platform, Alert, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { Input } from '../../components/Input';
import { Button } from '../../components/Button';
import { ServerConfigModal } from '../../components/ServerConfigModal';
import { AuthService } from '../../services/auth';
import { useAuth } from '../../context/AuthContext';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [serverModalVisible, setServerModalVisible] = useState(false);
  const router = useRouter();
  const { login } = useAuth();

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert('Error', 'Please fill in all fields');
      return;
    }
    
    try {
      setLoading(true);
      const data = await AuthService.login(email, password);
      await login(data);
      router.replace('/(tabs)');
    } catch (error: any) {
      Alert.alert(
        'Login Failed',
        error.response?.data?.message ||
          'Network error: could not connect to backend server. Tap "Server Settings" below to configure your server address.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.content}>
        <Text style={styles.title}>Welcome Back</Text>
        <Text style={styles.subtitle}>Sign in to SafePath AI</Text>
        
        <Input
          label="Email"
          placeholder="Enter your email"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        
        <Input
          label="Password"
          placeholder="Enter your password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
        />
        
        <Button 
          title={loading ? "Signing In..." : "Sign In"} 
          onPress={handleLogin} 
          disabled={loading}
          style={styles.button}
        />
        
        <Button 
          title="Create an Account" 
          variant="secondary"
          onPress={() => router.push('/(auth)/register')} 
          disabled={loading}
        />

        <TouchableOpacity 
          style={styles.serverSettingsLink}
          onPress={() => setServerModalVisible(true)}
        >
          <Text style={styles.serverSettingsText}>⚙️ Server Settings / Change API URL</Text>
        </TouchableOpacity>
      </View>

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
  content: {
    flex: 1,
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
    marginTop: 20,
    alignItems: 'center',
    padding: 8,
  },
  serverSettingsText: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '500',
  },
});
