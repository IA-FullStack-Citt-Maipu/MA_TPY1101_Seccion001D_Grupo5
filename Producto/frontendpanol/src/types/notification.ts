export type NotificationReferenceType = "loan" | "implement" | null;

export interface NotificationInboxItem {
  uuid: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  referenceType: NotificationReferenceType;
  referenceId: string | null;
  metadata: Record<string, unknown>;
}

export interface NotificationInboxPage {
  items: NotificationInboxItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  unreadCount: number;
}
