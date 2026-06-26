import type { LoanStatus } from "../types/loan";

const REQUESTER_CANCELLABLE_LOAN_STATUSES: readonly LoanStatus[] = [
  "pending",
  "approved",
  "prepared",
];

export function canRequesterCancelLoan(status: LoanStatus): boolean {
  return REQUESTER_CANCELLABLE_LOAN_STATUSES.includes(status);
}
