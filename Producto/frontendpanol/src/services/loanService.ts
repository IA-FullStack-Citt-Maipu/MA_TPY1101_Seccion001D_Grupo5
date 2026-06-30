import { apiClient } from "./apiClient";
import { AxiosError } from "axios";
import type {
  CreateLoanPayload,
  CompleteLoanPayload,
  DeliverLoanPayload,
  LoanPage,
  LoanRequesterHistoryItem,
  LoanRequesterHistoryPage,
  LoanRequesterPage,
  LoanRequesterSummary,
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

interface BackendLoanRequesterItem {
  requester_uuid?: string | null;
  requester_name?: string | null;
  requester_email?: string | null;
  requester_rut?: string | null;
  last_loan_at?: string | null;
  latest_loan_uuid?: string | null;
  latest_status?: string | null;
  latest_room_name?: string | null;
  latest_subject_name?: string | null;
  total_loans?: number;
  active_loans?: number;
}

interface BackendLoanRequesterPage {
  items?: BackendLoanRequesterItem[];
  page?: number;
  size?: number;
  total_items?: number;
  total_pages?: number;
  has_next?: boolean;
  has_previous?: boolean;
}

interface BackendLoanRequesterHistoryItem {
  uuid?: string | null;
  status?: LoanSummary["status"] | null;
  scheduled_at?: string | null;
  expected_return_at?: string | null;
  created_at?: string | null;
  completed_at?: string | null;
  approved_at?: string | null;
  prepared_at?: string | null;
  delivered_at?: string | null;
  rejected_at?: string | null;
  cancelled_at?: string | null;
  expired_at?: string | null;
  overdue_at?: string | null;
  room?: LoanSummary["room"] | null;
  subject?: LoanSummary["subject"] | null;
  items?: LoanSummary["items"] | null;
}

interface BackendLoanRequesterHistoryPage {
  requester?: BackendLoanRequesterItem | null;
  items?: BackendLoanRequesterHistoryItem[];
  page?: number;
  size?: number;
  total_items?: number;
  total_pages?: number;
  has_next?: boolean;
  has_previous?: boolean;
}

export interface CancelLoanPayload {
  notes?: string | null;
}

export interface FetchLoanRequestersQuery {
  page?: number;
  size?: number;
  search?: string;
}

export interface FetchLoanRequesterHistoryQuery {
  page?: number;
  size?: number;
}

export interface RequestOptions {
  signal?: AbortSignal;
}

function normalizeLoanRequesterSummary(row: BackendLoanRequesterItem | null | undefined): LoanRequesterSummary {
  return {
    requesterUuid: row?.requester_uuid?.trim() || "",
    requesterName: row?.requester_name?.trim() || "Docente sin nombre",
    requesterEmail: row?.requester_email?.trim() || null,
    requesterRut: row?.requester_rut?.trim() || null,
    lastLoanAt: row?.last_loan_at?.trim() || null,
    latestLoanUuid: row?.latest_loan_uuid?.trim() || null,
    latestStatus: (row?.latest_status as LoanSummary["status"] | null | undefined) ?? null,
    latestRoomName: row?.latest_room_name?.trim() || null,
    latestSubjectName: row?.latest_subject_name?.trim() || null,
    totalLoans: typeof row?.total_loans === "number" ? row.total_loans : 0,
    activeLoans: typeof row?.active_loans === "number" ? row.active_loans : 0,
  };
}

function normalizeLoanRequesterHistoryItem(row: BackendLoanRequesterHistoryItem): LoanRequesterHistoryItem {
  return {
    uuid: row.uuid?.trim() || "",
    status: (row.status ?? "approved") as LoanRequesterHistoryItem["status"],
    scheduledAt: row.scheduled_at?.trim() || "",
    expectedReturnAt: row.expected_return_at?.trim() || null,
    createdAt: row.created_at?.trim() || "",
    completedAt: row.completed_at?.trim() || null,
    approvedAt: row.approved_at?.trim() || null,
    preparedAt: row.prepared_at?.trim() || null,
    deliveredAt: row.delivered_at?.trim() || null,
    rejectedAt: row.rejected_at?.trim() || null,
    cancelledAt: row.cancelled_at?.trim() || null,
    expiredAt: row.expired_at?.trim() || null,
    overdueAt: row.overdue_at?.trim() || null,
    room: row.room ?? null,
    subject: row.subject ?? null,
    items: Array.isArray(row.items) ? row.items : [],
  };
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

export async function fetchLoanRequestersPage(
  query: FetchLoanRequestersQuery = {},
  options: RequestOptions = {},
): Promise<LoanRequesterPage> {
  const response = await apiClient.get<BackendLoanRequesterPage>("/api/v2/loans/requesters", {
    signal: options.signal,
    params: {
      page: query.page ?? 1,
      size: query.size ?? 15,
      search: query.search?.trim() ? query.search.trim() : undefined,
    },
  });

  const data = response.data ?? {};
  return {
    items: Array.isArray(data.items) ? data.items.map(normalizeLoanRequesterSummary) : [],
    page: typeof data.page === "number" ? data.page : 1,
    size: typeof data.size === "number" ? data.size : 15,
    totalItems: typeof data.total_items === "number" ? data.total_items : 0,
    totalPages: typeof data.total_pages === "number" ? data.total_pages : 1,
    hasNext: data.has_next === true,
    hasPrevious: data.has_previous === true,
  };
}

export async function fetchLoanRequesterHistoryPage(
  requesterUuid: string,
  query: FetchLoanRequesterHistoryQuery = {},
  options: RequestOptions = {},
): Promise<LoanRequesterHistoryPage> {
  const response = await apiClient.get<BackendLoanRequesterHistoryPage>(`/api/v2/loans/requesters/${requesterUuid}/history`, {
    signal: options.signal,
    params: {
      page: query.page ?? 1,
      size: query.size ?? 6,
    },
  });

  const data = response.data ?? {};
  return {
    requester: data.requester ? normalizeLoanRequesterSummary(data.requester) : null,
    items: Array.isArray(data.items) ? data.items.map(normalizeLoanRequesterHistoryItem) : [],
    page: typeof data.page === "number" ? data.page : 1,
    size: typeof data.size === "number" ? data.size : 6,
    totalItems: typeof data.total_items === "number" ? data.total_items : 0,
    totalPages: typeof data.total_pages === "number" ? data.total_pages : 1,
    hasNext: data.has_next === true,
    hasPrevious: data.has_previous === true,
  };
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
