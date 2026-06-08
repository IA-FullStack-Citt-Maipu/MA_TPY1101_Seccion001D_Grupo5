import type { LoanSummary } from "../types/loan";

export function canStartDelivery(loan: Pick<LoanSummary, "status">): boolean {
  return loan.status === "approved" || loan.status === "prepared";
}

