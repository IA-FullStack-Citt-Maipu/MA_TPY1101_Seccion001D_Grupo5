import { apiClient } from "./apiClient";
import { AxiosError } from "axios";
import type { CreateLoanPayload, LoanPage, LoanSummary } from "../types/loan";

export interface FetchLoansQuery {
  page?: number;
  size?: number;
  mine?: boolean;
}

export interface ReviewLoanPayload {
  decision: "APPROVE" | "REJECT";
  review_notes?: string | null;
  rejection_reason?: string | null;
}

export async function createLoan(payload: CreateLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>("/api/v2/loans", payload);
  return response.data;
}

export async function updateLoan(loanUuid: string, payload: CreateLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.patch<LoanSummary>(`/api/v2/loans/${loanUuid}`, payload);
  return response.data;
}

export async function fetchLoansPage(query: FetchLoansQuery = {}): Promise<LoanPage> {
  const response = await apiClient.get<LoanPage>("/api/v2/loans", {
    params: {
      page: query.page ?? 1,
      size: query.size ?? 20,
      mine: query.mine ?? true,
    },
  });
  return response.data;
}

export async function fetchLoans(): Promise<LoanSummary[]> {
  const page = await fetchLoansPage({ page: 1, size: 100, mine: true });
  return page.items;
}

export async function fetchLoanByUuid(loanUuid: string): Promise<LoanSummary | null> {
  try {
    const response = await apiClient.get<LoanSummary>(`/api/v2/loans/${loanUuid}`);
    return response.data ?? null;
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      return null;
    }
    throw error;
  }
}

export async function reviewLoan(loanUuid: string, payload: ReviewLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.patch<LoanSummary>(`/api/v2/loans/${loanUuid}/review`, payload);
  return response.data;
}
