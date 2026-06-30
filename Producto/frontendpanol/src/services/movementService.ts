import { apiClient } from "./apiClient";
import type { InventoryMovementDetail, InventoryMovementHistoryItem, InventoryMovementHistoryPage } from "../types/implement";
import type { StockMovementType } from "../types/stock";

export type ManualMovementType = StockMovementType;

export interface RegisterMovementPayload {
  action: ManualMovementType;
  quantity: number;
  notes?: string | null;
}

interface BackendInventoryMovementHistoryPage {
  items?: InventoryMovementHistoryItem[];
  page?: number;
  size?: number;
  total_items?: number;
  total_pages?: number;
  has_next?: boolean;
  has_previous?: boolean;
}

interface BackendInventoryMovementDashboardSummary {
  total_movements?: number;
  top_users?: Array<{
    name?: string | null;
    role?: string | null;
    movement_count?: number;
  }>;
  top_implements?: Array<{
    implement_uuid?: string | null;
    implement_name?: string | null;
    movement_count?: number;
  }>;
}

export interface FetchInventoryMovementHistoryQuery {
  page?: number;
  size?: number;
  search?: string;
  action?: StockMovementType | "";
  from?: string;
  to?: string;
}

export interface FetchInventoryMovementHistoryOptions {
  signal?: AbortSignal;
}

export interface InventoryMovementDashboardSummary {
  totalMovements: number;
  topUsers: Array<{
    name: string;
    role: string | null;
    movementCount: number;
  }>;
  topImplements: Array<{
    implementUuid: string | null;
    implementName: string | null;
    movementCount: number;
  }>;
}

function normalizeMovementRow(row: InventoryMovementDetail): InventoryMovementDetail {
  return {
    ...row,
    uuid: row.uuid ?? row.id ?? `${row.implement_uuid ?? "movement"}-${row.timestamp}-${row.action}`,
  };
}

export async function registerManualMovement(
  implementUuid: string,
  payload: RegisterMovementPayload,
): Promise<InventoryMovementDetail> {
  const response = await apiClient.post<InventoryMovementDetail>(
    `/api/v2/implements/${implementUuid}/movements`,
    payload,
  );
  return normalizeMovementRow(response.data);
}

export async function fetchInventoryMovements(limit?: number): Promise<InventoryMovementDetail[]> {
  const response = await apiClient.get<InventoryMovementDetail[]>("/api/v2/implements/movements", {
    params: {
      limit: limit && limit > 0 ? limit : undefined,
    },
  });
  return Array.isArray(response.data) ? response.data.map(normalizeMovementRow) : [];
}

export async function fetchInventoryMovementHistoryPage(
  query: FetchInventoryMovementHistoryQuery = {},
  options: FetchInventoryMovementHistoryOptions = {},
): Promise<InventoryMovementHistoryPage> {
  const response = await apiClient.get<BackendInventoryMovementHistoryPage>("/api/v2/implements/movements/history", {
    signal: options.signal,
    params: {
      page: query.page ?? 1,
      size: query.size ?? 15,
      search: query.search?.trim() ? query.search.trim() : undefined,
      action: query.action?.trim() ? query.action : undefined,
      from: query.from?.trim() ? query.from.trim() : undefined,
      to: query.to?.trim() ? query.to.trim() : undefined,
    },
  });

  const data = response.data ?? {};
  return {
    items: Array.isArray(data.items) ? data.items : [],
    page: typeof data.page === "number" ? data.page : 1,
    size: typeof data.size === "number" ? data.size : 15,
    totalItems: typeof data.total_items === "number" ? data.total_items : 0,
    totalPages: typeof data.total_pages === "number" ? data.total_pages : 1,
    hasNext: data.has_next === true,
    hasPrevious: data.has_previous === true,
  };
}

export async function fetchInventoryMovementDashboardSummary(): Promise<InventoryMovementDashboardSummary> {
  const response = await apiClient.get<BackendInventoryMovementDashboardSummary>("/api/v2/implements/movements/summary");
  const data = response.data ?? {};

  return {
    totalMovements: typeof data.total_movements === "number" ? data.total_movements : 0,
    topUsers: Array.isArray(data.top_users)
      ? data.top_users.map((item) => ({
          name: item?.name?.trim() || "Usuario no identificado",
          role: item?.role?.trim() || null,
          movementCount: typeof item?.movement_count === "number" ? item.movement_count : 0,
        }))
      : [],
    topImplements: Array.isArray(data.top_implements)
      ? data.top_implements.map((item) => ({
          implementUuid: item?.implement_uuid?.trim() || null,
          implementName: item?.implement_name?.trim() || "Implemento sin nombre",
          movementCount: typeof item?.movement_count === "number" ? item.movement_count : 0,
        }))
      : [],
  };
}
