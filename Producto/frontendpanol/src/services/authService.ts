import { apiClient } from "./apiClient";
import { clearSession, normalizeUserRole, setAccessToken, setSessionUser, type SessionUserSummary } from "../utils/auth";

export interface LoginPayload {
  rut: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResult {
  accessToken: string;
  role: string;
  expiresInSeconds: number;
  user: SessionUserSummary;
}

interface BackendLoginResponse {
  accessToken?: string;
  token?: string;
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
  const { data } = await apiClient.post<BackendLoginResponse>("/api/v2/auth/login", requestPayload);
  const token = data.accessToken ?? data.token;
  if (!token) {
    throw new Error("La respuesta de login no incluyo token");
  }

  const normalizedRole = normalizeUserRole(data.user?.role ?? data.role);
  const sessionUser: SessionUserSummary = {
    id: data.user?.id ?? "",
    name: data.user?.name?.trim() || "Usuario",
    email: data.user?.email ?? null,
    role: normalizedRole,
  };

  setAccessToken(token, rememberMe);
  setSessionUser(sessionUser, rememberMe);

  return {
    accessToken: token,
    role: normalizedRole,
    expiresInSeconds: data.expiresInSeconds,
    user: sessionUser,
  };
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post("/api/v2/auth/logout");
  } finally {
    clearSession();
  }
}

