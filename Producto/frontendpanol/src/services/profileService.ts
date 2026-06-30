import { apiClient } from "./apiClient";
import { normalizeUserRole, type SessionUserSummary } from "../utils/auth";

interface BackendProfileResponse {
  id: string;
  name: string;
  email?: string | null;
  role?: string | null;
}

interface BackendCurrentUserSessionResponse {
  id: string;
  current: boolean;
  persistentLogin: boolean;
  userAgent?: string | null;
  createdAt: string;
  accessExpiresAt?: string | null;
  sessionExpiresAt?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface CurrentUserSession {
  id: string;
  current: boolean;
  persistentLogin: boolean;
  userAgent: string | null;
  createdAt: string;
  accessExpiresAt: string | null;
  sessionExpiresAt: string | null;
}

function toSessionUserSummary(data: BackendProfileResponse): SessionUserSummary {
  return {
    id: data.id,
    name: data.name?.trim() || "Usuario",
    email: data.email ?? null,
    role: normalizeUserRole(data.role),
  };
}

export async function fetchCurrentUserProfile(): Promise<SessionUserSummary> {
  const { data } = await apiClient.get<BackendProfileResponse>("/api/v2/auth/me");
  return toSessionUserSummary(data);
}

export async function updateCurrentUserEmail(email: string): Promise<SessionUserSummary> {
  const { data } = await apiClient.patch<BackendProfileResponse>("/api/v2/auth/me/email", { email });
  return toSessionUserSummary(data);
}

export async function updateCurrentUserPassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.patch("/api/v2/auth/me/password", {
    current_password: payload.currentPassword,
    new_password: payload.newPassword,
  });
}

export async function fetchCurrentUserSessions(): Promise<CurrentUserSession[]> {
  const { data } = await apiClient.get<BackendCurrentUserSessionResponse[]>("/api/v2/auth/me/sessions");
  return data.map((session) => ({
    id: String(session.id),
    current: session.current === true,
    persistentLogin: session.persistentLogin === true,
    userAgent: typeof session.userAgent === "string" ? session.userAgent : null,
    createdAt: session.createdAt,
    accessExpiresAt: typeof session.accessExpiresAt === "string" ? session.accessExpiresAt : null,
    sessionExpiresAt: typeof session.sessionExpiresAt === "string" ? session.sessionExpiresAt : null,
  }));
}

export async function revokeCurrentUserSession(sessionId: string): Promise<void> {
  await apiClient.delete(`/api/v2/auth/me/sessions/${encodeURIComponent(sessionId)}`);
}
