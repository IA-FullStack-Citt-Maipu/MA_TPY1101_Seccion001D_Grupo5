import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, type ReactNode } from "react";

interface LoanDetailModalFrameProps {
  children: ReactNode;
  onClose: () => void;
  onPrevious?: () => void;
  onNext?: () => void;
  hasPrevious?: boolean;
  hasNext?: boolean;
  ariaLabel?: string;
}

export function LoanDetailModalFrame({
  children,
  onClose,
  onPrevious,
  onNext,
  hasPrevious = false,
  hasNext = false,
  ariaLabel = "Detalle de solicitud",
}: LoanDetailModalFrameProps) {
  const hasSequenceNavigation = Boolean(onPrevious || onNext);

  useEffect(() => {
    document.body.classList.add("modal-open");

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }

    window.addEventListener("keydown", handleEscape);

    return () => {
      document.body.classList.remove("modal-open");
      window.removeEventListener("keydown", handleEscape);
    };
  }, [onClose]);

  return (
    <div className={`loan-calendar-detail-modal${hasSequenceNavigation ? "" : " loan-calendar-detail-modal--simple"}`} role="dialog" aria-modal="true" aria-label={ariaLabel}>
      <button
        type="button"
        className="loan-calendar-detail-modal__backdrop"
        aria-label={`Cerrar ${ariaLabel.toLowerCase()}`}
        onClick={onClose}
      />
      <div className={`loan-calendar-detail-modal__shell${hasSequenceNavigation ? "" : " loan-calendar-detail-modal__shell--simple"}`}>
        {hasSequenceNavigation ? (
          <button
            type="button"
            className="loan-calendar-detail-modal__nav loan-calendar-detail-modal__nav--prev"
            onClick={onPrevious}
            disabled={!hasPrevious}
            aria-label="Prestamo anterior"
          >
            <ChevronLeft size={20} />
          </button>
        ) : null}

        <div className="loan-calendar-detail-modal__surface">
          <button
            type="button"
            className="loan-calendar-detail-modal__close"
            aria-label={`Cerrar ${ariaLabel.toLowerCase()}`}
            onClick={onClose}
          >
            <X size={18} />
          </button>

          <div className="loan-calendar-detail-modal__content">
            {children}
          </div>
        </div>

        {hasSequenceNavigation ? (
          <button
            type="button"
            className="loan-calendar-detail-modal__nav loan-calendar-detail-modal__nav--next"
            onClick={onNext}
            disabled={!hasNext}
            aria-label="Prestamo siguiente"
          >
            <ChevronRight size={20} />
          </button>
        ) : null}
      </div>
    </div>
  );
}
