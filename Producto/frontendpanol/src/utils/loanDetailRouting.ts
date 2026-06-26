const LOAN_UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/;
const LOAN_DETAIL_QUERY_PARAM = "detail";

export type LoanDetailContext = "list" | "calendar";

function splitHash(hash: string): { path: string; params: URLSearchParams } {
  const normalizedHash = hash || "";
  const queryIndex = normalizedHash.indexOf("?");
  if (queryIndex < 0) {
    return {
      path: normalizedHash,
      params: new URLSearchParams(),
    };
  }

  return {
    path: normalizedHash.slice(0, queryIndex),
    params: new URLSearchParams(normalizedHash.slice(queryIndex + 1)),
  };
}

function buildHash(path: string, params: URLSearchParams): string {
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

export function getHashPath(hash: string): string {
  return splitHash(hash).path;
}

export function getLoanDetailUuidFromHash(hash: string): string | null {
  const detailParam = splitHash(hash).params.get(LOAN_DETAIL_QUERY_PARAM);
  if (!detailParam || !LOAN_UUID_PATTERN.test(detailParam)) {
    return null;
  }
  return detailParam;
}

export function buildLoanDetailHash(loanUuid: string, context: LoanDetailContext = "list"): string {
  const path = context === "calendar" ? "#/inventory/prestamos/calendario" : "#/inventory/prestamos";
  const params = new URLSearchParams();
  params.set(LOAN_DETAIL_QUERY_PARAM, loanUuid);
  return buildHash(path, params);
}

export function replaceLoanDetailInHash(hash: string, loanUuid: string): string {
  const { path, params } = splitHash(hash);
  params.set(LOAN_DETAIL_QUERY_PARAM, loanUuid);
  return buildHash(path, params);
}

export function stripLoanDetailFromHash(hash: string): string {
  const { path, params } = splitHash(hash);
  params.delete(LOAN_DETAIL_QUERY_PARAM);
  return buildHash(path, params);
}

export function normalizeLegacyLoanDetailHash(hash: string): string | null {
  const match = getHashPath(hash).match(
    /^#\/inventory\/prestamos\/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$/,
  );

  if (!match) {
    return null;
  }

  return buildLoanDetailHash(match[1], "list");
}
