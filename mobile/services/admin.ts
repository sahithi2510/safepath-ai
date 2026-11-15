import { api } from './api';

export interface AdminUserSummary {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  enabled: boolean;
  roles: string[];
  createdAt: string;
}

export interface PaginatedAdminUsersResponse {
  content: AdminUserSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface UpdateRoleRequest {
  role: 'ROLE_USER' | 'ROLE_MODERATOR' | 'ROLE_ADMIN';
  action: 'ADD' | 'REMOVE';
}

/**
 * List / search users (ADMIN only).
 */
export const listAdminUsers = async (
  search?: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedAdminUsersResponse> => {
  const params: Record<string, any> = { page, size };
  if (search && search.trim()) {
    params.search = search.trim();
  }
  const response = await api.get<PaginatedAdminUsersResponse>('/admin/users', { params });
  return response.data;
};

/**
 * Update user role: promote to moderator or revoke permissions (ADMIN only).
 */
export const updateUserRole = async (
  userId: string,
  request: UpdateRoleRequest
): Promise<AdminUserSummary> => {
  const response = await api.put<AdminUserSummary>(`/admin/users/${userId}/roles`, request);
  return response.data;
};

