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
import { InventoryHealthDashboardPage } from "./pages/InventoryHealthDashboardPage";
import { InventoryItemDetailPage } from "./pages/InventoryItemDetailPage";
import { InventoryImplementCreatePage } from "./pages/InventoryImplementCreatePage";
import { InventoryItemsPage } from "./pages/InventoryItemsPage";
import { InventoryLocationsPage } from "./pages/InventoryLocationsPage";
import { InventoryMovesPage } from "./pages/InventoryMovesPage";
import { LoanCalendarPage } from "./pages/LoanCalendarPage";
import { LoanCreatePage } from "./pages/LoanCreatePage";
import { LoanCoordinatorPage } from "./pages/LoanCoordinatorPage";
import { LoanDetailPage } from "./pages/LoanDetailPage";
import { LoanDeliveryPage } from "./pages/LoanDeliveryPage";
import { LoanHistoryPage } from "./pages/LoanHistoryPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { OutboxMonitoringPage } from "./pages/OutboxMonitoringPage";
import { logout } from "./services/authService";
import {
  clearSession,
  getDefaultHashByRole,
  getRoleDisplayLabel,
  getSessionUser,
  getUserRoleFromToken,
  isAuthenticated,
  type UserRole,
} from "./utils/auth";

interface RouteView {
  key: string;
  activeSection: InventorySection;
  navigationMode: NavigationMode;
  breadcrumbs: BreadcrumbPart[];
  content: ReactNode;
  notFound?: boolean;
}

function isTeacher(role: UserRole): boolean {
  return role === "DOCENTE";
}

function isCoordinator(role: UserRole): boolean {
  return role === "COORDINADOR";
}

function isDirector(role: UserRole): boolean {
  return role === "DIRECTOR";
}


function renderAccessDenied(message: string) {
  return (
    <section className="panel">
      <div className="content-header"><h1>Acceso denegado</h1></div>
      <p className="text-muted">{message}</p>
    </section>
  );
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
  const sessionUser = getSessionUser();
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
    if (authenticated && role === "UNKNOWN") {
      clearSession();
      window.location.hash = "#/login";
      return;
    }
    if (authenticated && isDirector(role) && normalizedHash.startsWith("#/inventory")) {
      window.location.hash = "#/director/dashboard";
      return;
    }
    if (authenticated && normalizedHash === "#/login") {
      window.location.hash = defaultHash;
    }
  }, [authenticated, defaultHash, normalizedHash, role]);

  const routeView = useMemo<RouteView>(() => {
    const currentHash = effectiveHash;
    const teacherRole = isTeacher(role);
    const coordinatorRole = isCoordinator(role);
    const directorRole = isDirector(role);

    function inventoryDenied(activeSection: InventorySection, areaLabel: string, message: string): RouteView {
      return {
        key: `denied-${activeSection}`,
        navigationMode: "inventory",
        activeSection,
        breadcrumbs: [{ label: areaLabel }, { label: "Acceso denegado" }],
        content: renderAccessDenied(message),
      };
    }

    if (!directorRole && currentHash.startsWith("#/director")) {
      return {
        key: "director-forbidden",
        navigationMode: "inventory",
        activeSection: teacherRole ? "teacher-loans" : "dashboard",
        breadcrumbs: [{ label: "Director" }, { label: "Acceso denegado" }],
        content: renderAccessDenied("Esta vista solo esta disponible para Director de carrera."),
      };
    }

    if (directorRole && currentHash.startsWith("#/director/users")) {
      return {
        key: "director-users",
        navigationMode: "director",
        activeSection: "director-users",
        breadcrumbs: [{ label: "Director" }, { label: "Usuarios" }],
        content: <DirectorCreateUserPage embedded />,
      };
    }

    if (directorRole && currentHash.startsWith("#/director/dashboard")) {
      return {
        key: "director-dashboard",
        navigationMode: "director",
        activeSection: "director-dashboard",
        breadcrumbs: [{ label: "Director" }, { label: "Dashboard" }],
        content: <DirectorDashboardPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/monitoring/outbox")) {
      if (!coordinatorRole) {
        return inventoryDenied("reports", "Monitoreo", "Solo el rol Coordinador puede acceder al monitoreo tecnico.");
      }
      return {
        key: "outbox-monitoring",
        navigationMode: "inventory",
        activeSection: "reports",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Monitoreo" }],
        content: <OutboxMonitoringPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/prestamos/calendario")) {
      if (!teacherRole && !coordinatorRole) {
        return inventoryDenied("agenda", "Prestamos", "No tienes permisos para ver la agenda de prestamos.");
      }
      return {
        key: "loan-calendar",
        navigationMode: "inventory",
        activeSection: "agenda",
        breadcrumbs: teacherRole
          ? [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Agenda" }]
          : [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Agenda" }],
        content: <LoanCalendarPage embedded />,
      };
    }

    const itemDetailMatch = currentHash.match(
      /^#\/inventory\/(?:implementos|items)\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    if (itemDetailMatch) {
      if (!coordinatorRole) {
        return inventoryDenied("items", "Implementos", "Solo el rol Coordinador puede acceder al catalogo operativo.");
      }
      const implementUuid = itemDetailMatch[1];
      return {
        key: `detail-${implementUuid}`,
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Implementos", href: "#/inventory/implementos" }, { label: "Detalle" }],
        content: <InventoryItemDetailPage implementUuid={implementUuid} embedded />,
      };
    }

    const loanDetailMatch = currentHash.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    const loanDeliveryMatch = currentHash.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/entrega$/,
    );
    const loanEditMatch = currentHash.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/editar$/,
    );

    if (loanDeliveryMatch) {
      if (!coordinatorRole) {
        return inventoryDenied("coordinator-loans", "Prestamos", "Solo el rol Coordinador puede registrar entregas.");
      }

      const loanUuid = loanDeliveryMatch[1];
      return {
        key: `loan-delivery-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: "coordinator-loans",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Confirmar entrega" }],
        content: <LoanDeliveryPage loanUuid={loanUuid} embedded />,
      };
    }

    if (loanEditMatch) {
      if (!teacherRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "No tienes permisos para modificar solicitudes de prestamo.");
      }
      const loanUuid = loanEditMatch[1];
      return {
        key: `loan-edit-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: "loan-create",
        breadcrumbs: [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Modificar solicitud" }],
        content: <LoanCreatePage embedded editLoanUuid={loanUuid} />,
      };
    }

    if (loanDetailMatch) {
      if (!teacherRole && !coordinatorRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "No tienes permisos para acceder a solicitudes de prestamo.");
      }
      const loanUuid = loanDetailMatch[1];
      return {
        key: `loan-detail-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: teacherRole ? "teacher-loans" : "coordinator-loans",
        breadcrumbs: teacherRole
          ? [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Detalle" }]
          : [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Detalle prestamo" }],
        content: <LoanDetailPage loanUuid={loanUuid} embedded />,
      };
    }

    if (currentHash === "#/inventory/prestamos") {
      if (!teacherRole && !coordinatorRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "No tienes permisos para acceder a solicitudes de prestamo.");
      }
      if (coordinatorRole) {
        return {
          key: "loan-list-coordinator",
          navigationMode: "inventory",
          activeSection: "coordinator-loans",
          breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos" }],
          content: <LoanCoordinatorPage embedded />,
        };
      }
      return {
        key: "loan-list-teacher",
        navigationMode: "inventory",
        activeSection: "teacher-loans",
        breadcrumbs: [{ label: "Prestamos" }, { label: "Mis prestamos" }],
        content: <LoanHistoryPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/prestamos/nuevo")) {
      if (!teacherRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "Solo el rol Docente puede crear solicitudes de prestamo.");
      }
      return {
        key: "loan-create",
        navigationMode: "inventory",
        activeSection: "loan-create",
        breadcrumbs: [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Nueva solicitud" }],
        content: <LoanCreatePage embedded />,
      };
    }

    if (
      currentHash === "#/inventory/implementos/nuevo" ||
      currentHash === "#/inventory/implementos/new" ||
      currentHash === "#/inventory/items/new"
    ) {
      if (!coordinatorRole) {
        return inventoryDenied("items", "Implementos", "Solo el rol Coordinador puede crear implementos.");
      }
      return {
        key: "item-create",
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Implementos", href: "#/inventory/implementos" }, { label: "Nuevo implemento" }],
        content: <InventoryImplementCreatePage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/implementos") || currentHash.startsWith("#/inventory/items")) {
      if (!coordinatorRole) {
        return inventoryDenied("items", "Implementos", "Solo el rol Coordinador puede acceder al catalogo operativo.");
      }
      return {
        key: "items",
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [{ label: "Inventario" }, { label: "Implementos" }],
        content: <InventoryItemsPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/locations")) {
      if (!coordinatorRole) {
        return inventoryDenied("locations", "Ubicaciones", "Solo el rol Coordinador puede gestionar ubicaciones.");
      }
      return {
        key: "locations",
        navigationMode: "inventory",
        activeSection: "locations",
        breadcrumbs: [{ label: "Inventario" }, { label: "Ubicaciones" }],
        content: <InventoryLocationsPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/moves")) {
      if (!coordinatorRole) {
        return inventoryDenied("moves", "Movimientos", "Solo el rol Coordinador puede acceder a los movimientos operativos.");
      }
      return {
        key: "moves",
        navigationMode: "inventory",
        activeSection: "moves",
        breadcrumbs: [{ label: "Inventario" }, { label: "Movimientos" }],
        content: <InventoryMovesPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/categories")) {
      if (!coordinatorRole) {
        return inventoryDenied("categories", "Categorias", "Solo el rol Coordinador puede gestionar categorias.");
      }
      return {
        key: "categories",
        navigationMode: "inventory",
        activeSection: "categories",
        breadcrumbs: [{ label: "Inventario" }, { label: "Categorias" }],
        content: <InventoryCategoriesPage embedded />,
      };
    }

    if (currentHash.startsWith("#/inventory/dashboard")) {
      if (!coordinatorRole) {
        return inventoryDenied("dashboard", "Dashboard", "Solo el rol Coordinador puede acceder al dashboard operativo.");
      }
      return {
        key: "inventory-dashboard",
        navigationMode: "inventory",
        activeSection: "dashboard",
        breadcrumbs: [{ label: "Inventario" }, { label: "Dashboard" }],
        content: <InventoryHealthDashboardPage embedded />,
      };
    }

    return {
      key: "404",
      navigationMode: directorRole ? "director" : "inventory",
      activeSection: directorRole ? "director-dashboard" : teacherRole ? "teacher-loans" : "dashboard",
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
      searchPlaceholder="Buscar implementos..."
      showSearch={isCoordinator(role)}
      notificationCount={isDirector(role) ? 3 : 0}
      userName={sessionUser?.name?.trim() || "Usuario"}
      role={role}
      userRoleLabel={getRoleDisplayLabel(role)}
    >
      <div key={`${routeView.key}-${routeTransitionKey}`} className="route-transition">
        {routeView.content}
      </div>
    </InventoryLayout>
  );
}

export default App;





