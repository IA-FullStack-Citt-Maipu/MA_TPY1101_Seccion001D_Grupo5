import { CheckCircle2, Minus, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import type { ReviewLoanItemPayload } from "../../services/loanService";
import type { LoanSummary } from "../../types/loan";

export interface LoanApprovalSubmission {
  notes: string | null;
  items: ReviewLoanItemPayload[] | null;
}

interface ApprovalDraftItem {
  implementUuid: string;
  implementName: string;
  requestedQuantity: number;
  approvedQuantity: number;
}

export function LoanApprovalModal({
  loan,
  processing,
  onClose,
  onSubmit,
}: {
  loan: LoanSummary;
  processing: boolean;
  onClose: () => void;
  onSubmit: (payload: LoanApprovalSubmission) => Promise<void> | void;
}) {
  const [notes, setNotes] = useState("");
  const [modifyQuantities, setModifyQuantities] = useState(false);
  const [items, setItems] = useState<ApprovalDraftItem[]>(
    loan.items.map((item) => ({
      implementUuid: item.implement_uuid,
      implementName: item.implement_name,
      requestedQuantity: item.requested_quantity,
      approvedQuantity: item.requested_quantity,
    })),
  );

  const totalRequested = useMemo(
    () => items.reduce((total, item) => total + item.requestedQuantity, 0),
    [items],
  );
  const totalApproved = useMemo(
    () => items.reduce((total, item) => total + item.approvedQuantity, 0),
    [items],
  );

  const canSubmit = totalApproved > 0 && !processing;

  function adjustApprovedQuantity(implementUuid: string, delta: number) {
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid) {
          return item;
        }
        const nextQuantity = Math.max(0, Math.min(item.requestedQuantity, item.approvedQuantity + delta));
        return {
          ...item,
          approvedQuantity: nextQuantity,
        };
      }),
    );
  }

  async function handleSubmit() {
    if (!canSubmit) {
      return;
    }
    await onSubmit({
      notes: notes.trim() || null,
      items: modifyQuantities
        ? items.map((item) => ({
            implement_uuid: item.implementUuid,
            approved_quantity: item.approvedQuantity,
          }))
        : null,
    });
  }

  return (
    <div className="modal-overlay">
      <div className="modal teacher-loans-review-modal loan-approval-modal">
        <h3>Aprobar prestamo</h3>
        <p>Puedes aprobar la solicitud completa o ajustar la cantidad aprobada por implemento antes de continuar.</p>

        <label htmlFor="loan-approval-notes">Notas (opcional)</label>
        <textarea
          id="loan-approval-notes"
          rows={3}
          value={notes}
          maxLength={1000}
          onChange={(event) => setNotes(event.target.value)}
          placeholder="Observaciones de aprobacion..."
        />

        <label className="loan-approval-toggle">
          <input
            type="checkbox"
            checked={modifyQuantities}
            onChange={(event) => setModifyQuantities(event.target.checked)}
            disabled={processing}
          />
          <span>Modificar cantidades aprobadas</span>
        </label>

        {modifyQuantities ? (
          <div className="loan-approval-list">
            {items.map((item) => (
              <article key={item.implementUuid} className="loan-approval-row">
                <div>
                  <strong>{item.implementName}</strong>
                  <p>Solicitado: {item.requestedQuantity}</p>
                </div>
                <div className="loan-approval-row__controls">
                  <span className="loan-approval-row__label">Aprobado</span>
                  <div className="loan-stepper loan-stepper--compact">
                    <button
                      type="button"
                      onClick={() => adjustApprovedQuantity(item.implementUuid, -1)}
                      disabled={processing || item.approvedQuantity <= 0}
                      aria-label={`Disminuir cantidad aprobada de ${item.implementName}`}
                    >
                      <Minus size={14} />
                    </button>
                    <span className="loan-stepper__value">{item.approvedQuantity}</span>
                    <button
                      type="button"
                      onClick={() => adjustApprovedQuantity(item.implementUuid, 1)}
                      disabled={processing || item.approvedQuantity >= item.requestedQuantity}
                      aria-label={`Aumentar cantidad aprobada de ${item.implementName}`}
                    >
                      <Plus size={14} />
                    </button>
                  </div>
                </div>
              </article>
            ))}

            <div className="loan-approval-summary">
              <span>Total solicitado: {totalRequested}</span>
              <strong>Total aprobado: {totalApproved}</strong>
            </div>
          </div>
        ) : (
          <div className="loan-approval-summary loan-approval-summary--compact">
            <span>Total solicitado: {totalRequested}</span>
            <strong>Se aprobara completo</strong>
          </div>
        )}

        {totalApproved <= 0 ? (
          <small className="field-error">Debes aprobar al menos una unidad para continuar.</small>
        ) : null}

        <div className="modal-actions">
          <button type="button" className="button button--ghost" onClick={onClose} disabled={processing}>
            Cancelar
          </button>
          <button type="button" className="button" onClick={() => void handleSubmit()} disabled={!canSubmit}>
            <CheckCircle2 size={16} />
            {processing ? "Aprobando..." : "Aprobar"}
          </button>
        </div>
      </div>
    </div>
  );
}
