import { apiClient } from "./apiClient";
import type { InventoryMovementDetail } from "../types/implement";

export type ManualMovementType =
  | "stock_in"
  | "stock_out"
  | "loan_delivery"
  | "loan_return"
  | "damage_report"
  | "manual_adjustment"
  | "consumption"
  | "discard"
  | "loss";

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
