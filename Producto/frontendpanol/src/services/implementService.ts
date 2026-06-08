import { apiClient } from "./apiClient";
import type {
  ImplementCreatePayload,
  ImplementDetail,
  ImplementFilters,
  ImplementSummary,
  ImplementUpdatePayload,
} from "../types/implement";

export async function fetchImplements(filters?: ImplementFilters): Promise<ImplementSummary[]> {
  const stockStatus = filters?.stockStatus;
  const response = await apiClient.get<ImplementSummary[]>("/api/v2/implements", {
    params: {
      name: filters?.name || undefined,
      categoryUuid: filters?.categoryUuid ?? undefined,
      scheduledAt: filters?.scheduledAt ?? undefined,
      expectedReturnAt: filters?.expectedReturnAt ?? undefined,
      excludeLoanUuid: filters?.excludeLoanUuid ?? undefined,
      // Enviar stockStatus solo si no es "all"; el backend devuelve todos por defecto sin el param
      stockStatus: stockStatus && stockStatus !== "all" ? stockStatus : undefined,
    },
  });
  return response.data;
}

export async function createImplement(payload: ImplementCreatePayload): Promise<ImplementDetail> {
  const response = await apiClient.post<ImplementDetail>("/api/v2/implements", payload);
  return response.data;
}

export async function updateImplement(
  implementUuid: string,
  payload: ImplementUpdatePayload,
): Promise<ImplementDetail> {
  const response = await apiClient.put<ImplementDetail>(`/api/v2/implements/${implementUuid}`, payload);
  return response.data;
}

export async function fetchImplementById(implementUuid: string): Promise<ImplementDetail> {
  const response = await apiClient.get<ImplementDetail>(`/api/v2/implements/${implementUuid}`);
  return response.data;
}
