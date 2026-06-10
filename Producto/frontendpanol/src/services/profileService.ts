import { apiClient } from "./apiClient";
import { normalizeUserRole, type SessionUserSummary } from "../utils/auth";

interface BackendProfileResponse {
  id: string;
  name: string;
  email?: string | null;
  role?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
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
