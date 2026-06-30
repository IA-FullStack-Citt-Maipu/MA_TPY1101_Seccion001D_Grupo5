import {
  Bell,
  Boxes,
  Check,
  CircleHelp,
  ClipboardList,
  Headset,
  Handshake,
  History,
  LayoutDashboard,
  LogOut,
  MapPin,
  Menu,
  Search,
  Settings,
  RotateCcw,
  Users,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import type { ReactNode } from "react";
import { useInactivityPollingGate } from "../../hooks/useInactivityPollingGate";
import { ChatWidget } from "../chat/ChatWidget";
import { fetchImplements } from "../../services/implementService";
import {
  fetchNotificationsPage,
  markNotificationAsRead,
  markNotificationsAsRead,
  markNotificationsAsUnread,
} from "../../services/notificationService";
import type { ImplementSummary } from "../../types/implement";
import type { NotificationInboxItem, NotificationInboxPage } from "../../types/notification";
import { normalizeUserRole, type UserRole } from "../../utils/auth";
import {
  dispatchNotificationReadStateStaged,
  dispatchNotificationsChanged,
  formatNotificationRelativeTime,
  formatNotificationTimestamp,
  NOTIFICATIONS_CHANGED_EVENT,
  resolveNotificationHref,
} from "../../utils/notifications";

interface MenuItem {
  label: string;
  icon: typeof LayoutDashboard;
  href: string;
  activeSections: InventorySection[];
}

const coordinatorMenu: MenuItem[] = [
  { label: "Panel", icon: LayoutDashboard, href: "#/inventory/dashboard", activeSections: ["dashboard"] },
  { label: "Implementos", icon: Boxes, href: "#/inventory/implementos", activeSections: ["items"] },
  { label: "Categorias", icon: ClipboardList, href: "#/inventory/categories", activeSections: ["categories"] },
  { label: "Ubicaciones", icon: MapPin, href: "#/inventory/locations", activeSections: ["locations"] },
  { label: "Movimientos", icon: ClipboardList, href: "#/inventory/moves", activeSections: ["moves"] },
  { label: "Prestamos", icon: Handshake, href: "#/inventory/prestamos", activeSections: ["coordinator-loans"] },
  { label: "Docentes", icon: Users, href: "#/inventory/prestamos/docentes", activeSections: ["coordinator-requesters"] },
  { label: "Nueva solicitud", icon: ClipboardList, href: "#/inventory/prestamos/nuevo", activeSections: ["loan-create"] },
  { label: "Agenda", icon: History, href: "#/inventory/prestamos/calendario", activeSections: ["agenda"] },
];

const teacherMenu: MenuItem[] = [
  { label: "Mis prestamos", icon: Handshake, href: "#/inventory/prestamos", activeSections: ["teacher-loans"] },
  { label: "Nueva solicitud", icon: ClipboardList, href: "#/inventory/prestamos/nuevo", activeSections: ["loan-create"] },
  { label: "Agenda", icon: History, href: "#/inventory/prestamos/calendario", activeSections: ["agenda"] },
];

const directorMenu: MenuItem[] = [
  { label: "Panel", icon: LayoutDashboard, href: "#/director/dashboard", activeSections: ["director-dashboard"] },
  { label: "Movimientos", icon: ClipboardList, href: "#/director/movimientos", activeSections: ["director-moves"] },
  { label: "Docentes", icon: Users, href: "#/director/docentes", activeSections: ["director-requesters"] },
  { label: "Usuarios", icon: Users, href: "#/director/users/create", activeSections: ["director-users"] },
];

function getTopbarSuggestionMeta(row: ImplementSummary, query: string): string | null {
  const normalizedQuery = query.trim().toLowerCase();
  const barcode = row.barcode?.trim() ?? "";
  const individualAssetCodes = (row.individualAssetCodes ?? row.individual_asset_codes ?? [])
    .map((value) => value?.trim() ?? "")
    .filter((value) => value.length > 0);

  if (barcode && (normalizedQuery.length === 0 || barcode.toLowerCase().includes(normalizedQuery))) {
    return `Codigo: ${barcode}`;
  }

  const matchingAssetCode = individualAssetCodes.find((value) => normalizedQuery.length === 0 || value.toLowerCase().includes(normalizedQuery));
  if (matchingAssetCode) {
    return `Unidad: ${matchingAssetCode}`;
  }

  if (barcode) {
    return `Codigo: ${barcode}`;
  }

  if (individualAssetCodes.length > 0) {
    return `Unidad: ${individualAssetCodes[0]}`;
  }

  return null;
}

export type InventorySection =
  | "dashboard"
  | "items"
  | "categories"
  | "locations"
  | "moves"
  | "teacher-loans"
  | "coordinator-loans"
  | "coordinator-requesters"
  | "loan-create"
  | "agenda"
  | "reports"
  | "notifications"
  | "support"
  | "settings"
  | "director-dashboard"
  | "director-moves"
  | "director-requesters"
  | "director-users";

export type NavigationMode = "inventory" | "director";

export interface BreadcrumbPart {
  label: string;
  href?: string;
}

const NOTIFICATION_PREVIEW_SIZE = 5;
const NOTIFICATION_TOGGLE_SYNC_DELAY_MS = 10000;
const EMPTY_NOTIFICATION_PAGE: NotificationInboxPage = {
  items: [],
  page: 1,
  size: NOTIFICATION_PREVIEW_SIZE,
  totalItems: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
  unreadCount: 0,
};

function markNotificationPageItemAsRead(pageData: NotificationInboxPage, notificationUuid: string): NotificationInboxPage {
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

function toggleNotificationPageItemReadState(pageData: NotificationInboxPage, notificationUuid: string): NotificationInboxPage {
  const targetItem = pageData.items.find((item) => item.uuid === notificationUuid);
  if (!targetItem) {
    return pageData;
  }

  const nextRead = !targetItem.read;
  const unreadCount = nextRead
    ? Math.max(0, pageData.unreadCount - 1)
    : pageData.unreadCount + 1;

  return {
    ...pageData,
    items: pageData.items.map((item) => (
      item.uuid === notificationUuid ? { ...item, read: nextRead } : item
    )),
    unreadCount,
  };
}

function applyPendingNotificationReadStates(
  pageData: NotificationInboxPage,
  pendingStates: Map<string, boolean>,
): NotificationInboxPage {
  if (pendingStates.size === 0) {
    return pageData;
  }

  let unreadDelta = 0;
  const items = pageData.items.map((item) => {
    const pendingRead = pendingStates.get(item.uuid);
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


function resolveMenu(navigationMode: NavigationMode, role: UserRole): MenuItem[] {
  if (navigationMode === "director") {
    return directorMenu;
  }
  return role === "DOCENTE" ? teacherMenu : coordinatorMenu;
}

function resolveSidebarTitle(navigationMode: NavigationMode, role: UserRole): string {
  if (navigationMode === "director") {
    return "Director de carrera";
  }
  return role === "DOCENTE" ? "Prestamos" : "Inventario";
}

function resolveModePill(role: UserRole): string | null {
  if (role === "COORDINADOR") return "Modo coordinador";
  if (role === "DOCENTE") return "Modo docente";
  return null;
}

export function Sidebar({
  activeSection,
  navigationMode,
  role = "COORDINADOR",
  onNavigate,
  onLogout = () => {},
}: {
  activeSection: InventorySection;
  navigationMode: NavigationMode;
  role?: string;
  onNavigate?: () => void;
  onLogout?: () => void;
}) {
  const normalizedRole = normalizeUserRole(role);
  const menu = resolveMenu(navigationMode, normalizedRole);
  const modePill = resolveModePill(normalizedRole);
  const sidebarTitle = resolveSidebarTitle(navigationMode, normalizedRole);

  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <strong>Panol Salud</strong>
        <p>Inventario clinico</p>
        {navigationMode === "inventory" && modePill ? (
          <div className="sidebar__mode-pill" aria-label={modePill}>
            <span />
            <small>{modePill}</small>
          </div>
        ) : null}
      </div>

      <section className="sidebar__menu">
        <h3 className="sidebar__title">{sidebarTitle}</h3>
        <ul className="sidebar__list">
          {menu.map((item) => {
            const Icon = item.icon;
            const isActive = item.activeSections.includes(activeSection);
            return (
              <li key={item.href}>
                <a
                  href={item.href}
                  onClick={onNavigate}
                  className={isActive ? "sidebar__item sidebar__item--active" : "sidebar__item"}
                >
                  <Icon size={18} />
                  <span>{item.label}</span>
                </a>
              </li>
            );
          })}
        </ul>
      </section>

      {navigationMode === "director" ? (
        <section className="sidebar__help">
          <h4>Institucion de Salud</h4>
          <p>Ciencias de la Salud</p>
        </section>
      ) : null}

      <div className="sidebar__footer">
        <a
          href="#/support"
          onClick={onNavigate}
          className={activeSection === "support" ? "sidebar__item sidebar__item--support sidebar__item--active" : "sidebar__item sidebar__item--support"}
        >
          <Headset size={18} />
          <span>Soporte</span>
        </a>
        <a
          href="#/configuracion"
          onClick={onNavigate}
          className={activeSection === "settings" ? "sidebar__item sidebar__item--support sidebar__item--active" : "sidebar__item sidebar__item--support"}
        >
          <Settings size={18} />
          <span>Configuracion</span>
        </a>
        <button
          type="button"
          onClick={() => {
            onNavigate?.();
            onLogout();
          }}
          className="sidebar__item sidebar__item--logout"
        >
          <LogOut size={18} />
          <span>Cerrar sesion</span>
        </button>
      </div>
    </aside>
  );
}

export function TopBar({
  sidebarOpen,
  onToggleSidebar,
  breadcrumbs,
  onOpenSupport = () => {},
  searchPlaceholder = "Buscar implementos...",
  showSearch = true,
  notificationCount = 0,
  notificationItems = [],
  notificationsLoading = false,
  notificationsError = null,
  onNotificationsOpen = () => {},
  onNotificationMarkAsRead,
  onNotificationSelect,
  onNotificationsViewAll = () => {},
  userName = "Usuario",
  userRole = "COORDINADOR",
}: {
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  breadcrumbs: BreadcrumbPart[];
  onOpenSupport?: () => void;
  searchPlaceholder?: string;
  showSearch?: boolean;
  notificationCount?: number;
  notificationItems?: NotificationInboxItem[];
  notificationsLoading?: boolean;
  notificationsError?: string | null;
  onNotificationsOpen?: () => void;
  onNotificationMarkAsRead?: (notification: NotificationInboxItem) => void | Promise<void>;
  onNotificationSelect?: (notification: NotificationInboxItem) => void | Promise<void>;
  onNotificationsViewAll?: () => void;
  userName?: string;
  userRole?: string;
}) {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [suggestions, setSuggestions] = useState<ImplementSummary[]>([]);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [hoverIndex, setHoverIndex] = useState<number>(-1);
  const searchRef = useRef<HTMLDivElement | null>(null);
  const notificationsRef = useRef<HTMLDivElement | null>(null);
  const safeUserName = typeof userName === "string" && userName.trim().length > 0 ? userName : "Usuario";
  const userInitials = safeUserName
    .split(" ")
    .map((part) => part?.[0] ?? "")
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 250);
    return () => window.clearTimeout(timeout);
  }, [search]);

  useEffect(() => {
    if (!showSearch || debouncedSearch.length < 2) {
      setSuggestions([]);
      setLoadingSuggestions(false);
      setHoverIndex(-1);
      return;
    }

    let cancelled = false;
    setLoadingSuggestions(true);
    fetchImplements({ name: debouncedSearch })
      .then((rows) => {
        if (cancelled) return;
        setSuggestions(rows.slice(0, 8));
      })
      .catch(() => {
        if (cancelled) return;
        setSuggestions([]);
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingSuggestions(false);
      });

    return () => {
      cancelled = true;
    };
  }, [debouncedSearch, showSearch]);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (!searchRef.current) return;
      if (!searchRef.current.contains(event.target as Node)) {
        setSuggestionsOpen(false);
      }
    }

    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (!notificationsRef.current) return;
      if (!notificationsRef.current.contains(event.target as Node)) {
        setNotificationsOpen(false);
      }
    }

    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  useEffect(() => {
    function handleHashChange() {
      setNotificationsOpen(false);
    }

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  const shouldShowSuggestions = useMemo(
    () => showSearch && suggestionsOpen && search.trim().length >= 2,
    [search, showSearch, suggestionsOpen],
  );

  function goToImplement(row: ImplementSummary) {
    window.location.hash = `#/inventory/implementos/${row.uuid}`;
    setSuggestionsOpen(false);
    setSearch("");
    setHoverIndex(-1);
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!shouldShowSuggestions) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setHoverIndex((prev) => Math.min(prev + 1, suggestions.length - 1));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setHoverIndex((prev) => Math.max(prev - 1, 0));
      return;
    }
    if (event.key === "Enter" && suggestions.length > 0) {
      event.preventDefault();
      const target = hoverIndex >= 0 ? suggestions[hoverIndex] : suggestions[0];
      goToImplement(target);
      return;
    }
    if (event.key === "Escape") {
      setSuggestionsOpen(false);
      setHoverIndex(-1);
    }
  }

  return (
    <header className="topbar">
      <button type="button" className="topbar__burger topbar__burger--inline" onClick={onToggleSidebar} aria-label="Mostrar u ocultar menu lateral">
        {sidebarOpen ? <X size={18} /> : <Menu size={18} />}
      </button>

      <nav className="topbar__crumbs" aria-label="Ruta actual">
        {breadcrumbs.map((part, index) => {
          const isLast = index === breadcrumbs.length - 1;
          return (
            <span key={`${part.label}-${index}`} className="topbar__crumb-item">
              {part.href && !isLast ? <a href={part.href} className="topbar__crumb-link">{part.label}</a> : <span className={isLast ? "topbar__crumb-current" : "topbar__crumb-link"}>{part.label}</span>}
              {!isLast ? <span className="topbar__crumb-sep">/</span> : null}
            </span>
          );
        })}
      </nav>

      {showSearch ? (
        <div className="topbar__search" ref={searchRef}>
          <Search size={16} />
          <input type="search" placeholder={searchPlaceholder} value={search} onChange={(event) => { setSearch(event.target.value); setSuggestionsOpen(true); setHoverIndex(-1); }} onFocus={() => setSuggestionsOpen(true)} onKeyDown={handleSearchKeyDown} />
          {shouldShowSuggestions ? (
            <div className="topbar-search-suggest">
              {loadingSuggestions ? <div className="topbar-search-suggest__hint">Buscando implementos...</div> : suggestions.length === 0 ? <div className="topbar-search-suggest__hint">Sin coincidencias</div> : suggestions.map((row, index) => {
                const suggestionMeta = getTopbarSuggestionMeta(row, search);
                return (
                  <button key={row.uuid} type="button" className={`topbar-search-item ${index === hoverIndex ? "is-hover" : ""}`} onMouseEnter={() => setHoverIndex(index)} onClick={() => goToImplement(row)}>
                    <img src={row.imgUrl ?? "https://placehold.co/48x48/e9edf5/4d6284?text=Sin+img"} alt={row.name} className="topbar-search-item__thumb" />
                    <span className="topbar-search-item__copy">
                      <span className="topbar-search-item__name">{row.name}</span>
                      {suggestionMeta ? <span className="topbar-search-item__meta">{suggestionMeta}</span> : null}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="topbar__search-spacer" aria-hidden="true" />
      )}

      <div className="topbar__user">
        <div className="topbar__actions">
          <div className="topbar__notifications" ref={notificationsRef}>
            <button
              type="button"
              className={notificationsOpen ? "topbar__icon topbar__icon--notify is-active" : "topbar__icon topbar__icon--notify"}
              aria-label="Notificaciones"
              aria-expanded={notificationsOpen}
              onClick={() => {
                setNotificationsOpen((previous) => {
                  const nextOpen = !previous;
                  if (nextOpen) {
                    onNotificationsOpen();
                  }
                  return nextOpen;
                });
              }}
            >
              <Bell size={18} />
              {notificationCount > 0 ? <span className="topbar__notify-badge">{notificationCount > 99 ? "99+" : notificationCount}</span> : null}
            </button>
            {notificationsOpen ? (
              <>
                <button
                  type="button"
                  className="notifications-dropdown__backdrop"
                  aria-label="Cerrar notificaciones"
                  onClick={() => setNotificationsOpen(false)}
                />
                <div className="notifications-dropdown" role="dialog" aria-modal="true" aria-label="Bandeja de notificaciones">
                  <div className="notifications-dropdown__header">
                    <div>
                      <strong>Notificaciones</strong>
                      <p>{notificationCount} pendientes</p>
                    </div>
                    <div className="notifications-dropdown__header-actions">
                      <button
                        type="button"
                        className="button button--table"
                        onClick={() => {
                          setNotificationsOpen(false);
                          onNotificationsViewAll();
                        }}
                      >
                        Ver todas
                      </button>
                      <button
                        type="button"
                        className="notifications-dropdown__close"
                        aria-label="Cerrar notificaciones"
                        onClick={() => setNotificationsOpen(false)}
                      >
                        <X size={18} />
                      </button>
                    </div>
                  </div>
                  <div className="notifications-dropdown__body">
                    {notificationsLoading ? (
                      <div className="notifications-dropdown__state">
                        <p>Cargando notificaciones...</p>
                      </div>
                    ) : notificationsError ? (
                      <div className="notifications-dropdown__state">
                        <p>{notificationsError}</p>
                        <button type="button" className="button button--table" onClick={onNotificationsOpen}>
                          Reintentar
                        </button>
                      </div>
                    ) : notificationItems.length === 0 ? (
                      <div className="notifications-dropdown__state">
                        <p>No tienes notificaciones nuevas.</p>
                      </div>
                    ) : (
                      <div className="notifications-dropdown__list">
                        {notificationItems.map((notification) => {
                          const href = resolveNotificationHref(notification);
                          const quickMarkAsReadButton = (
                            <button
                              type="button"
                              className="notifications-dropdown__quick-action"
                              aria-label={notification.read
                                ? `Marcar como no leida ${notification.title}`
                                : `Marcar como leida ${notification.title}`}
                              onClick={(event) => {
                                event.stopPropagation();
                                void onNotificationMarkAsRead?.(notification);
                              }}
                            >
                              {notification.read ? <RotateCcw size={15} /> : <Check size={15} />}
                            </button>
                          );
                          const itemContent = (
                            <>
                              <div className="notifications-dropdown__title-row">
                                <div className="notifications-dropdown__title">
                                  <span className={notification.read ? "notifications-dropdown__dot" : "notifications-dropdown__dot is-unread"} aria-hidden="true" />
                                  <div>
                                    <strong>{notification.title}</strong>
                                    <p>{notification.message}</p>
                                  </div>
                                </div>
                                {!notification.read ? <span className="notifications-dropdown__badge">Nueva</span> : null}
                              </div>
                              <div className="notifications-dropdown__meta">
                                <time dateTime={notification.createdAt} title={formatNotificationTimestamp(notification.createdAt)}>
                                  {formatNotificationRelativeTime(notification.createdAt)}
                                </time>
                                <span>{href ? "Ver detalle" : "Informativa"}</span>
                              </div>
                            </>
                          );

                          if (!href) {
                            return (
                              <div
                                key={notification.uuid}
                                className={notification.read ? "notifications-dropdown__item is-static" : "notifications-dropdown__item is-unread is-static"}
                              >
                                <div className="notifications-dropdown__item-shell">
                                  <div className="notifications-dropdown__item-main">
                                    {itemContent}
                                  </div>
                                  {quickMarkAsReadButton}
                                </div>
                              </div>
                            );
                          }

                          return (
                            <div
                              key={notification.uuid}
                              className={notification.read ? "notifications-dropdown__item" : "notifications-dropdown__item is-unread"}
                            >
                              <div className="notifications-dropdown__item-shell">
                                <button
                                  type="button"
                                  className="notifications-dropdown__item-main notifications-dropdown__item--button"
                                  onClick={() => {
                                    setNotificationsOpen(false);
                                    void onNotificationSelect?.(notification);
                                  }}
                                >
                                  {itemContent}
                                </button>
                                {quickMarkAsReadButton}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              </>
            ) : null}
          </div>
          <button type="button" className="topbar__icon" aria-label="Soporte" onClick={onOpenSupport}>
            <CircleHelp size={18} />
          </button>
        </div>
        <div className="topbar__avatar" aria-hidden="true">{userInitials || "US"}</div>
        <div className="topbar__user-meta">
          <strong title={safeUserName}>{safeUserName}</strong>
          <p title={userRole}>{userRole}</p>
        </div>
      </div>
    </header>
  );
}

export function InventoryLayout({
  children,
  activeSection = "categories",
  navigationMode = "inventory",
  breadcrumbs = [{ label: "Inventario", href: "#/inventory/implementos" }],
  onLogout = () => {},
  onOpenSupport = () => {},
  searchPlaceholder = "Buscar implementos...",
  showSearch = true,
  userName = "Usuario",
  role = "COORDINADOR",
  userRoleLabel = "COORDINADOR",
}: {
  children: ReactNode;
  activeSection?: InventorySection;
  navigationMode?: NavigationMode;
  breadcrumbs?: BreadcrumbPart[];
  onLogout?: () => void;
  onOpenSupport?: () => void;
  searchPlaceholder?: string;
  showSearch?: boolean;
  userName?: string;
  role?: string;
  userRoleLabel?: string;
}) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationPreview, setNotificationPreview] = useState<NotificationInboxPage>(EMPTY_NOTIFICATION_PAGE);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);
  const { pollingPaused } = useInactivityPollingGate();
  const pendingNotificationReadStateRef = useRef<Map<string, boolean>>(new Map());
  const notificationSyncTimeoutRef = useRef<number | null>(null);
  const normalizedRole = normalizeUserRole(role);
  const canUseChatAssistant = normalizedRole === "COORDINADOR" || normalizedRole === "DIRECTOR";

  useEffect(() => {
    function onResize() {
      if (window.innerWidth > 1100) {
        setSidebarOpen(false);
      }
    }

    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const refreshNotifications = useCallback(async (showLoading: boolean) => {
    if (showLoading) {
      setNotificationsLoading(true);
    }

    try {
      const page = await fetchNotificationsPage({ page: 1, size: NOTIFICATION_PREVIEW_SIZE });
      setNotificationPreview(applyPendingNotificationReadStates(page, pendingNotificationReadStateRef.current));
      setNotificationsError(null);
    } catch {
      setNotificationsError("No fue posible cargar las notificaciones.");
    } finally {
      if (showLoading) {
        setNotificationsLoading(false);
      }
    }
  }, []);

  const flushPendingNotificationReadStates = useCallback(async () => {
    if (notificationSyncTimeoutRef.current !== null) {
      window.clearTimeout(notificationSyncTimeoutRef.current);
      notificationSyncTimeoutRef.current = null;
    }

    const snapshot = new Map(pendingNotificationReadStateRef.current);
    if (snapshot.size === 0) {
      return;
    }

    pendingNotificationReadStateRef.current.clear();

    const markAsReadUuids: string[] = [];
    const markAsUnreadUuids: string[] = [];

    snapshot.forEach((read, uuid) => {
      if (read) {
        markAsReadUuids.push(uuid);
      } else {
        markAsUnreadUuids.push(uuid);
      }
    });

    try {
      if (markAsReadUuids.length > 0) {
        await markNotificationsAsRead(markAsReadUuids);
      }
      if (markAsUnreadUuids.length > 0) {
        await markNotificationsAsUnread(markAsUnreadUuids);
      }
      dispatchNotificationsChanged();
    } catch {
      dispatchNotificationsChanged();
      void refreshNotifications(false);
    }
  }, [refreshNotifications]);

  const schedulePendingNotificationReadStateSync = useCallback(() => {
    if (notificationSyncTimeoutRef.current !== null) {
      window.clearTimeout(notificationSyncTimeoutRef.current);
    }

    notificationSyncTimeoutRef.current = window.setTimeout(() => {
      void flushPendingNotificationReadStates();
    }, NOTIFICATION_TOGGLE_SYNC_DELAY_MS);
  }, [flushPendingNotificationReadStates]);

  useEffect(() => {
    void refreshNotifications(true);
  }, [refreshNotifications]);

  useEffect(() => {
    function handleNotificationsChanged() {
      void refreshNotifications(false);
    }

    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);
    return () => window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);
  }, [refreshNotifications]);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void refreshNotifications(false);
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [pollingPaused, refreshNotifications]);

  useEffect(() => (
    () => {
      if (notificationSyncTimeoutRef.current !== null) {
        window.clearTimeout(notificationSyncTimeoutRef.current);
      }
      void flushPendingNotificationReadStates();
    }
  ), [flushPendingNotificationReadStates]);

  function closeSidebarOnNavigate() {
    if (window.innerWidth <= 1100) {
      setSidebarOpen(false);
    }
  }

  const handleNotificationsOpen = useCallback(() => {
    void refreshNotifications(false);
  }, [refreshNotifications]);

  const handleNotificationSelect = useCallback(async (notification: NotificationInboxItem) => {
    const href = resolveNotificationHref(notification);
    if (!href) {
      return;
    }

    pendingNotificationReadStateRef.current.delete(notification.uuid);
    setNotificationPreview((previous) => markNotificationPageItemAsRead(previous, notification.uuid));

    try {
      await markNotificationAsRead(notification.uuid);
      dispatchNotificationsChanged();
    } catch {
      // El refresh posterior reconciliara el estado.
    }

    window.location.hash = href;
  }, []);

  const handleNotificationMarkAsRead = useCallback(async (notification: NotificationInboxItem) => {
    const nextRead = !notification.read;
    setNotificationPreview((previous) => toggleNotificationPageItemReadState(previous, notification.uuid));
    pendingNotificationReadStateRef.current.set(notification.uuid, nextRead);
    dispatchNotificationReadStateStaged({
      notificationUuid: notification.uuid,
      read: nextRead,
    });
    schedulePendingNotificationReadStateSync();

    try {
      // Sync deferred on purpose to batch toggle actions.
    } catch {
      // no-op
    }
  }, [schedulePendingNotificationReadStateSync]);

  const handleNotificationsViewAll = useCallback(() => {
    window.location.hash = "#/notificaciones";
  }, []);

  return (
    <div className={`app-shell ${sidebarOpen ? "app-shell--sidebar-open" : ""}`}>
      <Sidebar
        activeSection={activeSection}
        navigationMode={navigationMode}
        role={role}
        onNavigate={closeSidebarOnNavigate}
        onLogout={onLogout}
      />
      <div className="app-shell__workspace">
        <TopBar
          sidebarOpen={sidebarOpen}
          onToggleSidebar={() => setSidebarOpen((value) => !value)}
          breadcrumbs={breadcrumbs}
          onOpenSupport={onOpenSupport}
          searchPlaceholder={searchPlaceholder}
          showSearch={showSearch}
          notificationCount={notificationPreview.unreadCount}
          notificationItems={notificationPreview.items}
          notificationsLoading={notificationsLoading}
          notificationsError={notificationsError}
          onNotificationsOpen={handleNotificationsOpen}
          onNotificationMarkAsRead={handleNotificationMarkAsRead}
          onNotificationSelect={handleNotificationSelect}
          onNotificationsViewAll={handleNotificationsViewAll}
          userName={userName}
          userRole={userRoleLabel}
        />
        <main className={canUseChatAssistant ? "app-shell__main app-shell__main--with-chat-assistant" : "app-shell__main"}>{children}</main>
      </div>
      {canUseChatAssistant ? <ChatWidget /> : null}
      {sidebarOpen ? <button type="button" className="sidebar-overlay" aria-label="Cerrar menu lateral" onClick={() => setSidebarOpen(false)} /> : null}
    </div>
  );
}

