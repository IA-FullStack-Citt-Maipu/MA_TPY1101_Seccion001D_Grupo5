import { useMemo, useState } from "react";
import { PasswordRecoveryLayout } from "../components/auth/PasswordRecoveryLayout";
import { getErrorMessage } from "../services/apiClient";
import { requestPasswordRecovery } from "../services/passwordRecoveryService";
import { cleanRut, formatRut, isValidRut } from "../utils/rut";

const RECOVERY_RUT_STORAGE_KEY = "panol.passwordRecovery.rut";
const RECOVERY_SENT_AT_STORAGE_KEY = "panol.passwordRecovery.lastSentAt";

function persistRecoveryRut(rut: string) {
  sessionStorage.setItem(RECOVERY_RUT_STORAGE_KEY, rut);
}

function persistRecoverySentAt(timestamp: number) {
  sessionStorage.setItem(RECOVERY_SENT_AT_STORAGE_KEY, String(timestamp));
}

export function PasswordRecoveryRequestPage() {
  const [rutRaw, setRutRaw] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [rutError, setRutError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const rutClean = useMemo(() => cleanRut(rutRaw), [rutRaw]);
  const rutFormatted = useMemo(() => formatRut(rutRaw), [rutRaw]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    setRutError(null);

    if (!rutClean) {
      setRutError("Ingresa el RUT asociado a tu cuenta.");
      return;
    }

    if (!isValidRut(rutClean)) {
      setRutError("Ingresa un RUT valido con digito verificador.");
      return;
    }

    setSubmitting(true);

    try {
      await requestPasswordRecovery(rutClean);
      persistRecoveryRut(rutClean);
      persistRecoverySentAt(Date.now());
      window.location.hash = `#/recuperar-contrasena/codigo?rut=${encodeURIComponent(rutClean)}`;
    } catch (error) {
      setFormError(getErrorMessage(error, "No fue posible iniciar la recuperacion de contraseña."));
      setSubmitting(false);
    }
  }

  return (
    <PasswordRecoveryLayout
      step={1}
      eyebrow="Paso 1 de 3"
      title="Recupera tu contraseña"
      description="Ingresa tu RUT institucional. Si existe una cuenta asociada, enviaremos un codigo temporal a tu correo."
      asideTitle="Validación por correo"
      asideCopy="El sistema usa tu RUT como llave de seguridad y un codigo temporal para confirmar la solicitud."
      helperItems={[
        "La respuesta siempre es discreta para no exponer cuentas existentes.",
        "El codigo llega al correo registrado en tu perfil.",
        "Podras definir una nueva contraseña al verificar el codigo.",
      ]}
    >
      <form className="login-form password-recovery-form" onSubmit={handleSubmit} noValidate>
        <div className="login-field">
          <label htmlFor="recovery-rut">RUT</label>
          <input
            id="recovery-rut"
            name="rut"
            type="text"
            autoComplete="username"
            inputMode="text"
            placeholder="12.345.678-K"
            value={rutFormatted}
            onChange={(event) => {
              setRutRaw(event.target.value);
              if (rutError) setRutError(null);
            }}
            aria-invalid={rutError ? "true" : "false"}
            aria-describedby={rutError ? "recovery-rut-error" : undefined}
            disabled={submitting}
          />
          {rutError ? (
            <p id="recovery-rut-error" className="login-field__error">
              {rutError}
            </p>
          ) : null}
        </div>

        <div className="password-recovery-card__note">
          <strong>Importante</strong>
          <span>
            Solo podras continuar si tu cuenta tiene un correo valido registrado en el sistema.
          </span>
        </div>

        {formError ? <div className="error-banner">{formError}</div> : null}

        <button className="button login-form__submit" type="submit" disabled={submitting}>
          {submitting ? "Enviando codigo..." : "Enviar codigo"}
        </button>

        <div className="password-recovery-form__footer">
          <a href="#/login" className="login-form__link">
            Volver al inicio de sesion
          </a>
        </div>
      </form>
    </PasswordRecoveryLayout>
  );
}
