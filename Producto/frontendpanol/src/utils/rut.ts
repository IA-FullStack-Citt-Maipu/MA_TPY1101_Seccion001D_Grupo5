const MAX_RUT_LENGTH = 9;

export function cleanRut(value: string): string {
  return value
    .replace(/[^0-9kK]/g, "")
    .toUpperCase()
    .slice(0, MAX_RUT_LENGTH);
}

export function formatRut(value: string): string {
  const cleaned = cleanRut(value);
  if (!cleaned) return "";

  const body = cleaned.slice(0, -1);
  const verifier = cleaned.slice(-1);
  const withDots = body.replace(/\B(?=(\d{3})+(?!\d))/g, ".");

  return body.length > 0 ? `${withDots}-${verifier}` : verifier;
}

export function isValidRut(value: string): boolean {
  const cleaned = cleanRut(value);
  if (cleaned.length < 8 || cleaned.length > 9) {
    return false;
  }

  const body = cleaned.slice(0, -1);
  const verifier = cleaned.slice(-1);

  return /^\d{7,8}$/.test(body) && /^[0-9K]$/.test(verifier);
}
