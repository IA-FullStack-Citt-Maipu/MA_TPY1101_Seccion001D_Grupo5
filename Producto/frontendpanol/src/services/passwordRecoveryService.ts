import { apiClient } from "./apiClient";

export interface PasswordRecoveryVerifyResult {
  resetToken: string;
  expiresInSeconds: number;
}

interface BackendPasswordRecoveryVerifyResponse {
  reset_token: string;
  expires_in_seconds: number;
}

export async function requestPasswordRecovery(rut: string): Promise<void> {
  await apiClient.post(
    "/api/v2/auth/password-recovery/request",
    { rut },
    { skipAuthRefresh: true },
  );
}

export async function verifyPasswordRecoveryCode(
  rut: string,
  code: string,
): Promise<PasswordRecoveryVerifyResult> {
  const { data } = await apiClient.post<BackendPasswordRecoveryVerifyResponse>(
    "/api/v2/auth/password-recovery/verify",
    { rut, code },
    { skipAuthRefresh: true },
  );

  return {
    resetToken: data.reset_token,
    expiresInSeconds: data.expires_in_seconds,
  };
}

export async function resetPasswordFromRecovery(
  resetToken: string,
  newPassword: string,
): Promise<void> {
  await apiClient.post(
    "/api/v2/auth/password-recovery/reset",
    {
      reset_token: resetToken,
      new_password: newPassword,
    },
    { skipAuthRefresh: true },
  );
}
