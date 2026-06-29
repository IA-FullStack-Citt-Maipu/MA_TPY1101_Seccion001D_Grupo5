import { ArrowLeft, BookOpenText, CalendarDays, CheckCircle2, ClipboardList, MapPin, Package2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { fetchLoanByUuid, prepareLoan } from "../services/loanService";
import type { LoanSummary } from "../types/loan";
import { buildLoanDetailHash } from "../utils/loanDetailRouting";
import { canStartPreparation } from "../utils/loanSchedule";

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(date)
    .replace(".", "");
}

function formatItemTypeLabel(itemType: LoanSummary["items"][number]["item_type"]): string {
  if (itemType === "individual") return "Activo";
  if (itemType === "reusable") return "Reutilizable";
  if (itemType === "consumable") return "Consumible";
  return "Sin tipo";
}

export function LoanPreparationPage({
  loanUuid,
  embedded = false,
}: {
  loanUuid: string;
  embedded?: boolean;
}) {
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadLoan() {
      setLoading(true);
      setError(null);
      try {
        const response = await fetchLoanByUuid(loanUuid);
        if (cancelled) {
          return;
        }
        if (!response) {
          setError("No se encontro el prestamo solicitado.");
          setLoan(null);
          return;
        }
        setLoan(response);
      } catch (requestError) {
        if (!cancelled) {
          setError(getErrorMessage(requestError, "No se pudo cargar la preparacion del prestamo."));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadLoan();
    return () => {
      cancelled = true;
    };
  }, [loanUuid]);

  const canPrepareNow = useMemo(
    () => (loan ? loan.status === "approved" && canStartPreparation(loan) : false),
    [loan],
  );

  function goBack() {
    window.location.assign(buildLoanDetailHash(loanUuid, "list"));
  }

  async function confirmPreparation() {
    if (!loan || !canPrepareNow) {
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const updated = await prepareLoan(loan.uuid);
      setLoan(updated);
      window.location.assign(buildLoanDetailHash(updated.uuid, "list"));
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo marcar el prestamo como preparado."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={`teacher-loan-detail-page${embedded ? " teacher-loan-detail-page--embedded" : ""}`}>
      <nav className="teacher-loan-detail-backnav">
        <button type="button" className="teacher-loan-detail-backnav__btn" onClick={goBack}>
          <ArrowLeft size={16} />
          Volver al detalle
        </button>
      </nav>

      {error ? <div className="error-banner">{error}</div> : null}

      {loading ? (
        <section className="panel">
          <p className="text-muted">Cargando preparacion del prestamo...</p>
        </section>
      ) : !loan ? (
        <section className="panel">
          <p className="text-muted">No se encontro informacion para este prestamo.</p>
        </section>
      ) : (
        <section className="loan-preparation-layout">
          <div className="loan-preparation-top">
            <article className="teacher-loan-detail-card loan-preparation-hero">
              <div className="loan-preparation-hero__eyebrow">Operacion previa a la entrega</div>
              <div className="loan-preparation-hero__header">
                <div>
                  <span className="teacher-loans-status teacher-loans-status--approved">Preparacion</span>
                  <h1>Preparar implementos reservados</h1>
                  <p>
                    Revisa lo que ya quedo reservado para esta solicitud y confirma la preparacion fisica
                    cuando los implementos esten listos para entrega.
                  </p>
                </div>
              </div>

              <div className="loan-preparation-hero__grid">
                <article className="loan-preparation-hero__metric">
                  <span>
                    <MapPin size={15} />
                    Sala
                  </span>
                  <strong>{loan.room?.name ?? "Sin sala"}</strong>
                </article>
                <article className="loan-preparation-hero__metric">
                  <span>
                    <BookOpenText size={15} />
                    Asignatura
                  </span>
                  <strong>{loan.subject?.name ?? "Sin asignatura"}</strong>
                </article>
                <article className="loan-preparation-hero__metric">
                  <span>
                    <CalendarDays size={15} />
                    Programado
                  </span>
                  <strong>{formatDateTime(loan.scheduled_at)}</strong>
                </article>
                <article className="loan-preparation-hero__metric">
                  <span>
                    <CheckCircle2 size={15} />
                    Estado
                  </span>
                  <strong>Reservado</strong>
                </article>
              </div>
            </article>

            <article className="teacher-loan-detail-card loan-preparation-panel">
              <header className="loan-preparation-panel__header">
                <CheckCircle2 size={17} />
                <h2>Confirmacion operativa</h2>
              </header>

              <div className="loan-preparation-panel__body">
                <div className="loan-preparation-panel__status">
                  <span>Estado de accion</span>
                  <strong>{canPrepareNow ? "Disponible ahora" : "Disponible cuando la solicitud este reservada"}</strong>
                </div>

                <div className="loan-preparation-panel__status">
                  <span>Accion</span>
                  <strong>Marcar como preparado</strong>
                </div>

                <ul className="loan-preparation-panel__notes">
                  <li>La preparacion no cambia cantidades reservadas.</li>
                  <li>Si algo cambia operativamente, el ajuste real se registra despues en la entrega.</li>
                </ul>
              </div>

              <div className="loan-preparation-panel__actions">
                <button type="button" className="teacher-loan-detail-action-btn" onClick={goBack}>
                  Cancelar
                </button>
                <button
                  type="button"
                  className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--complete"
                  onClick={() => void confirmPreparation()}
                  disabled={submitting || !canPrepareNow}
                >
                  <CheckCircle2 size={16} />
                  Marcar como preparado
                </button>
              </div>
            </article>
          </div>

          <article className="teacher-loan-detail-items loan-preparation-items">
            <header className="teacher-loan-detail-items__header">
              <h2>
                <ClipboardList size={18} />
                Implementos a preparar
              </h2>
              <span>{loan.items.length} item(s)</span>
            </header>
            <div className="teacher-loan-detail-items__table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Implemento</th>
                    <th>Tipo</th>
                    <th>Reservado</th>
                  </tr>
                </thead>
                <tbody>
                  {loan.items.map((item) => (
                    <tr key={item.implement_uuid}>
                      <td>
                        <div className="teacher-loan-detail-item-cell">
                          <div className="teacher-loan-detail-item-cell__thumb">
                            <Package2 size={18} />
                          </div>
                          <strong>{item.implement_name}</strong>
                        </div>
                      </td>
                      <td>
                        <span className="loan-preparation-type">{formatItemTypeLabel(item.item_type)}</span>
                      </td>
                      <td>{item.reserved_quantity}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <footer className="teacher-loan-detail-items__footer">
              <p>
                La preparacion no cambia cantidades. Cualquier ajuste real se hace despues, en la entrega.
              </p>
            </footer>
          </article>
        </section>
      )}
    </div>
  );
}
