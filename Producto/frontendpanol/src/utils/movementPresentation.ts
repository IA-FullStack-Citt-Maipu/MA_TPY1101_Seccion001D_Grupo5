import type { StockMovementType } from "../types/stock";

export const MOVEMENT_ACTION_LABELS: Record<StockMovementType, string> = {
  stock_in: "Ingreso de stock",
  stock_out: "Salida de stock",
  loan_delivery: "Entrega de prestamo",
  loan_return: "Devolucion de prestamo",
  damage_report: "Reporte de dano",
  manual_adjustment: "Ajuste manual",
  consumption: "Consumo",
  discard: "Descarte",
  loss: "Perdida",
};

export const MOVEMENT_ACTION_OPTIONS: Array<{ value: StockMovementType; label: string }> = [
  { value: "stock_in", label: MOVEMENT_ACTION_LABELS.stock_in },
  { value: "stock_out", label: MOVEMENT_ACTION_LABELS.stock_out },
  { value: "loan_delivery", label: MOVEMENT_ACTION_LABELS.loan_delivery },
  { value: "loan_return", label: MOVEMENT_ACTION_LABELS.loan_return },
  { value: "damage_report", label: MOVEMENT_ACTION_LABELS.damage_report },
  { value: "manual_adjustment", label: MOVEMENT_ACTION_LABELS.manual_adjustment },
  { value: "consumption", label: MOVEMENT_ACTION_LABELS.consumption },
  { value: "discard", label: MOVEMENT_ACTION_LABELS.discard },
  { value: "loss", label: MOVEMENT_ACTION_LABELS.loss },
];

export function getMovementActionLabel(action: string): string {
  return MOVEMENT_ACTION_LABELS[action as StockMovementType] ?? action;
}

export function getMovementBadgeTone(action: string): "active" | "inactive" | "warn" {
  if (action === "stock_in" || action === "loan_return") {
    return "active";
  }
  if (action === "manual_adjustment" || action === "damage_report" || action === "discard" || action === "loss") {
    return "warn";
  }
  return "inactive";
}

export function formatMovementDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "America/Santiago",
    hour12: false,
  }).format(parsed);
}
