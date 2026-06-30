import { apiClient } from "./apiClient";
import {
  clearSession,
  normalizeUserRole,
  setSessionUser,
  type SessionUserSummary,
} from "../utils/auth";

export interface LoginPayload {
  rut: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResult {
  role: string;
  expiresInSeconds: number;
  user: SessionUserSummary;
}

interface BackendLoginResponse {
  role: string;
  expiresInSeconds: number;
  user?: {
    id: string;
    name: string;
    email?: string | null;
    role?: string;
  };
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const { rememberMe = true, ...requestPayload } = payload;
  const { data } = await apiClient.post<BackendLoginResponse>("/api/v2/auth/login", {
    ...requestPayload,
    rememberMe,
  });

  const normalizedRole = normalizeUserRole(data.user?.role ?? data.role);
  const sessionUser: SessionUserSummary = {
    id: data.user?.id ?? "",
    name: data.user?.name?.trim() || "Usuario",
    email: data.user?.email ?? null,
    role: normalizedRole,
  };

  setSessionUser(sessionUser, rememberMe);

  return {
    role: normalizedRole,
    expiresInSeconds: data.expiresInSeconds,
    user: sessionUser,
  };
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post("/api/v2/auth/logout", undefined, { skipAuthRefresh: true });
  } finally {
    clearSession();
  }
}
