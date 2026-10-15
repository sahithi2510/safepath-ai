import { api } from './api';

export type RoutePreference =
  | 'STANDARD'
  | 'FASTEST'
  | 'BALANCED'
  | 'SAFEST'
  | 'SOLO_TRAVELLER'
  | 'TOURIST'
  | 'ELDERLY';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  routePreference: RoutePreference;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  phoneNumber?: string;
  routePreference?: RoutePreference;
}

export const ROUTE_PREFERENCE_OPTIONS: {
  key: RoutePreference;
  label: string;
  badge: string;
  description: string;
  weights: { safety: string; time: string; distance: string };
}[] = [
  {
    key: 'STANDARD',
    label: 'Standard',
    badge: 'Default',
    description: 'Balanced default weights taking into account safety, travel time, and distance.',
    weights: { safety: '33%', time: '34%', distance: '33%' },
  },
  {
    key: 'FASTEST',
    label: 'Fastest Route',
    badge: 'Speed',
    description: 'Prioritizes travel speed and minimal distance over safety ratings.',
    weights: { safety: '10%', time: '70%', distance: '20%' },
  },
  {
    key: 'BALANCED',
    label: 'Balanced Compromise',
    badge: 'Recommended',
    description: 'Moderate compromise balancing journey duration with route safety.',
    weights: { safety: '40%', time: '40%', distance: '20%' },
  },
  {
    key: 'SAFEST',
    label: 'Safest Route',
    badge: 'Safety',
    description: 'Minimizes risk, strongly prioritizing verified well-lit segments and safe zones.',
    weights: { safety: '70%', time: '20%', distance: '10%' },
  },
  {
    key: 'SOLO_TRAVELLER',
    label: 'Solo Traveller',
    badge: 'Personalized',
    description: 'Elevated safety priority tailored for individuals travelling independently.',
    weights: { safety: '70%', time: '20%', distance: '10%' },
  },
  {
    key: 'TOURIST',
    label: 'Tourist Explorer',
    badge: 'Personalized',
    description: 'Maximum safety priority favoring familiar, well-patrolled, and well-lit thoroughfares.',
    weights: { safety: '80%', time: '10%', distance: '10%' },
  },
  {
    key: 'ELDERLY',
    label: 'Elderly & Accessible',
    badge: 'Personalized',
    description: 'High safety focus combined with balanced travel time for predictable, secure paths.',
    weights: { safety: '60%', time: '30%', distance: '10%' },
  },
];

export const UserService = {
  getProfile: async (): Promise<UserProfile> => {
    const response = await api.get<UserProfile>('/users/me');
    return response.data;
  },

  updateProfile: async (payload: UpdateProfileRequest): Promise<UserProfile> => {
    const response = await api.put<UserProfile>('/users/me', payload);
    return response.data;
  },
};
