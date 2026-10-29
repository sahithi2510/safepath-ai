import { api } from './api';

export type ReportType =
  | 'HARASSMENT'
  | 'SUSPICIOUS_ACTIVITY'
  | 'POOR_LIGHTING'
  | 'UNSAFE_ROAD'
  | 'BROKEN_STREETLIGHT'
  | 'CROWD_ISSUE'
  | 'ROAD_BLOCKAGE'
  | 'OTHER';

export type ReportStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export type LocationPrecision = 'EXACT_COORDINATE' | 'LOCALITY' | 'UNKNOWN';

export interface CreateReportRequest {
  reportType: ReportType;
  description?: string;
  latitude?: number | null;
  longitude?: number | null;
  locationConfirmed?: boolean;
  incidentDatetime?: string | null;
}

export interface SubmitReportResponse {
  id: string;
  reportType: ReportType;
  status: ReportStatus;
  locationPrecision: LocationPrecision;
  createdAt: string;
  message: string;
}

export interface CommunityReportResponse {
  id: string;
  reportType: ReportType;
  description?: string;
  latitude?: number | null;
  longitude?: number | null;
  locationPrecision?: LocationPrecision;
  status: ReportStatus;
  incidentDatetime?: string | null;
  createdAt: string;
  distanceMeters?: number | null;
}

export interface PaginatedReportsResponse {
  content: CommunityReportResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/**
 * Submit a new community safety report.
 */
export const submitReport = async (
  request: CreateReportRequest
): Promise<SubmitReportResponse> => {
  const response = await api.post<SubmitReportResponse>('/reports', request);
  return response.data;
};

/**
 * Fetch the authenticated user's submitted reports and lifecycle status.
 */
export const getMyReports = async (
  page: number = 0,
  size: number = 20
): Promise<PaginatedReportsResponse> => {
  const response = await api.get<PaginatedReportsResponse>('/reports/my', {
    params: { page, size },
  });
  return response.data;
};

/**
 * Fetch nearby verified reports (publicly queryable).
 */
export const getNearbyReports = async (
  lat: number,
  lon: number,
  radiusMeters: number = 500,
  limit: number = 20
): Promise<CommunityReportResponse[]> => {
  const response = await api.get<CommunityReportResponse[]>('/reports/nearby', {
    params: { lat, lon, radiusMeters, limit },
  });
  return response.data;
};

export interface VerifyReportRequest {
  status: 'VERIFIED' | 'REJECTED';
  verificationNote?: string;
}

export const verifyReport = async (
  id: string,
  request: VerifyReportRequest
): Promise<CommunityReportResponse> => {
  const response = await api.put<CommunityReportResponse>(`/reports/${id}/verify`, request);
  return response.data;
};
