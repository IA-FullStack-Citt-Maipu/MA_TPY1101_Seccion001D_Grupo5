import type { ReactNode } from "react";

interface PasswordRecoveryLayoutProps {
  step: 1 | 2 | 3;
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
  asideTitle: string;
  asideCopy: string;
  helperItems: string[];
}

export function PasswordRecoveryLayout({
  step,
  eyebrow,
  title,
  description,
  children,
  asideTitle,
  asideCopy,
  helperItems,
}: PasswordRecoveryLayoutProps) {
  return (
    <div className="login-screen password-recovery-screen">
      <div className="login-screen__frame password-recovery-screen__frame">
        <section className="login-screen__hero password-recovery-screen__hero" aria-hidden="true">
          <div className="password-recovery-screen__hero-shell">
            <div className="password-recovery-screen__hero-brand">
              <img src="/IconPanol.png" alt="" className="login-card__brand-logo" />
              <div>
                <strong>Pañol Salud</strong>
                <span>Recuperación de acceso segura</span>
              </div>
            </div>

            <div className="password-recovery-screen__hero-copy">
              <p>{asideTitle}</p>
              <h2>{asideCopy}</h2>
            </div>

            <div className="password-recovery-screen__steps" aria-label="Progreso de recuperacion">
              {[1, 2, 3].map((item) => (
                <div
                  key={item}
                  className={
                    item === step
                      ? "password-recovery-screen__step is-active"
                      : item < step
                        ? "password-recovery-screen__step is-complete"
                        : "password-recovery-screen__step"
                  }
                >
                  <span>{item}</span>
                  <small>
                    {item === 1 ? "Solicitud" : item === 2 ? "Código" : "Nueva clave"}
                  </small>
                </div>
              ))}
            </div>

            <ul className="password-recovery-screen__hero-list">
              {helperItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </section>

        <section className="login-screen__form-side password-recovery-screen__form-side">
          <div className="login-card password-recovery-card">
            <header className="login-card__brand">
              <img src="/IconPanol.png" alt="Logo Pañol Salud" className="login-card__brand-logo" />
              <strong>Pañol Salud</strong>
            </header>

            <div className="login-card__copy password-recovery-card__copy">
              <p className="password-recovery-card__eyebrow">{eyebrow}</p>
              <h1>{title}</h1>
              <p>{description}</p>
            </div>

            {children}
          </div>
        </section>
      </div>
    </div>
  );
}
