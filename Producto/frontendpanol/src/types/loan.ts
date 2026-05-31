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
  status: string;
  scheduled_at: string;
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
