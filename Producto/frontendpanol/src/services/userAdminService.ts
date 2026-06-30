import { apiClient } from "./apiClient";

export type AdminRole = "DIRECTOR" | "COORDINADOR" | "DOCENTE";

export interface UserAdminSummary {
  uuid: string;
  name: string;
  rut: string;
  email: string | null;
  role: AdminRole;
  active: boolean;
  createdAt: string;
}

export interface CreateUserPayload {
  name: string;
  rut: string;
  email: string;
  role: "COORDINADOR" | "DOCENTE";
  password: string;
}

export interface UpdateUserPayload {
  name: string;
  rut: string;
  email: string;
}

export async function listUsers(): Promise<UserAdminSummary[]> {
  const { data } = await apiClient.get<UserAdminSummary[]>("/api/v2/users");
  return data;
}

export async function createUser(payload: CreateUserPayload): Promise<void> {
  await apiClient.post("/api/v2/users", payload);
}

export async function changeUserRole(userUuid: string, role: AdminRole): Promise<void> {
  await apiClient.put(`/api/v2/users/${userUuid}/role`, { role });
}

export async function setUserActive(userUuid: string, active: boolean): Promise<void> {
  await apiClient.patch(`/api/v2/users/${userUuid}/active`, null, { params: { active } });
}

export async function updateUser(userUuid: string, payload: UpdateUserPayload): Promise<void> {
  await apiClient.put(`/api/v2/users/${userUuid}`, payload);
}

export async function deleteUser(userUuid: string): Promise<void> {
  await apiClient.delete(`/api/v2/users/${userUuid}`);
}
