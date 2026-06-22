import { apiClient } from "./apiClient";
import type { NotificationInboxItem, NotificationInboxPage, NotificationReferenceType } from "../types/notification";

export interface FetchNotificationsQuery {
  page?: number;
  size?: number;
  unreadOnly?: boolean;
}

interface BackendNotificationInboxItem {
  uuid: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  referenceType?: string | null;
  referenceId?: string | null;
  metadata?: Record<string, unknown> | null;
}

interface BackendNotificationInboxPage {
  items?: BackendNotificationInboxItem[];
  page?: number;
  size?: number;
  totalItems?: number;
  totalPages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
  unreadCount?: number;
}

interface NotificationSelectionRequest {
  notificationUuids: string[];
}

function normalizeReferenceType(referenceType: string | null | undefined): NotificationReferenceType {
  if (referenceType === "loan") return "loan";
  if (referenceType === "implement") return "implement";
  return null;
}

function toSelectionRequest(notificationUuids: string[]): NotificationSelectionRequest {
  return {
    notificationUuids: Array.from(
      new Set(
        notificationUuids.filter((uuid): uuid is string => typeof uuid === "string" && uuid.trim().length > 0),
      ),
    ),
  };
}

function toNotificationItem(data: BackendNotificationInboxItem): NotificationInboxItem {
  return {
    uuid: data.uuid,
    title: data.title,
    message: data.message,
    read: data.read === true,
    createdAt: data.createdAt,
    referenceType: normalizeReferenceType(data.referenceType),
    referenceId: typeof data.referenceId === "string" ? data.referenceId : null,
    metadata: data.metadata ?? {},
  };
}

export async function fetchNotificationsPage(query: FetchNotificationsQuery = {}): Promise<NotificationInboxPage> {
  const response = await apiClient.get<BackendNotificationInboxPage>("/api/v2/notifications", {
    params: {
      page: query.page ?? 1,
      size: query.size ?? 20,
      unreadOnly: query.unreadOnly ?? false,
    },
  });

  const data = response.data ?? {};
  return {
    items: Array.isArray(data.items) ? data.items.map(toNotificationItem) : [],
    page: typeof data.page === "number" ? data.page : 1,
    size: typeof data.size === "number" ? data.size : 20,
    totalItems: typeof data.totalItems === "number" ? data.totalItems : 0,
    totalPages: typeof data.totalPages === "number" ? data.totalPages : 0,
    hasNext: data.hasNext === true,
    hasPrevious: data.hasPrevious === true,
    unreadCount: typeof data.unreadCount === "number" ? data.unreadCount : 0,
  };
}

export async function markNotificationAsRead(notificationUuid: string): Promise<void> {
  await apiClient.patch(`/api/v2/notifications/${notificationUuid}/read`);
}

export async function markNotificationAsUnread(notificationUuid: string): Promise<void> {
  await apiClient.patch(`/api/v2/notifications/${notificationUuid}/unread`);
}

export async function markNotificationsAsRead(notificationUuids: string[]): Promise<void> {
  const payload = toSelectionRequest(notificationUuids);
  if (payload.notificationUuids.length === 0) {
    return;
  }

  await apiClient.patch("/api/v2/notifications/read", payload);
}

export async function markNotificationsAsUnread(notificationUuids: string[]): Promise<void> {
  const payload = toSelectionRequest(notificationUuids);
  if (payload.notificationUuids.length === 0) {
    return;
  }

  await apiClient.patch("/api/v2/notifications/unread", payload);
}

export async function markAllNotificationsAsRead(): Promise<void> {
  await apiClient.patch("/api/v2/notifications/read-all", {});
}

export async function deleteNotifications(notificationUuids: string[]): Promise<void> {
  const payload = toSelectionRequest(notificationUuids);
  if (payload.notificationUuids.length === 0) {
    return;
  }

  await apiClient.delete("/api/v2/notifications", {
    data: payload,
  });
}
