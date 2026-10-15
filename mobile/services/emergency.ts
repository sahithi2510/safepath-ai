import { api } from './api';

export interface EmergencyContact {
  id: string;
  name: string;
  phoneNumber: string;
  relationship?: string;
  primary: boolean;
  createdAt: string;
}

export interface CreateContactRequest {
  name: string;
  phoneNumber: string;
  relationship?: string;
  primary?: boolean;
}

export interface SosRequest {
  latitude?: number;
  longitude?: number;
  note?: string;
}

export interface SosResponse {
  logged: boolean;
  eventId?: string;
  message: string;
  loggedAt: string;
  emergencyContactsCount?: number;
}

export interface HelplineInfo {
  number: string;
  title: string;
  category: string;
  description: string;
  badgeColor: string;
}

export const OFFICIAL_HELPLINES: HelplineInfo[] = [
  {
    number: '112',
    title: 'National Emergency',
    category: 'Police / Fire / Ambulance',
    description: 'Unified national emergency helpline across India.',
    badgeColor: '#FF3B30',
  },
  {
    number: '100',
    title: 'Police Control Room',
    category: 'Law Enforcement',
    description: 'Direct police emergency response and patrol assistance.',
    badgeColor: '#007AFF',
  },
  {
    number: '108',
    title: 'Emergency Medical Service',
    category: 'Ambulance & Healthcare',
    description: 'Toll-free emergency ambulance and trauma response.',
    badgeColor: '#34C759',
  },
  {
    number: '1091',
    title: 'Women Safety Helpline',
    category: 'Women Safety & Support',
    description: '24/7 dedicated support for women distress and protection.',
    badgeColor: '#AF52DE',
  },
];

export const EmergencyService = {
  getContacts: async (): Promise<EmergencyContact[]> => {
    const response = await api.get<EmergencyContact[]>('/emergency/contacts');
    return response.data;
  },

  addContact: async (payload: CreateContactRequest): Promise<EmergencyContact> => {
    const response = await api.post<EmergencyContact>('/emergency/contacts', payload);
    return response.data;
  },

  deleteContact: async (contactId: string): Promise<void> => {
    await api.delete(`/emergency/contacts/${contactId}`);
  },

  triggerSos: async (payload: SosRequest): Promise<SosResponse> => {
    const response = await api.post<SosResponse>('/emergency/sos', payload);
    return response.data;
  },
};
