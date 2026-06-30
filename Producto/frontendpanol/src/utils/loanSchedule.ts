import type { LoanSummary } from "../types/loan";

export function canStartPreparation(loan: Pick<LoanSummary, "status" | "scheduled_at">): boolean {
  return loan.status === "approved";
}

export function canStartDelivery(loan: Pick<LoanSummary, "status" | "scheduled_at">): boolean {
  return loan.status === "prepared";
}

