export interface LoanItem {
  implement_uuid: string;
  implement_name: string;
  requested_quantity: number;
  reserved_quantity: number;
  delivered_quantity: number;
}

export interface LoanRoomSummary {
  uuid: string;
  name: string;
}

export interface LoanSubjectSummary {
  uuid: string;
  name: string;
}

export interface LoanSummary {
  uuid: string;
  requester_uuid: string;
  status: LoanStatus;
  scheduled_at: string;
  expected_return_at: string;
  created_at: string;
  room: LoanRoomSummary | null;
  subject: LoanSubjectSummary | null;
  items: LoanItem[];
}

export interface LoanPage {
  items: LoanSummary[];
  page: number;
  size: number;
  total_items: number;
  total_pages: number;
  has_next: boolean;
  has_previous: boolean;
}

export interface CreateLoanItemPayload {
  implement_uuid: string;
  requested_quantity: number;
}

export interface CreateLoanPayload {
  room_uuid: string;
  subject_uuid?: string | null;
  scheduled_at: string;
  expected_return_at?: string | null;
  items: CreateLoanItemPayload[];
}

export interface DeliverLoanItemPayload {
  implement_uuid: string;
  quantity: number;
  asset_codes?: string[];
}

export interface DeliverLoanPayload {
  items: DeliverLoanItemPayload[];
}

export type LoanStatus =
  | "pending"
  | "approved"
  | "prepared"
  | "delivered"
  | "completed"
  | "rejected"
  | "cancelled"
  | "expired"
  | "overdue";

export interface LoanStateDates {
  approved_at: string | null;
  prepared_at: string | null;
  delivered_at: string | null;
  completed_at: string | null;
  rejected_at: string | null;
  cancelled_at: string | null;
  expired_at: string | null;
  overdue_at: string | null;
}

export interface LoanStatusTimelineEntry {
  history_id: number;
  from_status: LoanStatus | null;
  to_status: LoanStatus;
  actor_user_id: number;
  actor_name: string | null;
  actor_email: string | null;
  notes: string | null;
  changed_at: string;
}
