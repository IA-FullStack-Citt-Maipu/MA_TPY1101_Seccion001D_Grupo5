import axios, { AxiosError } from "axios";
import type { ApiErrorPayload } from "../types/api";
import { getAccessToken } from "../utils/auth";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.toString().trim() || "http://localhost:18080";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000,
});

apiClient.interceptors.request.use((config) => {
  try {
    const requestUrl = (config.url ?? "").toString();
    const isAuthLogin = requestUrl.includes("/api/v2/auth/login");
    const tokenFromLocalStorage = localStorage.getItem("token");
    const token = tokenFromLocalStorage || getAccessToken();
    if (token && !isAuthLogin) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // ignore
  }

  return config;
});

export function getApiErrorPayload(error: unknown): ApiErrorPayload | null {
  if (!(error instanceof AxiosError)) {
    return null;
  }

  const payload = error.response?.data as Partial<ApiErrorPayload> | undefined;
  if (!payload || typeof payload !== "object") {
    return null;
  }

  if (typeof payload.code !== "string" || typeof payload.message !== "string") {
    return null;
  }

  return {
    code: payload.code,
    message: payload.message,
    timestamp:
      typeof payload.timestamp === "string" ? payload.timestamp : new Date().toISOString(),
  };
}

export function getErrorMessage(error: unknown, fallback: string): string {
  const payload = getApiErrorPayload(error);
  return payload?.message ?? fallback;
}
