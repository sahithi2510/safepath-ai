import { api } from './api';

export interface NotificationItem {
  id: string;
  title: string;
  message: string;
  notificationType: string;
  referenceId?: string | null;
  read: boolean;
  createdAt: string;
}

export interface PaginatedNotificationsResponse {
  content: NotificationItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

/**
 * Get current user's paginated notifications.
 */
export const getNotifications = async (
  page: number = 0,
  size: number = 20
): Promise<PaginatedNotificationsResponse> => {
  const response = await api.get<PaginatedNotificationsResponse>('/notifications', {
    params: { page, size },
  });
  return response.data;
};

/**
 * Get only unread notifications.
 */
export const getUnreadNotifications = async (): Promise<NotificationItem[]> => {
  const response = await api.get<NotificationItem[]>('/notifications/unread');
  return response.data;
};

/**
 * Get count of unread notifications.
 */
export const getUnreadNotificationCount = async (): Promise<number> => {
  const response = await api.get<UnreadCountResponse>('/notifications/unread-count');
  return response.data.unreadCount;
};

/**
 * Mark a single notification as read.
 */
export const markNotificationAsRead = async (id: string): Promise<NotificationItem> => {
  const response = await api.patch<NotificationItem>(`/notifications/${id}/read`);
  return response.data;
};

/**
 * Mark all notifications as read.
 */
export const markAllNotificationsAsRead = async (): Promise<void> => {
  await api.post('/notifications/mark-all-read');
};
