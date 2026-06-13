import {
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Eye,
  Search,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { LoanApprovalModal, type LoanApprovalSubmission } from "../components/loans/LoanApprovalModal";
import { PresencePollingModal } from "../components/ui/PresencePollingModal";
import { useInactivityPollingGate } from "../hooks/useInactivityPollingGate";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { completeLoan, fetchLoansPage, reviewLoan } from "../services/loanService";
import type { LoanSummary } from "../types/loan";
import { canStartDelivery } from "../utils/loanSchedule";

const PAGE_SIZE = 10;

type LoanStatusFilter =
  | "all"
  | "pending"
  | "approved"
  | "prepared"
  | "delivered"
  | "overdue"
  | "completed"
  | "cancelled"
  | "rejected"
  | "expired";

function parseDate(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}

function formatDateForInput(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function buildLoanCode(uuid: string): string {
  return `#LN-${uuid.slice(0, 6).toUpperCase()}`;
}

function formatSchedule(value: string): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
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
  if (status === "pending") return "teacher-loans-status teacher-loans-status--pending";
  if (status === "approved") return "teacher-loans-status teacher-loans-status--approved";
  if (status === "prepared") return "teacher-loans-status teacher-loans-status--approved";
  if (status === "delivered") return "teacher-loans-status teacher-loans-status--delivered";
  if (status === "overdue") return "teacher-loans-status teacher-loans-status--danger";
  if (status === "cancelled" || status === "rejected" || status === "expired")
    return "teacher-loans-status teacher-loans-status--danger";
  return "teacher-loans-status teacher-loans-status--completed";
}

function summarizeItems(items: LoanSummary["items"]): string {
  if (items.length === 0) {
    return "Sin items";
  }
  const summary = items
    .slice(0, 3)
    .map((item) => `${item.implement_name} (x${item.requested_quantity})`)
    .join(", ");
  return items.length > 3 ? `${summary}, ...` : summary;
}

export function LoanCoordinatorPage({ embedded = false }: { embedded?: boolean }) {
  const [allLoans, setAllLoans] = useState<LoanSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processingLoanUuid, setProcessingLoanUuid] = useState<string | null>(null);

  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<LoanStatusFilter>("all");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [roomFilter, setRoomFilter] = useState("");
  const [page, setPage] = useState(1);

  const [rejectingLoan, setRejectingLoan] = useState<LoanSummary | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [approvingLoan, setApprovingLoan] = useState<LoanSummary | null>(null);
  const [completingLoan, setCompletingLoan] = useState<LoanSummary | null>(null);
  const [completionNotes, setCompletionNotes] = useState("");

  const loadLoans = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true);
    }
    setError(null);
    try {
      const firstPage = await fetchLoansPage({ page: 1, size: 50, mine: false });
      const merged: LoanSummary[] = [...firstPage.items];
      for (let next = 2; next <= firstPage.total_pages; next += 1) {
        const pageResult = await fetchLoansPage({ page: next, size: firstPage.size, mine: false });
        merged.push(...pageResult.items);
      }

      merged.sort((a, b) => {
        const left = parseDate(a.scheduled_at)?.getTime() ?? 0;
        const right = parseDate(b.scheduled_at)?.getTime() ?? 0;
        return right - left;
      });

      setAllLoans(merged);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar el listado de prestamos."));
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  const { promptVisible, pollingPaused, countdownSeconds, resumePolling } = useInactivityPollingGate({
    onContinue: async () => {
      await loadLoans(false);
    },
  });

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      await loadLoans(true);
      if (cancelled) {
        return;
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [loadLoans]);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void loadLoans(false);
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [loadLoans, pollingPaused]);

  const filteredLoans = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase();
    const normalizedRoom = roomFilter.trim().toLowerCase();

    return allLoans.filter((loan) => {
      const scheduleDate = parseDate(loan.scheduled_at);

      if (statusFilter !== "all" && loan.status !== statusFilter) {
        return false;
      }

      if (fromDate) {
        if (!scheduleDate || formatDateForInput(scheduleDate) < fromDate) {
          return false;
        }
      }

      if (toDate) {
        if (!scheduleDate || formatDateForInput(scheduleDate) > toDate) {
          return false;
        }
      }

      if (normalizedRoom) {
        const roomText = `${loan.room?.name ?? ""} ${loan.subject?.name ?? ""}`.toLowerCase();
        if (!roomText.includes(normalizedRoom)) {
          return false;
        }
      }

      if (!normalizedSearch) {
        return true;
      }

      const rowText = [
        loan.uuid,
        loan.requester_uuid,
        loan.room?.name ?? "",
        loan.subject?.name ?? "",
        ...loan.items.map((item) => item.implement_name),
      ]
        .join(" ")
        .toLowerCase();
      return rowText.includes(normalizedSearch);
    });
  }, [allLoans, fromDate, roomFilter, searchTerm, statusFilter, toDate]);

  const totalPages = Math.max(1, Math.ceil(filteredLoans.length / PAGE_SIZE));
  const safePage = useMemo(() => {
    if (page < 1) {
      return 1;
    }
    if (page > totalPages) {
      return totalPages;
    }
    return page;
  }, [page, totalPages]);

  const pageStart = (safePage - 1) * PAGE_SIZE;
  const pagedLoans = filteredLoans.slice(pageStart, pageStart + PAGE_SIZE);
  const rangeStart = filteredLoans.length === 0 ? 0 : pageStart + 1;
  const rangeEnd = filteredLoans.length === 0 ? 0 : Math.min(pageStart + pagedLoans.length, filteredLoans.length);

  const pageNumbers = useMemo(() => {
    const windowSize = 5;
    let start = Math.max(1, safePage - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [safePage, totalPages]);

  const pendingCount = useMemo(
    () => allLoans.filter((loan) => loan.status === "pending").length,
    [allLoans],
  );
  const inUseCount = useMemo(
    () => allLoans.filter((loan) => loan.status === "delivered" || loan.status === "overdue").length,
    [allLoans],
  );
  const completedToday = useMemo(() => {
    const now = new Date();
    const todayKey = formatDateForInput(now);
    return allLoans.filter((loan) => {
      if (loan.status !== "completed") return false;
      const completedDate = parseDate(loan.created_at);
      if (!completedDate) return false;
      return formatDateForInput(completedDate) === todayKey;
    }).length;
  }, [allLoans]);

  const overdueCount = useMemo(
    () => allLoans.filter((loan) => loan.status === "overdue").length,
    [allLoans],
  );

  function goToLoanDetail(loanUuid: string) {
    window.location.assign(`#/inventory/prestamos/${loanUuid}`);
  }

  function updateLoanInState(updated: LoanSummary) {
    setAllLoans((previous) =>
      previous.map((loan) => (loan.uuid === updated.uuid ? updated : loan)),
    );
  }

  function openApproveModal(loan: LoanSummary) {
    setApprovingLoan(loan);
  }

  function closeApproveModal() {
    setApprovingLoan(null);
  }

  async function approveLoan(payload: LoanApprovalSubmission) {
    if (!approvingLoan) {
      return;
    }
    setProcessingLoanUuid(approvingLoan.uuid);
    setError(null);
    try {
      const updated = await reviewLoan(approvingLoan.uuid, {
        decision: "APPROVE",
        notes: payload.notes,
        items: payload.items,
      });
      updateLoanInState(updated);
      closeApproveModal();
    } catch (requestError) {
      const payloadError = getApiErrorPayload(requestError);
      if (payloadError?.code === "LOAN_STOCK_CONFLICT") {
        setError("Esta solicitud excede el stock de algun implemento; no se puede aprobar.");
      } else {
        setError(getErrorMessage(requestError, "No se pudo aprobar el prestamo."));
      }
    } finally {
      setProcessingLoanUuid(null);
    }
  }

  async function markLoanCompleted(loan: LoanSummary) {
    setProcessingLoanUuid(loan.uuid);
    setError(null);
    try {
      const updated = await completeLoan(loan.uuid, { notes: completionNotes.trim() || null });
      updateLoanInState(updated);
      closeCompleteModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo completar el prestamo."));
    } finally {
      setProcessingLoanUuid(null);
    }
  }

  function goToDelivery(loanUuid: string) {
    window.location.assign(`#/inventory/prestamos/${loanUuid}/entrega`);
  }

  function openRejectModal(loan: LoanSummary) {
    setRejectingLoan(loan);
    setRejectionReason("");
  }

  function openCompleteModal(loan: LoanSummary) {
    setCompletingLoan(loan);
    setCompletionNotes("");
  }

  function closeCompleteModal() {
    setCompletingLoan(null);
    setCompletionNotes("");
  }

  function closeRejectModal() {
    setRejectingLoan(null);
    setRejectionReason("");
  }

  async function rejectLoan() {
    if (!rejectingLoan || rejectionReason.trim().length < 4) {
      return;
    }
    setProcessingLoanUuid(rejectingLoan.uuid);
    setError(null);
    try {
      const updated = await reviewLoan(rejectingLoan.uuid, {
        decision: "REJECT",
        notes: rejectionReason.trim(),
      });
      updateLoanInState(updated);
      closeRejectModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo rechazar el prestamo."));
    } finally {
      setProcessingLoanUuid(null);
    }
  }

  const content = (
    <div className="coordinator-loans-page">
      <section className="coordinator-loans-header">
        <h1>Listado de Prestamos</h1>
        <p>Control de solicitudes y estado de entrega/devolucion en tiempo real.</p>
      </section>

      <section className="coordinator-loans-kpis">
        <article className="coordinator-loans-kpi coordinator-loans-kpi--pending">
          <span className="coordinator-loans-kpi__strip" />
          <CircleAlert size={18} />
          <div>
            <p>Pendientes</p>
            <strong>{pendingCount}</strong>
          </div>
        </article>
        <article className="coordinator-loans-kpi coordinator-loans-kpi--inuse">
          <span className="coordinator-loans-kpi__strip" />
          <CalendarDays size={18} />
          <div>
            <p>En uso</p>
            <strong>{inUseCount}</strong>
          </div>
        </article>
        <article className="coordinator-loans-kpi coordinator-loans-kpi--completed">
          <span className="coordinator-loans-kpi__strip" />
          <CheckCircle2 size={18} />
          <div>
            <p>Completados hoy</p>
            <strong>{completedToday}</strong>
          </div>
        </article>
        <article className="coordinator-loans-kpi coordinator-loans-kpi--overdue">
          <span className="coordinator-loans-kpi__strip" />
          <XCircle size={18} />
          <div>
            <p>Atrasados</p>
            <strong>{overdueCount}</strong>
          </div>
        </article>
      </section>

      <section className="coordinator-loans-card">
        <div className="coordinator-loans-filters">
          <label className="coordinator-loans-search">
            <Search size={16} />
            <input
              type="search"
              placeholder="Buscar por UUID, solicitante o implemento..."
              value={searchTerm}
              onChange={(event) => {
                setSearchTerm(event.target.value);
                setPage(1);
              }}
            />
          </label>
          <label>
            <span>Estado</span>
            <select
              value={statusFilter}
              onChange={(event) => {
                setStatusFilter(event.target.value as LoanStatusFilter);
                setPage(1);
              }}
            >
              <option value="all">Todos</option>
              <option value="pending">Pendiente</option>
              <option value="approved">Aprobado</option>
              <option value="prepared">Preparado</option>
              <option value="delivered">En uso</option>
              <option value="overdue">Atrasado</option>
              <option value="completed">Completado</option>
              <option value="cancelled">Cancelado</option>
              <option value="rejected">Rechazado</option>
              <option value="expired">Expirado</option>
            </select>
          </label>
          <label>
            <span>Desde</span>
            <input
              type="date"
              value={fromDate}
              onChange={(event) => {
                setFromDate(event.target.value);
                setPage(1);
              }}
            />
          </label>
          <label>
            <span>Hasta</span>
            <input
              type="date"
              value={toDate}
              onChange={(event) => {
                setToDate(event.target.value);
                setPage(1);
              }}
            />
          </label>
          <label>
            <span>Sala / materia</span>
            <input
              type="text"
              placeholder="Ej: Sala 326"
              value={roomFilter}
              onChange={(event) => {
                setRoomFilter(event.target.value);
                setPage(1);
              }}
            />
          </label>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        <div className="coordinator-loans-table-wrap">
          <table className="coordinator-loans-table">
            <thead>
              <tr>
                <th>Estado</th>
                <th>UUID / Solicitante</th>
                <th>Fecha y hora</th>
                <th>Ubicacion / Materia</th>
                <th>Detalle items</th>
                <th className="coordinator-loans-table__actions-head">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="coordinator-loans-empty">
                    Cargando solicitudes...
                  </td>
                </tr>
              ) : pagedLoans.length === 0 ? (
                <tr>
                  <td colSpan={6} className="coordinator-loans-empty">
                    No hay prestamos que coincidan con los filtros seleccionados.
                  </td>
                </tr>
              ) : (
                pagedLoans.map((loan) => {
                  const isProcessing = processingLoanUuid === loan.uuid;
                  const deliveryEnabled = canStartDelivery(loan);
                  return (
                    <tr key={loan.uuid}>
                      <td>
                        <span className={statusClassName(loan.status)}>{normalizeStatusLabel(loan.status)}</span>
                      </td>
                      <td>
                        <div className="coordinator-loans-id-cell">
                          <strong>{buildLoanCode(loan.uuid)}</strong>
                          <span>{loan.requester_uuid}</span>
                        </div>
                      </td>
                      <td>{formatSchedule(loan.scheduled_at)}</td>
                      <td>
                        <div className="coordinator-loans-location">
                          <strong>{loan.room?.name ?? "Sin sala"}</strong>
                          <span>{loan.subject?.name ?? "Sin asignatura"}</span>
                        </div>
                      </td>
                      <td className="coordinator-loans-summary">{summarizeItems(loan.items)}</td>
                      <td>
                        <div className="coordinator-loans-actions">
                          <button
                            type="button"
                            className="coordinator-loans-icon-btn"
                            onClick={() => goToLoanDetail(loan.uuid)}
                            title="Ver detalle"
                          >
                            <Eye size={15} />
                          </button>
                          {loan.status === "pending" ? (
                            <>
                              <button
                                type="button"
                                className="coordinator-loans-action-btn coordinator-loans-action-btn--approve"
                                disabled={isProcessing}
                                onClick={() => openApproveModal(loan)}
                              >
                                Aceptar
                              </button>
                              <button
                                type="button"
                                className="coordinator-loans-action-btn coordinator-loans-action-btn--reject"
                                disabled={isProcessing}
                                onClick={() => openRejectModal(loan)}
                              >
                                Rechazar
                              </button>
                            </>
                          ) : loan.status === "approved" || loan.status === "prepared" ? (
                            <button
                              type="button"
                              className="coordinator-loans-action-btn coordinator-loans-action-btn--approve"
                              disabled={isProcessing || !deliveryEnabled}
                              onClick={() => goToDelivery(loan.uuid)}
                              title="Registrar entrega"
                            >
                              Entregar
                            </button>
                          ) : loan.status === "delivered" || loan.status === "overdue" ? (
                            <button
                              type="button"
                              className="coordinator-loans-action-btn coordinator-loans-action-btn--complete"
                              disabled={isProcessing}
                              onClick={() => openCompleteModal(loan)}
                            >
                              Completar
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="coordinator-loans-action-btn"
                              disabled
                            >
                              Gestionado
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <footer className="coordinator-loans-footer">
          <p>
            Mostrando {rangeStart} a {rangeEnd} de {filteredLoans.length} prestamos
          </p>
          <div className="coordinator-loans-pagination">
            <button
              type="button"
              className="coordinator-loans-pagination__btn"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              disabled={safePage <= 1}
            >
              <ChevronLeft size={16} />
            </button>
            {pageNumbers.map((pageNumber) => (
              <button
                key={pageNumber}
                type="button"
                className={`coordinator-loans-pagination__btn${pageNumber === safePage ? " coordinator-loans-pagination__btn--active" : ""}`}
                onClick={() => setPage(pageNumber)}
              >
                {pageNumber}
              </button>
            ))}
            <button
              type="button"
              className="coordinator-loans-pagination__btn"
              onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
              disabled={safePage >= totalPages}
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </footer>
      </section>

      {rejectingLoan ? (
        <div className="modal-overlay">
          <div className="modal">
            <h3>Rechazar prestamo</h3>
            <p>
              Escribe el motivo de rechazo para la solicitud <strong>{buildLoanCode(rejectingLoan.uuid)}</strong>.
            </p>
            <label htmlFor="rejection-reason">Motivo</label>
            <textarea
              id="rejection-reason"
              rows={3}
              value={rejectionReason}
              onChange={(event) => setRejectionReason(event.target.value)}
              placeholder="Motivo del rechazo..."
            />
            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeRejectModal}>
                Cancelar
              </button>
              <button
                type="button"
                className="button button--danger"
                onClick={() => void rejectLoan()}
                disabled={rejectionReason.trim().length < 4 || processingLoanUuid === rejectingLoan.uuid}
              >
                Rechazar
              </button>
            </div>
          </div>
        </div>
      ) : null}
      {approvingLoan ? (
        <LoanApprovalModal
          loan={approvingLoan}
          processing={processingLoanUuid === approvingLoan.uuid}
          onClose={closeApproveModal}
          onSubmit={approveLoan}
        />
      ) : null}
      {completingLoan ? (
        <div className="modal-overlay">
          <div className="modal">
            <h3>Completar prestamo</h3>
            <p>
              Puedes agregar una nota opcional antes de cerrar la solicitud <strong>{buildLoanCode(completingLoan.uuid)}</strong>.
            </p>
            <label htmlFor="completion-notes">Notas</label>
            <textarea
              id="completion-notes"
              rows={3}
              value={completionNotes}
              maxLength={1000}
              onChange={(event) => setCompletionNotes(event.target.value)}
              placeholder="Observaciones de cierre..."
            />
            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeCompleteModal}>
                Cancelar
              </button>
              <button
                type="button"
                className="coordinator-loans-action-btn coordinator-loans-action-btn--complete"
                onClick={() => void markLoanCompleted(completingLoan)}
                disabled={processingLoanUuid === completingLoan.uuid}
              >
                Completar
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <PresencePollingModal
        visible={promptVisible}
        pollingPaused={pollingPaused}
        countdownSeconds={countdownSeconds}
        onContinue={() => {
          void resumePolling();
        }}
      />
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
