export interface InventoryMovementDetail {
  id?: string;
  uuid: string;
  action: string;
  quantity: number;
  timestamp: string;
  notes: string | null;
  implement_uuid: string | null;
  performed_by: string | null;
}

export interface InventoryMovementHistoryItem {
  id: string;
  action: string;
  quantity: number;
  timestamp: string;
  notes: string | null;
  implement_uuid: string | null;
  implement_name: string | null;
  barcode: string | null;
  item_type: "consumable" | "reusable" | "individual" | null;
  category_name: string | null;
  location_name: string | null;
  performed_by_uuid: string | null;
  performed_by: string | null;
  performed_by_role: string | null;
}

export interface InventoryMovementHistoryPage {
  items: InventoryMovementHistoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ImplementCreatePayload {
  name: string;
  categoryUuid: string;
  item_type: "consumable" | "reusable" | "individual";
  locationUuid: string;
  description: string | null;
  barcode: string | null;
  img_url: string | null;
  min_stock: number;
  observations: string | null;
}

export interface ImplementUpdatePayload {
  name: string;
  categoryUuid: string;
  locationUuid: string;
  item_type: "consumable" | "reusable" | "individual";
  description: string | null;
  barcode: string | null;
  img_url: string | null;
  min_stock: number;
  observations: string | null;
}

export interface ImplementSummary {
  uuid: string;
  name: string;
  description?: string | null;
  barcode?: string | null;
  individualAssetCodes?: string[] | null;
  individual_asset_codes?: string[] | null;
  imgUrl?: string | null;
  img_url?: string | null;
  active?: boolean;
  available?: boolean;
  item_type?: "consumable" | "reusable" | "individual" | null;
  category: {
    uuid: string;
    name: string;
    active: boolean;
  } | null;
  location: {
    uuid: string;
    name: string;
    description: string | null;
  } | null;
  stock?: {
    total_stock?: number | null;
    min_stock?: number | null;
    available?: number | null;
    reserved?: number | null;
    loaned?: number | null;
    damaged?: number | null;
    available_display?: string | null;
  } | null;
}

export type ImplementStockFilterStatus =
  | "all"
  | "available"
  | "reserved"
  | "loaned"
  | "damaged";

export const STOCK_STATUS_LABELS: Record<ImplementStockFilterStatus, string> = {
  all: "Todos los estados",
  available: "Disponible",
  reserved: "Reservado",
  loaned: "Prestado",
  damaged: "Dañado",
};

export interface ImplementFilters {
  name?: string;
  categoryUuid?: string | null;
  stockStatus?: ImplementStockFilterStatus;
  scheduledAt?: string | null;
  expectedReturnAt?: string | null;
  excludeLoanUuid?: string | null;
}

export interface ImplementDetail {
  uuid: string;
  name: string;
  description: string | null;
  item_type: "consumable" | "reusable" | "individual" | null;
  display_location?: string | null;
  category: {
    uuid: string;
    name: string;
    active: boolean;
  } | null;
  location: {
    uuid: string;
    name: string;
    description: string | null;
  } | null;
  category_uuid: string | null;
  location_uuid: string | null;
  min_stock: number | null;
  barcode: string | null;
  img_url: string | null;
  observations: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  stock?: {
    available?: number | null;
    reserved?: number | null;
    loaned?: number | null;
    damaged?: number | null;
    total_stock?: number | null;
    available_display?: string | null;
  } | null;
  recent_movements?: InventoryMovementDetail[];
}
