import { useEffect, useMemo, useState } from "react";
import { PasswordRecoveryLayout } from "../components/auth/PasswordRecoveryLayout";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import {
  requestPasswordRecovery,
  verifyPasswordRecoveryCode,
} from "../services/passwordRecoveryService";
import { cleanRut, formatRut, isValidRut } from "../utils/rut";

const RECOVERY_RUT_STORAGE_KEY = "panol.passwordRecovery.rut";
const RECOVERY_SENT_AT_STORAGE_KEY = "panol.passwordRecovery.lastSentAt";
const RECOVERY_RESET_TOKEN_STORAGE_KEY = "panol.passwordRecovery.resetToken";
const RECOVERY_RESET_TOKEN_EXPIRES_AT_STORAGE_KEY = "panol.passwordRecovery.resetTokenExpiresAt";
const RECOVERY_RESEND_COOLDOWN_MS = 120_000;

function resolveRutFromHash(): string {
  const hash = window.location.hash || "";
  const queryIndex = hash.indexOf("?");
  if (queryIndex < 0) {
    return sessionStorage.getItem(RECOVERY_RUT_STORAGE_KEY) ?? "";
  }

  const params = new URLSearchParams(hash.slice(queryIndex + 1));
  const rut = cleanRut(params.get("rut") ?? "");
  return rut || (sessionStorage.getItem(RECOVERY_RUT_STORAGE_KEY) ?? "");
}

function resolveRemainingCooldownMs() {
  const lastSentAtRaw = sessionStorage.getItem(RECOVERY_SENT_AT_STORAGE_KEY);
  const lastSentAt = Number(lastSentAtRaw);
  if (!Number.isFinite(lastSentAt) || lastSentAt <= 0) {
    return 0;
  }

  return Math.max(RECOVERY_RESEND_COOLDOWN_MS - (Date.now() - lastSentAt), 0);
}

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}:${remainingSeconds.toString().padStart(2, "0")}`;
}

export function PasswordRecoveryCodePage() {
  const [rut] = useState(() => resolveRutFromHash());
  const [codeRaw, setCodeRaw] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(
    "Si la cuenta existe, deberias recibir el correo en unos instantes.",
  );
  const [cooldownMs, setCooldownMs] = useState(() => resolveRemainingCooldownMs());

  const normalizedCode = useMemo(
    () => codeRaw.replace(/[^a-zA-Z0-9]/g, "").toUpperCase().slice(0, 8),
    [codeRaw],
  );
  const formattedRut = useMemo(() => formatRut(rut), [rut]);
  const remainingSeconds = Math.ceil(cooldownMs / 1000);

  useEffect(() => {
    if (!rut || !isValidRut(rut)) {
      window.location.hash = "#/recuperar-contrasena";
      return;
    }

    sessionStorage.setItem(RECOVERY_RUT_STORAGE_KEY, rut);
  }, [rut]);

  useEffect(() => {
    if (cooldownMs <= 0) {
      return;
    }

    const timer = window.setInterval(() => {
      setCooldownMs(resolveRemainingCooldownMs());
    }, 1000);

    return () => window.clearInterval(timer);
  }, [cooldownMs]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    setCodeError(null);

    if (!normalizedCode) {
      setCodeError("Ingresa el codigo que recibiste por correo.");
      return;
    }

    if (normalizedCode.length !== 8) {
      setCodeError("El codigo debe tener 8 caracteres alfanumericos.");
      return;
    }

    setSubmitting(true);

    try {
      const result = await verifyPasswordRecoveryCode(rut, normalizedCode);
      sessionStorage.setItem(RECOVERY_RESET_TOKEN_STORAGE_KEY, result.resetToken);
      sessionStorage.setItem(
        RECOVERY_RESET_TOKEN_EXPIRES_AT_STORAGE_KEY,
        String(Date.now() + result.expiresInSeconds * 1000),
      );
      window.location.hash = "#/recuperar-contrasena/nueva";
    } catch (error) {
      const apiError = getApiErrorPayload(error);
      if (apiError?.code === "AUTH_PASSWORD_RECOVERY_CODE_INVALID") {
        setCodeError("El codigo no coincide o ya no se encuentra vigente.");
      } else if (apiError?.code === "AUTH_PASSWORD_RECOVERY_CODE_EXPIRED") {
        setCodeError("El codigo expiro. Solicita uno nuevo para continuar.");
      } else if (apiError?.code === "AUTH_PASSWORD_RECOVERY_CODE_ATTEMPTS_EXCEEDED") {
        setCodeError("Superaste el maximo de intentos. Solicita un nuevo codigo.");
      } else {
        setFormError(getErrorMessage(error, "No fue posible validar el codigo."));
      }
      setSubmitting(false);
    }
  }

  async function handleResend() {
    if (cooldownMs > 0 || resending) {
      return;
    }

    setFormError(null);
    setCodeError(null);
    setInfoMessage(null);
    setResending(true);

    try {
      await requestPasswordRecovery(rut);
      sessionStorage.setItem(RECOVERY_SENT_AT_STORAGE_KEY, String(Date.now()));
      setCooldownMs(RECOVERY_RESEND_COOLDOWN_MS);
      setInfoMessage("Te enviamos un nuevo codigo al correo asociado.");
    } catch (error) {
      const apiError = getApiErrorPayload(error);
      if (apiError?.code === "AUTH_PASSWORD_RECOVERY_RESEND_COOLDOWN") {
        setCooldownMs(RECOVERY_RESEND_COOLDOWN_MS);
      }
      setFormError(getErrorMessage(error, "No fue posible reenviar el codigo."));
    } finally {
      setResending(false);
    }
  }

  return (
    <PasswordRecoveryLayout
      step={2}
      eyebrow="Paso 2 de 3"
      title="Valida el codigo"
      description={`Ingresa el codigo de 8 caracteres enviado al correo asociado al RUT ${formattedRut}.`}
      asideTitle="Codigo temporal"
      asideCopy="El codigo confirma que eres quien solicito el cambio y habilita el paso final de nueva contraseña."
      helperItems={[
        "El codigo expira en 15 minutos.",
        "Solo se permiten 5 intentos fallidos por solicitud.",
        "Puedes reenviar el codigo cada 120 segundos.",
      ]}
    >
      <form className="login-form password-recovery-form" onSubmit={handleSubmit} noValidate>
        <div className="password-recovery-chip">
          <span>RUT asociado</span>
          <strong>{formattedRut}</strong>
        </div>

        <div className="login-field">
          <label htmlFor="recovery-code">Codigo de verificacion</label>
          <input
            id="recovery-code"
            name="code"
            type="text"
            autoComplete="one-time-code"
            inputMode="text"
            placeholder="AB12CD34"
            maxLength={8}
            value={normalizedCode}
            onChange={(event) => {
              setCodeRaw(event.target.value);
              if (codeError) setCodeError(null);
            }}
            aria-invalid={codeError ? "true" : "false"}
            aria-describedby={codeError ? "recovery-code-error" : undefined}
            disabled={submitting}
          />
          {codeError ? (
            <p id="recovery-code-error" className="login-field__error">
              {codeError}
            </p>
          ) : null}
        </div>

        <div className="password-recovery-resend">
          <div>
            <strong>Reenvio de codigo</strong>
            <span>
              {cooldownMs > 0
                ? `Disponible en ${formatCountdown(remainingSeconds)}`
                : "Ya puedes solicitar un nuevo codigo si lo necesitas."}
            </span>
          </div>
          <button
            type="button"
            className="button button--ghost"
            onClick={() => void handleResend()}
            disabled={cooldownMs > 0 || resending || submitting}
          >
            {resending ? "Reenviando..." : "Reenviar codigo"}
          </button>
        </div>

        {infoMessage ? <div className="success-banner">{infoMessage}</div> : null}
        {formError ? <div className="error-banner">{formError}</div> : null}

        <button className="button login-form__submit" type="submit" disabled={submitting}>
          {submitting ? "Validando codigo..." : "Continuar"}
        </button>

        <div className="password-recovery-form__footer">
          <a href="#/recuperar-contrasena" className="login-form__link">
            Cambiar RUT
          </a>
          <a href="#/login" className="login-form__link">
            Volver al login
          </a>
        </div>
      </form>
    </PasswordRecoveryLayout>
  );
}
