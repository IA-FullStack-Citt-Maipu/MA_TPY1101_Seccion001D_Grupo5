import { Bell, Check, CheckCheck, ChevronLeft, ChevronRight, ExternalLink, RotateCcw, Square, Trash2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useInactivityPollingGate } from "../hooks/useInactivityPollingGate";
import {
  deleteNotifications,
  fetchNotificationsPage,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  markNotificationsAsUnread,
  markNotificationsAsRead,
} from "../services/notificationService";
import type { NotificationInboxItem, NotificationInboxPage } from "../types/notification";
import {
  NOTIFICATION_READ_STATE_STAGED_EVENT,
  type NotificationReadStateStagedDetail,
  dispatchNotificationsChanged,
  formatNotificationRelativeTime,
  formatNotificationTimestamp,
  NOTIFICATIONS_CHANGED_EVENT,
  resolveNotificationHref,
} from "../utils/notifications";

const PAGE_SIZE = 20;
const EMPTY_NOTIFICATION_PAGE: NotificationInboxPage = {
  items: [],
  page: 1,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
  unreadCount: 0,
};

function markNotificationPageItemAsRead(
  pageData: NotificationInboxPage,
  notificationUuid: string,
): NotificationInboxPage {
  const targetItem = pageData.items.find((item) => item.uuid === notificationUuid);
  if (!targetItem || targetItem.read) {
    return pageData;
  }

  return {
    ...pageData,
    items: pageData.items.map((item) => (
      item.uuid === notificationUuid ? { ...item, read: true } : item
    )),
    unreadCount: Math.max(0, pageData.unreadCount - 1),
  };
}

function toggleNotificationPageItemReadState(
  pageData: NotificationInboxPage,
  notificationUuid: string,
  read: boolean,
): NotificationInboxPage {
  const targetItem = pageData.items.find((item) => item.uuid === notificationUuid);
  if (!targetItem || targetItem.read === read) {
    return pageData;
  }

  return {
    ...pageData,
    items: pageData.items.map((item) => (
      item.uuid === notificationUuid ? { ...item, read } : item
    )),
    unreadCount: Math.max(0, pageData.unreadCount + (read ? -1 : 1)),
  };
}

function applyPendingNotificationReadStates(
  pageData: NotificationInboxPage,
  pendingReadStates: Map<string, boolean>,
): NotificationInboxPage {
  if (pendingReadStates.size === 0) {
    return pageData;
  }

  let unreadDelta = 0;
  const items = pageData.items.map((item) => {
    const pendingRead = pendingReadStates.get(item.uuid);
    const read = typeof pendingRead === "boolean" ? pendingRead : item.read;
    if (read !== item.read) {
      unreadDelta += read ? -1 : 1;
    }
    return read === item.read ? item : { ...item, read };
  });

  return {
    ...pageData,
    items,
    unreadCount: Math.max(0, pageData.unreadCount + unreadDelta),
  };
}

export function NotificationsPage({ embedded = false }: { embedded?: boolean }) {
  const [page, setPage] = useState(1);
  const [pageData, setPageData] = useState<NotificationInboxPage>(EMPTY_NOTIFICATION_PAGE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [selectedNotificationUuids, setSelectedNotificationUuids] = useState<string[]>([]);
  const { pollingPaused } = useInactivityPollingGate();
  const pendingNotificationReadStatesRef = useRef<Map<string, boolean>>(new Map());

  const loadNotifications = useCallback(async (showLoading: boolean, targetPage: number, resetSelection = false) => {
    if (showLoading) {
      setLoading(true);
    }

    try {
      const nextPage = await fetchNotificationsPage({ page: targetPage, size: PAGE_SIZE });
      setPageData(applyPendingNotificationReadStates(nextPage, pendingNotificationReadStatesRef.current));
      if (resetSelection) {
        setSelectedNotificationUuids([]);
      }
      setError(null);
    } catch {
      setError("No fue posible cargar tus notificaciones.");
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    dispatchNotificationsChanged();
    void loadNotifications(true, page, true);
  }, [loadNotifications, page]);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void loadNotifications(false, page);
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [loadNotifications, page, pollingPaused]);

  useEffect(() => {
    function handleNotificationReadStateStaged(event: Event) {
      const customEvent = event as CustomEvent<NotificationReadStateStagedDetail>;
      const detail = customEvent.detail;
      if (!detail?.notificationUuid) {
        return;
      }

      pendingNotificationReadStatesRef.current.set(detail.notificationUuid, detail.read);
      setPageData((previous) => toggleNotificationPageItemReadState(
        previous,
        detail.notificationUuid,
        detail.read,
      ));
    }

    function handleNotificationsChanged() {
      pendingNotificationReadStatesRef.current.clear();
      void loadNotifications(false, page);
    }

    window.addEventListener(NOTIFICATION_READ_STATE_STAGED_EVENT, handleNotificationReadStateStaged as EventListener);
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);

    return () => {
      window.removeEventListener(NOTIFICATION_READ_STATE_STAGED_EVENT, handleNotificationReadStateStaged as EventListener);
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);
    };
  }, [loadNotifications, page]);

  async function handleNotificationOpen(notification: NotificationInboxItem) {
    const href = resolveNotificationHref(notification);
    if (!href) {
      return;
    }

    setPageData((previous) => markNotificationPageItemAsRead(previous, notification.uuid));

    try {
      await markNotificationAsRead(notification.uuid);
      dispatchNotificationsChanged();
    } catch {
      // El refresh posterior reconciliara el estado si el PATCH falla.
    }

    window.location.hash = href;
  }

  const selectedCount = selectedNotificationUuids.length;
  const allPageItemsSelected = pageData.items.length > 0 && selectedCount === pageData.items.length;
  const totalPages = pageData.totalPages || 1;
  const visiblePageWindow = 5;
  const halfPageWindow = Math.floor(visiblePageWindow / 2);
  const startPage = Math.max(1, Math.min(
    pageData.page - halfPageWindow,
    totalPages - visiblePageWindow + 1,
  ));
  const endPage = Math.min(totalPages, startPage + visiblePageWindow - 1);
  const visiblePageNumbers = Array.from(
    { length: Math.max(0, endPage - startPage + 1) },
    (_, index) => startPage + index,
  );

  function toggleNotificationSelection(notificationUuid: string) {
    setSelectedNotificationUuids((previous) => (
      previous.includes(notificationUuid)
        ? previous.filter((uuid) => uuid !== notificationUuid)
        : [...previous, notificationUuid]
    ));
  }

  function toggleSelectAllCurrentPage() {
    if (allPageItemsSelected) {
      setSelectedNotificationUuids([]);
      return;
    }

    setSelectedNotificationUuids(pageData.items.map((item) => item.uuid));
  }

  async function handleMarkSelectedAsRead() {
    if (selectedNotificationUuids.length === 0) {
      return;
    }

    setActionError(null);

    try {
      await markNotificationsAsRead(selectedNotificationUuids);
      dispatchNotificationsChanged();
      await loadNotifications(false, page, true);
    } catch {
      setActionError("No fue posible marcar como leidas las notificaciones seleccionadas.");
    }
  }

  async function handleMarkAllAsRead() {
    if (pageData.unreadCount === 0) {
      return;
    }

    setActionError(null);

    try {
      await markAllNotificationsAsRead();
      dispatchNotificationsChanged();
      await loadNotifications(false, page, true);
    } catch {
      setActionError("No fue posible marcar todas las notificaciones como leidas.");
    }
  }

  async function handleMarkSelectedAsUnread() {
    if (selectedNotificationUuids.length === 0) {
      return;
    }

    setActionError(null);

    try {
      await markNotificationsAsUnread(selectedNotificationUuids);
      dispatchNotificationsChanged();
      await loadNotifications(false, page, true);
    } catch {
      setActionError("No fue posible marcar como no leidas las notificaciones seleccionadas.");
    }
  }

  async function handleDeleteSelected() {
    if (selectedNotificationUuids.length === 0) {
      return;
    }

    const shouldGoToPreviousPage = selectedNotificationUuids.length === pageData.items.length && page > 1 && !pageData.hasNext;
    setActionError(null);

    try {
      await deleteNotifications(selectedNotificationUuids);
      dispatchNotificationsChanged();

      if (shouldGoToPreviousPage) {
        setPage((current) => Math.max(1, current - 1));
        return;
      }

      await loadNotifications(false, page, true);
    } catch {
      setActionError("No fue posible eliminar las notificaciones seleccionadas.");
    }
  }

  return (
    <section className={embedded ? "notifications-page notifications-page--embedded" : "notifications-page"}>
      <div className="content-header">
        <div>
          <h1>Bandeja de notificaciones</h1>
          <p className="notifications-page__summary">
            {pageData.unreadCount} pendientes de un total de {pageData.totalItems}.
          </p>
        </div>
        {!loading && !error && pageData.items.length > 0 ? (
          <div className="notifications-page__toolbar">
            <span className="notifications-page__selection">
              {selectedCount} seleccionadas
            </span>
            <div className="notifications-page__actions">
              <button type="button" className="button button--table" onClick={toggleSelectAllCurrentPage}>
                {allPageItemsSelected ? <Check size={16} /> : <Square size={16} />}
                {allPageItemsSelected ? "Quitar todo" : "Marcar todo"}
              </button>
              <button
                type="button"
                className="button button--table"
                disabled={selectedCount === 0}
                onClick={() => void handleMarkSelectedAsRead()}
              >
                <Check size={16} />
                Marcar leidas
              </button>
              <button
                type="button"
                className="button button--table"
                disabled={selectedCount === 0}
                onClick={() => void handleMarkSelectedAsUnread()}
              >
                <RotateCcw size={16} />
                Marcar no leidas
              </button>
              <button
                type="button"
                className="button button--table"
                disabled={pageData.unreadCount === 0}
                onClick={() => void handleMarkAllAsRead()}
              >
                <CheckCheck size={16} />
                Marcar todas como leidas
              </button>
              <button
                type="button"
                className="button button--table"
                disabled={selectedCount === 0}
                onClick={() => void handleDeleteSelected()}
              >
                <Trash2 size={16} />
                Eliminar seleccionadas
              </button>
            </div>
          </div>
        ) : null}
      </div>

      <section className="panel notifications-page__panel">
        {loading ? (
          <div className="notifications-page__state empty-state">
            <p>Cargando notificaciones...</p>
          </div>
        ) : error ? (
          <div className="notifications-page__state empty-state">
            <p>{error}</p>
            <button type="button" className="button button--table" onClick={() => void loadNotifications(true, page, false)}>
              Reintentar
            </button>
          </div>
        ) : pageData.items.length === 0 ? (
          <div className="notifications-page__state empty-state">
            <Bell size={20} aria-hidden="true" />
            <p>No tienes notificaciones por ahora.</p>
          </div>
        ) : (
          <>
            {actionError ? (
              <div className="error-banner" role="alert">
                <p>{actionError}</p>
              </div>
            ) : null}
            <div className="notifications-page__list">
              {pageData.items.map((notification) => {
                const href = resolveNotificationHref(notification);
                const itemContent = (
                  <>
                    <div className="notifications-page__item-head">
                      <div className="notifications-page__item-title">
                        <span className={notification.read ? "notifications-page__dot" : "notifications-page__dot is-unread"} aria-hidden="true" />
                        <div>
                          <strong>{notification.title}</strong>
                          <time dateTime={notification.createdAt} title={formatNotificationTimestamp(notification.createdAt)}>
                            {formatNotificationRelativeTime(notification.createdAt)}
                          </time>
                        </div>
                      </div>
                      {!notification.read ? <span className="notifications-page__status">Nueva</span> : null}
                    </div>
                    <p>{notification.message}</p>
                    <div className="notifications-page__item-meta">
                      <span>{formatNotificationTimestamp(notification.createdAt)}</span>
                      {href ? (
                        <span className="notifications-page__cta">
                          Ver detalle
                          <ExternalLink size={14} />
                        </span>
                      ) : (
                        <span className="notifications-page__hint">Notificacion informativa</span>
                      )}
                    </div>
                  </>
                );

                if (!href) {
                  return (
                    <article
                      key={notification.uuid}
                      className={notification.read ? "notifications-page__item" : "notifications-page__item is-unread"}
                    >
                      <div className="notifications-page__item-shell">
                        <label className="notifications-page__check">
                          <input
                            type="checkbox"
                            checked={selectedNotificationUuids.includes(notification.uuid)}
                            onChange={() => toggleNotificationSelection(notification.uuid)}
                            aria-label={`Seleccionar notificacion ${notification.title}`}
                          />
                        </label>
                        <div className="notifications-page__item-content">
                          {itemContent}
                        </div>
                      </div>
                    </article>
                  );
                }

                return (
                  <article
                    key={notification.uuid}
                    className={notification.read ? "notifications-page__item" : "notifications-page__item is-unread"}
                  >
                    <div className="notifications-page__item-shell">
                      <label className="notifications-page__check">
                        <input
                          type="checkbox"
                          checked={selectedNotificationUuids.includes(notification.uuid)}
                          onChange={() => toggleNotificationSelection(notification.uuid)}
                          aria-label={`Seleccionar notificacion ${notification.title}`}
                        />
                      </label>
                      <button
                        type="button"
                        className={notification.read ? "notifications-page__item-content notifications-page__item--button" : "notifications-page__item-content notifications-page__item--button is-unread"}
                        onClick={() => void handleNotificationOpen(notification)}
                      >
                        {itemContent}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>

            <footer className="notifications-page__footer">
              <p>
                Pagina {pageData.page} de {totalPages}
              </p>
              <div className="notifications-page__pager">
                <button
                  type="button"
                  className="button button--table"
                  disabled={!pageData.hasPrevious}
                  onClick={() => setPage((current) => Math.max(1, current - 1))}
                >
                  <ChevronLeft size={16} />
                  Anterior
                </button>
                {visiblePageNumbers.map((pageNumber) => (
                  <button
                    key={pageNumber}
                    type="button"
                    className={pageNumber === pageData.page
                      ? "button button--table notifications-page__pager-number is-active"
                      : "button button--table notifications-page__pager-number"}
                    aria-current={pageNumber === pageData.page ? "page" : undefined}
                    onClick={() => setPage(pageNumber)}
                  >
                    {pageNumber}
                  </button>
                ))}
                <button
                  type="button"
                  className="button button--table"
                  disabled={!pageData.hasNext}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Siguiente
                  <ChevronRight size={16} />
                </button>
              </div>
            </footer>
          </>
        )}
      </section>
    </section>
  );
}
