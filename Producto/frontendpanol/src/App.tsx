import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  InventoryLayout,
  type BreadcrumbPart,
  type InventorySection,
  type NavigationMode,
} from "./components/layout/InventoryLayout";
import { InventoryCategoriesPage } from "./pages/InventoryCategoriesPage";
import { DirectorCreateUserPage } from "./pages/DirectorCreateUserPage";
import { DirectorDashboardPage } from "./pages/DirectorDashboardPage";
import { DirectorMovementHistoryPage } from "./pages/DirectorMovementHistoryPage";
import { InventoryHealthDashboardPage } from "./pages/InventoryHealthDashboardPage";
import { InventoryItemDetailPage } from "./pages/InventoryItemDetailPage";
import { InventoryImplementCreatePage } from "./pages/InventoryImplementCreatePage";
import { InventoryItemsPage } from "./pages/InventoryItemsPage";
import { InventoryLocationsPage } from "./pages/InventoryLocationsPage";
import { InventoryMovesPage } from "./pages/InventoryMovesPage";
import { LoanCalendarPage } from "./pages/LoanCalendarPage";
import { LoanCreatePage } from "./pages/LoanCreatePage";
import { LoanCoordinatorPage } from "./pages/LoanCoordinatorPage";
import { LoanRequesterDirectoryPage } from "./pages/LoanRequesterDirectoryPage";
import { LoanDeliveryPage } from "./pages/LoanDeliveryPage";
import { LoanHistoryPage } from "./pages/LoanHistoryPage";
import { LoanPreparationPage } from "./pages/LoanPreparationPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { PasswordRecoveryCodePage } from "./pages/PasswordRecoveryCodePage";
import { PasswordRecoveryRequestPage } from "./pages/PasswordRecoveryRequestPage";
import { PasswordRecoveryResetPage } from "./pages/PasswordRecoveryResetPage";
import { SupportPage } from "./pages/SupportPage";
import { SettingsPage } from "./pages/SettingsPage";
import { logout } from "./services/authService";
import { fetchCurrentUserProfile } from "./services/profileService";
import {
  AUTH_SESSION_CHANGED_EVENT,
  clearSession,
  getDefaultHashByRole,
  getRoleDisplayLabel,
  isSessionUserCacheFresh,
  getSessionUser,
  replaceSessionUser,
  type SessionUserSummary,
  type UserRole,
} from "./utils/auth";
import { getHashPath, getLoanDetailUuidFromHash, normalizeLegacyLoanDetailHash } from "./utils/loanDetailRouting";
import { applyThemeMode, getStoredThemeMode, persistThemeMode, type ThemeMode } from "./utils/theme";

interface RouteView {
  key: string;
  activeSection: InventorySection;
  navigationMode: NavigationMode;
  breadcrumbs: BreadcrumbPart[];
  content: ReactNode;
  searchPlaceholder?: string;
  showSearch?: boolean;
  notFound?: boolean;
}

type AuthStatus = "bootstrapping" | "authenticated" | "unauthenticated";

const PUBLIC_HASH_PATHS = new Set([
  "#/login",
  "#/recuperar-contrasena",
  "#/recuperar-contrasena/codigo",
  "#/recuperar-contrasena/nueva",
]);

function getInitialAuthState(): { sessionUser: SessionUserSummary | null; authStatus: AuthStatus } {
  const sessionUser = getSessionUser();
  if (!sessionUser) {
    return {
      sessionUser: null,
      authStatus: "unauthenticated",
    };
  }

  return {
    sessionUser,
    authStatus: isSessionUserCacheFresh() ? "authenticated" : "bootstrapping",
  };
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
  const initialAuthState = useMemo(() => getInitialAuthState(), []);
  const [hash, setHash] = useState(() => window.location.hash || "#/login");
  const [routeTransitionKey, setRouteTransitionKey] = useState(0);
  const [sessionUser, setSessionUser] = useState<SessionUserSummary | null>(initialAuthState.sessionUser);
  const [authStatus, setAuthStatus] = useState<AuthStatus>(initialAuthState.authStatus);
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => getStoredThemeMode());
  const currentHashPathRef = useRef(getHashPath(window.location.hash || "#/login"));

  useEffect(() => {
    function handleHashChange() {
      const nextHash = window.location.hash || "#/login";
      const nextHashPath = getHashPath(nextHash);

      if (nextHashPath !== currentHashPathRef.current) {
        currentHashPathRef.current = nextHashPath;
        setRouteTransitionKey((previous) => previous + 1);
      }

      setHash(nextHash);
    }

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  useEffect(() => {
    function handleSessionChanged() {
      const nextSessionUser = getSessionUser();
      setSessionUser(nextSessionUser);
      setAuthStatus(nextSessionUser ? "authenticated" : "unauthenticated");
    }

    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
    return () => window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function bootstrapSession() {
      const cachedUser = getSessionUser();
      if (!cachedUser) {
        setSessionUser(null);
        setAuthStatus("unauthenticated");
        return;
      }

      setSessionUser(cachedUser);
      if (isSessionUserCacheFresh()) {
        setAuthStatus("authenticated");
        return;
      }

      setAuthStatus("bootstrapping");
      try {
        const currentUser = await fetchCurrentUserProfile();
        if (cancelled) {
          return;
        }
        replaceSessionUser(currentUser);
        setSessionUser(currentUser);
        setAuthStatus("authenticated");
      } catch {
        if (cancelled) {
          return;
        }
        clearSession();
        setSessionUser(null);
        setAuthStatus("unauthenticated");
      }
    }

    void bootstrapSession();

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleLogout() {
    await logout();
    window.location.hash = "#/login";
  }

  const role = sessionUser?.role ?? "UNKNOWN";
  const authenticated = authStatus === "authenticated" && sessionUser != null;
  const normalizedHash = hash || "#/login";
  const normalizedHashPath = getHashPath(normalizedHash);
  const isPublicHash = PUBLIC_HASH_PATHS.has(normalizedHashPath);
  const defaultHash = getDefaultHashByRole(role);
  const effectiveHash = !authenticated
    ? (isPublicHash ? normalizedHash : "#/login")
    : normalizedHashPath === "#/login" || normalizedHashPath.startsWith("#/recuperar-contrasena")
      ? defaultHash
      : normalizedHash;
  const effectiveHashPath = getHashPath(effectiveHash);
  const activeLoanDetailUuid = getLoanDetailUuidFromHash(effectiveHash);

  useEffect(() => {
    applyThemeMode(themeMode);
  }, [themeMode]);

  const handleSessionUserChange = useCallback((updatedUser: SessionUserSummary) => {
    replaceSessionUser(updatedUser);
    setSessionUser(updatedUser);
  }, []);

  const handleThemeModeChange = useCallback((nextThemeMode: ThemeMode) => {
    persistThemeMode(nextThemeMode);
    setThemeMode(nextThemeMode);
  }, []);

  useEffect(() => {
    if (authStatus === "bootstrapping") {
      return;
    }
    if (!authenticated && !isPublicHash) {
      window.location.hash = "#/login";
      return;
    }
    if (authenticated && role === "UNKNOWN") {
      clearSession();
      window.location.hash = "#/login";
      return;
    }
    if (authenticated && isDirector(role) && normalizedHashPath.startsWith("#/inventory")) {
      window.location.hash = "#/director/dashboard";
      return;
    }
    if (authenticated && (normalizedHashPath === "#/login" || normalizedHashPath.startsWith("#/recuperar-contrasena"))) {
      window.location.hash = defaultHash;
    }
  }, [authStatus, authenticated, defaultHash, isPublicHash, normalizedHashPath, role]);

  useEffect(() => {
    if (!authenticated) {
      return;
    }

    const redirectedHash = normalizeLegacyLoanDetailHash(normalizedHash);
    if (redirectedHash && redirectedHash !== normalizedHash) {
      window.location.replace(redirectedHash);
    }
  }, [authenticated, normalizedHash]);

  const routeView = useMemo<RouteView>(() => {
    const currentHashPath = effectiveHashPath;
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

    if (!directorRole && currentHashPath.startsWith("#/director")) {
      return {
        key: "director-forbidden",
        navigationMode: "inventory",
        activeSection: teacherRole ? "teacher-loans" : "dashboard",
        breadcrumbs: [{ label: "Director" }, { label: "Acceso denegado" }],
        content: renderAccessDenied("Esta vista solo esta disponible para Director de carrera."),
      };
    }

    if (directorRole && currentHashPath.startsWith("#/director/users")) {
      return {
        key: "director-users",
        navigationMode: "director",
        activeSection: "director-users",
        breadcrumbs: [{ label: "Director" }, { label: "Usuarios" }],
        content: <DirectorCreateUserPage embedded />,
      };
    }

    if (directorRole && currentHashPath.startsWith("#/director/movimientos")) {
      return {
        key: "director-moves",
        navigationMode: "director",
        activeSection: "director-moves",
        breadcrumbs: [{ label: "Director" }, { label: "Movimientos" }],
        showSearch: false,
        content: <DirectorMovementHistoryPage embedded />,
      };
    }

    if (directorRole && currentHashPath.startsWith("#/director/docentes")) {
      return {
        key: "director-requesters",
        navigationMode: "director",
        activeSection: "director-requesters",
        breadcrumbs: [{ label: "Director" }, { label: "Docentes" }],
        showSearch: false,
        content: <LoanRequesterDirectoryPage embedded viewMode="director" />,
      };
    }

    if (directorRole && currentHashPath.startsWith("#/director/dashboard")) {
      return {
        key: "director-dashboard",
        navigationMode: "director",
        activeSection: "director-dashboard",
        breadcrumbs: [{ label: "Director" }, { label: "Panel" }],
        content: <DirectorDashboardPage embedded />,
      };
    }

    if (currentHashPath.startsWith("#/notificaciones")) {
      return {
        key: "notifications",
        navigationMode: directorRole ? "director" : "inventory",
        activeSection: "notifications",
        breadcrumbs: directorRole
          ? [{ label: "Director", href: "#/director/dashboard" }, { label: "Notificaciones" }]
          : teacherRole
            ? [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Notificaciones" }]
            : [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Notificaciones" }],
        showSearch: false,
        content: <NotificationsPage embedded />,
      };
    }

    if (currentHashPath.startsWith("#/support")) {
      return {
        key: "support",
        navigationMode: directorRole ? "director" : "inventory",
        activeSection: "support",
        breadcrumbs: directorRole
          ? [{ label: "Director", href: "#/director/dashboard" }, { label: "Soporte" }]
          : teacherRole
            ? [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Soporte" }]
            : [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Soporte" }],
        searchPlaceholder: "Buscar en el inventario o guias...",
        showSearch: true,
        content: <SupportPage embedded />,
      };
    }

    if (currentHashPath.startsWith("#/configuracion")) {
      return {
        key: "settings",
        navigationMode: directorRole ? "director" : "inventory",
        activeSection: "settings",
        breadcrumbs: directorRole
          ? [{ label: "Director", href: "#/director/dashboard" }, { label: "Configuracion" }]
          : teacherRole
            ? [{ label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Configuracion" }]
            : [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Configuracion" }],
        showSearch: false,
        content: (
          <SettingsPage
            embedded
            sessionUser={sessionUser}
            onSessionUserChange={handleSessionUserChange}
            themeMode={themeMode}
            onThemeModeChange={handleThemeModeChange}
          />
        ),
      };
    }

    if (currentHashPath.startsWith("#/inventory/monitoring/outbox")) {
      return {
        key: "outbox-monitoring-disabled",
        navigationMode: "inventory",
        activeSection: coordinatorRole ? "dashboard" : "teacher-loans",
        breadcrumbs: [{ label: coordinatorRole ? "Inventario" : "Prestamos" }, { label: "Vista deshabilitada" }],
        content: renderAccessDenied("La vista de monitoreo esta temporalmente deshabilitada."),
      };
    }

    if (currentHashPath.startsWith("#/inventory/prestamos/calendario")) {
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
        content: <LoanCalendarPage embedded activeDetailLoanUuid={activeLoanDetailUuid} />,
      };
    }

    if (currentHashPath.startsWith("#/inventory/prestamos/docentes")) {
      if (!coordinatorRole) {
        return inventoryDenied("coordinator-requesters", "Docentes", "Solo el rol Coordinador puede acceder al historial de docentes.");
      }
      return {
        key: "loan-requesters",
        navigationMode: "inventory",
        activeSection: "coordinator-requesters",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Docentes" }],
        showSearch: false,
        content: <LoanRequesterDirectoryPage embedded viewMode="coordinator" />,
      };
    }

    const itemEditMatch = currentHashPath.match(
      /^#\/inventory\/(?:implementos|items)\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/editar$/,
    );
    const itemDetailMatch = currentHashPath.match(
      /^#\/inventory\/(?:implementos|items)\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    if (itemEditMatch) {
      if (!coordinatorRole) {
        return inventoryDenied("items", "Implementos", "Solo el rol Coordinador puede acceder al catalogo operativo.");
      }
      const implementUuid = itemEditMatch[1];
      return {
        key: `item-edit-${implementUuid}`,
        navigationMode: "inventory",
        activeSection: "items",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Implementos", href: "#/inventory/implementos" }, { label: "Edicion" }],
        content: <InventoryImplementCreatePage embedded implementUuid={implementUuid} />,
      };
    }

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

    const loanDetailMatch = currentHashPath.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
    );
    const loanDeliveryMatch = currentHashPath.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/entrega$/,
    );
    const loanPreparationMatch = currentHashPath.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/preparacion$/,
    );
    const loanEditMatch = currentHashPath.match(
      /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\/editar$/,
    );

    if (loanPreparationMatch) {
      if (!coordinatorRole) {
        return inventoryDenied("coordinator-loans", "Prestamos", "Solo el rol Coordinador puede preparar solicitudes.");
      }

      const loanUuid = loanPreparationMatch[1];
      return {
        key: `loan-preparation-${loanUuid}`,
        navigationMode: "inventory",
        activeSection: "coordinator-loans",
        breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos", href: "#/inventory/prestamos" }, { label: "Preparar implementos" }],
        content: <LoanPreparationPage loanUuid={loanUuid} embedded />,
      };
    }

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
      if (!teacherRole && !coordinatorRole) {
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

    if (loanDetailMatch || currentHashPath === "#/inventory/prestamos") {
      if (!teacherRole && !coordinatorRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "No tienes permisos para acceder a solicitudes de prestamo.");
      }
      if (coordinatorRole) {
        return {
          key: "loan-list-coordinator",
          navigationMode: "inventory",
          activeSection: "coordinator-loans",
          breadcrumbs: [{ label: "Inventario", href: "#/inventory/dashboard" }, { label: "Prestamos" }],
          content: <LoanCoordinatorPage embedded activeDetailLoanUuid={activeLoanDetailUuid} />,
        };
      }
      return {
        key: "loan-list-teacher",
        navigationMode: "inventory",
        activeSection: "teacher-loans",
        breadcrumbs: [{ label: "Prestamos" }, { label: "Mis prestamos" }],
        content: <LoanHistoryPage embedded activeDetailLoanUuid={activeLoanDetailUuid} />,
      };
    }

    if (currentHashPath.startsWith("#/inventory/prestamos/nuevo")) {
      if (!teacherRole && !coordinatorRole) {
        return inventoryDenied("teacher-loans", "Prestamos", "No tienes permisos para crear solicitudes de prestamo.");
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
      currentHashPath === "#/inventory/implementos/nuevo" ||
      currentHashPath === "#/inventory/implementos/new" ||
      currentHashPath === "#/inventory/items/new"
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

    if (currentHashPath.startsWith("#/inventory/implementos") || currentHashPath.startsWith("#/inventory/items")) {
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

    if (currentHashPath.startsWith("#/inventory/locations")) {
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

    if (currentHashPath.startsWith("#/inventory/moves")) {
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

    if (currentHashPath.startsWith("#/inventory/categories")) {
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

    if (currentHashPath.startsWith("#/inventory/dashboard")) {
      if (!coordinatorRole) {
        return inventoryDenied("dashboard", "Panel", "Solo el rol Coordinador puede acceder al panel operativo.");
      }
      return {
        key: "inventory-dashboard",
        navigationMode: "inventory",
        activeSection: "dashboard",
        breadcrumbs: [{ label: "Inventario" }, { label: "Panel" }],
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
  }, [activeLoanDetailUuid, effectiveHashPath, handleSessionUserChange, handleThemeModeChange, role, sessionUser, themeMode]);

  if (authStatus === "bootstrapping") {
    return (
      <section className="panel" style={{ margin: "24px auto", maxWidth: 520 }}>
        <div className="content-header"><h1>Validando sesion</h1></div>
        <p className="text-muted">Cargando credenciales del usuario...</p>
      </section>
    );
  }

  if (effectiveHashPath === "#/login") {
    return <LoginPage />;
  }

  if (effectiveHashPath === "#/recuperar-contrasena") {
    return <PasswordRecoveryRequestPage />;
  }

  if (effectiveHashPath === "#/recuperar-contrasena/codigo") {
    return <PasswordRecoveryCodePage />;
  }

  if (effectiveHashPath === "#/recuperar-contrasena/nueva") {
    return <PasswordRecoveryResetPage />;
  }

  return (
    <InventoryLayout
      activeSection={routeView.activeSection}
      navigationMode={routeView.navigationMode}
      breadcrumbs={routeView.breadcrumbs}
      onLogout={handleLogout}
      onOpenSupport={() => {
        window.location.hash = "#/support";
      }}
      searchPlaceholder={routeView.searchPlaceholder ?? "Buscar implementos..."}
      showSearch={routeView.showSearch ?? isCoordinator(role)}
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
