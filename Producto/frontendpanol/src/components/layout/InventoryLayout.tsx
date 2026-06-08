import {
  Bell,
  Boxes,
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
  Users,
  X,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import type { ReactNode } from "react";
import { fetchImplements } from "../../services/implementService";
import type { ImplementSummary } from "../../types/implement";
import { normalizeUserRole, type UserRole } from "../../utils/auth";

interface MenuItem {
  label: string;
  icon: typeof LayoutDashboard;
  href: string;
  activeSections: InventorySection[];
}

const coordinatorMenu: MenuItem[] = [
  { label: "Dashboard", icon: LayoutDashboard, href: "#/inventory/dashboard", activeSections: ["dashboard"] },
  { label: "Implementos", icon: Boxes, href: "#/inventory/implementos", activeSections: ["items"] },
  { label: "Categorias", icon: ClipboardList, href: "#/inventory/categories", activeSections: ["categories"] },
  { label: "Ubicaciones", icon: MapPin, href: "#/inventory/locations", activeSections: ["locations"] },
  { label: "Movimientos", icon: ClipboardList, href: "#/inventory/moves", activeSections: ["moves"] },
  { label: "Prestamos", icon: Handshake, href: "#/inventory/prestamos", activeSections: ["coordinator-loans"] },
  { label: "Agenda", icon: History, href: "#/inventory/prestamos/calendario", activeSections: ["agenda"] },
];

const teacherMenu: MenuItem[] = [
  { label: "Mis prestamos", icon: Handshake, href: "#/inventory/prestamos", activeSections: ["teacher-loans"] },
  { label: "Nueva solicitud", icon: ClipboardList, href: "#/inventory/prestamos/nuevo", activeSections: ["loan-create"] },
  { label: "Agenda", icon: History, href: "#/inventory/prestamos/calendario", activeSections: ["agenda"] },
];

const directorMenu: MenuItem[] = [
  { label: "Dashboard", icon: LayoutDashboard, href: "#/director/dashboard", activeSections: ["director-dashboard"] },
  { label: "Usuarios", icon: Users, href: "#/director/users/create", activeSections: ["director-users"] },
];

export type InventorySection =
  | "dashboard"
  | "items"
  | "categories"
  | "locations"
  | "moves"
  | "teacher-loans"
  | "coordinator-loans"
  | "loan-create"
  | "agenda"
  | "reports"
  | "support"
  | "director-dashboard"
  | "director-users";

export type NavigationMode = "inventory" | "director";

export interface BreadcrumbPart {
  label: string;
  href?: string;
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
        <p>Medical Inventory</p>
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
          <span>Support</span>
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
  userName?: string;
  userRole?: string;
}) {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [suggestions, setSuggestions] = useState<ImplementSummary[]>([]);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [hoverIndex, setHoverIndex] = useState<number>(-1);
  const searchRef = useRef<HTMLDivElement | null>(null);
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
      <button type="button" className="topbar__burger topbar__burger--inline" onClick={onToggleSidebar} aria-label="Toggle menu lateral">
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
              {loadingSuggestions ? <div className="topbar-search-suggest__hint">Buscando implementos...</div> : suggestions.length === 0 ? <div className="topbar-search-suggest__hint">Sin coincidencias</div> : suggestions.map((row, index) => (
                <button key={row.uuid} type="button" className={`topbar-search-item ${index === hoverIndex ? "is-hover" : ""}`} onMouseEnter={() => setHoverIndex(index)} onClick={() => goToImplement(row)}>
                  <img src={row.imgUrl ?? "https://placehold.co/48x48/e9edf5/4d6284?text=Sin+img"} alt={row.name} className="topbar-search-item__thumb" />
                  <span className="topbar-search-item__name">{row.name}</span>
                </button>
              ))}
            </div>
          ) : null}
        </div>
      ) : (
        <div aria-hidden="true" />
      )}

      <div className="topbar__user">
        <div className="topbar__actions">
          <button type="button" className="topbar__icon topbar__icon--notify" aria-label="Notificaciones">
            <Bell size={18} />
            {notificationCount > 0 ? <span className="topbar__notify-badge">{notificationCount}</span> : null}
          </button>
          <button type="button" className="topbar__icon" aria-label="Soporte" onClick={onOpenSupport}>
            <CircleHelp size={18} />
          </button>
        </div>
        <div className="topbar__avatar">{userInitials || "US"}</div>
        <div className="topbar__user-meta">
          <strong>{safeUserName}</strong>
          <p>{userRole}</p>
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
  notificationCount = 0,
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
  notificationCount?: number;
  userName?: string;
  role?: string;
  userRoleLabel?: string;
}) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    function onResize() {
      if (window.innerWidth > 1100) {
        setSidebarOpen(false);
      }
    }

    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  function closeSidebarOnNavigate() {
    if (window.innerWidth <= 1100) {
      setSidebarOpen(false);
    }
  }

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
          notificationCount={notificationCount}
          userName={userName}
          userRole={userRoleLabel}
        />
        <main className="app-shell__main">{children}</main>
      </div>
      {sidebarOpen ? <button type="button" className="sidebar-overlay" aria-label="Cerrar menu lateral" onClick={() => setSidebarOpen(false)} /> : null}
    </div>
  );
}


