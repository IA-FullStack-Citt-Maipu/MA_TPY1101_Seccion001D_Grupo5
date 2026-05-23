import {
  Bell,
  BookOpenText,
  Boxes,
  ClipboardList,
  FileBarChart2,
  Handshake,
  History,
  LayoutDashboard,
  LogOut,
  MapPin,
  Menu,
  Search,
  Siren,
  Users,
  X,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import type { ReactNode } from "react";
import { fetchImplements } from "../../services/implementService";
import type { ImplementSummary } from "../../types/implement";

const menuInventory = [
  { key: "items", label: "Implementos", icon: Boxes, href: "#/inventory/implementos" },
  { key: "categories", label: "Categorias", icon: ClipboardList, href: "#/inventory/categories" },
  { key: "locations", label: "Ubicaciones", icon: MapPin, href: "#/inventory/locations" },
  { key: "moves", label: "Movimientos", icon: ClipboardList, href: "#/inventory/moves" },
  { key: "loans", label: "Prestamos", icon: Handshake, href: "#/inventory/prestamos/nuevo" },
  { key: "reports", label: "Reportes", icon: FileBarChart2, href: "#/inventory/implementos" },
] as const;

const menuDirector = [
  { key: "director-dashboard", label: "Dashboard", icon: LayoutDashboard, href: "#/director/dashboard" },
  { key: "director-inventory", label: "Inventario", icon: Boxes, href: "#/director/dashboard" },
  { key: "director-requests", label: "Solicitudes", icon: ClipboardList, href: "#/director/dashboard" },
  { key: "director-users", label: "Usuarios", icon: Users, href: "#/director/users/create" },
  { key: "director-subjects", label: "Asignaturas", icon: BookOpenText, href: "#/director/dashboard" },
  { key: "director-alerts", label: "Alertas", icon: Siren, href: "#/director/dashboard" },
  { key: "director-reports", label: "Reportes", icon: FileBarChart2, href: "#/director/dashboard" },
  { key: "director-history", label: "Historial", icon: History, href: "#/director/dashboard" },
] as const;

export type InventorySection =
  | "items"
  | "categories"
  | "locations"
  | "moves"
  | "loans"
  | "reports"
  | "director-dashboard"
  | "director-users"
  | "director-inventory"
  | "director-requests"
  | "director-subjects"
  | "director-alerts"
  | "director-reports"
  | "director-history";

export type NavigationMode = "inventory" | "director";

export interface BreadcrumbPart {
  label: string;
  href?: string;
}

export function Sidebar({
  activeSection,
  navigationMode,
  onNavigate,
}: {
  activeSection: InventorySection;
  navigationMode: NavigationMode;
  onNavigate?: () => void;
}) {
  const menu = navigationMode === "director" ? menuDirector : menuInventory;

  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <div>
          <strong>{navigationMode === "director" ? "Pañol" : "Coordinador de laboratorio"}</strong>
          <p>{navigationMode === "director" ? "Gestion de inventario y prestamos" : "Escuela de salud"}</p>
        </div>
      </div>

      <section>
        <h3 className="sidebar__title">{navigationMode === "director" ? "Director de carrera" : "Inventario"}</h3>
        <ul className="sidebar__list">
          {menu.map((item) => {
            const Icon = item.icon;
            const isActive = item.key === activeSection;
            return (
              <li key={item.label}>
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
    </aside>
  );
}

export function TopBar({
  sidebarOpen,
  onToggleSidebar,
  breadcrumbs,
  onLogout = () => {},
  searchPlaceholder = "Buscar implementos...",
  notificationCount = 0,
  userName = "Usuario",
  userRole = "COORDINADOR",
}: {
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  breadcrumbs: BreadcrumbPart[];
  onLogout?: () => void;
  searchPlaceholder?: string;
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
    if (debouncedSearch.length < 2) {
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
  }, [debouncedSearch]);

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
    () => suggestionsOpen && search.trim().length >= 2,
    [search, suggestionsOpen],
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

      <div className="topbar__search" ref={searchRef}>
        <Search size={16} />
        <input type="search" placeholder={searchPlaceholder} value={search} onChange={(event) => { setSearch(event.target.value); setSuggestionsOpen(true); setHoverIndex(-1); }} onFocus={() => setSuggestionsOpen(true)} onKeyDown={handleSearchKeyDown} />
        {shouldShowSuggestions ? (
          <div className="topbar-search-suggest">
            {loadingSuggestions ? <div className="topbar-search-suggest__hint">Buscando implementos...</div> : suggestions.length === 0 ? <div className="topbar-search-suggest__hint">Sin coincidencias</div> : suggestions.map((row, index) => (
              <button key={row.uuid} type="button" className={`topbar-search-item ${index === hoverIndex ? "is-hover" : ""}`} onMouseEnter={() => setHoverIndex(index)} onClick={() => goToImplement(row)}>
                <img src={(row.imgUrl ?? (row as any).img_url) ?? "https://placehold.co/48x48/e9edf5/4d6284?text=Sin+img"} alt={row.name} className="topbar-search-item__thumb" />
                <span className="topbar-search-item__name">{row.name}</span>
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="topbar__user">
        <button type="button" className="topbar__icon topbar__icon--notify" aria-label="Notificaciones">
          <Bell size={18} />
          {notificationCount > 0 ? <span className="topbar__notify-badge">{notificationCount}</span> : null}
        </button>
        <div className="topbar__avatar">{userInitials || "US"}</div>
        <div>
          <strong>{safeUserName}</strong>
          <p>{userRole}</p>
        </div>
        <button type="button" className="button button--ghost" onClick={onLogout}><LogOut size={16} /> Salir</button>
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
  searchPlaceholder = "Buscar implementos...",
  notificationCount = 0,
  userName = "Usuario",
  userRole = "COORDINADOR",
}: {
  children: ReactNode;
  activeSection?: InventorySection;
  navigationMode?: NavigationMode;
  breadcrumbs?: BreadcrumbPart[];
  onLogout?: () => void;
  searchPlaceholder?: string;
  notificationCount?: number;
  userName?: string;
  userRole?: string;
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
      <Sidebar activeSection={activeSection} navigationMode={navigationMode} onNavigate={closeSidebarOnNavigate} />
      <div className="app-shell__workspace">
        <TopBar
          sidebarOpen={sidebarOpen}
          onToggleSidebar={() => setSidebarOpen((value) => !value)}
          breadcrumbs={breadcrumbs}
          onLogout={onLogout}
          searchPlaceholder={searchPlaceholder}
          notificationCount={notificationCount}
          userName={userName}
          userRole={userRole}
        />
        <main className="app-shell__main">{children}</main>
      </div>
      {sidebarOpen ? <button type="button" className="sidebar-overlay" aria-label="Cerrar menu lateral" onClick={() => setSidebarOpen(false)} /> : null}
    </div>
  );
}
