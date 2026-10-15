import { api } from './api';

import { RoutePreference } from './user';

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  routePreference?: RoutePreference;
}

export interface AuthResponse {
  token: string;
  user: AuthUser;
}

export const AuthService = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/login', { email, password });
    return response.data;
  },
  
  register: async (email: string, password: string, fullName: string, phoneNumber?: string): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/register', { 
      email, 
      password, 
      fullName,
      phoneNumber 
    });
    return response.data;
  },
  
  me: async () => {
    const response = await api.get('/users/me');
    return response.data;
  }
};
