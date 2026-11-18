import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, KeyboardAvoidingView, Platform, Alert, TouchableOpacity, ActivityIndicator } from 'react-native';
import { useRouter } from 'expo-router';
import { Input } from '../../components/Input';
import { Button } from '../../components/Button';
import { ServerConfigModal } from '../../components/ServerConfigModal';
import { AuthService } from '../../services/auth';
import { useAuth } from '../../context/AuthContext';
import { checkAndWakeServer, ServerHealthStatus } from '../../services/api';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [serverModalVisible, setServerModalVisible] = useState(false);
  const [serverStatus, setServerStatus] = useState<ServerHealthStatus | null>(null);
  const [isPingingServer, setIsPingingServer] = useState(false);
  const router = useRouter();
  const { login } = useAuth();

  const pingServer = async () => {
    setIsPingingServer(true);
    const res = await checkAndWakeServer();
    setServerStatus(res);
    setIsPingingServer(false);
  };

  useEffect(() => {
    pingServer();
  }, []);

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
      const isNetworkError = !error.response || error.code === 'ECONNABORTED' || error.message?.includes('Network Error') || error.message?.includes('timeout');
      if (isNetworkError) {
        // Trigger server wake-up in background
        pingServer();
        Alert.alert(
          'Server Waking Up',
          'The presentation cloud server was in sleep mode and is now starting up (~30s). Please wait a moment and tap Sign In again.'
        );
      } else {
        Alert.alert(
          'Login Failed',
          error.response?.data?.message || 'Invalid credentials or request failed.'
        );
      }
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
        
        {/* Real-time Server Readiness Pill for Presentation */}
        <TouchableOpacity 
          style={[
            styles.serverStatusBanner,
            serverStatus?.status === 'ONLINE' ? styles.serverStatusOnline : styles.serverStatusWaking
          ]}
          onPress={pingServer}
          activeOpacity={0.8}
        >
          {isPingingServer ? (
            <ActivityIndicator size="small" color={serverStatus?.status === 'ONLINE' ? '#10B981' : '#D97706'} />
          ) : (
            <View style={[
              styles.statusDot, 
              { backgroundColor: serverStatus?.status === 'ONLINE' ? '#10B981' : '#F59E0B' }
            ]} />
          )}
          <Text style={[
            styles.serverStatusText,
            { color: serverStatus?.status === 'ONLINE' ? '#065F46' : '#92400E' }
          ]}>
            {isPingingServer 
              ? 'Checking cloud server...'
              : serverStatus?.status === 'ONLINE'
              ? `Presentation Cloud Server Ready (${serverStatus.latencyMs}ms)`
              : 'Waking cloud server for presentation... Tap to recheck'}
          </Text>
        </TouchableOpacity>

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
          title={loading ? "Connecting to Cloud Server..." : "Sign In"} 
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
          <Text style={styles.serverSettingsText}>Server Settings / API URL</Text>
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
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 15,
    color: '#8E8E93',
    marginBottom: 20,
  },
  serverStatusBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    marginBottom: 20,
    gap: 8,
    borderWidth: 1,
  },
  serverStatusOnline: {
    backgroundColor: '#ECFDF5',
    borderColor: '#A7F3D0',
  },
  serverStatusWaking: {
    backgroundColor: '#FFFBEB',
    borderColor: '#FDE68A',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  serverStatusText: {
    fontSize: 12,
    fontWeight: '600',
    flex: 1,
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
