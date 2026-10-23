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
    await api.post('/auth/register', { 
      email, 
      password, 
      fullName, 
      phoneNumber 
    });
    // Automatically log in to retrieve the JWT token and user profile
    return await AuthService.login(email, password);
  },
  
  me: async () => {
    const response = await api.get('/users/me');
    return response.data;
  }
};
