export interface LoanItem {
  implement_uuid: string;
  requested_quantity: number;
  reserved_quantity: number;
  delivered_quantity: number;
}

export interface LoanSummary {
  uuid: string;
  requester_uuid: string;
  room_uuid: string | null;
  subject_uuid: string | null;
  status: string;
  // OffsetDateTime serializado por backend como string ISO-8601 con zona horaria.
  scheduled_at: string;
  due_date: string | null;
  created_at: string;
  items: LoanItem[];
}

export interface CreateLoanItemPayload {
  implement_uuid: string;
  requested_quantity: number;
}

export interface CreateLoanPayload {
  room_uuid?: string | null;
  subject_uuid?: string | null;
  // Enviar siempre formato ISO-8601 con offset (ej: 2026-05-17T10:30:00-04:00).
  scheduled_at: string;
  due_date?: string | null;
  items: CreateLoanItemPayload[];
}
