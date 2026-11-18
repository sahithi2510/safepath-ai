import { api } from './api';

export interface CrimeSpot {
  id: string;
  crimeType: string;
  crimeSubtype?: string;
  severity: number;
  latitude: number;
  longitude: number;
  locationPrecision?: string;
  address?: string;
  locality?: string;
  city?: string;
  source?: string;
  incidentDatetime?: string;
  createdAt?: string;
}

export const CrimeService = {
  getCorridorCrimeSpots: async (
    minLat: number,
    maxLat: number,
    minLon: number,
    maxLon: number,
    limit: number = 50
  ): Promise<CrimeSpot[]> => {
    try {
      const response = await api.get<CrimeSpot[]>('/crime-incidents/corridor', {
        params: { minLat, maxLat, minLon, maxLon, limit },
        timeout: 8000,
      });
      return response.data || [];
    } catch (error) {
      console.warn('Could not fetch corridor crime spots:', error);
      return [];
    }
  },

  getNearbyCrimeSpots: async (
    lat: number,
    lon: number,
    radiusMeters: number = 2000,
    limit: number = 25
  ): Promise<CrimeSpot[]> => {
    try {
      const response = await api.get<CrimeSpot[]>('/crime-incidents/nearby', {
        params: { lat, lon, radiusMeters, limit },
        timeout: 8000,
      });
      return response.data || [];
    } catch (error) {
      console.warn('Could not fetch nearby crime spots:', error);
      return [];
    }
  },
};

export const getCorridorCrimeSpots = async (
  startLat: number,
  startLon: number,
  endLat: number,
  endLon: number,
  bufferDegrees: number = 0.015,
  limit: number = 50
): Promise<CrimeSpot[]> => {
  const minLat = Math.min(startLat, endLat) - bufferDegrees;
  const maxLat = Math.max(startLat, endLat) + bufferDegrees;
  const minLon = Math.min(startLon, endLon) - bufferDegrees;
  const maxLon = Math.max(startLon, endLon) + bufferDegrees;
  return CrimeService.getCorridorCrimeSpots(minLat, maxLat, minLon, maxLon, limit);
};

export const getNearbyCrimeSpots = CrimeService.getNearbyCrimeSpots;
