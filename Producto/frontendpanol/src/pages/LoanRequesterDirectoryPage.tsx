import {
  BookOpenText,
  ChevronLeft,
  ChevronRight,
  Clock3,
  ExternalLink,
  Mail,
  Search,
  UserRound,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { LoanDetailModalFrame } from "../components/loans/LoanDetailModalFrame";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Input } from "../components/ui/Input";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { getErrorMessage } from "../services/apiClient";
import { fetchLoanRequesterHistoryPage, fetchLoanRequestersPage } from "../services/loanService";
import type { LoanItem, LoanRequesterHistoryItem, LoanRequesterHistoryPage, LoanRequesterPage, LoanRequesterSummary, LoanStatus } from "../types/loan";
import { formatNotificationRelativeTime } from "../utils/notifications";
import { buildLoanDetailHash } from "../utils/loanDetailRouting";
import { formatRut } from "../utils/rut";

const PAGE_SIZE = 15;
const HISTORY_PAGE_SIZE = 6;

const EMPTY_REQUESTERS_PAGE: LoanRequesterPage = {
  items: [],
  page: 1,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const EMPTY_HISTORY_PAGE: LoanRequesterHistoryPage = {
  requester: null,
  items: [],
  page: 1,
  size: HISTORY_PAGE_SIZE,
  totalItems: 0,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

function formatSchedule(value: string | null): string {
  if (!value) {
    return "Sin fecha";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(parsed);
}

function formatLastLoanLabel(value: string | null): string {
  if (!value) {
    return "Sin historial reciente";
  }
  return `Ultimo prestamo ${formatNotificationRelativeTime(value)}`;
}

function normalizeStatusLabel(status: LoanStatus | null): string {
  const labels: Record<LoanStatus, string> = {
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
  if (!status) {
    return "Sin estado";
  }
  return labels[status] ?? status;
}

function statusClassName(status: LoanStatus | null): string {
  if (status === "pending") return "teacher-loans-status teacher-loans-status--pending";
  if (status === "approved" || status === "prepared") return "teacher-loans-status teacher-loans-status--approved";
  if (status === "delivered") return "teacher-loans-status teacher-loans-status--delivered";
  if (status === "overdue" || status === "cancelled" || status === "rejected" || status === "expired") {
    return "teacher-loans-status teacher-loans-status--danger";
  }
  return "teacher-loans-status teacher-loans-status--completed";
}

function summarizeItems(items: LoanItem[]): string {
  if (items.length === 0) {
    return "Sin implementos asociados";
  }
  return items
    .map((item) => `${item.implement_name} x${item.requested_quantity}`)
    .join(" | ");
}

function buildHistoryTimeline(item: LoanRequesterHistoryItem): Array<{ label: string; value: string | null }> {
  return [
    { label: "Reservado", value: item.approvedAt },
    { label: "Preparado", value: item.preparedAt },
    { label: "Entregado", value: item.deliveredAt },
    { label: "Finalizado", value: item.completedAt },
    { label: "Atrasado", value: item.overdueAt },
    { label: "Cancelado", value: item.cancelledAt },
    { label: "Rechazado", value: item.rejectedAt },
    { label: "Expirado", value: item.expiredAt },
  ].filter((entry) => entry.value);
}

function openLoanDetail(loanUuid: string) {
  window.location.assign(buildLoanDetailHash(loanUuid, "list"));
}

function RequesterHistoryModal({
  requester,
  historyPage,
  loading,
  error,
  allowLoanDetailNavigation,
  onClose,
  onPageChange,
}: {
  requester: LoanRequesterSummary;
  historyPage: LoanRequesterHistoryPage;
  loading: boolean;
  error: string | null;
  allowLoanDetailNavigation: boolean;
  onClose: () => void;
  onPageChange: (page: number) => void;
}) {
  const historyPageNumbers = useMemo(() => {
    const totalPages = Math.max(historyPage.totalPages, 1);
    const windowSize = 5;
    let start = Math.max(1, historyPage.page - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [historyPage.page, historyPage.totalPages]);

  return (
    <LoanDetailModalFrame onClose={onClose} ariaLabel={`Historial de ${requester.requesterName}`}>
      <div className="coordinator-requesters-modal">
        <header className="coordinator-requesters-modal__header">
          <div className="coordinator-requesters-modal__identity">
            <div className="coordinator-requesters-modal__avatar" aria-hidden="true">
              {requester.requesterName
                .split(" ")
                .map((part) => part[0] ?? "")
                .filter(Boolean)
                .slice(0, 2)
                .join("")
                .toUpperCase() || "DO"}
            </div>
            <div>
              <p className="coordinator-requesters-modal__eyebrow">Historial del docente</p>
              <h2>{requester.requesterName}</h2>
              <div className="coordinator-requesters-modal__meta">
                {requester.requesterEmail ? <span><Mail size={14} /> {requester.requesterEmail}</span> : null}
                {requester.requesterRut ? <span>RUT {formatRut(requester.requesterRut)}</span> : null}
                <span><Clock3 size={14} /> {formatLastLoanLabel(requester.lastLoanAt)}</span>
              </div>
            </div>
          </div>

          <div className="coordinator-requesters-modal__stats">
            <div>
              <span>Solicitudes</span>
              <strong>{requester.totalLoans}</strong>
            </div>
            <div>
              <span>Activas</span>
              <strong>{requester.activeLoans}</strong>
            </div>
            <div>
              <span>Ultimo estado</span>
              <strong>{normalizeStatusLabel(requester.latestStatus)}</strong>
            </div>
          </div>
        </header>

        {error ? <div className="error-banner">{error}</div> : null}

        {loading ? (
          <div className="coordinator-requesters-modal__state">
            <p>Cargando historial detallado...</p>
          </div>
        ) : historyPage.items.length === 0 ? (
          <div className="coordinator-requesters-modal__state">
            <p>Este docente no tiene prestamos para mostrar en este momento.</p>
          </div>
        ) : (
          <>
            <div className="coordinator-requesters-history">
              {historyPage.items.map((loan) => {
                const timeline = buildHistoryTimeline(loan);
                return (
                  <article key={loan.uuid} className="coordinator-requesters-history-card">
                    <div className="coordinator-requesters-history-card__head">
                      <div>
                        <div className="coordinator-requesters-history-card__title-row">
                          <span className={statusClassName(loan.status)}>{normalizeStatusLabel(loan.status)}</span>
                          <strong>{loan.subject?.name ?? "Prestamo sin asignatura"}</strong>
                        </div>
                        <p>{loan.room?.name ?? "Sin sala asignada"} | Programado {formatSchedule(loan.scheduledAt)}</p>
                      </div>
                      {allowLoanDetailNavigation ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="coordinator-requesters-history-card__open"
                          onClick={() => openLoanDetail(loan.uuid)}
                        >
                          Abrir prestamo
                          <ExternalLink size={14} />
                        </Button>
                      ) : (
                        <span className="coordinator-requesters-history-card__readonly">
                          Vista ejecutiva
                        </span>
                      )}
                    </div>

                    <div className="coordinator-requesters-history-card__grid">
                      <div>
                        <span>Creado</span>
                        <strong>{formatSchedule(loan.createdAt)}</strong>
                      </div>
                      <div>
                        <span>Devolucion estimada</span>
                        <strong>{formatSchedule(loan.expectedReturnAt)}</strong>
                      </div>
                      <div>
                        <span>Finalizacion</span>
                        <strong>{loan.completedAt ? formatSchedule(loan.completedAt) : "Aun en curso"}</strong>
                      </div>
                    </div>

                    <div className="coordinator-requesters-history-card__items">
                      <span>Implementos</span>
                      <p>{summarizeItems(loan.items)}</p>
                    </div>

                    {timeline.length > 0 ? (
                      <div className="coordinator-requesters-history-card__timeline">
                        {timeline.map((entry) => (
                          <div key={`${loan.uuid}-${entry.label}`} className="coordinator-requesters-history-card__timeline-item">
                            <small>{entry.label}</small>
                            <strong>{formatSchedule(entry.value)}</strong>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>

            <footer className="coordinator-requesters-modal__footer">
              <p>
                Pagina {historyPage.page} de {Math.max(historyPage.totalPages, 1)}
              </p>
              <div className="inventory-pagination">
                <button
                  type="button"
                  className="inventory-pagination__btn"
                  onClick={() => onPageChange(Math.max(1, historyPage.page - 1))}
                  disabled={!historyPage.hasPrevious}
                >
                  <ChevronLeft size={16} />
                </button>
                {historyPageNumbers.map((pageNumber) => (
                  <button
                    key={pageNumber}
                    type="button"
                    className={pageNumber === historyPage.page ? "inventory-pagination__btn inventory-pagination__btn--active" : "inventory-pagination__btn"}
                    onClick={() => onPageChange(pageNumber)}
                  >
                    {pageNumber}
                  </button>
                ))}
                <button
                  type="button"
                  className="inventory-pagination__btn"
                  onClick={() => onPageChange(historyPage.page + 1)}
                  disabled={!historyPage.hasNext}
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </footer>
          </>
        )}
      </div>
    </LoanDetailModalFrame>
  );
}

export function LoanRequesterDirectoryPage({
  embedded = false,
  viewMode = "coordinator",
}: {
  embedded?: boolean;
  viewMode?: "coordinator" | "director";
}) {
  const [searchInput, setSearchInput] = useState("");
  const [page, setPage] = useState(1);
  const [pageData, setPageData] = useState<LoanRequesterPage>(EMPTY_REQUESTERS_PAGE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRequester, setSelectedRequester] = useState<LoanRequesterSummary | null>(null);
  const [historyPageNumber, setHistoryPageNumber] = useState(1);
  const [historyPageData, setHistoryPageData] = useState<LoanRequesterHistoryPage>(EMPTY_HISTORY_PAGE);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const debouncedSearch = useDebouncedValue(searchInput.trim(), 300);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    async function loadRequesters() {
      setLoading(true);
      try {
        const nextPage = await fetchLoanRequestersPage({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
        }, {
          signal: controller.signal,
        });
        if (cancelled) {
          return;
        }
        setPageData(nextPage);
        setError(null);
      } catch (requestError) {
        if (cancelled || controller.signal.aborted) {
          return;
        }
        setError(getErrorMessage(requestError, "No se pudo cargar el listado de docentes con solicitudes."));
        setPageData(EMPTY_REQUESTERS_PAGE);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadRequesters();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [debouncedSearch, page]);

  useEffect(() => {
    if (!selectedRequester) {
      setHistoryPageData(EMPTY_HISTORY_PAGE);
      setHistoryError(null);
      setHistoryLoading(false);
      return;
    }

    const requesterUuid = selectedRequester.requesterUuid;

    const controller = new AbortController();
    let cancelled = false;

    async function loadHistory() {
      setHistoryLoading(true);
      try {
        const nextHistoryPage = await fetchLoanRequesterHistoryPage(requesterUuid, {
          page: historyPageNumber,
          size: HISTORY_PAGE_SIZE,
        }, {
          signal: controller.signal,
        });
        if (cancelled) {
          return;
        }
        setHistoryPageData(nextHistoryPage);
        setHistoryError(null);
      } catch (requestError) {
        if (cancelled || controller.signal.aborted) {
          return;
        }
        setHistoryError(getErrorMessage(requestError, "No se pudo cargar el historial detallado del docente."));
        setHistoryPageData(EMPTY_HISTORY_PAGE);
      } finally {
        if (!cancelled) {
          setHistoryLoading(false);
        }
      }
    }

    void loadHistory();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [historyPageNumber, selectedRequester]);

  const visibleActiveLoans = useMemo(
    () => pageData.items.reduce((total, requester) => total + requester.activeLoans, 0),
    [pageData.items],
  );
  const pageNumbers = useMemo(() => {
    const totalPages = Math.max(pageData.totalPages, 1);
    const windowSize = 5;
    let start = Math.max(1, pageData.page - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [pageData.page, pageData.totalPages]);

  const rangeStart = pageData.totalItems === 0 ? 0 : (pageData.page - 1) * pageData.size + 1;
  const rangeEnd = pageData.totalItems === 0 ? 0 : Math.min(rangeStart + pageData.items.length - 1, pageData.totalItems);

  function openRequesterModal(requester: LoanRequesterSummary) {
    setSelectedRequester(requester);
    setHistoryPageNumber(1);
  }

  const allowLoanDetailNavigation = viewMode === "coordinator";
  const title = viewMode === "director" ? "Docentes con solicitudes" : "Docentes con solicitudes";
  const subtitle = viewMode === "director"
    ? "Consulta ejecutiva del historial docente sin exponer la operacion detallada de cada prestamo."
    : "Consulta rapida del historial por docente, sin cargar usuarios que nunca hayan solicitado prestamos.";

  const content = (
    <section className={embedded ? "coordinator-requesters-page coordinator-requesters-page--embedded" : "coordinator-requesters-page"}>
      <section className="coordinator-loans-header coordinator-requesters-header">
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </section>

      <section className="coordinator-loans-kpis coordinator-requesters-kpis">
        <article className="coordinator-loans-kpi coordinator-loans-kpi--inuse">
          <span className="coordinator-loans-kpi__strip" />
          <UserRound size={18} />
          <div>
            <p>Docentes con historial</p>
            <strong>{pageData.totalItems}</strong>
          </div>
        </article>
        <article className="coordinator-loans-kpi coordinator-loans-kpi--pending">
          <span className="coordinator-loans-kpi__strip" />
          <BookOpenText size={18} />
          <div>
            <p>Solicitudes activas visibles</p>
            <strong>{visibleActiveLoans}</strong>
          </div>
        </article>
      </section>

      <section className="panel coordinator-requesters-panel">
        <div className="coordinator-requesters-toolbar">
          <label className="coordinator-requesters-search">
            <Search size={16} />
            <Input
              type="search"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(1);
              }}
              placeholder="Buscar por nombre, correo o RUT"
            />
          </label>

          <div className="coordinator-requesters-toolbar__summary">
            <p>
              Mostrando <strong>{rangeStart}</strong> a <strong>{rangeEnd}</strong> de <strong>{pageData.totalItems}</strong> docentes
            </p>
          </div>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        {loading ? (
          <div className="coordinator-requesters-state">
            <p>Cargando docentes con solicitudes...</p>
          </div>
        ) : pageData.items.length === 0 ? (
          <div className="coordinator-requesters-state">
            <p>No encontramos docentes con solicitudes para los criterios actuales.</p>
          </div>
        ) : (
          <div className="coordinator-requesters-list">
            {pageData.items.map((requester) => (
              <button
                key={requester.requesterUuid}
                type="button"
                className="coordinator-requester-card"
                onClick={() => openRequesterModal(requester)}
              >
                <div className="coordinator-requester-card__avatar" aria-hidden="true">
                  {requester.requesterName
                    .split(" ")
                    .map((part) => part[0] ?? "")
                    .filter(Boolean)
                    .slice(0, 2)
                    .join("")
                    .toUpperCase() || "DO"}
                </div>

                <div className="coordinator-requester-card__main">
                  <div className="coordinator-requester-card__head">
                    <div>
                      <strong>{requester.requesterName}</strong>
                      <p>{requester.requesterEmail ?? "Sin correo registrado"}</p>
                    </div>
                    {requester.latestStatus ? (
                      <span className={statusClassName(requester.latestStatus)}>{normalizeStatusLabel(requester.latestStatus)}</span>
                    ) : (
                      <Badge tone="inactive">Sin estado</Badge>
                    )}
                  </div>

                  <div className="coordinator-requester-card__meta">
                    <span>{formatLastLoanLabel(requester.lastLoanAt)}</span>
                    <span>{requester.totalLoans} solicitudes</span>
                    <span>{requester.activeLoans} activas</span>
                  </div>

                  <div className="coordinator-requester-card__context">
                    <span>{requester.latestRoomName ?? "Sin sala reciente"}</span>
                    <span>{requester.latestSubjectName ?? "Sin asignatura reciente"}</span>
                    {requester.requesterRut ? <span>RUT {formatRut(requester.requesterRut)}</span> : null}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}

        {!loading ? (
          <footer className="coordinator-requesters-footer">
            <p>
              Pagina {pageData.page} de {Math.max(pageData.totalPages, 1)}
            </p>
            <div className="inventory-pagination">
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                disabled={!pageData.hasPrevious}
              >
                <ChevronLeft size={16} />
              </button>
              {pageNumbers.map((pageNumber) => (
                <button
                  key={pageNumber}
                  type="button"
                  className={pageNumber === pageData.page ? "inventory-pagination__btn inventory-pagination__btn--active" : "inventory-pagination__btn"}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </button>
              ))}
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => current + 1)}
                disabled={!pageData.hasNext}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </footer>
        ) : null}
      </section>

      {selectedRequester ? (
        <RequesterHistoryModal
          requester={selectedRequester}
          historyPage={historyPageData}
          loading={historyLoading}
          error={historyError}
          allowLoanDetailNavigation={allowLoanDetailNavigation}
          onClose={() => setSelectedRequester(null)}
          onPageChange={setHistoryPageNumber}
        />
      ) : null}
    </section>
  );

  return embedded ? content : content;
}
