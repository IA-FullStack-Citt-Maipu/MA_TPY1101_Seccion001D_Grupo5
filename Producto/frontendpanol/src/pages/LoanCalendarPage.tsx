import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Eye,
  Package2,
  Plus,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { LoanDetailModalFrame } from "../components/loans/LoanDetailModalFrame";
import { PresencePollingModal } from "../components/ui/PresencePollingModal";
import { useInactivityPollingGate } from "../hooks/useInactivityPollingGate";
import { getErrorMessage } from "../services/apiClient";
import { fetchAllLoansPages } from "../services/loanService";
import type { LoanSummary } from "../types/loan";
import { getSessionUserRole } from "../utils/auth";
import {
  buildLoanDetailHash,
  replaceLoanDetailInHash,
  stripLoanDetailFromHash,
} from "../utils/loanDetailRouting";
import { LoanDetailPage } from "./LoanDetailPage";

const WEEKDAY_LABELS = ["Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"] as const;
const CALENDAR_PAGE_SIZE = 50;
const MONTH_CACHE_TTL_MS = 180000;
const INITIAL_CALENDAR_LOADER_MIN_MS = 450;

type CalendarLoadMode = "initial" | "navigate" | "refresh";
type CalendarMonthCacheEntry = {
  items: LoanSummary[];
  loadedAt: number;
};

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

function getMonthKey(date: Date): string {
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, "0")}`;
}

function getMonthStart(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function shiftMonth(date: Date, delta: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + delta, 1);
}

function getMonthRange(date: Date): { from: string; to: string } {
  const start = getMonthStart(date);
  const nextMonthStart = shiftMonth(start, 1);
  return {
    from: start.toISOString(),
    to: nextMonthStart.toISOString(),
  };
}

function isMonthCacheFresh(entry: CalendarMonthCacheEntry | undefined): boolean {
  if (!entry) {
    return false;
  }
  return Date.now() - entry.loadedAt < MONTH_CACHE_TTL_MS;
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

function sortLoansByScheduledAt(left: LoanSummary, right: LoanSummary): number {
  const leftDate = parseDate(left.scheduled_at)?.getTime() ?? 0;
  const rightDate = parseDate(right.scheduled_at)?.getTime() ?? 0;
  return leftDate - rightDate;
}

function normalizeStatus(status: LoanSummary["status"]): string {
  const labels: Record<LoanSummary["status"], string> = {
    pending: "Reservado",
    approved: "Reservado",
    prepared: "Preparado",
    delivered: "En uso",
    completed: "Finalizado",
    rejected: "Rechazado",
    cancelled: "Cancelado",
    expired: "Expirado",
    overdue: "Atrasado",
  };
  return labels[status];
}

function statusChipClass(status: LoanSummary["status"]): string {
  if (status === "pending" || status === "approved" || status === "prepared") return "loan-calendar-chip loan-calendar-chip--approved";
  if (status === "delivered") return "loan-calendar-chip loan-calendar-chip--delivered";
  if (status === "overdue") return "loan-calendar-chip loan-calendar-chip--danger";
  if (status === "cancelled" || status === "rejected" || status === "expired") return "loan-calendar-chip loan-calendar-chip--danger";
  return "loan-calendar-chip loan-calendar-chip--completed";
}

function statusBadgeClass(status: LoanSummary["status"]): string {
  if (status === "pending" || status === "approved" || status === "prepared") return "loan-calendar-status loan-calendar-status--approved";
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

function LoanCalendarDayDrawer({
  selectedDateKey,
  selectedDateLabel,
  selectedDayLoans,
  onOpenLoan,
  mobile = false,
  onClose,
}: {
  selectedDateKey: string | null;
  selectedDateLabel: string;
  selectedDayLoans: LoanSummary[];
  onOpenLoan: (loanUuid: string) => void;
  mobile?: boolean;
  onClose?: () => void;
}) {
  return (
    <aside className={mobile ? "loan-calendar-drawer loan-calendar-drawer--mobile" : "loan-calendar-drawer"}>
      <header className="loan-calendar-drawer__header">
        <div>
          <h2>Detalle del dia</h2>
          <p>{selectedDateLabel}</p>
        </div>
        {mobile ? (
          <button type="button" className="loan-calendar-drawer__icon-btn" aria-label="Cerrar detalle del dia" onClick={onClose}>
            <X size={16} />
          </button>
        ) : (
          <button type="button" className="loan-calendar-drawer__icon-btn" aria-label="Dia seleccionado">
            <CalendarDays size={16} />
          </button>
        )}
      </header>

      {selectedDateKey == null ? (
        <div className="loan-calendar-empty">Selecciona un dia del calendario para ver los prestamos.</div>
      ) : selectedDayLoans.length === 0 ? (
        <div className="loan-calendar-empty">No hay solicitudes registradas para este dia.</div>
      ) : (
        <div className="loan-calendar-drawer__list">
          {selectedDayLoans.map((loan) => (
            <button
              type="button"
              key={loan.uuid}
              className="loan-calendar-loan-card loan-calendar-loan-card--link"
              aria-label={`Ver detalle del prestamo ${loan.room?.name ?? "sin sala"} ${formatTime(loan.scheduled_at)}`}
              onClick={() => onOpenLoan(loan.uuid)}
            >
              <div className="loan-calendar-loan-card__head">
                <span className={statusBadgeClass(loan.status)}>{normalizeStatus(loan.status)}</span>
                <small>
                  <Clock3 size={13} />
                  {formatTime(loan.scheduled_at)}
                </small>
              </div>
              <strong>{loan.room?.name ?? "Sin sala"}</strong>
              <p>{loan.subject?.name ?? "Sin asignatura"}</p>
              <p className="loan-calendar-loan-card__items">
                <Package2 size={14} />
                {summarizeItems(loan)}
              </p>
              <div className="loan-calendar-loan-card__actions">
                <span className="loan-calendar-loan-card__cta">
                  <Eye size={14} /> Ver detalle
                </span>
                <span>{formatDateTime(loan.expected_return_at)}</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </aside>
  );
}

function LoanCalendarInitialSkeleton({ mobile = false }: { mobile?: boolean }) {
  return (
    <section className="loan-calendar-layout loan-calendar-layout--loading" aria-hidden="true">
      <article className="loan-calendar-card loan-calendar-card--loading">
        <header className="loan-calendar-card__header">
          <div className="loan-calendar-nav loan-calendar-nav--loading">
            <div className="skeleton loan-calendar-skeleton__icon-btn" />
            <div className="skeleton loan-calendar-skeleton__month-pill" />
            <div className="skeleton loan-calendar-skeleton__icon-btn" />
          </div>
          <div className="loan-calendar-card__controls">
            <div className="skeleton loan-calendar-skeleton__counter-pill" />
          </div>
        </header>

        <div className="loan-calendar-grid loan-calendar-grid--weekday">
          {WEEKDAY_LABELS.map((label) => (
            <div key={label}>{label}</div>
          ))}
        </div>

        <div className="loan-calendar-grid loan-calendar-grid--days">
          {Array.from({ length: 42 }).map((_, index) => (
            <div key={`calendar-skeleton-day-${index}`} className="loan-calendar-day loan-calendar-day--loading">
              <div className="loan-calendar-day__top">
                <span className="skeleton loan-calendar-skeleton__day-number" />
                <small className="skeleton loan-calendar-skeleton__day-badge" />
              </div>
              <div className="loan-calendar-day__events">
                <span className="skeleton loan-calendar-skeleton__chip" />
                <span className="skeleton loan-calendar-skeleton__chip loan-calendar-skeleton__chip--alt" />
                <span className="skeleton loan-calendar-skeleton__chip loan-calendar-skeleton__chip--sm" />
              </div>
            </div>
          ))}
        </div>
      </article>

      {!mobile ? (
        <aside className="loan-calendar-drawer loan-calendar-drawer--loading">
          <header className="loan-calendar-drawer__header">
            <div className="loan-calendar-skeleton__drawer-copy">
              <div className="skeleton loan-calendar-skeleton__drawer-title" />
              <div className="skeleton loan-calendar-skeleton__drawer-subtitle" />
            </div>
            <div className="skeleton loan-calendar-skeleton__icon-btn" />
          </header>

          <div className="loan-calendar-drawer__list">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={`calendar-skeleton-loan-${index}`} className="loan-calendar-loan-card">
                <div className="loan-calendar-loan-card__head">
                  <span className="skeleton loan-calendar-skeleton__status" />
                  <span className="skeleton loan-calendar-skeleton__time" />
                </div>
                <div className="skeleton loan-calendar-skeleton__line loan-calendar-skeleton__line--md" />
                <div className="skeleton loan-calendar-skeleton__line loan-calendar-skeleton__line--sm" />
                <div className="skeleton loan-calendar-skeleton__line loan-calendar-skeleton__line--lg" />
              </div>
            ))}
          </div>
        </aside>
      ) : null}
    </section>
  );
}

function LoanCalendarSummarySkeleton() {
  return (
    <section className="loan-calendar-summary loan-calendar-summary--loading" aria-hidden="true">
      <div className="loan-calendar-summary__metrics">
        {Array.from({ length: 4 }).map((_, index) => (
          <article key={`calendar-skeleton-metric-${index}`} className="loan-calendar-summary__metric">
            <span className="loan-calendar-summary__dot" />
            <div className="loan-calendar-summary__copy">
              <div className="skeleton loan-calendar-skeleton__metric-value" />
              <div className="skeleton loan-calendar-skeleton__metric-label" />
            </div>
          </article>
        ))}
      </div>
      <div className="loan-calendar-summary__total">
        <div className="skeleton loan-calendar-skeleton__metric-value" />
        <div className="skeleton loan-calendar-skeleton__metric-label" />
      </div>
    </section>
  );
}

export function LoanCalendarPage({
  embedded = false,
  activeDetailLoanUuid = null,
}: {
  embedded?: boolean;
  activeDetailLoanUuid?: string | null;
}) {
  const currentRole = getSessionUserRole();
  const isCoordinator = currentRole === "COORDINADOR";
  const canCreateLoan = currentRole === "DOCENTE" || isCoordinator;
  const todayKey = useMemo(() => toDateKey(new Date()), []);
  const [monthAnchor, setMonthAnchor] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const [selectedDateKey, setSelectedDateKey] = useState<string | null>(todayKey);
  const [monthLoanCache, setMonthLoanCache] = useState<Map<string, CalendarMonthCacheEntry>>(
    () => new Map(),
  );
  const [initialLoading, setInitialLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isMobileCalendar, setIsMobileCalendar] = useState(() =>
    typeof window !== "undefined" ? window.innerWidth <= 820 : false,
  );
  const [isMobileDayDrawerOpen, setIsMobileDayDrawerOpen] = useState(false);
  const hasLoadedOnceRef = useRef(false);
  const monthLoanCacheRef = useRef<Map<string, CalendarMonthCacheEntry>>(new Map());
  const inFlightMonthRequestsRef = useRef<Map<string, Promise<LoanSummary[]>>>(new Map());
  const visibleLoadTokenRef = useRef(0);

  const requestMonthLoans = useCallback(
    async (targetMonth: Date, options: { force?: boolean } = {}): Promise<LoanSummary[]> => {
      const normalizedMonth = getMonthStart(targetMonth);
      const monthKey = getMonthKey(normalizedMonth);
      const cachedEntry = monthLoanCacheRef.current.get(monthKey);
      if (!options.force && isMonthCacheFresh(cachedEntry)) {
        return cachedEntry?.items ?? [];
      }

      const inFlightRequest = inFlightMonthRequestsRef.current.get(monthKey);
      if (inFlightRequest) {
        return inFlightRequest;
      }

      const mine = currentRole === "DOCENTE";
      const { from, to } = getMonthRange(normalizedMonth);
      const request = fetchAllLoansPages({
        page: 1,
        size: CALENDAR_PAGE_SIZE,
        mine,
        from,
        to,
      })
        .then((items) => {
          const sortedItems = [...items].sort(sortLoansByScheduledAt);
          setMonthLoanCache((current) => {
            const next = new Map(current);
            next.set(monthKey, {
              items: sortedItems,
              loadedAt: Date.now(),
            });
            monthLoanCacheRef.current = next;
            return next;
          });
          return sortedItems;
        })
        .finally(() => {
          inFlightMonthRequestsRef.current.delete(monthKey);
        });

      inFlightMonthRequestsRef.current.set(monthKey, request);
      return request;
    },
    [currentRole],
  );

  const prefetchAdjacentMonths = useCallback(
    async (targetMonth: Date) => {
      await Promise.allSettled([
        requestMonthLoans(shiftMonth(targetMonth, -1)),
        requestMonthLoans(shiftMonth(targetMonth, 1)),
      ]);
    },
    [requestMonthLoans],
  );

  const loadVisibleMonth = useCallback(
    async (mode: CalendarLoadMode = "initial") => {
      const normalizedMonth = getMonthStart(monthAnchor);
      const monthKey = getMonthKey(normalizedMonth);
      const cachedEntry = monthLoanCacheRef.current.get(monthKey);
      const shouldFetchVisibleMonth =
        mode === "refresh" || !cachedEntry || !isMonthCacheFresh(cachedEntry);
      const shouldShowInitialLoading =
        mode === "initial" && !hasLoadedOnceRef.current && !cachedEntry;
      const loadToken = ++visibleLoadTokenRef.current;
      const loadingStartedAt = shouldShowInitialLoading ? Date.now() : null;

      if (shouldShowInitialLoading) {
        setInitialLoading(true);
      } else if (shouldFetchVisibleMonth) {
        setRefreshing(true);
      }

      setError(null);

      try {
        if (shouldFetchVisibleMonth) {
          await requestMonthLoans(normalizedMonth, { force: mode === "refresh" });
        }

        if (visibleLoadTokenRef.current !== loadToken) {
          return;
        }

        hasLoadedOnceRef.current = true;
        void prefetchAdjacentMonths(normalizedMonth);
      } catch (requestError) {
        if (visibleLoadTokenRef.current !== loadToken) {
          return;
        }
        setError(getErrorMessage(requestError, "No se pudo cargar la agenda de prestamos."));
      } finally {
        if (visibleLoadTokenRef.current !== loadToken) {
          return;
        }

        if (loadingStartedAt != null) {
          const elapsed = Date.now() - loadingStartedAt;
          const remaining = INITIAL_CALENDAR_LOADER_MIN_MS - elapsed;
          if (remaining > 0) {
            await new Promise((resolve) => window.setTimeout(resolve, remaining));
          }
          if (visibleLoadTokenRef.current !== loadToken) {
            return;
          }
        }

        setInitialLoading(false);
        setRefreshing(false);
      }
    },
    [monthAnchor, prefetchAdjacentMonths, requestMonthLoans],
  );

  const { promptVisible, pollingPaused, countdownSeconds, resumePolling } = useInactivityPollingGate({
    onContinue: async () => {
      await loadVisibleMonth("refresh");
    },
  });

  useEffect(() => {
    void loadVisibleMonth(hasLoadedOnceRef.current ? "navigate" : "initial");
  }, [loadVisibleMonth]);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void loadVisibleMonth("refresh");
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [loadVisibleMonth, pollingPaused]);

  useEffect(() => {
    function handleResize() {
      const nextIsMobile = window.innerWidth <= 820;
      setIsMobileCalendar(nextIsMobile);
      if (!nextIsMobile) {
        setIsMobileDayDrawerOpen(false);
      }
    }

    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  useEffect(() => {
    if (!isMobileCalendar || !isMobileDayDrawerOpen) {
      return;
    }

    document.body.classList.add("modal-open");

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsMobileDayDrawerOpen(false);
      }
    }

    window.addEventListener("keydown", handleEscape);
    return () => {
      document.body.classList.remove("modal-open");
      window.removeEventListener("keydown", handleEscape);
    };
  }, [isMobileCalendar, isMobileDayDrawerOpen]);

  const monthGrid = useMemo(() => getMonthGrid(monthAnchor), [monthAnchor]);
  const monthKey = useMemo(() => getMonthKey(monthAnchor), [monthAnchor]);

  const visibleMonthLoans = useMemo(
    () => monthLoanCache.get(monthKey)?.items ?? [],
    [monthKey, monthLoanCache],
  );

  const calendarWindowLoans = useMemo(() => {
    const monthKeys = [
      getMonthKey(shiftMonth(monthAnchor, -1)),
      monthKey,
      getMonthKey(shiftMonth(monthAnchor, 1)),
    ];
    return monthKeys
      .flatMap((key) => monthLoanCache.get(key)?.items ?? [])
      .sort(sortLoansByScheduledAt);
  }, [monthAnchor, monthKey, monthLoanCache]);

  const loansByDay = useMemo(() => {
    const map = new Map<string, LoanSummary[]>();
    calendarWindowLoans.forEach((loan) => {
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
  }, [calendarWindowLoans]);

  const detailSelectedDateKey = useMemo(() => {
    if (!activeDetailLoanUuid) {
      return null;
    }

    const activeLoan = calendarWindowLoans.find((loan) => loan.uuid === activeDetailLoanUuid);
    const scheduledDate = activeLoan ? parseDate(activeLoan.scheduled_at) : null;
    return scheduledDate ? toDateKey(scheduledDate) : null;
  }, [activeDetailLoanUuid, calendarWindowLoans]);

  const effectiveSelectedDateKey = detailSelectedDateKey ?? selectedDateKey;

  const selectedDayLoans = useMemo(() => {
    if (!effectiveSelectedDateKey) return [];
    return loansByDay.get(effectiveSelectedDateKey) ?? [];
  }, [effectiveSelectedDateKey, loansByDay]);

  const activeDetailLoanIndex = useMemo(
    () => selectedDayLoans.findIndex((loan) => loan.uuid === activeDetailLoanUuid),
    [selectedDayLoans, activeDetailLoanUuid],
  );

  const hasPreviousDetailLoan = activeDetailLoanIndex > 0;
  const hasNextDetailLoan =
    activeDetailLoanIndex >= 0 && activeDetailLoanIndex < selectedDayLoans.length - 1;

  const selectedDateLabel = useMemo(() => {
    if (!effectiveSelectedDateKey) return "Selecciona un dia";
    const parsed = parseDate(`${effectiveSelectedDateKey}T00:00:00`);
    if (!parsed) return effectiveSelectedDateKey;
    return capitalizeLabel(
      new Intl.DateTimeFormat("es-CL", {
        weekday: "long",
        day: "2-digit",
        month: "long",
        year: "numeric",
      }).format(parsed),
    );
  }, [effectiveSelectedDateKey]);

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
      { key: "approved", label: "Reservadas", value: counts.approved },
      { key: "completed", label: "Completadas", value: counts.completed },
      { key: "cancelled", label: "Canceladas", value: counts.cancelled },
      { key: "rejected", label: "Rechazadas", value: counts.rejected },
    ] as const;
  }, [visibleMonthLoans]);

  const newLoanHref = useMemo(() => {
    const selectedDate = effectiveSelectedDateKey ?? todayKey;
    return `#/inventory/prestamos/nuevo?date=${selectedDate}`;
  }, [effectiveSelectedDateKey, todayKey]);

  useEffect(() => {
    if (!activeDetailLoanUuid) {
      return;
    }
    if (initialLoading || !hasLoadedOnceRef.current) {
      return;
    }
    if (error && calendarWindowLoans.length === 0) {
      return;
    }
    if (selectedDayLoans.length === 0) {
      window.location.replace(stripLoanDetailFromHash(window.location.hash));
      return;
    }
    const stillExists = selectedDayLoans.some((loan) => loan.uuid === activeDetailLoanUuid);
    if (!stillExists) {
      const fallbackLoanUuid = selectedDayLoans[0]?.uuid;
      if (fallbackLoanUuid) {
        window.location.replace(replaceLoanDetailInHash(window.location.hash, fallbackLoanUuid));
      } else {
        window.location.replace(stripLoanDetailFromHash(window.location.hash));
      }
    }
  }, [activeDetailLoanUuid, calendarWindowLoans.length, error, initialLoading, selectedDayLoans]);

  function goToPreviousMonth() {
    setMonthAnchor((current) => new Date(current.getFullYear(), current.getMonth() - 1, 1));
  }

  function goToNextMonth() {
    setMonthAnchor((current) => new Date(current.getFullYear(), current.getMonth() + 1, 1));
  }

  function handleSelectDay(dateKey: string) {
    setSelectedDateKey(dateKey);
    if (isMobileCalendar) {
      setIsMobileDayDrawerOpen(true);
    }
  }

  function handleOpenLoanDetail(loanUuid: string) {
    if (isMobileCalendar) {
      setIsMobileDayDrawerOpen(false);
    }
    window.location.assign(buildLoanDetailHash(loanUuid, "calendar"));
  }

  function handleCloseLoanDetail() {
    window.location.replace(stripLoanDetailFromHash(window.location.hash));
  }

  function showPreviousDetailLoan() {
    if (!hasPreviousDetailLoan) {
      return;
    }
    const previousLoanUuid = selectedDayLoans[activeDetailLoanIndex - 1]?.uuid;
    if (previousLoanUuid) {
      window.location.replace(replaceLoanDetailInHash(window.location.hash, previousLoanUuid));
    }
  }

  function showNextDetailLoan() {
    if (!hasNextDetailLoan) {
      return;
    }
    const nextLoanUuid = selectedDayLoans[activeDetailLoanIndex + 1]?.uuid;
    if (nextLoanUuid) {
      window.location.replace(replaceLoanDetailInHash(window.location.hash, nextLoanUuid));
    }
  }

  const handleDetailLoanChanged = useCallback((updatedLoan: LoanSummary) => {
    const scheduledDate = parseDate(updatedLoan.scheduled_at);
    if (!scheduledDate) {
      return;
    }

    const targetMonthKey = getMonthKey(scheduledDate);
    setMonthLoanCache((current) => {
      const next = new Map<string, CalendarMonthCacheEntry>();
      current.forEach((entry, key) => {
        next.set(key, {
          items: entry.items.filter((loan) => loan.uuid !== updatedLoan.uuid),
          loadedAt: entry.loadedAt,
        });
      });

      const targetEntry = next.get(targetMonthKey);
      if (targetEntry) {
        next.set(targetMonthKey, {
          items: [...targetEntry.items, updatedLoan].sort(sortLoansByScheduledAt),
          loadedAt: Date.now(),
        });
      }

      monthLoanCacheRef.current = next;
      return next;
    });
  }, []);

  const content = (
    <div className="loan-calendar-stage">
      <div className="loan-calendar-page">
        <section className="loan-calendar-header">
          <div className="loan-calendar-header__copy">
              <h1>Agenda de Prestamos</h1>
              <p>
                {isCoordinator
                  ? "Vista operacional del calendario de solicitudes, entregas y nuevas reservas."
                  : "Calendario de tus solicitudes y devoluciones programadas."}
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

        {initialLoading ? (
          <>
            <LoanCalendarInitialSkeleton mobile={isMobileCalendar} />
            <LoanCalendarSummarySkeleton />
          </>
        ) : (
          <>
            <section className="loan-calendar-layout">
              <article className="loan-calendar-card" aria-busy={refreshing}>
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
                    const isCurrentMonth = getMonthKey(day) === monthKey;
                    const isSelected = key === effectiveSelectedDateKey;
                    const isToday = key === todayKey;
                    return (
                      <button
                        type="button"
                        key={key}
                        className={`loan-calendar-day${isCurrentMonth ? "" : " is-faded"}${isSelected ? " is-selected" : ""}${isToday ? " is-today" : ""}`}
                        onClick={() => handleSelectDay(key)}
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
              </article>

              {!isMobileCalendar ? (
                <LoanCalendarDayDrawer
                  selectedDateKey={effectiveSelectedDateKey}
                  selectedDateLabel={selectedDateLabel}
                  selectedDayLoans={selectedDayLoans}
                  onOpenLoan={handleOpenLoanDetail}
                />
              ) : null}
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
          </>
        )}

        <PresencePollingModal
          visible={promptVisible}
          pollingPaused={pollingPaused}
          countdownSeconds={countdownSeconds}
          onContinue={() => {
            void resumePolling();
          }}
        />
      </div>

      {isMobileCalendar && isMobileDayDrawerOpen ? (
        <div className="loan-calendar-mobile-drawer" role="dialog" aria-modal="true" aria-label="Detalle del dia">
          <button
            type="button"
            className="loan-calendar-mobile-drawer__backdrop"
            aria-label="Cerrar detalle del dia"
            onClick={() => setIsMobileDayDrawerOpen(false)}
          />
          <LoanCalendarDayDrawer
            selectedDateKey={effectiveSelectedDateKey}
            selectedDateLabel={selectedDateLabel}
            selectedDayLoans={selectedDayLoans}
            onOpenLoan={handleOpenLoanDetail}
            mobile
            onClose={() => setIsMobileDayDrawerOpen(false)}
          />
        </div>
      ) : null}

      {activeDetailLoanUuid ? (
        <LoanDetailModalFrame
          onClose={handleCloseLoanDetail}
          onPrevious={showPreviousDetailLoan}
          onNext={showNextDetailLoan}
          hasPrevious={hasPreviousDetailLoan}
          hasNext={hasNextDetailLoan}
        >
          <LoanDetailPage
            loanUuid={activeDetailLoanUuid}
            embedded
            hideBackNav
            onLoanChanged={handleDetailLoanChanged}
          />
        </LoanDetailModalFrame>
      ) : null}
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
