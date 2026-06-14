export type UserRole = "COORDINADOR" | "DIRECTOR" | "DOCENTE" | "UNKNOWN";

export interface SessionUserSummary {
  id: string;
  name: string;
  email: string | null;
  role: UserRole;
}

export const AUTH_SESSION_CHANGED_EVENT = "panol:auth-session-changed";

const ACCESS_TOKEN_KEY = "access_token";
const LEGACY_TOKEN_KEY = "token";
const AUTH_USER_KEY = "auth_user";

function notifySessionChanged() {
  if (typeof window === "undefined") {
    return;
  }
  window.dispatchEvent(new Event(AUTH_SESSION_CHANGED_EVENT));
}

function shouldPersistSessionInLocalStorage() {
  return (
    localStorage.getItem(AUTH_USER_KEY) !== null ||
    localStorage.getItem(ACCESS_TOKEN_KEY) !== null ||
    localStorage.getItem(LEGACY_TOKEN_KEY) !== null
  );
}

function removeLegacyTokenKeys() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(LEGACY_TOKEN_KEY);
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
}

export function normalizeUserRole(roleRaw: string | null | undefined): UserRole {
  const normalized = String(roleRaw ?? "")
    .replace("ROLE_", "")
    .trim()
    .toUpperCase();

  if (normalized === "COORDINADOR") return "COORDINADOR";
  if (normalized === "DIRECTOR") return "DIRECTOR";
  if (normalized === "DOCENTE") return "DOCENTE";
  return "UNKNOWN";
}

export function getDefaultHashByRole(role: UserRole): string {
  if (role === "DIRECTOR") return "#/director/dashboard";
  if (role === "DOCENTE") return "#/inventory/prestamos";
  if (role === "COORDINADOR") return "#/inventory/dashboard";
  return "#/login";
}

export function getRoleDisplayLabel(role: UserRole): string {
  if (role === "DIRECTOR") return "Director";
  if (role === "COORDINADOR") return "Coordinador";
  if (role === "DOCENTE") return "Docente";
  return "Sin rol";
}

function getStorageValue(key: string): string | null {
  return localStorage.getItem(key) ?? sessionStorage.getItem(key);
}

export function setSessionUser(user: SessionUserSummary, rememberMe: boolean) {
  const payload = JSON.stringify(user);
  if (rememberMe) {
    localStorage.setItem(AUTH_USER_KEY, payload);
    sessionStorage.removeItem(AUTH_USER_KEY);
  } else {
    sessionStorage.setItem(AUTH_USER_KEY, payload);
    localStorage.removeItem(AUTH_USER_KEY);
  }
  removeLegacyTokenKeys();
  notifySessionChanged();
}

export function getSessionUser(): SessionUserSummary | null {
  const raw = getStorageValue(AUTH_USER_KEY);
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as Partial<SessionUserSummary>;
    if (typeof parsed.id !== "string" || typeof parsed.name !== "string") return null;
    return {
      id: parsed.id,
      name: parsed.name,
      email: typeof parsed.email === "string" ? parsed.email : null,
      role: normalizeUserRole(parsed.role),
    };
  } catch {
    return null;
  }
}

export function getSessionUserRole(): UserRole {
  return getSessionUser()?.role ?? "UNKNOWN";
}

export function replaceSessionUser(user: SessionUserSummary) {
  const payload = JSON.stringify(user);
  if (shouldPersistSessionInLocalStorage()) {
    localStorage.setItem(AUTH_USER_KEY, payload);
    sessionStorage.removeItem(AUTH_USER_KEY);
  } else {
    sessionStorage.setItem(AUTH_USER_KEY, payload);
    localStorage.removeItem(AUTH_USER_KEY);
  }
  removeLegacyTokenKeys();
  notifySessionChanged();
}

export function clearSession() {
  localStorage.removeItem(AUTH_USER_KEY);
  sessionStorage.removeItem(AUTH_USER_KEY);
  removeLegacyTokenKeys();
  notifySessionChanged();
}
