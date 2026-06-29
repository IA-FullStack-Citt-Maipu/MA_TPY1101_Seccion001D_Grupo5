import {
  ArrowRight,
  ArrowLeft,
  BookOpenText,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ClipboardList,
  Clock3,
  Edit3,
  Info,
  MapPin,
  Minus,
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
  fetchLoanReturnContext,
  fetchLoanStateDates,
  fetchLoanStatusTimeline,
  returnLoan,
} from "../services/loanService";
import {
  clearLastCreatedLoan,
  loadLastCreatedLoan,
} from "../services/loanSessionService";
import type { LoanReturnContextItem, LoanStateDates, LoanStatusTimelineEntry, LoanSummary } from "../types/loan";
import { getSessionUser, getSessionUserRole } from "../utils/auth";
import { canStartDelivery, canStartPreparation } from "../utils/loanSchedule";
import { canRequesterCancelLoan } from "../utils/loanStatus";

const DELETE_CONFIRM_TEXT = "eliminar";
const SYSTEM_OUTBOX_NAME = "SISTEMA_OUTBOX";
const SYSTEM_OUTBOX_EMAIL = "sistema.outbox@duocuc.cl";

interface ReturnItemState {
  implementUuid: string;
  implementName: string;
  deliveredQuantity: number;
  pendingReturnQuantity: number;
  returnedQuantity: number;
  itemType: LoanReturnContextItem["item_type"] | "unknown";
  individuals: ReturnIndividualState[];
}

interface ReturnIndividualState {
  individualUuid: string;
  assetCode: string;
  returnCondition: "good" | "damaged" | "lost" | "discarded";
}

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
    approved: "Reservado",
    prepared: "Preparado",
    delivered: "En uso",
    overdue: "Atrasado",
    completed: "Finalizado",
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

function timelineTransitionTitle(entry: LoanStatusTimelineEntry): string {
  if (entry.from_status == null && entry.to_status === "approved") {
    return "Reserva automatica";
  }

  const transitionKey = `${entry.from_status ?? "new"}->${entry.to_status}`;
  const labels: Record<string, string> = {
    "approved->approved": "Reserva actualizada",
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

function isSystemTimelineActor(entry: Pick<LoanStatusTimelineEntry, "actor_name" | "actor_email">): boolean {
  return entry.actor_name?.trim() === SYSTEM_OUTBOX_NAME
    || entry.actor_email?.trim()?.toLowerCase() === SYSTEM_OUTBOX_EMAIL;
}

function resolveTimelineActorLabel(entry: LoanStatusTimelineEntry): string {
  if (isSystemTimelineActor(entry)) {
    return "Sistema";
  }
  return entry.actor_name?.trim() || entry.actor_email?.trim() || `User #${entry.actor_user_id}`;
}

export function LoanDetailPage({
  loanUuid,
  embedded = false,
  hideBackNav = false,
  onLoanChanged,
}: {
  loanUuid: string;
  embedded?: boolean;
  hideBackNav?: boolean;
  onLoanChanged?: (loan: LoanSummary) => void;
}) {
  const currentRole = getSessionUserRole();
  const currentUser = getSessionUser();
  const isCoordinator = currentRole === "COORDINADOR";
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreatedBanner, setShowCreatedBanner] = useState(false);
  const [stateDates, setStateDates] = useState<LoanStateDates | null>(null);
  const [timeline, setTimeline] = useState<LoanStatusTimelineEntry[]>([]);
  const [loadingTraceability, setLoadingTraceability] = useState(false);
  const [processingLoan, setProcessingLoan] = useState(false);
  const [isTimelineExpanded, setIsTimelineExpanded] = useState(false);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = useState("");
  const [deleteNotes, setDeleteNotes] = useState("");
  const [showCompleteModal, setShowCompleteModal] = useState(false);
  const [completionNotes, setCompletionNotes] = useState("");
  const [returnItems, setReturnItems] = useState<ReturnItemState[]>([]);
  const [loadingReturnItems, setLoadingReturnItems] = useState(false);
  const [returnMode, setReturnMode] = useState<"all_good" | "variation" | null>(null);

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

  const canPrepareNow = useMemo(
    () => (loan ? canStartPreparation(loan) : false),
    [loan],
  );
  const canDeliverNow = useMemo(
    () => (loan ? canStartDelivery(loan) : false),
    [loan],
  );
  const isRequester = loan?.requester_uuid === currentUser?.id;
  const canModifyLoan = Boolean(loan && isRequester && loan.status === "approved");
  const canCancelLoan = Boolean(loan && isRequester && canRequesterCancelLoan(loan.status));
  const hasVisibleActions = Boolean(
      canModifyLoan ||
      canCancelLoan ||
      (isCoordinator && loan && (loan.status === "approved" || loan.status === "prepared")) ||
      (isCoordinator && loan && (loan.status === "delivered" || loan.status === "overdue")),
  );
  const requesterDisplay = useMemo(() => {
    const creationEntry = timeline.find((entry) => entry.from_status == null) ?? timeline[0];
    if (!creationEntry) {
      return loadingTraceability ? "Cargando solicitante..." : "Solicitante no disponible";
    }
    if (isSystemTimelineActor(creationEntry)) {
      if (loan?.requester_uuid === currentUser?.id && currentUser?.name) {
        return currentUser.name;
      }
      return "Solicitante registrado";
    }
    return creationEntry.actor_name?.trim() || creationEntry.actor_email?.trim() || "Solicitante no disponible";
  }, [currentUser?.id, currentUser?.name, loadingTraceability, loan?.requester_uuid, timeline]);
  const orderedTimeline = useMemo(
    () =>
      [...timeline].sort((left, right) => {
        const leftDate = parseDate(left.changed_at)?.getTime() ?? 0;
        const rightDate = parseDate(right.changed_at)?.getTime() ?? 0;
        if (rightDate !== leftDate) {
          return rightDate - leftDate;
        }
        return right.history_id - left.history_id;
      }),
    [timeline],
  );
  const timelineContentId = `loan-timeline-${loanUuid}`;

  const canConfirmDeletion = deleteConfirmationInput.trim().toLowerCase() === DELETE_CONFIRM_TEXT;
  const returnableReusableItems = useMemo(
    () =>
      returnItems.filter(
        (item) =>
          item.pendingReturnQuantity > 0 && item.itemType === "reusable",
      ),
    [returnItems],
  );
  const returnableActiveItems = useMemo(
    () =>
      returnItems.filter(
        (item) => item.pendingReturnQuantity > 0 && item.itemType === "individual",
      ),
    [returnItems],
  );
  const selectedReturnModeLabel = useMemo(() => {
    if (returnMode === "all_good") {
      return "Devolucion completa sin variaciones";
    }
    if (returnMode === "variation") {
      return "Devolucion con variaciones por cantidad o condicion";
    }
    return null;
  }, [returnMode]);

  function goBackToList() {
    window.location.hash = "#/inventory/prestamos";
  }

  function goToLoanEdit() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/editar`;
  }

  function goToLoanPreparation() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/preparacion`;
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

  async function loadReturnItems(currentLoan: LoanSummary) {
    setLoadingReturnItems(true);
    try {
      const context = await fetchLoanReturnContext(currentLoan.uuid);
      const rows: ReturnItemState[] = context.items.map((item) => ({
        implementUuid: item.implement_uuid,
        implementName: item.implement_name,
        deliveredQuantity: item.delivered_quantity,
        pendingReturnQuantity: item.pending_return_quantity,
        returnedQuantity: item.pending_return_quantity,
        itemType: (item.item_type ?? "unknown") as ReturnItemState["itemType"],
        individuals: item.individuals.map((individual) => ({
          individualUuid: individual.individual_uuid,
          assetCode: individual.asset_code,
          returnCondition: "good" as const,
        })),
      }));
      setReturnItems(rows);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar el contexto de devolucion."));
    } finally {
      setLoadingReturnItems(false);
    }
  }

  function openCompleteModal() {
    if (!loan || (loan.status !== "delivered" && loan.status !== "overdue")) {
      return;
    }
    setCompletionNotes("");
    setReturnMode(null);
    setShowCompleteModal(true);
    void loadReturnItems(loan);
  }

  function closeCompleteModal() {
    setShowCompleteModal(false);
    setCompletionNotes("");
    setReturnItems([]);
    setReturnMode(null);
  }

  function adjustReturnQuantity(implementUuid: string, delta: number) {
    setReturnItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid) {
          return item;
        }
        const next = Math.max(0, Math.min(item.pendingReturnQuantity, item.returnedQuantity + delta));
        return { ...item, returnedQuantity: next };
      }),
    );
  }

  function updateIndividualReturnCondition(
    implementUuid: string,
    individualUuid: string,
    returnCondition: ReturnIndividualState["returnCondition"],
  ) {
    setReturnItems((previous) =>
      previous.map((item) =>
        item.implementUuid !== implementUuid
          ? item
          : {
              ...item,
              individuals: item.individuals.map((individual) =>
                individual.individualUuid === individualUuid
                  ? { ...individual, returnCondition }
                  : individual,
              ),
            },
      ),
    );
  }

  async function handleCompleteLoan() {
    if (!loan || (loan.status !== "delivered" && loan.status !== "overdue")) {
      return;
    }
    if (loadingReturnItems) {
      return;
    }
    if (returnMode == null) {
      setError("Selecciona como registrar la devolucion antes de continuar.");
      return;
    }

    setError(null);
    setProcessingLoan(true);
    try {
      const notes = completionNotes.trim() || null;
      const updated = returnMode === "variation"
        ? await returnLoan(loan.uuid, {
            notes,
            consumable_returns: returnableReusableItems.map((item) => ({
              implement_uuid: item.implementUuid,
              quantity: item.returnedQuantity,
            })),
            returned_individuals: returnableActiveItems.flatMap((item) =>
              item.individuals.map((individual) => ({
                individual_uuid: individual.individualUuid,
                return_condition: individual.returnCondition,
              })),
            ),
          })
        : await completeLoan(loan.uuid, { notes });
      setLoan(updated);
      onLoanChanged?.(updated);
      await refreshTraceability(updated.uuid);
      closeCompleteModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo completar el prestamo."));
    } finally {
      setProcessingLoan(false);
    }
  }

  function openDeleteModal() {
    setDeleteConfirmationInput("");
    setDeleteNotes("");
    setShowDeleteModal(true);
  }

  function closeDeleteModal() {
    setDeleteConfirmationInput("");
    setDeleteNotes("");
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
      const cancelled = await cancelLoan(loan.uuid, { notes: deleteNotes.trim() || null });
      setLoan(cancelled);
      onLoanChanged?.(cancelled);
      await refreshTraceability(cancelled.uuid);
      closeDeleteModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cancelar el prestamo."));
    } finally {
      setProcessingLoan(false);
    }
  }

  const content = (
    <div className={`teacher-loan-detail-page${embedded ? " teacher-loan-detail-page--embedded" : ""}`}>
      {!hideBackNav ? (
        <nav className="teacher-loan-detail-backnav">
          <button type="button" className="teacher-loan-detail-backnav__btn" onClick={goBackToList}>
            <ArrowLeft size={16} />
            Volver al listado
          </button>
        </nav>
      ) : null}

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
            <div className="teacher-loan-detail-header__copy">
              <p className="teacher-loan-detail-header__eyebrow">Solicitud de prestamo</p>
              <h1>Detalle de solicitud</h1>
              <p className="teacher-loan-detail-header__requester">
                <User size={15} />
                <span>Solicitante:</span>
                <strong>{requesterDisplay}</strong>
              </p>
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

              {hasVisibleActions ? (
                <article className="teacher-loan-detail-card teacher-loan-detail-card--actions">
                  {canModifyLoan ? (
                    <button type="button" className="teacher-loan-detail-action-btn" onClick={goToLoanEdit}>
                      <Edit3 size={16} />
                      Modificar solicitud
                    </button>
                  ) : null}
                  {isCoordinator && loan.status === "approved" ? (
                    <button
                      type="button"
                      className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--complete"
                      onClick={goToLoanPreparation}
                      disabled={processingLoan || !canPrepareNow}
                      title="Abrir preparacion operativa"
                    >
                      <CheckCircle2 size={16} />
                      Preparar implementos
                    </button>
                  ) : null}
                  {isCoordinator && loan.status === "prepared" ? (
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
                      onClick={openCompleteModal}
                      disabled={processingLoan}
                    >
                      <CheckCircle2 size={16} />
                      Registrar devolucion
                    </button>
                  ) : null}
                  {canCancelLoan ? (
                    <button
                      type="button"
                      className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--danger"
                      onClick={openDeleteModal}
                      disabled={processingLoan}
                    >
                      <Trash2 size={16} />
                      Cancelar solicitud
                    </button>
                  ) : null}
                </article>
              ) : null}
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
                      <th>Reservado</th>
                      <th>Entregado</th>
                      <th>Devuelto</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loan.items.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="teacher-loan-detail-items__empty">
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
                              <strong>{item.implement_name}</strong>
                            </div>
                          </td>
                          <td>{item.reserved_quantity}</td>
                          <td>{item.delivered_quantity}</td>
                          <td>{item.returned_quantity}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <footer className="teacher-loan-detail-items__footer">
                <Info size={16} />
                <p>
                  La reserva se realiza automaticamente al crear la solicitud y la preparacion fisica ocurre
                  mas cerca de la fecha programada.
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
                <div><span>Reservado</span><p>{stateDates?.approved_at ? formatDateTime(stateDates.approved_at) : "--"}</p></div>
                <div><span>Preparado</span><p>{stateDates?.prepared_at ? formatDateTime(stateDates.prepared_at) : "--"}</p></div>
                <div><span>Entregado</span><p>{stateDates?.delivered_at ? formatDateTime(stateDates.delivered_at) : "--"}</p></div>
                <div><span>Atrasado</span><p>{stateDates?.overdue_at ? formatDateTime(stateDates.overdue_at) : "--"}</p></div>
                <div><span>Finalizado</span><p>{stateDates?.completed_at ? formatDateTime(stateDates.completed_at) : "--"}</p></div>
                <div><span>Cancelado</span><p>{stateDates?.cancelled_at ? formatDateTime(stateDates.cancelled_at) : "--"}</p></div>
              </div>
            </article>

            <article className="teacher-loan-detail-items teacher-loan-timeline-panel">
              <header className="teacher-loan-detail-items__header teacher-loan-timeline-panel__header">
                <button
                  type="button"
                  className="teacher-loan-timeline-toggle teacher-loan-timeline-toggle--header"
                  aria-expanded={isTimelineExpanded}
                  aria-controls={timelineContentId}
                  onClick={() => setIsTimelineExpanded((current) => !current)}
                >
                  <div className="teacher-loan-timeline-toggle__summary">
                    <h2>
                      <ClipboardList size={18} />
                      Historial de Actividad
                    </h2>
                    <span>{timeline.length} evento(s)</span>
                  </div>
                  <ChevronDown size={18} className={isTimelineExpanded ? "is-open" : ""} />
                </button>
              </header>
              {isTimelineExpanded ? (
                <div id={timelineContentId} className="teacher-loan-timeline-wrap">
                  {loadingTraceability ? (
                    <div className="teacher-loan-detail-items__empty">Cargando timeline...</div>
                  ) : orderedTimeline.length === 0 ? (
                    <div className="teacher-loan-detail-items__empty">Sin eventos de estado.</div>
                  ) : (
                    <div className="teacher-loan-timeline-list">
                      {orderedTimeline.map((entry, index) => (
                        <article
                          key={entry.history_id}
                          className={`teacher-loan-timeline-entry${index === orderedTimeline.length - 1 ? " is-last" : ""}`}
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
                              {resolveTimelineActorLabel(entry)}
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
              ) : null}
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
            <label htmlFor="loan-delete-detail-notes">Notas (opcional)</label>
            <textarea
              id="loan-delete-detail-notes"
              rows={3}
              value={deleteNotes}
              maxLength={1000}
              onChange={(event) => setDeleteNotes(event.target.value)}
              placeholder="Motivo u observacion de cancelacion..."
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

      {showCompleteModal ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-return-modal">
            <h3>Registrar devolucion</h3>
            <p>Define si todo fue devuelto correctamente o si hubo variacion antes de cerrar el prestamo.</p>

            <label htmlFor="loan-complete-notes">Notas (opcional)</label>
            <textarea
              id="loan-complete-notes"
              rows={3}
              value={completionNotes}
              maxLength={1000}
              onChange={(event) => setCompletionNotes(event.target.value)}
              placeholder="Observaciones del cierre o devolucion..."
            />

            {loadingReturnItems ? <p className="text-muted">Cargando contexto de devolucion...</p> : null}

            {!loadingReturnItems ? (
              <div className="loan-return-choice-group">
                <p className="loan-return-choice-group__label">Como quieres registrar esta devolucion?</p>
                <div className="loan-return-choice-list">
                  <button
                    type="button"
                    className={`loan-return-choice${returnMode === "all_good" ? " is-selected" : ""}`}
                    onClick={() => setReturnMode("all_good")}
                    aria-pressed={returnMode === "all_good"}
                  >
                    <span className="loan-return-choice__copy">
                      <strong>Se devolvio todo correctamente</strong>
                      <small>Cierra todos los implementos retornables como devueltos en buen estado.</small>
                    </span>
                    <span className="loan-return-choice__state">
                      {returnMode === "all_good" ? "Seleccionado" : "Seleccionar"}
                    </span>
                  </button>

                  <button
                    type="button"
                    className={`loan-return-choice${returnMode === "variation" ? " is-selected" : ""}`}
                    onClick={() => setReturnMode("variation")}
                    aria-pressed={returnMode === "variation"}
                  >
                    <span className="loan-return-choice__copy">
                      <strong>Hubo variacion en la cantidad devuelta</strong>
                      <small>Permite ajustar cantidades reutilizables y clasificar activos por condicion.</small>
                    </span>
                    <span className="loan-return-choice__state">
                      {returnMode === "variation" ? "Seleccionado" : "Seleccionar"}
                    </span>
                  </button>
                </div>
                <p className={`loan-return-choice__hint${returnMode == null ? "" : " is-selected"}`}>
                  {selectedReturnModeLabel ?? "Selecciona una opcion para habilitar la confirmacion de devolucion."}
                </p>
              </div>
            ) : null}

            {!loadingReturnItems && returnMode === "variation" && returnableReusableItems.length > 0 ? (
              <div className="loan-return-list">
                <div className="loan-return-section__header">
                  <strong>Implementos reutilizables</strong>
                  <p>Indica cuantas unidades volvieron efectivamente. La diferencia quedara registrada como consumo o merma.</p>
                </div>
                {returnableReusableItems.map((item) => {
                  const consumedQuantity = item.pendingReturnQuantity - item.returnedQuantity;
                  return (
                    <article key={item.implementUuid} className="loan-return-row">
                      <div>
                        <strong>{item.implementName}</strong>
                        <p>
                          Entregado: {item.deliveredQuantity} | Pendiente: {item.pendingReturnQuantity} | Devuelto: {item.returnedQuantity} | Consumido: {consumedQuantity}
                        </p>
                      </div>
                      <div className="loan-stepper">
                        <button
                          type="button"
                          onClick={() => adjustReturnQuantity(item.implementUuid, -1)}
                          disabled={item.returnedQuantity <= 0 || processingLoan}
                        >
                          <Minus size={14} />
                        </button>
                        <span className="loan-stepper__value">{item.returnedQuantity}</span>
                        <button
                          type="button"
                          onClick={() => adjustReturnQuantity(item.implementUuid, 1)}
                          disabled={item.returnedQuantity >= item.pendingReturnQuantity || processingLoan}
                        >
                          <Plus size={14} />
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : null}

            {!loadingReturnItems && returnMode === "variation" && returnableActiveItems.length > 0 ? (
              <div className="loan-return-list">
                <div className="loan-return-section__header">
                  <strong>Activos entregados</strong>
                  <p>Selecciona la condicion final de cada unidad para dejar claro que vuelve disponible y que no.</p>
                </div>
                {returnableActiveItems.map((item) => (
                  <article key={item.implementUuid} className="loan-return-row loan-return-row--stacked">
                    <div>
                      <strong>{item.implementName}</strong>
                      <p>Activo entregado: {item.deliveredQuantity} | Pendiente: {item.pendingReturnQuantity}</p>
                    </div>
                    <div className="loan-return-individuals">
                      {item.individuals.map((individual) => (
                        <label key={individual.individualUuid} className="loan-return-individual">
                          <span>{individual.assetCode}</span>
                          <select
                            value={individual.returnCondition}
                            onChange={(event) =>
                              updateIndividualReturnCondition(
                                item.implementUuid,
                                individual.individualUuid,
                                event.target.value as ReturnIndividualState["returnCondition"],
                              )
                            }
                            disabled={processingLoan}
                          >
                            <option value="good">Devuelto bueno</option>
                            <option value="damaged">Devuelto danado</option>
                            <option value="lost">Perdido</option>
                            <option value="discarded">Descartado</option>
                          </select>
                        </label>
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            ) : null}

            {!loadingReturnItems && returnMode === "variation" && returnableReusableItems.length === 0 && returnableActiveItems.length === 0 ? (
              <p className="text-muted">No hay implementos retornables pendientes para esta solicitud.</p>
            ) : null}

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeCompleteModal} disabled={processingLoan}>
                Cancelar
              </button>
              <button
                type="button"
                className="button"
                disabled={processingLoan || loadingReturnItems || returnMode == null}
                onClick={() => void handleCompleteLoan()}
              >
                <CheckCircle2 size={16} />
                {processingLoan ? "Registrando..." : returnMode === "variation" ? "Registrar variacion" : "Confirmar devolucion"}
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
