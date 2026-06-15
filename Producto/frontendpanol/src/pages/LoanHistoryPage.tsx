import {
  AlertCircle,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Download,
  Eye,
  Plus,
  RotateCcw,
  Search,
  Trash2,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { PresencePollingModal } from "../components/ui/PresencePollingModal";
import { useInactivityPollingGate } from "../hooks/useInactivityPollingGate";
import { getErrorMessage } from "../services/apiClient";
import { cancelLoan, fetchLoansPage } from "../services/loanService";
import type { LoanSummary } from "../types/loan";

const CLIENT_PAGE_SIZE = 4;
const DELETE_CONFIRM_TEXT = "eliminar";

type LoanStatusFilter =
  | "all"
  | "pending"
  | "approved"
  | "prepared"
  | "delivered"
  | "completed"
  | "cancelled"
  | "rejected"
  | "expired"
  | "overdue";

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}

function parseDate(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function formatDateForFilter(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatLoanDate(value: string): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }

  const dayMonth = new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "short",
  })
    .format(date)
    .replace(".", "");

  const time = new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);

  return `${dayMonth}, ${time}`;
}

function formatKpiDate(value: string | null): string {
  if (!value) {
    return "--";
  }
  const date = parseDate(value);
  if (!date) {
    return "--";
  }
  return new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "short",
  })
    .format(date)
    .replace(".", "");
}

function buildLoanCode(uuid: string): string {
  return `#PS-${uuid.slice(0, 6).toUpperCase()}`;
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

function isActiveLoanStatus(status: string): boolean {
  return (
    status === "pending" ||
    status === "approved" ||
    status === "prepared" ||
    status === "delivered" ||
    status === "overdue"
  );
}

function isPendingReturnStatus(status: string): boolean {
  return status === "delivered" || status === "overdue";
}

function canCancelLoan(status: string): boolean {
  return status === "pending" || status === "approved" || status === "prepared";
}

function summarizeItems(items: LoanSummary["items"]): string {
  if (items.length === 0) {
    return "Sin implementos registrados.";
  }

  const summary = items
    .slice(0, 3)
    .map((item) => `${item.implement_name} (x${item.requested_quantity})`)
    .join(", ");
  return items.length > 3 ? `${summary}, ...` : summary;
}

export function LoanHistoryPage({ embedded = false }: { embedded?: boolean }) {
  const [allLoans, setAllLoans] = useState<LoanSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processingLoanUuid, setProcessingLoanUuid] = useState<string | null>(null);

  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<LoanStatusFilter>("all");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);
  const [nowMs, setNowMs] = useState(() => Date.now());

  const [loanToDelete, setLoanToDelete] = useState<LoanSummary | null>(null);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = useState("");
  const [deleteNotes, setDeleteNotes] = useState("");

  const loadHistory = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true);
    }
    setError(null);
    try {
      const firstPage = await fetchLoansPage({ page: 1, size: 50, mine: true });
      const merged: LoanSummary[] = [...firstPage.items];
      for (let nextPage = 2; nextPage <= firstPage.total_pages; nextPage += 1) {
        const pageData = await fetchLoansPage({ page: nextPage, size: firstPage.size, mine: true });
        merged.push(...pageData.items);
      }

      merged.sort((a, b) => {
        const left = parseDate(a.scheduled_at)?.getTime() ?? 0;
        const right = parseDate(b.scheduled_at)?.getTime() ?? 0;
        return right - left;
      });

      setAllLoans(merged);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar tu historial de prestamos."));
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  const { promptVisible, pollingPaused, countdownSeconds, resumePolling } = useInactivityPollingGate({
    onContinue: async () => {
      await loadHistory(false);
    },
  });

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      await loadHistory(true);
      if (cancelled) {
        return;
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
    };
  }, [loadHistory]);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void loadHistory(false);
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [loadHistory, pollingPaused]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNowMs(Date.now());
    }, 30000);
    return () => window.clearInterval(intervalId);
  }, []);

  const filteredLoans = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase();
    return allLoans.filter((loan) => {
      const scheduledDate = parseDate(loan.scheduled_at);

      if (statusFilter !== "all" && loan.status !== statusFilter) {
        return false;
      }

      if (fromDate) {
        if (!scheduledDate || formatDateForFilter(scheduledDate) < fromDate) {
          return false;
        }
      }

      if (toDate) {
        if (!scheduledDate || formatDateForFilter(scheduledDate) > toDate) {
          return false;
        }
      }

      if (!normalizedSearch) {
        return true;
      }

      const searchableText = [
        loan.uuid,
        buildLoanCode(loan.uuid),
        loan.room?.name ?? "",
        loan.subject?.name ?? "",
        ...loan.items.map((item) => item.implement_name),
      ]
        .join(" ")
        .toLowerCase();
      return searchableText.includes(normalizedSearch);
    });
  }, [allLoans, fromDate, searchTerm, statusFilter, toDate]);

  const totalPages = Math.max(1, Math.ceil(filteredLoans.length / CLIENT_PAGE_SIZE));
  const safePage = useMemo(() => {
    if (page < 1) {
      return 1;
    }
    if (page > totalPages) {
      return totalPages;
    }
    return page;
  }, [page, totalPages]);

  const pageStart = (safePage - 1) * CLIENT_PAGE_SIZE;
  const pagedLoans = filteredLoans.slice(pageStart, pageStart + CLIENT_PAGE_SIZE);
  const rangeStart = filteredLoans.length === 0 ? 0 : pageStart + 1;
  const rangeEnd = filteredLoans.length === 0 ? 0 : Math.min(pageStart + pagedLoans.length, filteredLoans.length);

  const pageNumbers = useMemo(() => {
    const windowSize = 3;
    let start = Math.max(1, safePage - 1);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [safePage, totalPages]);

  const activeRequestsCount = useMemo(
    () => allLoans.filter((loan) => isActiveLoanStatus(loan.status)).length,
    [allLoans],
  );

  const pendingReturnsCount = useMemo(
    () => allLoans.filter((loan) => isPendingReturnStatus(loan.status)).length,
    [allLoans],
  );

  const nextDeliveryDate = useMemo(() => {
    const activeDates = allLoans
      .filter((loan) => isActiveLoanStatus(loan.status))
      .map((loan) => parseDate(loan.scheduled_at))
      .filter((date): date is Date => date !== null && date.getTime() >= nowMs)
      .sort((a, b) => a.getTime() - b.getTime());

    return activeDates.length > 0 ? activeDates[0].toISOString() : null;
  }, [allLoans, nowMs]);

  const canConfirmDeletion = deleteConfirmationInput.trim().toLowerCase() === DELETE_CONFIRM_TEXT;

  function goToCreateLoan() {
    window.location.assign("#/inventory/prestamos/nuevo");
  }

  function goToLoanDetail(loanUuid: string) {
    window.location.assign(`#/inventory/prestamos/${loanUuid}`);
  }

  function requestDeletion(loan: LoanSummary) {
    setLoanToDelete(loan);
    setDeleteConfirmationInput("");
    setDeleteNotes("");
  }

  function closeDeletionModal() {
    setLoanToDelete(null);
    setDeleteConfirmationInput("");
    setDeleteNotes("");
  }

  async function confirmDeletion() {
    if (!loanToDelete || !canConfirmDeletion) {
      return;
    }
    setProcessingLoanUuid(loanToDelete.uuid);
    setError(null);
    try {
      const cancelled = await cancelLoan(loanToDelete.uuid, {
        notes: deleteNotes.trim() || null,
      });
      setAllLoans((previous) =>
        previous.map((loan) => (loan.uuid === cancelled.uuid ? cancelled : loan)),
      );
      closeDeletionModal();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cancelar el prestamo."));
    } finally {
      setProcessingLoanUuid(null);
    }
  }

  const content = (
    <div className="teacher-loans-page">
      <section className="teacher-loans-header">
        <h1>Mis Prestamos</h1>
        <p>Gestiona y haz seguimiento de tus solicitudes de implementos medicos.</p>
      </section>

      <section className="teacher-loans-kpis">
        <article className="teacher-loans-kpi teacher-loans-kpi--active">
          <span className="teacher-loans-kpi__strip" />
          <div className="teacher-loans-kpi__icon">
            <ClipboardCheck size={20} />
          </div>
          <div>
            <p>Solicitudes Activas</p>
            <strong>{activeRequestsCount}</strong>
          </div>
        </article>

        <article className="teacher-loans-kpi teacher-loans-kpi--upcoming">
          <span className="teacher-loans-kpi__strip" />
          <div className="teacher-loans-kpi__icon">
            <CalendarDays size={20} />
          </div>
          <div>
            <p>Proximas Entregas</p>
            <strong>{formatKpiDate(nextDeliveryDate)}</strong>
          </div>
        </article>

        <article className="teacher-loans-kpi teacher-loans-kpi--returns">
          <span className="teacher-loans-kpi__strip" />
          <div className="teacher-loans-kpi__icon">
            <RotateCcw size={20} />
          </div>
          <div>
            <p>Devoluciones Pendientes</p>
            <strong>{pendingReturnsCount}</strong>
          </div>
        </article>
      </section>

      <section className="teacher-loans-card">
        <div className="teacher-loans-toolbar">
          <div className="teacher-loans-toolbar__filters">
            <label className="teacher-loans-toolbar__search">
              <Search size={16} />
              <input
                type="search"
                placeholder="Buscar prestamos, implementos o salas..."
                value={searchTerm}
                onChange={(event) => {
                  setSearchTerm(event.target.value);
                  setPage(1);
                }}
              />
            </label>

            <label className="teacher-loans-toolbar__field">
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

            <label className="teacher-loans-toolbar__field">
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

            <label className="teacher-loans-toolbar__field">
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
          </div>

          <button type="button" className="teacher-loans-new-btn" onClick={goToCreateLoan}>
            <Plus size={16} />
            Nuevo prestamo
          </button>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        <div className="teacher-loans-table-wrap">
          <table className="teacher-loans-table">
            <thead>
              <tr>
                <th>ID / UUID</th>
                <th>Fecha y hora</th>
                <th>Sala / Lab</th>
                <th>Resumen de implementos</th>
                <th>Estado</th>
                <th className="teacher-loans-table__actions-head">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="teacher-loans-table__empty">
                    Cargando historial de prestamos...
                  </td>
                </tr>
              ) : pagedLoans.length === 0 ? (
                <tr>
                  <td colSpan={6} className="teacher-loans-table__empty">
                    No hay prestamos que coincidan con los filtros seleccionados.
                  </td>
                </tr>
              ) : (
                pagedLoans.map((loan) => (
                  <tr key={loan.uuid}>
                    <td>
                      <div className="teacher-loans-id-cell">
                        <strong>{buildLoanCode(loan.uuid)}</strong>
                        <span>{loan.uuid}</span>
                      </div>
                    </td>
                    <td>{formatLoanDate(loan.scheduled_at)}</td>
                    <td>{loan.room?.name ?? "Sin sala"}</td>
                    <td className="teacher-loans-summary-cell">{summarizeItems(loan.items)}</td>
                    <td>
                      <span className={statusClassName(loan.status)}>{normalizeStatusLabel(loan.status)}</span>
                    </td>
                    <td>
                      <div className="teacher-loans-actions">
                        <button
                          type="button"
                          className="teacher-loans-icon-btn"
                          onClick={() => goToLoanDetail(loan.uuid)}
                          aria-label="Ver detalle"
                          title="Ver detalle"
                        >
                          <Eye size={16} />
                        </button>
                        <button
                          type="button"
                          className="teacher-loans-icon-btn"
                          disabled
                          aria-label="Descargar comprobante (proximamente)"
                          title="Descarga deshabilitada por ahora"
                        >
                          <Download size={16} />
                        </button>
                        {canCancelLoan(loan.status) ? (
                          <button
                            type="button"
                            className="teacher-loans-icon-btn teacher-loans-icon-btn--danger"
                            onClick={() => requestDeletion(loan)}
                            aria-label="Cancelar prestamo"
                            title="Cancelar prestamo"
                            disabled={processingLoanUuid === loan.uuid}
                          >
                            <Trash2 size={16} />
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="teacher-loans-icon-btn"
                            disabled
                            aria-label="Prestamo no cancelable"
                            title="No se puede cancelar en este estado"
                          >
                            <AlertCircle size={16} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <footer className="teacher-loans-footer">
          <p>
            Mostrando {rangeStart} a {rangeEnd} de {filteredLoans.length} prestamos
          </p>
          <div className="teacher-loans-pagination">
            <button
              type="button"
              className="teacher-loans-pagination__btn"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              disabled={safePage <= 1}
              aria-label="Pagina anterior"
            >
              <ChevronLeft size={16} />
            </button>
            {pageNumbers.map((pageNumber) => (
              <button
                key={pageNumber}
                type="button"
                className={`teacher-loans-pagination__btn${pageNumber === safePage ? " teacher-loans-pagination__btn--active" : ""}`}
                onClick={() => setPage(pageNumber)}
              >
                {pageNumber}
              </button>
            ))}
            <button
              type="button"
              className="teacher-loans-pagination__btn"
              onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
              disabled={safePage >= totalPages}
              aria-label="Pagina siguiente"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </footer>
      </section>

      {loanToDelete ? (
        <div className="modal-overlay">
          <div className="modal teacher-loans-delete-modal">
            <h3>Eliminar prestamo</h3>
            <p>
              Seguro que quieres eliminar la solicitud {buildLoanCode(loanToDelete.uuid)}? Escribe{" "}
              <strong>"{DELETE_CONFIRM_TEXT}"</strong> para confirmar.
            </p>
            <label htmlFor="loan-delete-confirmation">Confirmacion</label>
            <input
              id="loan-delete-confirmation"
              value={deleteConfirmationInput}
              onChange={(event) => setDeleteConfirmationInput(event.target.value)}
              placeholder={DELETE_CONFIRM_TEXT}
            />
            <label htmlFor="loan-delete-notes">Notas (opcional)</label>
            <textarea
              id="loan-delete-notes"
              rows={3}
              value={deleteNotes}
              maxLength={1000}
              onChange={(event) => setDeleteNotes(event.target.value)}
              placeholder="Motivo u observacion de cancelacion..."
            />
            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeDeletionModal}>
                Cancelar
              </button>
              <button
                type="button"
                className="button button--danger"
                disabled={!canConfirmDeletion}
                onClick={() => void confirmDeletion()}
              >
                <Trash2 size={16} />
                {processingLoanUuid === loanToDelete.uuid ? "Cancelando..." : "Cancelar prestamo"}
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
