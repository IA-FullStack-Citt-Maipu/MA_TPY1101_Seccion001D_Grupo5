import { apiClient } from "./apiClient";
import type { InventoryMovementDetail } from "../types/implement";

export type ManualMovementType =
  | "STOCK_IN"
  | "STOCK_OUT"
  | "LOAN_DELIVERY"
  | "LOAN_RETURN"
  | "DAMAGE_REPORT"
  | "MANUAL_ADJUSTMENT";

export interface RegisterMovementPayload {
  action: ManualMovementType;
  quantity: number;
  notes?: string | null;
}

export async function registerManualMovement(
  implementUuid: string,
  payload: RegisterMovementPayload,
): Promise<InventoryMovementDetail> {
  const response = await apiClient.post<InventoryMovementDetail>(
    `/api/v2/implements/${implementUuid}/movements`,
    payload,
  );
  return response.data;
}

export async function fetchInventoryMovements(): Promise<InventoryMovementDetail[]> {
  const response = await apiClient.get<InventoryMovementDetail[]>("/api/v2/implements/movements");
  return response.data;
}
