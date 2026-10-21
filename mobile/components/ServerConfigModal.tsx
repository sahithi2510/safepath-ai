import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import axios from 'axios';
import { Input } from './Input';
import { Button } from './Button';
import { getApiBaseUrl, setApiBaseUrl } from '../services/api';

interface ServerConfigModalProps {
  visible: boolean;
  onClose: () => void;
}

export const ServerConfigModal: React.FC<ServerConfigModalProps> = ({ visible, onClose }) => {
  const [url, setUrl] = useState('');
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  useEffect(() => {
    if (visible) {
      getApiBaseUrl().then((current) => {
        setUrl(current);
        setTestResult(null);
      });
    }
  }, [visible]);

  const handleTestConnection = async () => {
    const trimmed = url.trim().replace(/\/+$/, '');
    if (!trimmed) {
      Alert.alert('Invalid URL', 'Please enter a server URL');
      return;
    }

    setTesting(true);
    setTestResult(null);

    // Ping /health endpoint
    const healthUrl = trimmed.endsWith('/api') ? `${trimmed}/health` : `${trimmed}/api/health`;

    try {
      const response = await axios.get(healthUrl, { timeout: 5000 });
      if (response.status >= 200 && response.status < 300) {
        setTestResult({
          success: true,
          message: 'Connection successful! Server responded with status ' + response.status,
        });
      } else {
        setTestResult({
          success: false,
          message: `Server returned unexpected status ${response.status}`,
        });
      }
    } catch (err: any) {
      setTestResult({
        success: false,
        message: err.message || 'Network request failed. Is the server running?',
      });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    const trimmed = url.trim().replace(/\/+$/, '');
    if (!trimmed) {
      Alert.alert('Invalid URL', 'Server URL cannot be empty');
      return;
    }
    await setApiBaseUrl(trimmed);
    Alert.alert('Saved', 'Server URL has been updated to: ' + trimmed);
    onClose();
  };

  const handleReset = async () => {
    await setApiBaseUrl('');
    const reset = await getApiBaseUrl();
    setUrl(reset);
    setTestResult(null);
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <View style={styles.dialog}>
          <Text style={styles.title}>Server Configuration</Text>
          <Text style={styles.description}>
            Configure the backend server URL. If running locally on your computer, enter your PC local Wi-Fi IP (e.g. http://192.168.x.x:8080/api) or an HTTPS tunnel URL.
          </Text>

          <Input
            label="Backend API Base URL"
            value={url}
            onChangeText={setUrl}
            placeholder="http://192.168.x.x:8080/api"
            autoCapitalize="none"
            autoCorrect={false}
          />

          {testResult && (
            <View
              style={[
                styles.resultBadge,
                testResult.success ? styles.badgeSuccess : styles.badgeError,
              ]}
            >
              <Text
                style={[
                  styles.resultText,
                  testResult.success ? styles.textSuccess : styles.textError,
                ]}
              >
                {testResult.message}
              </Text>
            </View>
          )}

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.testButton, testing && styles.buttonDisabled]}
              onPress={handleTestConnection}
              disabled={testing}
            >
              {testing ? (
                <ActivityIndicator size="small" color="#007AFF" />
              ) : (
                <Text style={styles.testButtonText}>Test Connection</Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity style={styles.resetButton} onPress={handleReset}>
              <Text style={styles.resetButtonText}>Reset Default</Text>
            </TouchableOpacity>
          </View>

          <Button
            title="Save & Use URL"
            onPress={handleSave}
            style={styles.saveButton}
          />

          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Text style={styles.closeButtonText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    padding: 20,
  },
  dialog: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 8,
  },
  description: {
    fontSize: 13,
    color: '#636366',
    lineHeight: 18,
    marginBottom: 12,
  },
  resultBadge: {
    padding: 10,
    borderRadius: 8,
    marginVertical: 8,
  },
  badgeSuccess: {
    backgroundColor: '#E8F5E9',
    borderColor: '#4CAF50',
    borderWidth: 1,
  },
  badgeError: {
    backgroundColor: '#FFEBEE',
    borderColor: '#EF5350',
    borderWidth: 1,
  },
  resultText: {
    fontSize: 13,
    lineHeight: 18,
  },
  textSuccess: {
    color: '#2E7D32',
  },
  textError: {
    color: '#C62828',
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginVertical: 8,
    gap: 8,
  },
  testButton: {
    flex: 1,
    padding: 12,
    borderWidth: 1,
    borderColor: '#007AFF',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  testButtonText: {
    color: '#007AFF',
    fontWeight: '600',
    fontSize: 14,
  },
  resetButton: {
    padding: 12,
    borderWidth: 1,
    borderColor: '#C7C7CC',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  resetButtonText: {
    color: '#636366',
    fontSize: 14,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  saveButton: {
    marginTop: 8,
    marginBottom: 4,
  },
  closeButton: {
    padding: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    color: '#8E8E93',
    fontSize: 14,
  },
});
