import { Clock3 } from "lucide-react";

interface PresencePollingModalProps {
  visible: boolean;
  countdownSeconds: number;
  pollingPaused: boolean;
  onContinue: () => void;
}

function formatCountdown(seconds: number): string {
  return `00:${String(Math.max(0, seconds)).padStart(2, "0")}`;
}

export function PresencePollingModal({
  visible,
  countdownSeconds,
  pollingPaused,
  onContinue,
}: PresencePollingModalProps) {
  if (!visible) {
    return null;
  }

  return (
    <div className="modal-overlay presence-prompt-overlay">
      <div className={`modal presence-prompt${pollingPaused ? " is-paused" : ""}`}>
        <div className="presence-prompt__orb" aria-hidden="true">
          <Clock3 size={28} />
        </div>
        <h3>Sigues ahi ?</h3>
        <p>
          {pollingPaused
            ? "No recibimos respuesta a tiempo. Pausamos su actividad momentaneamente para evitar refrescos innecesarios. Presione el boton para seguir navegando."
            : "No detectamos actividad hace 5 minutos. Responde dentro del tiempo indicado para mantener las actualizaciones automaticas."}
        </p>
        <div className="presence-prompt__timer" aria-live="polite">
          {pollingPaused ? "Actividad pausada" : formatCountdown(countdownSeconds)}
        </div>
        <div className="modal-actions">
          <button type="button" className="button" onClick={onContinue}>
            Sigo aqui
          </button>
        </div>
      </div>
    </div>
  );
}
