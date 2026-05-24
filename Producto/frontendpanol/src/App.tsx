import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  InventoryLayout,
  type BreadcrumbPart,
  type InventorySection,
  type NavigationMode,
} from "./components/layout/InventoryLayout";
import { InventoryCategoriesPage } from "./pages/InventoryCategoriesPage";
import { DirectorCreateUserPage } from "./pages/DirectorCreateUserPage";
import { DirectorDashboardPage } from "./pages/DirectorDashboardPage";
import { InventoryItemDetailPage } from "./pages/InventoryItemDetailPage";
import { InventoryImplementCreatePage } from "./pages/InventoryImplementCreatePage";
import { InventoryItemsPage } from "./pages/InventoryItemsPage";
import { InventoryLocationsPage } from "./pages/InventoryLocationsPage";
import { InventoryMovesPage } from "./pages/InventoryMovesPage";
import { LoanCreatePage } from "./pages/LoanCreatePage";
import { LoanCoordinatorPage } from "./pages/LoanCoordinatorPage";
import { LoanDetailPage } from "./pages/LoanDetailPage";
import { LoanHistoryPage } from "./pages/LoanHistoryPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { logout } from "./services/authService";
import { clearSession, getUserRoleFromToken, isAuthenticated } from "./utils/auth";

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
  const [hash, setHash] = useState(() => window.location.hash || "#/login");
  const [routeTransitionKey, setRouteTransitionKey] = useState(0);

  useEffect(() => {
    function handleHashChange() {
      setHash(window.location.hash || "#/login");
      setRouteTransitionKey((previous) => previous + 1);
    }

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  async function handleLogout() {
    await logout();
    window.location.hash = "#/login";
  }

  const role = getUserRoleFromToken();
  const authenticated = isAuthenticated();
  const normalizedHash = hash || "#/login";
  const defaultHash = getDefaultHashByRole(role);
  const effectiveHash = !authenticated
    ? "#/login"
    : normalizedHash === "#/login"
      ? defaultHash
      : normalizedHash;

  useEffect(() => {
    if (!authenticated && normalizedHash !== "#/login") {
      clearSession();
      window.location.hash = "#/login";
      return;
    }
    if (authenticated && role === "DIRECTOR" && normalizedHash.startsWith("#/inventory")) {
      window.location.hash = "#/director/dashboard";
      return;
    }
    if (authenticated && normalizedHash === "#/login") {
      window.location.hash = defaultHash;
      return;
    }
  }, [authenticated, normalizedHash, defaultHash, role]);

  const routeView = useMemo<RouteView>(() => {
    const currentHash = effectiveHash;

    if (role !== "DIRECTOR" && currentHash.startsWith("#/director")) {
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

    if (role === "DIRECTOR" && currentHash.startsWith("#/director/dashboard")) {
      return {
        key: "director-dashboard",
        navigationMode: "director",
        activeSection: "director-dashboard",
        breadcrumbs: [{ label: "Dashboard" }, { label: "Director de Carrera" }],
        content: <DirectorDashboardPage embedded />,
      };
    }

    if (role === "DIRECTOR" && currentHash.startsWith("#/director/users/create")) {
      return {
        key: "director-users-create",
        navigationMode: "director",
        activeSection: "director-users",
        breadcrumbs: [{ label: "Usuarios" }, { label: "Director de Carrera" }],
        content: <DirectorCreateUserPage embedded />,
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

    const loanDetailMatch = currentHash.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    const loanEditMatch = currentHash.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/editar$/,
    );
    if (loanEditMatch) {
      if (role !== "DOCENTE") {
        return {
          key: "loan-edit-forbidden",
          navigationMode: "inventory",
          activeSection: "loans",
          breadcrumbs: [{ label: "Prestamos" }, { label: "Acceso denegado" }],
          content: (
            <section className="panel">
              <div className="content-header"><h1>Acceso denegado</h1></div>
              <p className="text-muted">No tienes permisos para modificar solicitudes de prestamo.</p>
            </section>
          ),
        };
      }
      const loanUuid = loanEditMatch[1];
      return {
        key: `loan-edit-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: "loans",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Prestamos", href: "#/inventory/prestamos" },
          { label: "Modificar prestamo" },
        ],
        content: <LoanCreatePage embedded editLoanUuid={loanUuid} />,
      };
    }

    if (loanDetailMatch) {
      if (role !== "DOCENTE" && role !== "COORDINADOR") {
        return {
          key: "loan-forbidden",
          navigationMode: "inventory",
          activeSection: "loans",
          breadcrumbs: [{ label: "Prestamos" }, { label: "Acceso denegado" }],
          content: (
            <section className="panel">
              <div className="content-header"><h1>Acceso denegado</h1></div>
              <p className="text-muted">Solo el rol Docente puede acceder a solicitudes de prestamo.</p>
            </section>
          ),
        };
      }
      const loanUuid = loanDetailMatch[1];
      return {
        key: `loan-detail-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: "loans",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Prestamos", href: "#/inventory/prestamos" },
          { label: "Detalle prestamo" },
        ],
        content: <LoanDetailPage loanUuid={loanUuid} embedded />,
      };
    }

    if (currentHash === "#/inventory/prestamos") {
      if (role !== "DOCENTE" && role !== "COORDINADOR") {
        return {
          key: "loan-list-forbidden",
          navigationMode: "inventory",
          activeSection: "loans",
          breadcrumbs: [{ label: "Prestamos" }, { label: "Acceso denegado" }],
          content: (
            <section className="panel">
              <div className="content-header"><h1>Acceso denegado</h1></div>
              <p className="text-muted">Solo el rol Docente puede acceder a solicitudes de prestamo.</p>
            </section>
          ),
        };
      }
      if (role === "COORDINADOR") {
        return {
          key: "loan-list-coordinator",
          navigationMode: "inventory",
          activeSection: "loans",
          breadcrumbs: [
            { label: "Inventario", href: "#/inventory/implementos" },
            { label: "Prestamos", href: "#/inventory/prestamos" },
            { label: "Listado prestamos" },
          ],
          content: <LoanCoordinatorPage embedded />,
        };
      }
      return {
        key: "loan-list",
        navigationMode: "inventory",
        activeSection: "loans",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Prestamos", href: "#/inventory/prestamos" },
          { label: "Mis prestamos" },
        ],
        content: <LoanHistoryPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/prestamos/nuevo")) {
      if (role !== "DOCENTE") {
        return {
          key: "loan-create-forbidden",
          navigationMode: "inventory",
          activeSection: "loans",
          breadcrumbs: [{ label: "Prestamos" }, { label: "Acceso denegado" }],
          content: (
            <section className="panel">
              <div className="content-header"><h1>Acceso denegado</h1></div>
              <p className="text-muted">Solo el rol Docente puede crear solicitudes de prestamo.</p>
            </section>
          ),
        };
      }
      return {
        key: "loan-create",
        navigationMode: "inventory",
        activeSection: "loans",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Prestamos", href: "#/inventory/prestamos" },
          { label: "Nueva solicitud" },
        ],
        content: <LoanCreatePage embedded />,
      };
    }

    if (
      currentHash === "#/inventory/implementos/nuevo" ||
      currentHash === "#/inventory/implementos/new" ||
      currentHash === "#/inventory/items/new"
    ) {
      return {
        key: "item-create",
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [
          { label: "Inventario", href: "#/inventory/implementos" },
          { label: "Implementos", href: "#/inventory/implementos" },
          { label: "Nuevo implemento" },
        ],
        content: <InventoryImplementCreatePage embedded />,
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

  if (effectiveHash === "#/login") {
    return <LoginPage />;
  }

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
      userName={role === "DIRECTOR" ? "Director de Carrera" : "Usuario"}
      userRole={role === "DIRECTOR" ? "Director de Carrera" : role}
    >
      <div key={`${routeView.key}-${routeTransitionKey}`} className="route-transition">
        {routeView.content}
      </div>
    </InventoryLayout>
  );
}

export default App;

