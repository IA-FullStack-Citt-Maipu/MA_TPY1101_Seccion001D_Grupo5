import { apiClient } from "./apiClient";
import type { CreateLoanPayload, LoanSummary } from "../types/loan";

export async function createLoan(payload: CreateLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>("/api/v2/loans", payload);
  return response.data;
}

export async function fetchLoans(): Promise<LoanSummary[]> {
  const response = await apiClient.get<LoanSummary[]>("/api/v2/loans");
  return response.data;
}

export async function fetchLoanByUuid(loanUuid: string): Promise<LoanSummary | null> {
  const loans = await fetchLoans();
  return loans.find((loan) => loan.uuid === loanUuid) ?? null;
}
