import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { clearSession } from "../utils/auth";
import type { ApiErrorPayload } from "../types/api";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.toString().trim() || "http://localhost:18080";

type AuthAwareRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
  skipAuthRefresh?: boolean;
};

let refreshInFlight: Promise<void> | null = null;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000,
  withCredentials: true,
});

async function refreshSession(): Promise<void> {
  await apiClient.post(
    "/api/v2/auth/refresh",
    undefined,
    { skipAuthRefresh: true } as AuthAwareRequestConfig,
  );
}

function handleAuthFailure() {
  clearSession();
  if (typeof window !== "undefined" && window.location.hash !== "#/login") {
    window.location.hash = "#/login";
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!(error instanceof AxiosError)) {
      return Promise.reject(error);
    }

    const originalRequest = error.config as AuthAwareRequestConfig | undefined;
    const requestUrl = (originalRequest?.url ?? "").toString();
    const status = error.response?.status;
    const isAuthRefresh = requestUrl.includes("/api/v2/auth/refresh");
    const isAuthLogin = requestUrl.includes("/api/v2/auth/login");
    const isAuthLogout = requestUrl.includes("/api/v2/auth/logout");
    const shouldSkipRefresh =
      originalRequest?.skipAuthRefresh === true ||
      originalRequest?._retry === true ||
      isAuthRefresh ||
      isAuthLogin ||
      isAuthLogout;

    if (status !== 401 || !originalRequest || shouldSkipRefresh) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      if (refreshInFlight == null) {
        refreshInFlight = refreshSession().finally(() => {
          refreshInFlight = null;
        });
      }
      await refreshInFlight;
      return apiClient.request(originalRequest);
    } catch (refreshError) {
      handleAuthFailure();
      return Promise.reject(refreshError);
    }
  },
);

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
