import { useEffect, useState } from "react";
import { PasswordRecoveryLayout } from "../components/auth/PasswordRecoveryLayout";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { resetPasswordFromRecovery } from "../services/passwordRecoveryService";

const RECOVERY_RESET_TOKEN_STORAGE_KEY = "panol.passwordRecovery.resetToken";
const RECOVERY_RESET_TOKEN_EXPIRES_AT_STORAGE_KEY = "panol.passwordRecovery.resetTokenExpiresAt";
const RECOVERY_SUCCESS_STORAGE_KEY = "panol.passwordRecovery.success";
const RECOVERY_RUT_STORAGE_KEY = "panol.passwordRecovery.rut";
const RECOVERY_SENT_AT_STORAGE_KEY = "panol.passwordRecovery.lastSentAt";

function clearRecoverySession() {
  sessionStorage.removeItem(RECOVERY_RESET_TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(RECOVERY_RESET_TOKEN_EXPIRES_AT_STORAGE_KEY);
  sessionStorage.removeItem(RECOVERY_RUT_STORAGE_KEY);
  sessionStorage.removeItem(RECOVERY_SENT_AT_STORAGE_KEY);
}

function resolveResetToken() {
  const token = sessionStorage.getItem(RECOVERY_RESET_TOKEN_STORAGE_KEY);
  const expiresAt = Number(sessionStorage.getItem(RECOVERY_RESET_TOKEN_EXPIRES_AT_STORAGE_KEY));

  if (!token) {
    return null;
  }

  if (Number.isFinite(expiresAt) && expiresAt > 0 && expiresAt <= Date.now()) {
    clearRecoverySession();
    return null;
  }

  return token;
}

export function PasswordRecoveryResetPage() {
  const [resetToken] = useState(() => resolveResetToken());
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!resetToken) {
      window.location.hash = "#/recuperar-contrasena";
    }
  }, [resetToken]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    setConfirmError(null);
    setFormError(null);

    if (!newPassword) {
      setPasswordError("Ingresa una nueva contraseña.");
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError("La nueva contraseña debe tener al menos 8 caracteres.");
      return;
    }
    if (!confirmPassword) {
      setConfirmError("Repite la nueva contraseña.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setConfirmError("La confirmacion de contraseña no coincide.");
      return;
    }
    if (!resetToken) {
      window.location.hash = "#/recuperar-contrasena";
      return;
    }

    setSubmitting(true);

    try {
      await resetPasswordFromRecovery(resetToken, newPassword);
      clearRecoverySession();
      sessionStorage.setItem(
        RECOVERY_SUCCESS_STORAGE_KEY,
        "La contraseña se actualizo correctamente. Ya puedes iniciar sesion.",
      );
      window.location.hash = "#/login";
    } catch (error) {
      const apiError = getApiErrorPayload(error);
      if (apiError?.code === "AUTH_PASSWORD_RECOVERY_TOKEN_INVALID") {
        clearRecoverySession();
        setFormError("La sesion de recuperacion ya no es valida. Solicita un nuevo codigo.");
      } else if (apiError?.code === "AUTH_PASSWORD_RECOVERY_TOKEN_EXPIRED") {
        clearRecoverySession();
        setFormError("La verificacion expiro. Solicita un nuevo codigo para continuar.");
      } else {
        setFormError(getErrorMessage(error, "No fue posible actualizar la contraseña."));
      }
      setSubmitting(false);
    }
  }

  return (
    <PasswordRecoveryLayout
      step={3}
      eyebrow="Paso 3 de 3"
      title="Define tu nueva contraseña"
      description="Crea una nueva clave de acceso. Al confirmar el cambio, se cerraran tus sesiones activas."
      asideTitle="Cierre de sesiones"
      asideCopy="Por seguridad, cualquier sesion abierta con la contraseña anterior quedara invalidada al finalizar el proceso."
      helperItems={[
        "La nueva contraseña debe tener al menos 8 caracteres.",
        "No puedes reutilizar tu contraseña actual.",
        "Tras guardar, volveras al login para ingresar con tu nueva clave.",
      ]}
    >
      <form className="login-form password-recovery-form" onSubmit={handleSubmit} noValidate>
        <div className="login-field">
          <label htmlFor="recovery-new-password">Nueva contraseña</label>
          <input
            id="recovery-new-password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => {
              setNewPassword(event.target.value);
              if (passwordError) setPasswordError(null);
            }}
            aria-invalid={passwordError ? "true" : "false"}
            aria-describedby={passwordError ? "recovery-new-password-error" : undefined}
            disabled={submitting}
          />
          {passwordError ? (
            <p id="recovery-new-password-error" className="login-field__error">
              {passwordError}
            </p>
          ) : null}
        </div>

        <div className="login-field">
          <label htmlFor="recovery-confirm-password">Repite la nueva contraseña</label>
          <input
            id="recovery-confirm-password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => {
              setConfirmPassword(event.target.value);
              if (confirmError) setConfirmError(null);
            }}
            aria-invalid={confirmError ? "true" : "false"}
            aria-describedby={confirmError ? "recovery-confirm-password-error" : undefined}
            disabled={submitting}
          />
          {confirmError ? (
            <p id="recovery-confirm-password-error" className="login-field__error">
              {confirmError}
            </p>
          ) : null}
        </div>

        <p className="field-hint">
          Recomendacion: usa una combinacion robusta y distinta a tu contraseña anterior.
        </p>

        {formError ? <div className="error-banner">{formError}</div> : null}

        <button className="button login-form__submit" type="submit" disabled={submitting}>
          {submitting ? "Actualizando contraseña..." : "Actualizar contraseña"}
        </button>

        <div className="password-recovery-form__footer">
          <a href="#/recuperar-contrasena" className="login-form__link">
            Iniciar de nuevo
          </a>
        </div>
      </form>
    </PasswordRecoveryLayout>
  );
}
