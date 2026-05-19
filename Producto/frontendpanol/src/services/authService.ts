import { apiClient } from "./apiClient";
import { clearSession, setAccessToken } from "../utils/auth";

export interface LoginPayload {
  rut: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResult {
  accessToken: string;
  role: string;
  expiresInSeconds: number;
}

interface BackendLoginResponse {
  accessToken?: string;
  token?: string;
  role: string;
  expiresInSeconds: number;
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const { rememberMe = true, ...requestPayload } = payload;
  const { data } = await apiClient.post<BackendLoginResponse>("/api/v2/auth/login", requestPayload);
  const token = data.accessToken ?? data.token;
  if (!token) {
    throw new Error("La respuesta de login no incluyo token");
  }

  // Compatibilidad con integraciones legacy que leen "token" desde localStorage.
  localStorage.setItem("token", token);
  setAccessToken(token, rememberMe);

  return {
    accessToken: token,
    role: data.role,
    expiresInSeconds: data.expiresInSeconds,
  };
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post("/api/v2/auth/logout");
  } finally {
    clearSession();
  }
}
