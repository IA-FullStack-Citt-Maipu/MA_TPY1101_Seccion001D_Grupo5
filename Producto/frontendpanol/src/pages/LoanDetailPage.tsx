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
  XCircle,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { LoanApprovalModal, type LoanApprovalSubmission } from "../components/loans/LoanApprovalModal";
import { getErrorMessage } from "../services/apiClient";
import {
  cancelLoan,
  completeLoan,
  fetchLoanByUuid,
  fetchLoanStateDates,
  fetchLoanStatusTimeline,
  reviewLoan,
  returnLoan,
} from "../services/loanService";
import { fetchImplementStock } from "../services/stockService";
import {
  clearLastCreatedLoan,
  loadLastCreatedLoan,
} from "../services/loanSessionService";
import type { LoanStateDates, LoanStatusTimelineEntry, LoanSummary } from "../types/loan";
import type { StockDetail } from "../types/stock";
import { getSessionUser, getSessionUserRole } from "../utils/auth";
import { canStartDelivery } from "../utils/loanSchedule";

const DELETE_CONFIRM_TEXT = "eliminar";

interface ReturnItemState {
  implementUuid: string;
  implementName: string;
  deliveredQuantity: number;
  returnedQuantity: number;
  itemType: NonNullable<StockDetail["item_type"]> | "unknown";
  stockError: string | null;
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

function itemStatusLabel(item: LoanSummary["items"][number], loanStatus: LoanSummary["status"]): string {
  const plannedQuantity = item.reserved_quantity > 0 || loanStatus !== "pending"
    ? item.reserved_quantity
    : item.requested_quantity;

  if (item.delivered_quantity >= plannedQuantity && plannedQuantity > 0) {
    return "Entregado";
  }
  if (item.delivered_quantity > 0) {
    return "Parcial";
  }
  if (item.reserved_quantity > 0) {
    return item.reserved_quantity < item.requested_quantity ? "Reservado parcial" : "Reservado";
  }
  if (loanStatus !== "pending" && item.requested_quantity > 0) {
    return "Sin reserva";
  }
  return "Por procesar";
}

function itemStatusClassName(item: LoanSummary["items"][number], loanStatus: LoanSummary["status"]): string {
  const plannedQuantity = item.reserved_quantity > 0 || loanStatus !== "pending"
    ? item.reserved_quantity
    : item.requested_quantity;

  if (item.delivered_quantity >= plannedQuantity && plannedQuantity > 0) {
    return "teacher-loan-item-status teacher-loan-item-status--delivered";
  }
  if (item.delivered_quantity > 0 || item.reserved_quantity > 0) {
    return "teacher-loan-item-status teacher-loan-item-status--partial";
  }
  if (loanStatus !== "pending" && item.requested_quantity > 0) {
    return "teacher-loan-item-status teacher-loan-item-status--danger";
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

function resolveTimelineActorLabel(entry: LoanStatusTimelineEntry): string {
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
  const [reviewDecision, setReviewDecision] = useState<"APPROVE" | "REJECT" | null>(null);
  const [reviewNotes, setReviewNotes] = useState("");
  const [showCompleteModal, setShowCompleteModal] = useState(false);
  const [completionNotes, setCompletionNotes] = useState("");
  const [returnItems, setReturnItems] = useState<ReturnItemState[]>([]);
  const [loadingReturnItems, setLoadingReturnItems] = useState(false);
  const [returnVariationConfirm, setReturnVariationConfirm] = useState(false);

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
  const isRequester = loan?.requester_uuid === currentUser?.id;
  const canModifyLoan = Boolean(loan && isRequester && loan.status === "pending");
  const canCancelLoan = canModifyLoan;
  const canReviewLoan = Boolean(isCoordinator && loan && !isRequester && loan.status === "pending");
  const hasVisibleActions = Boolean(
    canReviewLoan ||
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
    return creationEntry.actor_name?.trim() || creationEntry.actor_email?.trim() || "Solicitante no disponible";
  }, [loadingTraceability, timeline]);
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
  const canSubmitRejectionReview = reviewDecision === "REJECT" && reviewNotes.trim().length >= 4;
  const returnableFungibleItems = useMemo(
    () =>
      returnItems.filter(
        (item) =>
          item.deliveredQuantity > 0 &&
          (item.itemType === "consumable" || item.itemType === "reusable"),
      ),
    [returnItems],
  );
  const hasReturnVariation = useMemo(
    () => returnableFungibleItems.some((item) => item.returnedQuantity !== item.deliveredQuantity),
    [returnableFungibleItems],
  );

  function goBackToList() {
    window.location.hash = "#/inventory/prestamos";
  }

  function goToLoanEdit() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/editar`;
  }

  function goToLoanDelivery() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/entrega`;
  }

  function openReviewModal(decision: "APPROVE" | "REJECT") {
    setReviewDecision(decision);
    setReviewNotes("");
  }

  function closeReviewModal() {
    setReviewDecision(null);
    setReviewNotes("");
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
      const rows: ReturnItemState[] = await Promise.all(
        currentLoan.items.map(async (item) => {
          if (item.delivered_quantity <= 0) {
            return {
              implementUuid: item.implement_uuid,
              implementName: item.implement_name,
              deliveredQuantity: item.delivered_quantity,
              returnedQuantity: 0,
              itemType: "unknown" as const,
              stockError: null,
            };
          }

          try {
            const stock = await fetchImplementStock(item.implement_uuid);
            return {
              implementUuid: item.implement_uuid,
              implementName: item.implement_name,
              deliveredQuantity: item.delivered_quantity,
              returnedQuantity: item.delivered_quantity,
              itemType: (stock.item_type ?? "unknown") as ReturnItemState["itemType"],
              stockError: null,
            };
          } catch (requestError) {
            return {
              implementUuid: item.implement_uuid,
              implementName: item.implement_name,
              deliveredQuantity: item.delivered_quantity,
              returnedQuantity: item.delivered_quantity,
              itemType: "unknown" as const,
              stockError: getErrorMessage(requestError, "No se pudo consultar el tipo de implemento."),
            };
          }
        }),
      );
      setReturnItems(rows);
    } finally {
      setLoadingReturnItems(false);
    }
  }

  async function submitApprovalReview(payload: LoanApprovalSubmission) {
    if (!loan || reviewDecision !== "APPROVE") {
      return;
    }

    setError(null);
    setProcessingLoan(true);
    try {
      const updated = await reviewLoan(loan.uuid, {
        decision: "APPROVE",
        notes: payload.notes,
        items: payload.items,
      });
      setLoan(updated);
      onLoanChanged?.(updated);
      await refreshTraceability(updated.uuid);
      closeReviewModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo aprobar la solicitud."));
    } finally {
      setProcessingLoan(false);
    }
  }

  async function submitRejectReview() {
    if (!loan || reviewDecision !== "REJECT" || !canSubmitRejectionReview) {
      return;
    }

    setError(null);
    setProcessingLoan(true);
    try {
      const updated = await reviewLoan(loan.uuid, {
        decision: "REJECT",
        notes: reviewNotes.trim(),
      });
      setLoan(updated);
      onLoanChanged?.(updated);
      await refreshTraceability(updated.uuid);
      closeReviewModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo rechazar la solicitud."));
    } finally {
      setProcessingLoan(false);
    }
  }

  function openCompleteModal() {
    if (!loan || (loan.status !== "delivered" && loan.status !== "overdue")) {
      return;
    }
    setCompletionNotes("");
    setReturnVariationConfirm(false);
    setShowCompleteModal(true);
    void loadReturnItems(loan);
  }

  function closeCompleteModal() {
    setShowCompleteModal(false);
    setCompletionNotes("");
    setReturnItems([]);
    setReturnVariationConfirm(false);
  }

  function adjustReturnQuantity(implementUuid: string, delta: number) {
    setReturnVariationConfirm(false);
    setReturnItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid) {
          return item;
        }
        const next = Math.max(0, Math.min(item.deliveredQuantity, item.returnedQuantity + delta));
        return { ...item, returnedQuantity: next };
      }),
    );
  }

  async function handleCompleteLoan(confirmedVariation = false) {
    if (!loan || (loan.status !== "delivered" && loan.status !== "overdue")) {
      return;
    }
    if (loadingReturnItems) {
      return;
    }
    if (hasReturnVariation && !confirmedVariation) {
      setReturnVariationConfirm(true);
      return;
    }

    setError(null);
    setProcessingLoan(true);
    try {
      const notes = completionNotes.trim() || null;
      const updated = hasReturnVariation
        ? await returnLoan(loan.uuid, {
            notes,
            consumable_returns: returnableFungibleItems.map((item) => ({
              implement_uuid: item.implementUuid,
              quantity: item.returnedQuantity,
            })),
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
                  {canReviewLoan ? (
                    <>
                      <button
                        type="button"
                        className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--complete"
                        onClick={() => openReviewModal("APPROVE")}
                        disabled={processingLoan}
                      >
                        <CheckCircle2 size={16} />
                        Aprobar solicitud
                      </button>
                      <button
                        type="button"
                        className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--danger"
                        onClick={() => openReviewModal("REJECT")}
                        disabled={processingLoan}
                      >
                        <XCircle size={16} />
                        Rechazar solicitud
                      </button>
                    </>
                  ) : null}
                  {canModifyLoan ? (
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
                      onClick={openCompleteModal}
                      disabled={processingLoan}
                    >
                      <CheckCircle2 size={16} />
                      Completar prestamo
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
                              <strong>{item.implement_name}</strong>
                            </div>
                          </td>
                          <td>{item.requested_quantity}</td>
                          <td>{item.reserved_quantity}</td>
                          <td>{item.delivered_quantity}</td>
                          <td>
                            <span className={itemStatusClassName(item, loan.status)}>{itemStatusLabel(item, loan.status)}</span>
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

      {reviewDecision === "APPROVE" && loan ? (
        <LoanApprovalModal
          loan={loan}
          processing={processingLoan}
          onClose={closeReviewModal}
          onSubmit={submitApprovalReview}
        />
      ) : null}

      {reviewDecision === "REJECT" ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-review-modal">
            <h3>Rechazar solicitud</h3>
            <p>Escribe una nota para dejar registrado el motivo del rechazo.</p>
            <label htmlFor="loan-review-notes">Notas</label>
            <textarea
              id="loan-review-notes"
              rows={4}
              value={reviewNotes}
              maxLength={1000}
              onChange={(event) => setReviewNotes(event.target.value)}
              placeholder="Motivo del rechazo..."
            />
            {reviewNotes.trim().length > 0 && reviewNotes.trim().length < 4 ? (
              <small className="field-error">La nota de rechazo debe tener al menos 4 caracteres.</small>
            ) : null}
            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeReviewModal} disabled={processingLoan}>
                Cancelar
              </button>
              <button
                type="button"
                className="button button--danger"
                disabled={!canSubmitRejectionReview || processingLoan}
                onClick={() => void submitRejectReview()}
              >
                <XCircle size={16} />
                {processingLoan ? "Rechazando..." : "Rechazar"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {showCompleteModal ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-return-modal">
            <h3>Completar prestamo</h3>
            <p>Registra notas opcionales y confirma las cantidades devueltas antes de cerrar el prestamo.</p>

            <label htmlFor="loan-complete-notes">Notas (opcional)</label>
            <textarea
              id="loan-complete-notes"
              rows={3}
              value={completionNotes}
              maxLength={1000}
              onChange={(event) => setCompletionNotes(event.target.value)}
              placeholder="Observaciones del cierre o devolucion..."
            />

            {loadingReturnItems ? (
              <p className="text-muted">Cargando cantidades entregadas...</p>
            ) : null}

            {!loadingReturnItems && returnableFungibleItems.length > 0 ? (
              <div className="loan-return-list">
                {returnableFungibleItems.map((item) => {
                  const consumedQuantity = item.deliveredQuantity - item.returnedQuantity;
                  return (
                    <article key={item.implementUuid} className="loan-return-row">
                      <div>
                        <strong>{item.implementName}</strong>
                        <p>Entregado: {item.deliveredQuantity} | Devuelto: {item.returnedQuantity} | Usado/gastado: {consumedQuantity}</p>
                        {item.stockError ? <small className="field-error">{item.stockError}</small> : null}
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
                          disabled={item.returnedQuantity >= item.deliveredQuantity || processingLoan}
                        >
                          <Plus size={14} />
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : null}

            {!loadingReturnItems && returnableFungibleItems.length === 0 ? (
              <p className="text-muted">
                No hay consumibles o reutilizables entregados para ajustar. Los individuales se cerraran como devueltos en buen estado.
              </p>
            ) : null}

            {returnVariationConfirm ? (
              <div className="loan-return-warning">
                Se esta cerrando con diferencias entre lo entregado y lo devuelto. Seguro que quieres seguir?
              </div>
            ) : null}

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeCompleteModal} disabled={processingLoan}>
                Cancelar
              </button>
              <button
                type="button"
                className="button"
                disabled={processingLoan || loadingReturnItems}
                onClick={() => void handleCompleteLoan(returnVariationConfirm)}
              >
                <CheckCircle2 size={16} />
                {processingLoan
                  ? "Completando..."
                  : returnVariationConfirm
                    ? "Si, cerrar con variacion"
                    : "Completar prestamo"}
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
