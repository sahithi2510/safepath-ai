import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

const DEFAULT_API_URL = process.env.EXPO_PUBLIC_API_URL || process.env.EXPO_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';

export const api = axios.create({
  baseURL: DEFAULT_API_URL,
  headers: {
    'Content-Type': 'application/json',
    'Bypass-Tunnel-Reminder': 'true',
  },
  timeout: 10000,
});

export const getApiBaseUrl = async (): Promise<string> => {
  const custom = await SecureStore.getItemAsync('custom_api_url');
  return custom || DEFAULT_API_URL;
};

export const setApiBaseUrl = async (url: string): Promise<void> => {
  if (url && url.trim()) {
    await SecureStore.setItemAsync('custom_api_url', url.trim());
  } else {
    await SecureStore.deleteItemAsync('custom_api_url');
  }
};

// Add a request interceptor to inject the JWT token and custom server URL
api.interceptors.request.use(
  async (config) => {
    const customUrl = await SecureStore.getItemAsync('custom_api_url');
    if (customUrl) {
      config.baseURL = customUrl;
    }
    const token = await SecureStore.getItemAsync('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add a response interceptor to handle 401 Unauthorized
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response && error.response.status === 401) {
      // Clear token to force logout
      await SecureStore.deleteItemAsync('token');
      await SecureStore.deleteItemAsync('user');
      // In a real app we might use an event emitter here to notify AuthContext to update state
      // For now, the next API request will just be unauthenticated, and UI state will remain until re-checked.
    }
    return Promise.reject(error);
  }
);
