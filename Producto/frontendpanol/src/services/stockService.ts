import { apiClient } from "./apiClient";
import type {
  IndividualStatusSummary,
  IndividualUpdatePayload,
  StockDetail,
  StockEntryPayload,
  StockMovementPayload,
} from "../types/stock";

export async function fetchImplementStock(implementUuid: string): Promise<StockDetail> {
  const response = await apiClient.get<StockDetail>(`/api/v2/implements/${implementUuid}/stock`);
  return response.data;
}

export async function fetchInventoryDashboardIndividualSummary(): Promise<IndividualStatusSummary> {
  const response = await apiClient.get<IndividualStatusSummary>("/api/v2/inventory/dashboard/individual-summary");
  return response.data;
}

export async function addStockEntry(
  implementUuid: string,
  payload: StockEntryPayload,
): Promise<StockDetail> {
  const response = await apiClient.post<StockDetail>(`/api/v2/implements/${implementUuid}/stock/entries`, payload);
  return response.data;
}

export async function applyStockMovement(
  implementUuid: string,
  payload: StockMovementPayload,
): Promise<StockDetail> {
  const response = await apiClient.post<StockDetail>(`/api/v2/implements/${implementUuid}/stock/movements`, payload);
  return response.data;
}

export async function updateIndividualState(
  implementUuid: string,
  individualUuid: string,
  payload: IndividualUpdatePayload,
): Promise<StockDetail> {
  const response = await apiClient.put<StockDetail>(
    `/api/v2/implements/${implementUuid}/stock/individuals/${individualUuid}`,
    payload,
  );
  return response.data;
}
