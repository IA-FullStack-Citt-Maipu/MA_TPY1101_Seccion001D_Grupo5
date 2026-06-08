export type UserRole = "COORDINADOR" | "DIRECTOR" | "DOCENTE" | "UNKNOWN";

export interface SessionUserSummary {
  id: string;
  name: string;
  email: string | null;
  role: UserRole;
}

interface TokenPayload {
  exp?: number;
  sub?: string;
  role?: string;
  user_role?: string;
  roles?: string[];
  app_metadata?: { role?: string; roles?: string[] };
}

const ACCESS_TOKEN_KEY = "access_token";
const LEGACY_TOKEN_KEY = "token";
const AUTH_USER_KEY = "auth_user";

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

export function getAccessToken(): string | null {
  return getStorageValue(ACCESS_TOKEN_KEY) ?? getStorageValue(LEGACY_TOKEN_KEY);
}

export function setAccessToken(token: string, rememberMe: boolean) {
  clearSession();
  if (rememberMe) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
    localStorage.setItem(LEGACY_TOKEN_KEY, token);
    return;
  }
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  sessionStorage.setItem(LEGACY_TOKEN_KEY, token);
}

export function setSessionUser(user: SessionUserSummary, rememberMe: boolean) {
  const payload = JSON.stringify(user);
  if (rememberMe) {
    localStorage.setItem(AUTH_USER_KEY, payload);
    return;
  }
  sessionStorage.setItem(AUTH_USER_KEY, payload);
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

function parseTokenPayload(): TokenPayload | null {
  try {
    const rawToken = getAccessToken();
    if (!rawToken) return null;
    const payloadPart = rawToken.split(".")[1];
    if (!payloadPart) return null;
    const normalized = payloadPart.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(normalized)
        .split("")
        .map((char) => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`)
        .join(""),
    );
    return JSON.parse(jsonPayload) as TokenPayload;
  } catch {
    return null;
  }
}

export function getUserRoleFromToken(): UserRole {
  const parsed = parseTokenPayload();
  if (!parsed) return "UNKNOWN";

  const roles = [
    parsed.role,
    parsed.user_role,
    ...(parsed.roles ?? []),
    parsed.app_metadata?.role,
    ...(parsed.app_metadata?.roles ?? []),
  ]
    .filter(Boolean)
    .map((role) => normalizeUserRole(String(role)));

  if (roles.includes("COORDINADOR")) return "COORDINADOR";
  if (roles.includes("DIRECTOR")) return "DIRECTOR";
  if (roles.includes("DOCENTE")) return "DOCENTE";
  return "UNKNOWN";
}

export function isAuthenticated(): boolean {
  const payload = parseTokenPayload();
  if (!payload?.exp) return false;
  return payload.exp * 1000 > Date.now();
}

export function getUserUuidFromToken(): string | null {
  const payload = parseTokenPayload();
  if (typeof payload?.sub === "string" && payload.sub.length > 0) return payload.sub;
  return null;
}

export function clearSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(LEGACY_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
  sessionStorage.removeItem(AUTH_USER_KEY);
}

