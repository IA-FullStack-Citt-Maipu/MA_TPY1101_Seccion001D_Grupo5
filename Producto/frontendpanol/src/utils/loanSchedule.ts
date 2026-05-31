import type { LoanSummary } from "../types/loan";

export function parseLoanSchedule(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

export function getDeliveryWindowOpenAt(schedule: string): Date | null {
  const date = parseLoanSchedule(schedule);
  if (!date) {
    return null;
  }
  return new Date(date.getTime() - 10 * 60 * 1000);
}

export function canStartDelivery(loan: Pick<LoanSummary, "status" | "scheduled_at">, now = Date.now()): boolean {
  if (loan.status !== "approved") {
    return false;
  }
  const openAt = getDeliveryWindowOpenAt(loan.scheduled_at);
  if (!openAt) {
    return false;
  }
  return now >= openAt.getTime();
}

