import type { NotificationInboxItem } from "../types/notification";
import { buildLoanDetailHash } from "./loanDetailRouting";

export const NOTIFICATIONS_CHANGED_EVENT = "panol:notifications-changed";
export const NOTIFICATION_READ_STATE_STAGED_EVENT = "panol:notification-read-state-staged";

export interface NotificationReadStateStagedDetail {
  notificationUuid: string;
  read: boolean;
}

const relativeTimeFormatter = new Intl.RelativeTimeFormat("es-CL", { numeric: "auto" });
const dateTimeFormatter = new Intl.DateTimeFormat("es-CL", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function dispatchNotificationsChanged() {
  if (typeof window === "undefined") {
    return;
  }
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
}

export function dispatchNotificationReadStateStaged(detail: NotificationReadStateStagedDetail) {
  if (typeof window === "undefined") {
    return;
  }
  window.dispatchEvent(new CustomEvent<NotificationReadStateStagedDetail>(
    NOTIFICATION_READ_STATE_STAGED_EVENT,
    { detail },
  ));
}

export function resolveNotificationHref(notification: NotificationInboxItem): string | null {
  if (!notification.referenceType || !notification.referenceId) {
    return null;
  }
  if (notification.referenceType === "loan") {
    return buildLoanDetailHash(notification.referenceId, "list");
  }
  if (notification.referenceType === "implement") {
    return `#/inventory/implementos/${notification.referenceId}`;
  }
  return null;
}

export function formatNotificationTimestamp(rawTimestamp: string): string {
  const parsed = new Date(rawTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  return dateTimeFormatter.format(parsed);
}

export function formatNotificationRelativeTime(rawTimestamp: string): string {
  const parsed = new Date(rawTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }

  const diffMs = parsed.getTime() - Date.now();
  const absoluteDiffMs = Math.abs(diffMs);
  const minuteMs = 60_000;
  const hourMs = 60 * minuteMs;
  const dayMs = 24 * hourMs;
  const weekMs = 7 * dayMs;

  if (absoluteDiffMs < hourMs) {
    return relativeTimeFormatter.format(Math.round(diffMs / minuteMs), "minute");
  }
  if (absoluteDiffMs < dayMs) {
    return relativeTimeFormatter.format(Math.round(diffMs / hourMs), "hour");
  }
  if (absoluteDiffMs < weekMs) {
    return relativeTimeFormatter.format(Math.round(diffMs / dayMs), "day");
  }
  return formatNotificationTimestamp(rawTimestamp);
}
