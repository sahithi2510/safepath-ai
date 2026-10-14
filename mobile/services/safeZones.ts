import { api } from './api';

export type SafeZoneCategory =
  | 'POLICE_STATION'
  | 'HOSPITAL'
  | 'PHARMACY'
  | 'PUBLIC_TRANSPORT'
  | 'OTHER';

export type VerificationStatus = 'VERIFIED' | 'UNVERIFIED' | 'UNAVAILABLE';

export interface SafeZone {
  id: string;
  name: string;
  zoneType: SafeZoneCategory;
  verificationStatus: VerificationStatus;
  latitude: number;
  longitude: number;
  address: string | null;
  phoneNumber: string | null;
  operatingHours: string | null;
  is24h: boolean | null;
  source: string | null;
  osmNodeId: number | null;
  distanceMeters?: number | null;
  createdAt?: string;
  updatedAt?: string;
  disclaimer?: string;
}

export interface ViewportSafeZoneParams {
  minLat: number;
  minLon: number;
  maxLat: number;
  maxLon: number;
  types?: string[];
  limit?: number;
}

export interface NearbySafeZoneParams {
  lat: number;
  lon: number;
  radiusMeters?: number;
  types?: string[];
  limit?: number;
}

export const CATEGORY_CONFIG: Record<
  SafeZoneCategory,
  { label: string; icon: string; pinColor: string; bgHex: string }
> = {
  POLICE_STATION: {
    label: 'Police Station',
    icon: '👮',
    pinColor: '#1D68BD',
    bgHex: '#EBF5FF',
  },
  HOSPITAL: {
    label: 'Hospital / Clinic',
    icon: '🏥',
    pinColor: '#E02424',
    bgHex: '#FDE8E8',
  },
  PHARMACY: {
    label: 'Pharmacy',
    icon: '💊',
    pinColor: '#057A55',
    bgHex: '#DEF7EC',
  },
  PUBLIC_TRANSPORT: {
    label: 'Transit Hub',
    icon: '🚏',
    pinColor: '#7E3AF2',
    bgHex: '#EDEBFE',
  },
  OTHER: {
    label: 'Public Assistance',
    icon: '🏢',
    pinColor: '#D97706',
    bgHex: '#FEF3C7',
  },
};

export const VERIFICATION_CONFIG: Record<
  VerificationStatus,
  { label: string; badgeColor: string; bgHex: string; description: string }
> = {
  VERIFIED: {
    label: 'Verified Facility',
    badgeColor: '#057A55',
    bgHex: '#DEF7EC',
    description: 'Confirmed via official registry or administrative audit.',
  },
  UNVERIFIED: {
    label: 'Unverified (OSM)',
    badgeColor: '#D97706',
    bgHex: '#FEF3C7',
    description: 'Imported from open spatial data; operational status not individually audited.',
  },
  UNAVAILABLE: {
    label: 'Status Unknown / Inactive',
    badgeColor: '#6B7280',
    bgHex: '#F3F4F6',
    description: 'Facility reported closed or current operational status unavailable.',
  },
};

export const fetchSafeZonesInViewport = async (
  params: ViewportSafeZoneParams
): Promise<SafeZone[]> => {
  const response = await api.get<SafeZone[]>('/safe-zones', {
    params: {
      minLat: params.minLat,
      minLon: params.minLon,
      maxLat: params.maxLat,
      maxLon: params.maxLon,
      types: params.types?.join(','),
      limit: params.limit,
    },
  });
  return response.data || [];
};

export const fetchNearbySafeZones = async (
  params: NearbySafeZoneParams
): Promise<SafeZone[]> => {
  const response = await api.get<SafeZone[]>('/safe-zones/nearby', {
    params: {
      lat: params.lat,
      lon: params.lon,
      radiusMeters: params.radiusMeters,
      types: params.types?.join(','),
      limit: params.limit,
    },
  });
  return response.data || [];
};

export const fetchSafeZoneById = async (id: string): Promise<SafeZone> => {
  const response = await api.get<SafeZone>(`/safe-zones/${id}`);
  return response.data;
};
