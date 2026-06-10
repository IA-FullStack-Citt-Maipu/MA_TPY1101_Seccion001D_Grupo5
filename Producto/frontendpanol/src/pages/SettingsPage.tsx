import { KeyRound, Mail, MoonStar, ShieldCheck, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import {
  fetchCurrentUserProfile,
  updateCurrentUserEmail,
  updateCurrentUserPassword,
} from "../services/profileService";
import { getRoleDisplayLabel, type SessionUserSummary } from "../utils/auth";
import type { ThemeMode } from "../utils/theme";

interface SettingsPageProps {
  embedded?: boolean;
  sessionUser: SessionUserSummary | null;
  onSessionUserChange: (user: SessionUserSummary) => void;
  themeMode: ThemeMode;
  onThemeModeChange: (mode: ThemeMode) => void;
}

export function SettingsPage({
  embedded = false,
  sessionUser,
  onSessionUserChange,
  themeMode,
  onThemeModeChange,
}: SettingsPageProps) {
  const [profile, setProfile] = useState<SessionUserSummary | null>(sessionUser);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [emailDraft, setEmailDraft] = useState(sessionUser?.email ?? "");
  const [emailSaving, setEmailSaving] = useState(false);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [emailSuccess, setEmailSuccess] = useState<string | null>(null);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoadingProfile(true);
    setProfileError(null);

    fetchCurrentUserProfile()
      .then((user) => {
        if (cancelled) return;
        setProfile(user);
        setEmailDraft(user.email ?? "");
        onSessionUserChange(user);
      })
      .catch((error) => {
        if (cancelled) return;
        setProfileError(getErrorMessage(error, "No fue posible cargar la informacion del usuario."));
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingProfile(false);
      });

    return () => {
      cancelled = true;
    };
  }, [onSessionUserChange]);

  async function handleEmailSubmit(event: React.FormEvent) {
    event.preventDefault();
    setEmailError(null);
    setEmailSuccess(null);

    const normalizedEmail = emailDraft.trim().toLowerCase();
    if (!normalizedEmail) {
      setEmailError("Debes ingresar un correo.");
      return;
    }

    setEmailSaving(true);
    try {
      const updatedUser = await updateCurrentUserEmail(normalizedEmail);
      setProfile(updatedUser);
      setEmailDraft(updatedUser.email ?? "");
      onSessionUserChange(updatedUser);
      setEmailSuccess("El correo se actualizo correctamente.");
    } catch (error) {
      setEmailError(getErrorMessage(error, "No fue posible actualizar el correo."));
    } finally {
      setEmailSaving(false);
    }
  }

  async function handlePasswordSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(null);

    if (!currentPassword) {
      setPasswordError("Debes ingresar tu contrasena actual.");
      return;
    }
    if (!newPassword) {
      setPasswordError("Debes ingresar una nueva contrasena.");
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError("La nueva contrasena debe tener al menos 8 caracteres.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError("La confirmacion de contrasena no coincide.");
      return;
    }

    setPasswordSaving(true);
    try {
      await updateCurrentUserPassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordSuccess("La contrasena se actualizo correctamente.");
    } catch (error) {
      setPasswordError(getErrorMessage(error, "No fue posible actualizar la contrasena."));
    } finally {
      setPasswordSaving(false);
    }
  }

  const roleLabel = getRoleDisplayLabel(profile?.role ?? sessionUser?.role ?? "UNKNOWN");
  const emailLabel = profile?.email ?? sessionUser?.email ?? "Sin correo";

  return (
    <section className={embedded ? "settings-page settings-page--embedded" : "settings-page"}>
      <header className="settings-page__hero">
        <div>
          <p className="settings-page__eyebrow">Cuenta / Configuracion</p>
          <h1>Configuracion de usuario</h1>
          <p className="settings-page__lead">
            Revisa la informacion de tu cuenta, actualiza tu correo, cambia tu contrasena y
            define la preferencia visual de la plataforma.
          </p>
        </div>
      </header>

      {profileError ? <div className="error-banner">{profileError}</div> : null}

      <div className="settings-page__grid">
        <article className="panel settings-card settings-card--summary">
          <div className="settings-card__header">
            <div className="settings-card__icon">
              <UserRound size={20} />
            </div>
            <div>
              <h2>Resumen de la cuenta</h2>
              <p>Informacion del usuario que esta operando en este momento.</p>
            </div>
          </div>

          {loadingProfile ? <p className="field-hint">Cargando informacion del usuario...</p> : null}

          <dl className="settings-summary-list">
            <div className="settings-summary-list__row">
              <dt>Nombre</dt>
              <dd>{profile?.name ?? sessionUser?.name ?? "Usuario"}</dd>
            </div>
            <div className="settings-summary-list__row">
              <dt>Correo</dt>
              <dd>{emailLabel}</dd>
            </div>
            <div className="settings-summary-list__row">
              <dt>Rol asignado</dt>
              <dd>{roleLabel}</dd>
            </div>
            <div className="settings-summary-list__row">
              <dt>ID de usuario</dt>
              <dd className="settings-summary-list__mono">{profile?.id ?? sessionUser?.id ?? "-"}</dd>
            </div>
          </dl>
        </article>

        <article className="panel settings-card">
          <div className="settings-card__header">
            <div className="settings-card__icon">
              <Mail size={20} />
            </div>
            <div>
              <h2>Correo de acceso</h2>
              <p>Actualiza el correo asociado a tu cuenta.</p>
            </div>
          </div>

          {emailError ? <div className="error-banner">{emailError}</div> : null}
          {emailSuccess ? <div className="success-banner">{emailSuccess}</div> : null}

          <form className="settings-form" onSubmit={handleEmailSubmit}>
            <label className="settings-field">
              <span>Correo electronico</span>
              <input
                className="ui-input"
                type="email"
                value={emailDraft}
                onChange={(event) => setEmailDraft(event.target.value)}
                placeholder="usuario@panol.local"
                disabled={emailSaving}
              />
            </label>

            <div className="settings-form__actions">
              <button className="button" type="submit" disabled={emailSaving}>
                {emailSaving ? "Guardando..." : "Actualizar correo"}
              </button>
            </div>
          </form>
        </article>

        <article className="panel settings-card">
          <div className="settings-card__header">
            <div className="settings-card__icon">
              <KeyRound size={20} />
            </div>
            <div>
              <h2>Seguridad</h2>
              <p>Cambia tu contrasena sin salir de la plataforma.</p>
            </div>
          </div>

          {passwordError ? <div className="error-banner">{passwordError}</div> : null}
          {passwordSuccess ? <div className="success-banner">{passwordSuccess}</div> : null}

          <form className="settings-form" onSubmit={handlePasswordSubmit}>
            <label className="settings-field">
              <span>Contrasena actual</span>
              <input
                className="ui-input"
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                disabled={passwordSaving}
              />
            </label>

            <label className="settings-field">
              <span>Nueva contrasena</span>
              <input
                className="ui-input"
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={passwordSaving}
              />
            </label>

            <label className="settings-field">
              <span>Confirmar nueva contrasena</span>
              <input
                className="ui-input"
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={passwordSaving}
              />
            </label>

            <p className="field-hint">
              Recomendacion: usa al menos 8 caracteres y evita reutilizar tu contrasena actual.
            </p>

            <div className="settings-form__actions">
              <button className="button" type="submit" disabled={passwordSaving}>
                {passwordSaving ? "Actualizando..." : "Cambiar contrasena"}
              </button>
            </div>
          </form>
        </article>

        <article className="panel settings-card settings-card--preferences">
          <div className="settings-card__header">
            <div className="settings-card__icon">
              <MoonStar size={20} />
            </div>
            <div>
              <h2>Preferencias visuales</h2>
              <p>Activa o desactiva el modo oscuro y guarda la preferencia localmente.</p>
            </div>
          </div>

          <div className="settings-theme-row">
            <div className="settings-theme-copy">
              <strong>Modo oscuro</strong>
              <span>La preferencia queda guardada en este navegador usando localStorage.</span>
            </div>

            <button
              type="button"
              className={themeMode === "dark" ? "settings-switch settings-switch--active" : "settings-switch"}
              role="switch"
              aria-checked={themeMode === "dark"}
              onClick={() => onThemeModeChange(themeMode === "dark" ? "light" : "dark")}
            >
              <span className="settings-switch__track">
                <span className="settings-switch__thumb" />
              </span>
              <span className="settings-switch__label">
                {themeMode === "dark" ? "Activado" : "Desactivado"}
              </span>
            </button>
          </div>

          <div className="settings-preference-note">
            <ShieldCheck size={18} />
            <span>La preferencia se aplica a toda la interfaz en futuras visitas.</span>
          </div>
        </article>
      </div>
    </section>
  );
}
