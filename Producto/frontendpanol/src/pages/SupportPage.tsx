import { Mail, MessageCircle } from "lucide-react";

export function SupportPage({ embedded = false }: { embedded?: boolean }) {
  return (
    <section className={embedded ? "support-page support-page--embedded" : "support-page"}>
      <div className="support-page__glow support-page__glow--left" aria-hidden="true" />
      <div className="support-page__glow support-page__glow--right" aria-hidden="true" />

      <div className="support-page__inner">
        <div className="support-page__hero">
          <img
            src="/ImagenSupportPanol.webp"
            alt="Soporte Pañol Salud"
            className="support-page__image"
          />
          <div className="support-page__bubble" aria-hidden="true">
            <MessageCircle size={26} />
          </div>
        </div>

        <div className="support-page__content">
          <h1>
            ¿Tienes <span>problemas</span> o sugerencias?
          </h1>
          <p className="support-page__subtitle">Contáctanos al siguiente correo:</p>

          <a className="support-page__mail-card" href="mailto:panolproject@gmail.com">
            <span className="support-page__mail-icon" aria-hidden="true">
              <Mail size={34} />
            </span>
            <strong>panolproject@gmail.com</strong>
          </a>

          <p className="support-page__caption">
            Tu opinión es muy importante para nosotros.
            <br />
            Estamos aquí para ayudarte.
          </p>
        </div>
      </div>
    </section>
  );
}
