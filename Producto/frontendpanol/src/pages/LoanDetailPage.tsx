import {
  ArrowRight,
  ArrowLeft,
  BookOpenText,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Copy,
  Edit3,
  Info,
  MapPin,
  Package2,
  Plus,
  ShieldAlert,
  SendHorizontal,
  TimerReset,
  Trash2,
  User,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import {
  cancelLoan,
  completeLoan,
  fetchLoanByUuid,
  fetchLoanStateDates,
  fetchLoanStatusTimeline,
} from "../services/loanService";
import {
  clearLastCreatedLoan,
  loadLastCreatedLoan,
} from "../services/loanSessionService";
import type { LoanStateDates, LoanStatusTimelineEntry, LoanSummary } from "../types/loan";
import { getUserRoleFromToken } from "../utils/auth";
import { canStartDelivery } from "../utils/loanSchedule";

const DELETE_CONFIRM_TEXT = "eliminar";

function parseDate(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function formatDateTime(value: string): string {
  const date = parseDate(value);
  if (!date) {
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

function normalizeStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    pending: "Pendiente",
    approved: "Aprobado",
    prepared: "Preparado",
    delivered: "En uso",
    overdue: "Atrasado",
    completed: "Completado",
    cancelled: "Cancelado",
    rejected: "Rechazado",
    expired: "Expirado",
  };
  return labels[status] ?? status;
}

function statusClassName(status: string): string {
  if (status === "pending") {
    return "teacher-loans-status teacher-loans-status--pending";
  }
  if (status === "approved") {
    return "teacher-loans-status teacher-loans-status--approved";
  }
  if (status === "prepared") {
    return "teacher-loans-status teacher-loans-status--approved";
  }
  if (status === "delivered") {
    return "teacher-loans-status teacher-loans-status--delivered";
  }
  if (status === "overdue") {
    return "teacher-loans-status teacher-loans-status--danger";
  }
  if (status === "completed") {
    return "teacher-loans-status teacher-loans-status--completed";
  }
  if (status === "cancelled" || status === "rejected" || status === "expired") {
    return "teacher-loans-status teacher-loans-status--danger";
  }
  return "teacher-loans-status teacher-loans-status--completed";
}

function itemStatusLabel(item: LoanSummary["items"][number]): string {
  if (item.delivered_quantity >= item.requested_quantity && item.requested_quantity > 0) {
    return "Entregado";
  }
  if (item.delivered_quantity > 0) {
    return "Parcial";
  }
  if (item.reserved_quantity > 0) {
    return "Reservado";
  }
  return "Por procesar";
}

function itemStatusClassName(item: LoanSummary["items"][number]): string {
  if (item.delivered_quantity >= item.requested_quantity && item.requested_quantity > 0) {
    return "teacher-loan-item-status teacher-loan-item-status--delivered";
  }
  if (item.delivered_quantity > 0 || item.reserved_quantity > 0) {
    return "teacher-loan-item-status teacher-loan-item-status--partial";
  }
  return "teacher-loan-item-status teacher-loan-item-status--pending";
}

function timelineTransitionTitle(entry: LoanStatusTimelineEntry): string {
  if (entry.from_status == null && entry.to_status === "pending") {
    return "Creacion de Solicitud";
  }

  const transitionKey = `${entry.from_status ?? "new"}->${entry.to_status}`;
  const labels: Record<string, string> = {
    "pending->approved": "Aprobacion de Solicitud",
    "pending->rejected": "Rechazo de Solicitud",
    "pending->cancelled": "Cancelacion de Solicitud",
    "approved->prepared": "Preparacion de Implementos",
    "prepared->delivered": "Entrega de Implementos",
    "delivered->completed": "Cierre de Prestamo",
    "delivered->overdue": "Prestamo Atrasado",
    "overdue->completed": "Cierre de Prestamo",
  };

  return labels[transitionKey] ?? "Actualizacion de Estado";
}

function timelineStatusChipClass(
  status: LoanStatusTimelineEntry["to_status"] | LoanStatusTimelineEntry["from_status"],
): string {
  if (status == null) {
    return "teacher-loan-timeline-chip teacher-loan-timeline-chip--new";
  }
  if (status === "pending") return "teacher-loan-timeline-chip teacher-loan-timeline-chip--pending";
  if (status === "approved" || status === "prepared") return "teacher-loan-timeline-chip teacher-loan-timeline-chip--approved";
  if (status === "delivered") return "teacher-loan-timeline-chip teacher-loan-timeline-chip--delivered";
  if (status === "completed") return "teacher-loan-timeline-chip teacher-loan-timeline-chip--completed";
  if (status === "rejected" || status === "cancelled" || status === "expired" || status === "overdue") {
    return "teacher-loan-timeline-chip teacher-loan-timeline-chip--danger";
  }
  return "teacher-loan-timeline-chip";
}

export function LoanDetailPage({
  loanUuid,
  embedded = false,
}: {
  loanUuid: string;
  embedded?: boolean;
}) {
  const currentRole = getUserRoleFromToken();
  const canEditLoan = currentRole === "DOCENTE";
  const isCoordinator = currentRole === "COORDINADOR";
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreatedBanner, setShowCreatedBanner] = useState(false);
  const [copyFeedback, setCopyFeedback] = useState<"" | "ok" | "error">("");
  const [stateDates, setStateDates] = useState<LoanStateDates | null>(null);
  const [timeline, setTimeline] = useState<LoanStatusTimelineEntry[]>([]);
  const [loadingTraceability, setLoadingTraceability] = useState(false);
  const [processingLoan, setProcessingLoan] = useState(false);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = useState("");

  useEffect(() => {
    const cached = loadLastCreatedLoan(loanUuid);
    if (cached) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLoan(cached);
      setShowCreatedBanner(true);
    }
  }, [loanUuid]);

  useEffect(() => {
    let cancelled = false;

    async function loadDetail() {
      setLoading(true);
      setError(null);
      setLoadingTraceability(true);
      try {
        const [resolved, resolvedStateDates, resolvedTimeline] = await Promise.all([
          fetchLoanByUuid(loanUuid),
          fetchLoanStateDates(loanUuid),
          fetchLoanStatusTimeline(loanUuid),
        ]);
        if (cancelled) {
          return;
        }
        if (!resolved) {
          setError("No se encontro la solicitud indicada.");
          return;
        }
        setLoan(resolved);
        setStateDates(resolvedStateDates);
        setTimeline(resolvedTimeline);
      } catch (requestError) {
        if (cancelled) {
          return;
        }
        setError(getErrorMessage(requestError, "No se pudo cargar el detalle de la solicitud."));
      } finally {
        if (!cancelled) {
          setLoading(false);
          setLoadingTraceability(false);
        }
      }
    }

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [loanUuid]);

  useEffect(() => {
    if (showCreatedBanner) {
      clearLastCreatedLoan();
    }
  }, [showCreatedBanner]);

  const subjectLabel = useMemo(() => {
    if (!loan?.subject) {
      return "Sin asignatura";
    }
    return loan.subject.name;
  }, [loan]);

  const scheduledLabel = useMemo(() => (loan ? formatDateTime(loan.scheduled_at) : "--"), [loan]);
  const createdLabel = useMemo(() => (loan ? formatDateTime(loan.created_at) : "--"), [loan]);

  const totalRequestedItems = useMemo(() => {
    if (!loan) {
      return 0;
    }
    return loan.items.reduce((total, item) => total + item.requested_quantity, 0);
  }, [loan]);

  const canDeliverNow = useMemo(
    () => (loan ? canStartDelivery(loan) : false),
    [loan],
  );

  const canConfirmDeletion = deleteConfirmationInput.trim().toLowerCase() === DELETE_CONFIRM_TEXT;

  function goBackToList() {
    window.location.hash = "#/inventory/prestamos";
  }

  function goToLoanEdit() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/editar`;
  }

  function goToLoanDelivery() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/entrega`;
  }

  async function refreshTraceability(loanId: string) {
    const [resolvedStateDates, resolvedTimeline] = await Promise.all([
      fetchLoanStateDates(loanId),
      fetchLoanStatusTimeline(loanId),
    ]);
    setStateDates(resolvedStateDates);
    setTimeline(resolvedTimeline);
  }

  async function handleCompleteLoan() {
    if (!loan || (loan.status !== "delivered" && loan.status !== "overdue")) {
      return;
    }
    setError(null);
    setProcessingLoan(true);
    try {
      const updated = await completeLoan(loan.uuid);
      setLoan(updated);
      await refreshTraceability(updated.uuid);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo completar el prestamo."));
    } finally {
      setProcessingLoan(false);
    }
  }

  async function copyLoanUuid() {
    if (!loan) {
      return;
    }

    try {
      await navigator.clipboard.writeText(loan.uuid);
      setCopyFeedback("ok");
      window.setTimeout(() => setCopyFeedback(""), 1200);
    } catch {
      setCopyFeedback("error");
      window.setTimeout(() => setCopyFeedback(""), 1500);
    }
  }

  function openDeleteModal() {
    setDeleteConfirmationInput("");
    setShowDeleteModal(true);
  }

  function closeDeleteModal() {
    setDeleteConfirmationInput("");
    setShowDeleteModal(false);
  }

  async function confirmDelete() {
    if (!canConfirmDeletion) {
      return;
    }
    if (!loan) {
      return;
    }
    setError(null);
    setProcessingLoan(true);
    try {
      const cancelled = await cancelLoan(loan.uuid, { notes: "Cancelado por docente desde detalle" });
      setLoan(cancelled);
      await refreshTraceability(cancelled.uuid);
      closeDeleteModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cancelar el prestamo."));
    } finally {
      setProcessingLoan(false);
    }
  }

  const content = (
    <div className="teacher-loan-detail-page">
      <nav className="teacher-loan-detail-backnav">
        <button type="button" className="teacher-loan-detail-backnav__btn" onClick={goBackToList}>
          <ArrowLeft size={16} />
          Volver al listado
        </button>
      </nav>

      {showCreatedBanner && loan ? (
        <div className="success-banner">
          Solicitud creada correctamente para {scheduledLabel} en sala {loan.room?.name ?? "sin sala"}.
        </div>
      ) : null}

      {error ? <div className="error-banner">{error}</div> : null}

      {loading && !loan ? (
        <section className="panel">
          <p className="text-muted">Cargando detalle del prestamo...</p>
        </section>
      ) : null}

      {!loading && !loan ? (
        <section className="panel">
          <p className="text-muted">No se pudo recuperar informacion para esta solicitud.</p>
        </section>
      ) : null}

      {loan ? (
        <>
          <section className="teacher-loan-detail-header">
            <div>
              <p className="teacher-loan-detail-header__eyebrow">UUID DE SOLICITUD</p>
              <div className="teacher-loan-detail-header__uuid">
                <h1>{loan.uuid}</h1>
                <button
                  type="button"
                  className="teacher-loan-detail-copy-btn"
                  onClick={copyLoanUuid}
                  aria-label="Copiar UUID"
                >
                  <Copy size={16} />
                </button>
              </div>
              {copyFeedback === "ok" ? <small>UUID copiado.</small> : null}
              {copyFeedback === "error" ? <small>No se pudo copiar.</small> : null}
            </div>
            <span className={statusClassName(loan.status)}>{normalizeStatusLabel(loan.status)}</span>
          </section>

          <section className="teacher-loan-detail-grid">
            <div className="teacher-loan-detail-grid__left">
              <article className="teacher-loan-detail-card teacher-loan-detail-card--summary">
                <header>
                  <Info size={17} />
                  <h2>Resumen de informacion</h2>
                </header>
                <div className="teacher-loan-detail-info-list">
                  <div>
                    <span>Sala / ubicacion</span>
                    <p>
                      <MapPin size={15} />
                      {loan.room?.name ?? "Sin sala"}
                    </p>
                  </div>
                  <div>
                    <span>Asignatura / practica</span>
                    <p>
                      <BookOpenText size={15} />
                      {subjectLabel}
                    </p>
                  </div>
                  <div>
                    <span>Fecha programada</span>
                    <p>
                      <CalendarDays size={15} />
                      {scheduledLabel}
                    </p>
                  </div>
                  <div>
                    <span>Fecha de creacion</span>
                    <p>
                      <Clock3 size={15} />
                      {createdLabel}
                    </p>
                  </div>
                  <div>
                    <span>Retorno esperado</span>
                    <p>
                      <TimerReset size={15} />
                      {formatDateTime(loan.expected_return_at)}
                    </p>
                  </div>
                  <div>
                    <span>Total solicitado</span>
                    <p>
                      <Package2 size={15} />
                      {totalRequestedItems} unidades
                    </p>
                  </div>
                </div>
              </article>

              <article className="teacher-loan-detail-card teacher-loan-detail-card--actions">
                {canEditLoan ? (
                  <button type="button" className="teacher-loan-detail-action-btn" onClick={goToLoanEdit}>
                    <Edit3 size={16} />
                    Modificar solicitud
                  </button>
                ) : null}
                {isCoordinator && (loan.status === "approved" || loan.status === "prepared") ? (
                  <button
                    type="button"
                    className="teacher-loan-detail-action-btn"
                    onClick={goToLoanDelivery}
                    disabled={!canDeliverNow}
                    title="Registrar entrega de implementos"
                  >
                    <SendHorizontal size={16} />
                    Entregar solicitud
                  </button>
                ) : null}
                {isCoordinator && (loan.status === "delivered" || loan.status === "overdue") ? (
                  <button
                    type="button"
                    className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--complete"
                    onClick={() => void handleCompleteLoan()}
                    disabled={processingLoan}
                  >
                    <CheckCircle2 size={16} />
                    Completar prestamo
                  </button>
                ) : null}
                <button
                  type="button"
                  className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--danger"
                  onClick={openDeleteModal}
                  disabled={processingLoan || !canEditLoan}
                >
                  <Trash2 size={16} />
                  Cancelar solicitud
                </button>
              </article>
            </div>

            <article className="teacher-loan-detail-items">
              <header className="teacher-loan-detail-items__header">
                <h2>
                  <ClipboardList size={18} />
                  Implementos solicitados
                </h2>
                <span>{loan.items.length} item(s)</span>
              </header>

              <div className="teacher-loan-detail-items__table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Descripcion del implemento</th>
                      <th>Solicitado</th>
                      <th>Reservado</th>
                      <th>Entregado</th>
                      <th>Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loan.items.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="teacher-loan-detail-items__empty">
                          Esta solicitud no contiene implementos.
                        </td>
                      </tr>
                    ) : (
                      loan.items.map((item) => (
                        <tr key={item.implement_uuid}>
                          <td>
                            <div className="teacher-loan-detail-item-cell">
                              <div className="teacher-loan-detail-item-cell__thumb">
                                <Package2 size={18} />
                              </div>
                              <div>
                                <strong>{item.implement_name}</strong>
                                <small>{item.implement_uuid}</small>
                              </div>
                            </div>
                          </td>
                          <td>{item.requested_quantity}</td>
                          <td>{item.reserved_quantity}</td>
                          <td>{item.delivered_quantity}</td>
                          <td>
                            <span className={itemStatusClassName(item)}>{itemStatusLabel(item)}</span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <footer className="teacher-loan-detail-items__footer">
                <Info size={16} />
                <p>
                  La reserva de implementos se confirma en funcion del stock disponible y del estado
                  operativo del panol.
                </p>
              </footer>
            </article>
          </section>

          <section className="teacher-loan-detail-grid">
            <article className="teacher-loan-detail-card teacher-loan-detail-card--summary">
              <header>
                <ShieldAlert size={17} />
                <h2>Fechas por estado</h2>
              </header>
              <div className="teacher-loan-detail-info-list">
                <div><span>Aprobado</span><p>{stateDates?.approved_at ? formatDateTime(stateDates.approved_at) : "--"}</p></div>
                <div><span>Preparado</span><p>{stateDates?.prepared_at ? formatDateTime(stateDates.prepared_at) : "--"}</p></div>
                <div><span>Entregado</span><p>{stateDates?.delivered_at ? formatDateTime(stateDates.delivered_at) : "--"}</p></div>
                <div><span>Atrasado</span><p>{stateDates?.overdue_at ? formatDateTime(stateDates.overdue_at) : "--"}</p></div>
                <div><span>Completado</span><p>{stateDates?.completed_at ? formatDateTime(stateDates.completed_at) : "--"}</p></div>
                <div><span>Cancelado</span><p>{stateDates?.cancelled_at ? formatDateTime(stateDates.cancelled_at) : "--"}</p></div>
              </div>
            </article>

            <article className="teacher-loan-detail-items teacher-loan-timeline-panel">
              <header className="teacher-loan-detail-items__header teacher-loan-timeline-panel__header">
                <h2>
                  <ClipboardList size={18} />
                  Historial de Actividad
                </h2>
                <span>{timeline.length} evento(s)</span>
              </header>
              <div className="teacher-loan-timeline-wrap">
                {loadingTraceability ? (
                  <div className="teacher-loan-detail-items__empty">Cargando timeline...</div>
                ) : timeline.length === 0 ? (
                  <div className="teacher-loan-detail-items__empty">Sin eventos de estado.</div>
                ) : (
                  <div className="teacher-loan-timeline-list">
                    {timeline.map((entry, index) => (
                      <article
                        key={entry.history_id}
                        className={`teacher-loan-timeline-entry${index === timeline.length - 1 ? " is-last" : ""}`}
                      >
                        <div className={`teacher-loan-timeline-entry__node${entry.from_status == null ? "" : " is-done"}`}>
                          {entry.from_status == null ? <Plus size={16} /> : <CheckCircle2 size={16} />}
                        </div>
                        <div className="teacher-loan-timeline-entry__card">
                          <div className="teacher-loan-timeline-entry__meta">
                            <div className="teacher-loan-timeline-entry__chips">
                              <span className={timelineStatusChipClass(entry.from_status)}>
                                {entry.from_status ? normalizeStatusLabel(entry.from_status).toUpperCase() : "NUEVO"}
                              </span>
                              <ArrowRight size={13} />
                              <span className={timelineStatusChipClass(entry.to_status)}>
                                {normalizeStatusLabel(entry.to_status).toUpperCase()}
                              </span>
                            </div>
                            <time>{formatDateTime(entry.changed_at)}</time>
                          </div>
                          <h3>{timelineTransitionTitle(entry)}</h3>
                          <p className="teacher-loan-timeline-entry__actor">
                            <User size={14} />
                            {entry.actor_name ?? entry.actor_email ?? `User #${entry.actor_user_id}`}
                          </p>
                          {entry.notes ? (
                            <blockquote className="teacher-loan-timeline-entry__notes">
                              "{entry.notes}"
                            </blockquote>
                          ) : null}
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </article>
          </section>
        </>
      ) : null}

      {showDeleteModal ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-delete-modal">
            <h3>Cancelar prestamo</h3>
            <p>
              Seguro que quieres cancelar esta solicitud? Escribe <strong>"{DELETE_CONFIRM_TEXT}"</strong>{" "}
              para confirmar.
            </p>
            <label htmlFor="loan-delete-detail-confirmation">Confirmacion</label>
            <input
              id="loan-delete-detail-confirmation"
              value={deleteConfirmationInput}
              onChange={(event) => setDeleteConfirmationInput(event.target.value)}
              placeholder={DELETE_CONFIRM_TEXT}
            />
            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeDeleteModal}>
                Cancelar
              </button>
              <button
                type="button"
                className="button button--danger"
                disabled={!canConfirmDeletion}
                onClick={() => void confirmDelete()}
              >
                <Trash2 size={16} />
                {processingLoan ? "Cancelando..." : "Cancelar"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
