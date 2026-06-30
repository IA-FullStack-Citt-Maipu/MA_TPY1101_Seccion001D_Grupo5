import { Eye, EyeOff } from "lucide-react";
import { useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { login } from "../services/authService";
import { getDefaultHashByRole } from "../utils/auth";
import { cleanRut, formatRut } from "../utils/rut";

export function LoginPage() {
  const [rutRaw, setRutRaw] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [rutError, setRutError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const rutClean = useMemo(() => cleanRut(rutRaw), [rutRaw]);
  const rutFormatted = useMemo(() => formatRut(rutRaw), [rutRaw]);

  function validate(): boolean {
    let valid = true;
    setRutError(null);
    setPasswordError(null);

    if (!rutClean) {
      setRutError("Ingresa tu RUT.");
      valid = false;
    }

    if (!password.trim()) {
      setPasswordError("Ingresa tu contraseña.");
      valid = false;
    }

    return valid;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);

    if (!validate()) {
      return;
    }

    setSubmitting(true);

    try {
      const result = await login({ rut: rutClean, password: password.trim(), rememberMe });
      window.location.hash = getDefaultHashByRole(result.user.role);
    } catch (e) {
      setFormError(getErrorMessage(e, "No fue posible iniciar sesión"));
      setSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-screen__frame">
        <section className="login-screen__hero" aria-hidden="true">
          <img src="/login-hero.png" alt="" className="login-screen__hero-image" />
        </section>

        <section className="login-screen__form-side">
          <div className="login-card">
          <header className="login-card__brand">
            <img src="/IconPanol.png" alt="Logo Pañol Salud" className="login-card__brand-logo" />
            <strong>Pañol Salud</strong>
          </header>

          <div className="login-card__copy">
            <h1>Bienvenido de nuevo</h1>
            <p>Inicia sesión para acceder al sistema de gestión de pañol de salud.</p>
          </div>

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <div className="login-field">
              <label htmlFor="rut">RUT</label>
              <input
                id="rut"
                name="rut"
                type="text"
                autoComplete="username"
                inputMode="text"
                placeholder="22.307.980-1"
                value={rutFormatted}
                onChange={(event) => {
                  setRutRaw(event.target.value);
                  if (rutError) setRutError(null);
                }}
                aria-invalid={rutError ? "true" : "false"}
                aria-describedby={rutError ? "rut-error" : undefined}
                disabled={submitting}
              />
              {rutError ? <p id="rut-error" className="login-field__error">{rutError}</p> : null}
            </div>

            <div className="login-field">
              <label htmlFor="password">Contraseña</label>
              <div className="login-field__password-wrap">
                <input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    if (passwordError) setPasswordError(null);
                  }}
                  aria-invalid={passwordError ? "true" : "false"}
                  aria-describedby={passwordError ? "password-error" : undefined}
                  disabled={submitting}
                />
                <button
                  type="button"
                  className="login-field__password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
                  disabled={submitting}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {passwordError ? <p id="password-error" className="login-field__error">{passwordError}</p> : null}
            </div>

            <div className="login-form__meta">
              <label className="login-check">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                  disabled={submitting}
                />
                <span>Recordarme</span>
              </label>
              <a href="#/login" className="login-form__link" onClick={(e) => e.preventDefault()}>
                ¿Olvidaste tu contraseña?
              </a>
            </div>

            {formError ? <div className="error-banner">{formError}</div> : null}

            <button className="button login-form__submit" type="submit" disabled={submitting}>
              {submitting ? "Iniciando sesión..." : "Iniciar sesión"}
            </button>

            <p className="login-form__footnote">Uso interno · Escuela de Salud</p>
          </form>
          </div>
        </section>
      </div>
    </div>
  );
}

