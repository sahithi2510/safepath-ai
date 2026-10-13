import { api } from './api';

export interface AuthResponse {
  token: string;
  user: {
    id: string;
    email: string;
    fullName: string;
  };
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
