import {
  ArrowLeft,
  BookOpenText,
  CalendarDays,
  ClipboardList,
  Clock3,
  Copy,
  Edit3,
  Info,
  MapPin,
  Package2,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { fetchLoanByUuid } from "../services/loanService";
import {
  clearLastCreatedLoan,
  loadLastCreatedLoan,
} from "../services/loanSessionService";
import type { LoanSummary } from "../types/loan";
import { getUserRoleFromToken } from "../utils/auth";

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
    delivered: "En uso",
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
  if (status === "delivered") {
    return "teacher-loans-status teacher-loans-status--delivered";
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

export function LoanDetailPage({
  loanUuid,
  embedded = false,
}: {
  loanUuid: string;
  embedded?: boolean;
}) {
  const currentRole = getUserRoleFromToken();
  const canEditLoan = currentRole === "DOCENTE";
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreatedBanner, setShowCreatedBanner] = useState(false);
  const [copyFeedback, setCopyFeedback] = useState<"" | "ok" | "error">("");

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = useState("");

  useEffect(() => {
    const cached = loadLastCreatedLoan(loanUuid);
    if (cached) {
      setLoan(cached);
      setShowCreatedBanner(true);
    }
  }, [loanUuid]);

  useEffect(() => {
    let cancelled = false;

    async function loadDetail() {
      setLoading(true);
      setError(null);
      try {
        const resolved = await fetchLoanByUuid(loanUuid);
        if (cancelled) {
          return;
        }
        if (!resolved) {
          setError("No se encontro la solicitud indicada.");
          return;
        }
        setLoan(resolved);
      } catch (requestError) {
        if (cancelled) {
          return;
        }
        setError(getErrorMessage(requestError, "No se pudo cargar el detalle de la solicitud."));
      } finally {
        if (!cancelled) {
          setLoading(false);
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

  const canConfirmDeletion = deleteConfirmationInput.trim().toLowerCase() === DELETE_CONFIRM_TEXT;

  function goBackToList() {
    window.location.hash = "#/inventory/prestamos";
  }

  function goToLoanEdit() {
    window.location.hash = `#/inventory/prestamos/${loanUuid}/editar`;
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

  function confirmDelete() {
    if (!canConfirmDeletion) {
      return;
    }
    closeDeleteModal();
    window.location.hash = "#/inventory/prestamos";
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
                <button
                  type="button"
                  className="teacher-loan-detail-action-btn teacher-loan-detail-action-btn--danger"
                  onClick={openDeleteModal}
                >
                  <Trash2 size={16} />
                  Eliminar solicitud
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
        </>
      ) : null}

      {showDeleteModal ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-delete-modal">
            <h3>Eliminar prestamo</h3>
            <p>
              Seguro que quieres eliminar esta solicitud? Escribe <strong>"{DELETE_CONFIRM_TEXT}"</strong>{" "}
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
                onClick={confirmDelete}
              >
                <Trash2 size={16} />
                Eliminar
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
