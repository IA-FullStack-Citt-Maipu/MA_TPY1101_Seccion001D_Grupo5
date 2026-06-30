export type StockMovementType =
  | "stock_in"
  | "stock_out"
  | "loan_delivery"
  | "loan_return"
  | "damage_report"
  | "manual_adjustment"
  | "consumption"
  | "discard"
  | "loss";

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
  status: "available" | "loaned" | "maintenance" | "damaged" | "blocked" | "retired";
  condition: "good" | "damaged_repairable" | "damaged_no_diagnosis" | "irreparable";
  notes: string | null;
  current_location_uuid: string | null;
  active: boolean;
  remaining_life: number | null;
  asset_code_reprint_required: boolean;
}

export interface StockDetail {
  implement_uuid: string;
  item_type: "consumable" | "reusable" | "individual" | null;
  stock: StockCounters;
  individuals: IndividualItem[];
}

export interface IndividualStatusSummary {
  available: number;
  loaned: number;
  maintenance: number;
  damaged: number;
  blocked: number;
  retired: number;
  total: number;
}

export interface StockEntryIndividualPayload {
  asset_code: string;
  status?: IndividualItem["status"];
  condition?: IndividualItem["condition"];
  current_location_uuid?: string | null;
  remaining_life?: number | null;
  asset_code_reprint_required?: boolean;
}

export interface StockEntryPayload {
  quantity: number;
  asset_codes?: string[];
  status?: IndividualItem["status"];
  condition?: IndividualItem["condition"];
  notes?: string | null;
  current_location_uuid?: string | null;
  remaining_life?: number | null;
  asset_code_reprint_required?: boolean;
  individual_entries?: StockEntryIndividualPayload[];
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
  remaining_life?: number | null;
  asset_code_reprint_required?: boolean;
}
