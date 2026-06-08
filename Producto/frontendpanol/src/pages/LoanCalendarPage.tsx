import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Eye,
  Package2,
  Plus,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { fetchLoansPage } from "../services/loanService";
import type { LoanSummary } from "../types/loan";
import { getUserRoleFromToken } from "../utils/auth";

const WEEKDAY_LABELS = ["Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"] as const;

function parseDate(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function toDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function capitalizeLabel(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function formatMonthLabel(date: Date): string {
  return capitalizeLabel(
    new Intl.DateTimeFormat("es-CL", {
      month: "long",
      year: "numeric",
    }).format(date),
  );
}

function formatTime(value: string): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function formatDateTime(value: string): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(date)
    .replace(".", "");
}

function normalizeStatus(status: LoanSummary["status"]): string {
  const labels: Record<LoanSummary["status"], string> = {
    pending: "Pendiente",
    approved: "Aprobado",
    prepared: "Preparado",
    delivered: "En uso",
    completed: "Completado",
    rejected: "Rechazado",
    cancelled: "Cancelado",
    expired: "Expirado",
    overdue: "Atrasado",
  };
  return labels[status];
}

function statusChipClass(status: LoanSummary["status"]): string {
  if (status === "pending") return "loan-calendar-chip loan-calendar-chip--pending";
  if (status === "approved" || status === "prepared") return "loan-calendar-chip loan-calendar-chip--approved";
  if (status === "delivered") return "loan-calendar-chip loan-calendar-chip--delivered";
  if (status === "overdue") return "loan-calendar-chip loan-calendar-chip--danger";
  if (status === "cancelled" || status === "rejected" || status === "expired") return "loan-calendar-chip loan-calendar-chip--danger";
  return "loan-calendar-chip loan-calendar-chip--completed";
}

function statusBadgeClass(status: LoanSummary["status"]): string {
  if (status === "pending") return "loan-calendar-status loan-calendar-status--pending";
  if (status === "approved" || status === "prepared") return "loan-calendar-status loan-calendar-status--approved";
  if (status === "delivered") return "loan-calendar-status loan-calendar-status--delivered";
  if (status === "overdue") return "loan-calendar-status loan-calendar-status--danger";
  if (status === "cancelled" || status === "rejected" || status === "expired") return "loan-calendar-status loan-calendar-status--danger";
  return "loan-calendar-status loan-calendar-status--completed";
}

function getMonthGrid(anchor: Date): Date[] {
  const firstDay = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const firstWeekday = (firstDay.getDay() + 6) % 7;
  const gridStart = new Date(firstDay);
  gridStart.setDate(firstDay.getDate() - firstWeekday);

  return Array.from({ length: 42 }, (_, index) => {
    const day = new Date(gridStart);
    day.setDate(gridStart.getDate() + index);
    return day;
  });
}

function summarizeItems(loan: LoanSummary): string {
  if (loan.items.length === 0) {
    return "Sin implementos";
  }
  const value = loan.items
    .slice(0, 2)
    .map((item) => `${item.implement_name} (x${item.requested_quantity})`)
    .join(", ");
  return loan.items.length > 2 ? `${value}, ...` : value;
}

export function LoanCalendarPage({ embedded = false }: { embedded?: boolean }) {
  const currentRole = getUserRoleFromToken();
  const canCreateLoan = currentRole === "DOCENTE";
  const todayKey = useMemo(() => toDateKey(new Date()), []);
  const [monthAnchor, setMonthAnchor] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const [selectedDateKey, setSelectedDateKey] = useState<string | null>(todayKey);
  const [allLoans, setAllLoans] = useState<LoanSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadLoans() {
      setLoading(true);
      setError(null);
      try {
        const mine = currentRole === "DOCENTE";
        const firstPage = await fetchLoansPage({ page: 1, size: 50, mine });
        if (cancelled) return;
        const merged = [...firstPage.items];
        for (let page = 2; page <= firstPage.total_pages; page += 1) {
          const nextPage = await fetchLoansPage({ page, size: firstPage.size, mine });
          if (cancelled) return;
          merged.push(...nextPage.items);
        }
        setAllLoans(merged);
      } catch (requestError) {
        if (cancelled) return;
        setError(getErrorMessage(requestError, "No se pudo cargar la agenda de prestamos."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadLoans();
    return () => {
      cancelled = true;
    };
  }, [currentRole]);

  const monthGrid = useMemo(() => getMonthGrid(monthAnchor), [monthAnchor]);
  const monthKey = useMemo(() => `${monthAnchor.getFullYear()}-${monthAnchor.getMonth()}`, [monthAnchor]);

  const visibleMonthLoans = useMemo(() => {
    return allLoans.filter((loan) => {
      const schedule = parseDate(loan.scheduled_at);
      if (!schedule) return false;
      return schedule.getFullYear() === monthAnchor.getFullYear() && schedule.getMonth() === monthAnchor.getMonth();
    });
  }, [allLoans, monthAnchor]);

  const loansByDay = useMemo(() => {
    const map = new Map<string, LoanSummary[]>();
    allLoans.forEach((loan) => {
      const schedule = parseDate(loan.scheduled_at);
      if (!schedule) return;
      const key = toDateKey(schedule);
      const rows = map.get(key) ?? [];
      rows.push(loan);
      map.set(key, rows);
    });
    map.forEach((rows) => {
      rows.sort((left, right) => {
        const leftDate = parseDate(left.scheduled_at)?.getTime() ?? 0;
        const rightDate = parseDate(right.scheduled_at)?.getTime() ?? 0;
        return leftDate - rightDate;
      });
    });
    return map;
  }, [allLoans]);

  const selectedDayLoans = useMemo(() => {
    if (!selectedDateKey) return [];
    return loansByDay.get(selectedDateKey) ?? [];
  }, [loansByDay, selectedDateKey]);

  const selectedDateLabel = useMemo(() => {
    if (!selectedDateKey) return "Selecciona un dia";
    const parsed = parseDate(`${selectedDateKey}T00:00:00`);
    if (!parsed) return selectedDateKey;
    return capitalizeLabel(
      new Intl.DateTimeFormat("es-CL", {
        weekday: "long",
        day: "2-digit",
        month: "long",
        year: "numeric",
      }).format(parsed),
    );
  }, [selectedDateKey]);

  const summaryMetrics = useMemo(() => {
    const counts = {
      approved: 0,
      completed: 0,
      cancelled: 0,
      rejected: 0,
    };

    visibleMonthLoans.forEach((loan) => {
      if (loan.status === "approved" || loan.status === "prepared" || loan.status === "delivered" || loan.status === "overdue") {
        counts.approved += 1;
        return;
      }
      if (loan.status === "completed") {
        counts.completed += 1;
        return;
      }
      if (loan.status === "cancelled" || loan.status === "expired") {
        counts.cancelled += 1;
        return;
      }
      if (loan.status === "rejected") {
        counts.rejected += 1;
      }
    });

    return [
      { key: "approved", label: "Aprobadas", value: counts.approved },
      { key: "completed", label: "Completadas", value: counts.completed },
      { key: "cancelled", label: "Canceladas", value: counts.cancelled },
      { key: "rejected", label: "Rechazadas", value: counts.rejected },
    ] as const;
  }, [visibleMonthLoans]);

  const newLoanHref = useMemo(() => {
    const selectedDate = selectedDateKey ?? todayKey;
    return `#/inventory/prestamos/nuevo?date=${selectedDate}`;
  }, [selectedDateKey, todayKey]);

  function goToPreviousMonth() {
    setMonthAnchor((current) => new Date(current.getFullYear(), current.getMonth() - 1, 1));
  }

  function goToNextMonth() {
    setMonthAnchor((current) => new Date(current.getFullYear(), current.getMonth() + 1, 1));
  }

  const content = (
    <div className="loan-calendar-stage">
      <div className="loan-calendar-page">
        <section className="loan-calendar-header">
        <div className="loan-calendar-header__copy">
          <h1>Agenda de Prestamos</h1>
          <p>
            {canCreateLoan
              ? "Calendario de tus solicitudes y devoluciones programadas."
              : "Vista operacional del calendario de solicitudes y entregas programadas."}
          </p>
        </div>
        <div className="loan-calendar-header__actions">
          {canCreateLoan ? (
            <a href={newLoanHref} className="loan-calendar-link-btn loan-calendar-link-btn--primary">
              <Plus size={16} />
              Nueva solicitud
            </a>
          ) : null}
        </div>
      </section>

        {error ? <div className="error-banner">{error}</div> : null}

        <section className="loan-calendar-layout">
        <article className="loan-calendar-card">
          <header className="loan-calendar-card__header">
            <div className="loan-calendar-nav">
              <button type="button" onClick={goToPreviousMonth} aria-label="Mes anterior">
                <ChevronLeft size={16} />
              </button>
              <div className="loan-calendar-nav__pill">
                <strong>{formatMonthLabel(monthAnchor)}</strong>
                <CalendarDays size={15} />
              </div>
              <button type="button" onClick={goToNextMonth} aria-label="Mes siguiente">
                <ChevronRight size={16} />
              </button>
            </div>
            <div className="loan-calendar-card__controls">
              <span className="loan-calendar-counter-pill">
                <CalendarDays size={15} />
                {visibleMonthLoans.length} solicitudes
              </span>
            </div>
          </header>

          <div className="loan-calendar-grid loan-calendar-grid--weekday">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label}>{label}</div>
            ))}
          </div>

          <div className="loan-calendar-grid loan-calendar-grid--days">
            {monthGrid.map((day) => {
              const key = toDateKey(day);
              const dayLoans = loansByDay.get(key) ?? [];
              const isCurrentMonth = `${day.getFullYear()}-${day.getMonth()}` === monthKey;
              const isSelected = key === selectedDateKey;
              const isToday = key === todayKey;
              return (
                <button
                  type="button"
                  key={key}
                  className={`loan-calendar-day${isCurrentMonth ? "" : " is-faded"}${isSelected ? " is-selected" : ""}${isToday ? " is-today" : ""}`}
                  onClick={() => setSelectedDateKey(key)}
                >
                  <div className="loan-calendar-day__top">
                    <span>{day.getDate()}</span>
                    {dayLoans.length > 0 ? <small>{dayLoans.length}</small> : null}
                  </div>
                  <div className="loan-calendar-day__events">
                    {dayLoans.slice(0, 3).map((loan) => (
                      <span key={loan.uuid} className={statusChipClass(loan.status)}>
                        {formatTime(loan.scheduled_at)} {normalizeStatus(loan.status)}
                      </span>
                    ))}
                    {dayLoans.length > 3 ? <span className="loan-calendar-chip loan-calendar-chip--more">+{dayLoans.length - 3} mas</span> : null}
                  </div>
                </button>
              );
            })}
          </div>

          {loading ? <p className="loan-calendar-loading">Cargando agenda...</p> : null}
        </article>

        <aside className="loan-calendar-drawer">
          <header className="loan-calendar-drawer__header">
            <div>
              <h2>Detalle del dia</h2>
              <p>{selectedDateLabel}</p>
            </div>
            <button type="button" className="loan-calendar-drawer__icon-btn" aria-label="Dia seleccionado">
              <CalendarDays size={16} />
            </button>
          </header>

          {selectedDateKey == null ? (
            <div className="loan-calendar-empty">Selecciona un dia del calendario para ver los prestamos.</div>
          ) : selectedDayLoans.length === 0 ? (
            <div className="loan-calendar-empty">No hay solicitudes registradas para este dia.</div>
          ) : (
            <div className="loan-calendar-drawer__list">
              {selectedDayLoans.map((loan) => (
                <article key={loan.uuid} className="loan-calendar-loan-card">
                  <div className="loan-calendar-loan-card__head">
                    <span className={statusBadgeClass(loan.status)}>{normalizeStatus(loan.status)}</span>
                    <small>
                      <Clock3 size={13} />
                      {formatTime(loan.scheduled_at)}
                    </small>
                  </div>
                  <strong>{loan.room?.name ?? "Sin sala"}</strong>
                  <p>{loan.subject?.name ?? "Sin asignatura"}</p>
                  <p className="loan-calendar-loan-card__meta">{loan.requester_uuid}</p>
                  <p className="loan-calendar-loan-card__items">
                    <Package2 size={14} />
                    {summarizeItems(loan)}
                  </p>
                  <div className="loan-calendar-loan-card__actions">
                    <a href={`#/inventory/prestamos/${loan.uuid}`}>
                      <Eye size={14} /> Ver detalle
                    </a>
                    <span>{formatDateTime(loan.expected_return_at)}</span>
                  </div>
                </article>
              ))}
            </div>
          )}
        </aside>
        </section>

        <section className="loan-calendar-summary">
          <div className="loan-calendar-summary__metrics">
            {summaryMetrics.map((metric) => (
              <article key={metric.key} className={`loan-calendar-summary__metric loan-calendar-summary__metric--${metric.key}`}>
                <span className="loan-calendar-summary__dot" />
                <div>
                  <strong>{metric.value}</strong>
                  <small>{metric.label}</small>
                </div>
              </article>
            ))}
          </div>
          <div className="loan-calendar-summary__total">
            <strong>{visibleMonthLoans.length}</strong>
            <small>Total solicitudes</small>
          </div>
        </section>
      </div>
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
