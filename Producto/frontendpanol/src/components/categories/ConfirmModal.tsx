interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  tone?: "default" | "warn" | "danger";
  loading: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export function ConfirmModal({
  isOpen,
  title,
  message,
  confirmLabel,
  tone = "default",
  loading,
  onClose,
  onConfirm,
}: ConfirmModalProps) {
  if (!isOpen) {
    return null;
  }

  const toneClass = tone === "danger" ? "button--danger" : tone === "warn" ? "button--warn" : "";

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal">
        <h3>{title}</h3>
        <p>{message}</p>

        <div className="modal-actions">
          <button type="button" className="button button--ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="button" className={`button ${toneClass}`.trim()} disabled={loading} onClick={onConfirm}>
            {loading ? "Procesando..." : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

