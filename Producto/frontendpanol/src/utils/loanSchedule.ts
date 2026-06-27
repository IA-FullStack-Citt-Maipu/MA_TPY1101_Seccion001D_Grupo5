import type { LoanSummary } from "../types/loan";

function parseScheduledAt(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

export function canStartPreparation(loan: Pick<LoanSummary, "status" | "scheduled_at">): boolean {
  if (loan.status !== "approved") {
    return false;
  }

  const scheduledAt = parseScheduledAt(loan.scheduled_at);
  if (!scheduledAt) {
    return false;
  }

  return Date.now() >= scheduledAt.getTime() - 60 * 60 * 1000;
}

export function canStartDelivery(loan: Pick<LoanSummary, "status" | "scheduled_at">): boolean {
  if (loan.status !== "prepared") {
    return false;
  }

  const scheduledAt = parseScheduledAt(loan.scheduled_at);
  if (!scheduledAt) {
    return false;
  }

  return Date.now() >= scheduledAt.getTime();
}

