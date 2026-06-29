import { apiClient } from "./apiClient";
import { AxiosError } from "axios";
import type {
  CreateLoanPayload,
  CompleteLoanPayload,
  DeliverLoanPayload,
  LoanPage,
  LoanReturnContext,
  PrepareLoanPayload,
  LoanStateDates,
  LoanStatusTimelineEntry,
  LoanSummary,
  ReturnLoanPayload,
} from "../types/loan";

export interface FetchLoansQuery {
  page?: number;
  size?: number;
  mine?: boolean;
  from?: string;
  to?: string;
}

export interface CancelLoanPayload {
  notes?: string | null;
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
      from: query.from,
      to: query.to,
    },
  });
  return response.data;
}

export async function fetchAllLoansPages(query: FetchLoansQuery = {}): Promise<LoanSummary[]> {
  const firstPage = await fetchLoansPage({
    ...query,
    page: query.page ?? 1,
    size: query.size ?? 50,
  });
  const merged: LoanSummary[] = [...firstPage.items];
  for (let page = firstPage.page + 1; page <= firstPage.total_pages; page += 1) {
    const nextPage = await fetchLoansPage({
      ...query,
      page,
      size: firstPage.size,
    });
    merged.push(...nextPage.items);
  }
  return merged;
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

export async function prepareLoan(loanUuid: string, payload: PrepareLoanPayload = {}): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>(`/api/v2/loans/${loanUuid}/prepare`, payload);
  return response.data;
}

export async function deliverLoan(loanUuid: string, payload: DeliverLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>(`/api/v2/loans/${loanUuid}/delivery`, payload);
  return response.data;
}

export async function completeLoan(loanUuid: string, payload: CompleteLoanPayload = {}): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>(`/api/v2/loans/${loanUuid}/complete`, payload);
  return response.data;
}

export async function returnLoan(loanUuid: string, payload: ReturnLoanPayload): Promise<LoanSummary> {
  const response = await apiClient.post<LoanSummary>(`/api/v2/loans/${loanUuid}/return`, payload);
  return response.data;
}

export async function cancelLoan(loanUuid: string, payload: CancelLoanPayload = {}): Promise<LoanSummary> {
  const response = await apiClient.patch<LoanSummary>(`/api/v2/loans/${loanUuid}/cancel`, payload);
  return response.data;
}

export async function fetchLoanStateDates(loanUuid: string): Promise<LoanStateDates> {
  const response = await apiClient.get<LoanStateDates>(`/api/v2/loans/${loanUuid}/state-dates`);
  return response.data;
}

export async function fetchLoanReturnContext(loanUuid: string): Promise<LoanReturnContext> {
  const response = await apiClient.get<LoanReturnContext>(`/api/v2/loans/${loanUuid}/return-context`);
  return response.data;
}

export async function fetchLoanStatusTimeline(loanUuid: string): Promise<LoanStatusTimelineEntry[]> {
  const response = await apiClient.get<LoanStatusTimelineEntry[]>(`/api/v2/loans/${loanUuid}/status-timeline`);
  return response.data;
}
