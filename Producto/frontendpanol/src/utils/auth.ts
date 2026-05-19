export type UserRole = "COORDINADOR" | "DIRECTOR" | "DOCENTE" | "UNKNOWN";

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

export function getAccessToken(): string | null {
  return (
    localStorage.getItem(ACCESS_TOKEN_KEY) ??
    localStorage.getItem(LEGACY_TOKEN_KEY) ??
    sessionStorage.getItem(ACCESS_TOKEN_KEY) ??
    sessionStorage.getItem(LEGACY_TOKEN_KEY)
  );
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
    .map((role) => String(role).replace("ROLE_", "").toUpperCase());

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
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
}
