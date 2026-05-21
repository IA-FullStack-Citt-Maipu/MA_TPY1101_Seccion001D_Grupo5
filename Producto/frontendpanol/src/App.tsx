import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  InventoryLayout,
  type BreadcrumbPart,
  type InventorySection,
  type NavigationMode,
} from "./components/layout/InventoryLayout";
import { InventoryCategoriesPage } from "./pages/InventoryCategoriesPage";
import { InventoryItemDetailPage } from "./pages/InventoryItemDetailPage";
import { InventoryItemsPage } from "./pages/InventoryItemsPage";
import { InventoryLocationsPage } from "./pages/InventoryLocationsPage";
import { InventoryMovesPage } from "./pages/InventoryMovesPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { clearSession, type UserRole } from "./utils/auth";

interface RouteView {
  key: string;
  activeSection: InventorySection;
  navigationMode: NavigationMode;
  breadcrumbs: BreadcrumbPart[];
  content: ReactNode;
  notFound?: boolean;
}

function getDefaultHashByRole(role: string): string {
  if (role === "DIRECTOR") return "#/director/dashboard";
  return "#/inventory/categories";
}

function App() {
  const [hash, setHash] = useState(() => window.location.hash || "#/inventory/categories");
  const [routeTransitionKey, setRouteTransitionKey] = useState(0);

  useEffect(() => {
    function handleHashChange() {
      setHash(window.location.hash || "#/inventory/categories");
      setRouteTransitionKey((previous) => previous + 1);
    }

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  async function handleLogout() {
    // Solo limpia la sesión pero no redirige al login
    clearSession();
  }

  // Modo desarrollo: usar rol por defecto y omitir autenticación
  const role: UserRole = "COORDINADOR"; // Cambiar a "DIRECTOR" o "DOCENTE" según necesites
  const normalizedHash = hash || "#/inventory/categories";
  const defaultHash = getDefaultHashByRole(role);
  const effectiveHash = normalizedHash === "#/login" ? defaultHash : normalizedHash;

  useEffect(() => {
    if (normalizedHash === "#/login") {
      window.location.hash = defaultHash;
    }
  }, [normalizedHash, defaultHash]);

  const routeView = useMemo<RouteView>(() => {
    const currentHash = effectiveHash;

    // Acceso denegado a rutas de director
    if (currentHash.startsWith("#/director")) {
      return {
        key: "director-forbidden",
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [{ label: "Acceso" }, { label: "Denegado" }],
        content: (
          <section className="panel">
            <div className="content-header"><h1>Acceso denegado</h1></div>
            <p className="text-muted">Esta vista solo esta disponible para Director de carrera.</p>
          </section>
        ),
      };
    }

    const itemDetailMatch = currentHash.match(
      /^#\/inventory\/(?:implementos|items)\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    if (itemDetailMatch) {
      const implementUuid = itemDetailMatch[1];
      return {
        key: `detail-${implementUuid}`,
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Implementos", href: "#/inventory/implementos" },
          { label: "Detalle" },
        ],
        content: <InventoryItemDetailPage implementUuid={implementUuid} embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/implementos") || currentHash.startsWith("#/inventory/items")) {
      return { key: "items", navigationMode: "inventory", activeSection: "items", breadcrumbs: [{ label: "Inventario" }, { label: "Implementos" }], content: <InventoryItemsPage embedded /> };
    }
    if (currentHash.startsWith("#/inventory/locations")) {
      return { key: "locations", navigationMode: "inventory", activeSection: "locations", breadcrumbs: [{ label: "Inventario" }, { label: "Ubicaciones" }], content: <InventoryLocationsPage embedded /> };
    }
    if (currentHash.startsWith("#/inventory/moves")) {
      return { key: "moves", navigationMode: "inventory", activeSection: "moves", breadcrumbs: [{ label: "Inventario" }, { label: "Movimientos" }], content: <InventoryMovesPage embedded /> };
    }
    if (currentHash.startsWith("#/inventory/categories")) {
      return { key: "categories", navigationMode: "inventory", activeSection: "categories", breadcrumbs: [{ label: "Inventario" }, { label: "Categorias" }], content: <InventoryCategoriesPage embedded /> };
    }

    return {
      key: "404",
      navigationMode: "inventory",
      activeSection: "items",
      breadcrumbs: [{ label: "Error" }, { label: "404" }],
      content: <NotFoundPage />,
      notFound: true,
    };
  }, [effectiveHash, role]);

  return (
    <InventoryLayout
      activeSection={routeView.activeSection}
      navigationMode={routeView.navigationMode}
      breadcrumbs={routeView.breadcrumbs}
      onLogout={handleLogout}
      searchPlaceholder={
        routeView.navigationMode === "director"
          ? "Buscar implementos, solicitudes, usuarios..."
          : "Buscar implementos..."
      }
      notificationCount={routeView.navigationMode === "director" ? 3 : 0}
      userName="Coordinador"
      userRole={role}
    >
      <div key={`${routeView.key}-${routeTransitionKey}`} className="route-transition">
        {routeView.content}
      </div>
    </InventoryLayout>
  );
}

export default App;

