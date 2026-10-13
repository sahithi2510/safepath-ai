import { api } from './api';

export interface Coordinate {
  latitude: number;
  longitude: number;
}

export interface HeatmapSegment {
  segmentId: number;
  osmId: number | null;
  name: string;
  highwayType: string;
  coordinates: Coordinate[];
  relativeRiskScore: number | null;
  safetyScore: number | null;
  riskCategory: 'VERY_LOW' | 'LOW' | 'MODERATE' | 'HIGH' | 'VERY_HIGH' | 'UNSCORED';
  scoringStatus: 'SCORED' | 'UNSCORED' | 'UNAVAILABLE';
  colorHex: string;
  isLit: boolean | null;
  surface: string | null;
  lengthMeters: number | null;
  isSyntheticDemonstration: boolean;
  modelVersion: string | null;
  scoredAt: string | null;
}

export interface HeatmapResponse {
  boundingBox: {
    minLat: number;
    minLon: number;
    maxLat: number;
    maxLon: number;
  };
  totalSegmentsReturned: number;
  scoredSegmentsCount: number;
  unscoredSegmentsCount: number;
  mlServiceAvailable: boolean;
  isSyntheticDemonstration: boolean;
  modelVersion: string | null;
  generatedAt: string;
  disclaimer: string;
  dataProvenanceNote: string;
  segments: HeatmapSegment[];
}

export interface HeatmapQueryParams {
  minLat: number;
  minLon: number;
  maxLat: number;
  maxLon: number;
  limit?: number;
}

export const fetchHeatmap = async (params: HeatmapQueryParams): Promise<HeatmapResponse> => {
  const response = await api.get<HeatmapResponse>('/heatmap', { params });
  return response.data;
};
