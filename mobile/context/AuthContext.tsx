import React, { createContext, useContext, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import { AuthResponse, AuthUser } from '../services/auth';
import { UserService, UserProfile } from '../services/user';

interface AuthContextType {
  token: string | null;
  user: AuthUser | null;
  isLoading: boolean;
  login: (data: AuthResponse) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (updatedFields: Partial<AuthUser>) => Promise<void>;
  refreshProfile: () => Promise<UserProfile | null>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Load token and user from secure storage on startup
    const loadAuth = async () => {
      try {
        const storedToken = await SecureStore.getItemAsync('token');
        const storedUser = await SecureStore.getItemAsync('user');
        
        if (storedToken && storedUser) {
          setToken(storedToken);
          setUser(JSON.parse(storedUser));
        }
      } catch (error) {
        console.error('Failed to load auth state', error);
      } finally {
        setIsLoading(false);
      }
    };

    loadAuth();
  }, []);

  const login = async (data: AuthResponse) => {
    await SecureStore.setItemAsync('token', data.token);
    await SecureStore.setItemAsync('user', JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);
  };

  const logout = async () => {
    await SecureStore.deleteItemAsync('token');
    await SecureStore.deleteItemAsync('user');
    setToken(null);
    setUser(null);
  };

  const updateUser = async (updatedFields: Partial<AuthUser>) => {
    if (!user) return;
    const updatedUser: AuthUser = { ...user, ...updatedFields };
    await SecureStore.setItemAsync('user', JSON.stringify(updatedUser));
    setUser(updatedUser);
  };

  const refreshProfile = async (): Promise<UserProfile | null> => {
    try {
      const profile = await UserService.getProfile();
      const updatedUser: AuthUser = {
        id: profile.id,
        email: profile.email,
        fullName: profile.fullName,
        phoneNumber: profile.phoneNumber,
        routePreference: profile.routePreference,
      };
      await SecureStore.setItemAsync('user', JSON.stringify(updatedUser));
      setUser(updatedUser);
      return profile;
    } catch (e) {
      console.warn('Failed to refresh user profile from backend', e);
      return null;
    }
  };

  return (
    <AuthContext.Provider value={{ token, user, isLoading, login, logout, updateUser, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
};
