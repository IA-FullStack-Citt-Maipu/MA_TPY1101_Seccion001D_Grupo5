import type { LoanSummary } from "../types/loan";

const LAST_CREATED_LOAN_KEY = "loan.last_created_summary";

interface LastCreatedLoanSnapshot {
  loan: LoanSummary;
  created_at: string;
}

export function saveLastCreatedLoan(loan: LoanSummary) {
  const snapshot: LastCreatedLoanSnapshot = {
    loan,
    created_at: new Date().toISOString(),
  };
  sessionStorage.setItem(LAST_CREATED_LOAN_KEY, JSON.stringify(snapshot));
}

export function loadLastCreatedLoan(loanUuid?: string): LoanSummary | null {
  const raw = sessionStorage.getItem(LAST_CREATED_LOAN_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<LastCreatedLoanSnapshot>;
    if (!parsed || typeof parsed !== "object" || !parsed.loan || typeof parsed.loan !== "object") {
      return null;
    }

    if (loanUuid && parsed.loan.uuid !== loanUuid) {
      return null;
    }

    return parsed.loan as LoanSummary;
  } catch {
    return null;
  }
}

export function clearLastCreatedLoan() {
  sessionStorage.removeItem(LAST_CREATED_LOAN_KEY);
}
