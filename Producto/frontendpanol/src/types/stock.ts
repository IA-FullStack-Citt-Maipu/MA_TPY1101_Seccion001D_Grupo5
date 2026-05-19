export type StockMovementType =
  | "STOCK_IN"
  | "STOCK_OUT"
  | "LOAN_DELIVERY"
  | "LOAN_RETURN"
  | "DAMAGE_REPORT"
  | "MANUAL_ADJUSTMENT";

export interface StockCounters {
  total_stock: number;
  min_stock: number;
  available: number;
  reserved: number;
  loaned: number;
  damaged: number;
}

export interface IndividualItem {
  uuid: string;
  asset_code: string;
  status: "available" | "loaned" | "maintenance" | "damaged";
  condition: "good" | "fair" | "poor";
  notes: string | null;
  current_location_uuid: string | null;
  active: boolean;
}

export interface StockDetail {
  implement_uuid: string;
  item_type: "fungible" | "no_fungible" | null;
  stock: StockCounters;
  individuals: IndividualItem[];
}

export interface StockEntryPayload {
  quantity: number;
  asset_codes?: string[];
}

export interface StockMovementPayload {
  movement_type: StockMovementType;
  quantity?: number;
  individual_uuids?: string[];
  condition?: IndividualItem["condition"];
}

export interface IndividualUpdatePayload {
  status?: IndividualItem["status"];
  condition?: IndividualItem["condition"];
  notes?: string | null;
  current_location_uuid?: string | null;
  active?: boolean;
}
