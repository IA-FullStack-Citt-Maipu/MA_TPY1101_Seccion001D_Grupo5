import {
  BookOpenText,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileText,
  Minus,
  PackageSearch,
  Plus,
  Search,
  SendHorizontal,
  ShoppingCart,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import { createLoan, fetchLoanByUuid, updateLoan } from "../services/loanService";
import { saveLastCreatedLoan } from "../services/loanSessionService";
import { fetchRooms } from "../services/roomService";
import { fetchSubjects } from "../services/subjectService";
import type { ImplementSummary } from "../types/implement";
import type { CreateLoanPayload, LoanStatus, LoanSummary } from "../types/loan";
import type { RoomOption } from "../types/room";
import type { SubjectOption } from "../types/subject";
import { buildLoanDetailHash } from "../utils/loanDetailRouting";

interface LoanCartItem {
  implement_uuid: string;
  implement_name: string;
  implement_img_url: string | null;
  requested_quantity: number;
}

const RESULTS_PAGE_SIZE = 6;
const LOAN_MIN_TIME = "08:00";
const LOAN_MAX_TIME = "22:00";
const MAX_SCHEDULE_DAYS_AHEAD = 14;

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}

function formatDateForInput(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatTimeForInput(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function getEarliestSelectableMoment(): Date {
  const next = new Date();
  if (next.getSeconds() > 0 || next.getMilliseconds() > 0) {
    next.setMinutes(next.getMinutes() + 1);
  }
  next.setSeconds(0, 0);
  return next;
}

function parseDateOnly(value: string): Date | null {
  if (!value) {
    return null;
  }
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) {
    return null;
  }
  const parsed = new Date(year, month - 1, day, 0, 0, 0, 0);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed;
}

function isSundayDateValue(value: string): boolean {
  const parsed = parseDateOnly(value);
  return parsed?.getDay() === 0;
}

function parseTimeValue(value: string): { hours: number; minutes: number } | null {
  if (!/^\d{2}:\d{2}$/.test(value)) {
    return null;
  }
  const [hours, minutes] = value.split(":").map(Number);
  if (
    Number.isNaN(hours) ||
    Number.isNaN(minutes) ||
    hours < 0 ||
    hours > 23 ||
    minutes < 0 ||
    minutes > 59
  ) {
    return null;
  }
  return { hours, minutes };
}

function isAllowedLoanTimeValue(value: string): boolean {
  const parsed = parseTimeValue(value);
  if (!parsed) {
    return false;
  }
  const totalMinutes = parsed.hours * 60 + parsed.minutes;
  return totalMinutes >= 8 * 60 && totalMinutes <= 22 * 60;
}

function nextAllowedLoanMoment(anchor = new Date()): Date {
  const next = new Date(anchor);
  if (next.getSeconds() > 0 || next.getMilliseconds() > 0) {
    next.setMinutes(next.getMinutes() + 1);
  }
  next.setSeconds(0, 0);

  while (true) {
    if (next.getDay() === 0) {
      next.setDate(next.getDate() + 1);
      next.setHours(8, 0, 0, 0);
      continue;
    }

    const minutes = next.getHours() * 60 + next.getMinutes();
    if (minutes < 8 * 60) {
      next.setHours(8, 0, 0, 0);
      continue;
    }
    if (minutes > 22 * 60) {
      next.setDate(next.getDate() + 1);
      next.setHours(8, 0, 0, 0);
      continue;
    }
    return next;
  }
}

function getLatestSelectableScheduleDate(anchor = new Date()): string {
  const latest = new Date(anchor);
  latest.setHours(0, 0, 0, 0);
  latest.setDate(latest.getDate() + MAX_SCHEDULE_DAYS_AHEAD);
  return formatDateForInput(latest);
}

function getScheduleRangeErrorMessage(): string {
  return "La fecha requerida solo se puede programar entre hoy y los proximos 14 dias corridos.";
}

function validateLoanScheduleSelection({
  dateValue,
  timeValue,
  returnDateValue,
  returnTimeValue,
  latestSelectableDate,
}: {
  dateValue: string;
  timeValue: string;
  returnDateValue: string;
  returnTimeValue: string;
  latestSelectableDate: string;
}): string | null {
  if (!dateValue || !timeValue) {
    return null;
  }
  if (dateValue > latestSelectableDate) {
    return getScheduleRangeErrorMessage();
  }
  if (isSundayDateValue(dateValue)) {
    return "Las solicitudes solo se pueden programar de lunes a sabado.";
  }
  if (!isAllowedLoanTimeValue(timeValue)) {
    return "Las solicitudes solo se pueden programar entre las 08:00 y las 22:00.";
  }

  const scheduledAt = buildScheduledAtIso(dateValue, timeValue);
  if (scheduledAt && isIsoInPast(scheduledAt)) {
    return "La fecha y hora de solicitud no puede estar en el pasado.";
  }

  if (!returnDateValue || !returnTimeValue) {
    return null;
  }
  if (isSundayDateValue(returnDateValue)) {
    return "La devolucion solo se puede programar de lunes a sabado.";
  }
  if (!isAllowedLoanTimeValue(returnTimeValue)) {
    return "La devolucion solo se puede programar entre las 08:00 y las 22:00.";
  }

  const expectedReturnAt = buildScheduledAtIso(returnDateValue, returnTimeValue);
  if (expectedReturnAt && isIsoInPast(expectedReturnAt)) {
    return "La fecha y hora de devolucion no puede estar en el pasado.";
  }
  if (scheduledAt && expectedReturnAt && new Date(expectedReturnAt).getTime() <= new Date(scheduledAt).getTime()) {
    return "La fecha y hora de devolucion debe ser posterior a la fecha y hora de solicitud.";
  }

  return null;
}

function getRequestedDateFromHash(): string | null {
  const hash = window.location.hash || "";
  const queryIndex = hash.indexOf("?");
  if (queryIndex < 0) {
    return null;
  }
  const params = new URLSearchParams(hash.slice(queryIndex + 1));
  const selectedDate = params.get("date")?.trim() ?? "";
  return /^\d{4}-\d{2}-\d{2}$/.test(selectedDate) ? selectedDate : null;
}

function getDefaultLoanMoment(selectedDateValue?: string | null): Date {
  const earliest = nextAllowedLoanMoment(getEarliestSelectableMoment());
  if (!selectedDateValue) {
    return earliest;
  }

  const selectedDate = parseDateOnly(selectedDateValue);
  if (!selectedDate) {
    return earliest;
  }

  const todayKey = formatDateForInput(new Date());
  if (selectedDateValue < todayKey) {
    return earliest;
  }

  if (selectedDate.getDay() === 0) {
    return nextAllowedLoanMoment(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), 8, 0, 0, 0));
  }

  if (selectedDateValue === todayKey) {
    return earliest;
  }

  return new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), 8, 0, 0, 0);
}

function formatScheduledDateForInput(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return formatDateForInput(date);
}

function formatScheduledTimeForInput(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function buildScheduledAtIso(dateValue: string, timeValue: string): string | null {
  if (!dateValue || !timeValue) {
    return null;
  }

  const dateParts = dateValue.split("-").map(Number);
  const timeParts = timeValue.split(":").map(Number);
  if (dateParts.length !== 3 || timeParts.length < 2) {
    return null;
  }

  const [year, month, day] = dateParts;
  const [hours, minutes] = timeParts;
  const local = new Date(year, month - 1, day, hours, minutes, 0, 0);
  if (Number.isNaN(local.getTime())) {
    return null;
  }

  const offsetMinutes = -local.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absoluteOffsetMinutes = Math.abs(offsetMinutes);
  const offsetHoursPart = pad(Math.floor(absoluteOffsetMinutes / 60));
  const offsetMinutesPart = pad(absoluteOffsetMinutes % 60);

  return `${year}-${pad(month)}-${pad(day)}T${pad(hours)}:${pad(minutes)}:00${sign}${offsetHoursPart}:${offsetMinutesPart}`;
}

function isIsoInPast(value: string): boolean {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  return date.getTime() < Date.now();
}

function addHoursToIso(value: string, hours: number): string | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Date(date.getTime() + hours * 60 * 60 * 1000).toISOString();
}

function safePositiveInt(rawValue: string | number, fallback = 1): number {
  const numeric = typeof rawValue === "number" ? rawValue : Number(rawValue);
  if (!Number.isFinite(numeric) || numeric < 1) {
    return fallback;
  }
  return Math.floor(numeric);
}

function getAvailableStock(implement: ImplementSummary): number | null {
  const available = implement.stock?.available;
  if (typeof available === "number") {
    return available;
  }

  const totalStock = implement.stock?.total_stock;
  if (typeof totalStock === "number") {
    return totalStock;
  }

  return null;
}

function getStockLabel(implement: ImplementSummary): string {
  if (implement.stock?.available_display && implement.stock.available_display.trim().length > 0) {
    return implement.stock.available_display;
  }

  const availableStock = getAvailableStock(implement);
  if (availableStock === null) {
    return "Sin dato de stock";
  }

  return `${availableStock} unidades`;
}

function isLowStock(implement: ImplementSummary): boolean {
  const availableStock = getAvailableStock(implement);
  const minStock = implement.stock?.min_stock;
  if (availableStock === null || typeof minStock !== "number") {
    return false;
  }
  return availableStock <= minStock;
}

function normalizeLoanStatusLabel(status: LoanStatus): string {
  const labels: Record<LoanStatus, string> = {
    pending: "Pendiente",
    approved: "Reservado",
    prepared: "Preparado",
    delivered: "Entregado",
    completed: "Finalizado",
    rejected: "Rechazado",
    cancelled: "Cancelado",
    expired: "Expirado",
    overdue: "Atrasado",
  };
  return labels[status];
}

export function LoanCreatePage({
  embedded = false,
  editLoanUuid,
}: {
  embedded?: boolean;
  editLoanUuid?: string;
}) {
  const isEditMode = Boolean(editLoanUuid);
  const [rooms, setRooms] = useState<RoomOption[]>([]);
  const [subjects, setSubjects] = useState<SubjectOption[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [loadingEditLoan, setLoadingEditLoan] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [roomInlineError, setRoomInlineError] = useState<string | null>(null);
  const [scheduleServerError, setScheduleServerError] = useState<string | null>(null);
  const [searchInlineError, setSearchInlineError] = useState<string | null>(null);
  const [duplicateWarning, setDuplicateWarning] = useState<string | null>(null);

  const [roomUuid, setRoomUuid] = useState("");
  const [subjectUuid, setSubjectUuid] = useState("");
  const [dateValue, setDateValue] = useState("");
  const [timeValue, setTimeValue] = useState("");
  const [returnDateValue, setReturnDateValue] = useState("");
  const [returnTimeValue, setReturnTimeValue] = useState("");
  const [notes, setNotes] = useState("");

  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [allImplements, setAllImplements] = useState<ImplementSummary[]>([]);
  const [pendingQuantities, setPendingQuantities] = useState<Record<string, number>>({});
  const [resultsPage, setResultsPage] = useState(1);
  const [cart, setCart] = useState<LoanCartItem[]>([]);
  const [editingLoan, setEditingLoan] = useState<LoanSummary | null>(null);
  const scheduledAt = buildScheduledAtIso(dateValue, timeValue);
  const hasCustomExpectedReturn = Boolean(returnDateValue && returnTimeValue);
  const expectedReturnAt = hasCustomExpectedReturn
    ? buildScheduledAtIso(returnDateValue, returnTimeValue)
    : null;
  const effectiveExpectedReturnAt = expectedReturnAt ?? (scheduledAt ? addHoursToIso(scheduledAt, 2) : null);
  const editBlockedMessage =
    isEditMode && editingLoan && editingLoan.status !== "approved"
      ? `Esta solicitud ya no se puede modificar porque esta en estado ${normalizeLoanStatusLabel(editingLoan.status).toLowerCase()}.`
      : null;

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [search]);

  useEffect(() => {
    if (isEditMode) {
      return;
    }
    const defaultMoment = getDefaultLoanMoment(getRequestedDateFromHash());
    setDateValue((current) => current || formatDateForInput(defaultMoment));
    setTimeValue((current) => current || formatTimeForInput(defaultMoment));
  }, [isEditMode]);

  useEffect(() => {
    async function bootstrap() {
      setLoadingOptions(true);
      setLoadingEditLoan(Boolean(editLoanUuid));
      setGlobalError(null);
      try {
        const [roomsResponse, subjectsResponse, editingLoanResponse] = await Promise.all([
          fetchRooms(),
          fetchSubjects(),
          editLoanUuid ? fetchLoanByUuid(editLoanUuid) : Promise.resolve(null),
        ]);
        setRooms(roomsResponse);
        setSubjects(subjectsResponse);

        if (editLoanUuid) {
          if (!editingLoanResponse) {
            setGlobalError("No se pudo cargar la solicitud a modificar.");
          } else {
            setEditingLoan(editingLoanResponse);
            setRoomUuid(editingLoanResponse.room?.uuid ?? "");
            setSubjectUuid(editingLoanResponse.subject?.uuid ?? "");
            setDateValue(formatScheduledDateForInput(editingLoanResponse.scheduled_at));
            setTimeValue(formatScheduledTimeForInput(editingLoanResponse.scheduled_at));
            setReturnDateValue(formatScheduledDateForInput(editingLoanResponse.expected_return_at));
            setReturnTimeValue(formatScheduledTimeForInput(editingLoanResponse.expected_return_at));
            setNotes("");
            setCart(
              editingLoanResponse.items.map((item) => {
                return {
                  implement_uuid: item.implement_uuid,
                  implement_name: item.implement_name,
                  implement_img_url: null,
                  requested_quantity: safePositiveInt(item.requested_quantity, 1),
                };
              }),
            );
          }
        } else {
          setEditingLoan(null);
          setNotes("");
        }
      } catch (requestError) {
        setGlobalError(getErrorMessage(requestError, "No se pudieron cargar las opciones del formulario."));
      } finally {
        setLoadingOptions(false);
        setLoadingEditLoan(false);
      }
    }

    void bootstrap();
  }, [editLoanUuid]);

  useEffect(() => {
    let cancelled = false;

    async function loadCatalog() {
      if (!scheduledAt || !effectiveExpectedReturnAt) {
        setAllImplements([]);
        setCatalogLoading(false);
        return;
      }

      setCatalogLoading(true);
      try {
        const rows = await fetchImplements({
          scheduledAt,
          expectedReturnAt: effectiveExpectedReturnAt,
          excludeLoanUuid: editLoanUuid ?? undefined,
        });

        if (cancelled) {
          return;
        }

        setAllImplements(rows);
      } catch (requestError) {
        if (cancelled) {
          return;
        }
        setGlobalError((current) => current ?? getErrorMessage(requestError, "No se pudo consultar la disponibilidad de implementos."));
        setAllImplements([]);
      } finally {
        if (!cancelled) {
          setCatalogLoading(false);
        }
      }
    }

    void loadCatalog();

    return () => {
      cancelled = true;
    };
  }, [editLoanUuid, effectiveExpectedReturnAt, scheduledAt]);

  const filteredResults = useMemo(() => {
    const normalizedTerm = debouncedSearch.trim().toLowerCase();
    if (!normalizedTerm) {
      return allImplements;
    }

    return allImplements.filter((implement) => {
      const haystack = [
        implement.name,
        implement.category?.name ?? "",
        implement.barcode ?? "",
      ]
        .join(" ")
        .toLowerCase();
      return haystack.includes(normalizedTerm);
    });
  }, [allImplements, debouncedSearch]);

  const totalResultPages = Math.max(1, Math.ceil(filteredResults.length / RESULTS_PAGE_SIZE));
  const safeResultsPage = Math.min(resultsPage, totalResultPages);
  const resultsStartIndex = (safeResultsPage - 1) * RESULTS_PAGE_SIZE;
  const pagedResults = filteredResults.slice(resultsStartIndex, resultsStartIndex + RESULTS_PAGE_SIZE);

  const resultsRangeStart = filteredResults.length === 0 ? 0 : resultsStartIndex + 1;
  const resultsRangeEnd = filteredResults.length === 0
    ? 0
    : Math.min(resultsStartIndex + pagedResults.length, filteredResults.length);

  const visiblePageNumbers = useMemo(() => {
    const windowSize = 5;
    let start = Math.max(1, safeResultsPage - 2);
    const end = Math.min(totalResultPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [safeResultsPage, totalResultPages]);

  useEffect(() => {
    setResultsPage(1);
  }, [debouncedSearch]);

  useEffect(() => {
    if (resultsPage > totalResultPages) {
      setResultsPage(totalResultPages);
    }
  }, [resultsPage, totalResultPages]);

  const earliestSelectableMoment = nextAllowedLoanMoment(getEarliestSelectableMoment());
  const earliestSelectableDate = formatDateForInput(earliestSelectableMoment);
  const earliestSelectableTime = formatTimeForInput(earliestSelectableMoment);
  const latestSelectableDate = getLatestSelectableScheduleDate();
  const scheduledMinTime = dateValue === earliestSelectableDate ? earliestSelectableTime : LOAN_MIN_TIME;
  const expectedReturnMinTime = returnDateValue === earliestSelectableDate ? earliestSelectableTime : LOAN_MIN_TIME;
  const scheduleClientError = useMemo(
    () =>
      validateLoanScheduleSelection({
        dateValue,
        timeValue,
        returnDateValue,
        returnTimeValue,
        latestSelectableDate,
      }),
    [dateValue, latestSelectableDate, returnDateValue, returnTimeValue, timeValue],
  );
  const scheduleInlineError = scheduleClientError ?? scheduleServerError;
  const hasValidQuantities = cart.every(
    (item) => Number.isInteger(item.requested_quantity) && item.requested_quantity > 0,
  );
  const canSubmit = Boolean(
    roomUuid && scheduledAt && cart.length > 0 && hasValidQuantities && !scheduleInlineError && !saving && !loadingEditLoan,
  );

  const resultCategoryChips = useMemo(() => {
    const unique = new Set<string>();
    const chips: string[] = [];
    for (const result of filteredResults) {
      const categoryName = result.category?.name?.trim();
      if (!categoryName) {
        continue;
      }
      const normalized = categoryName.toLowerCase();
      if (!unique.has(normalized)) {
        unique.add(normalized);
        chips.push(categoryName);
      }
      if (chips.length >= 2) {
        break;
      }
    }
    return chips;
  }, [filteredResults]);

  const implementByUuid = useMemo(
    () =>
      new Map(
        allImplements.map((implement) => [implement.uuid, implement] as const),
      ),
    [allImplements],
  );

  useEffect(() => {
    if (allImplements.length === 0) {
      return;
    }
    setCart((previous) =>
      previous.map((item) => {
        if (item.implement_img_url) {
          return item;
        }
        const relatedImplement = implementByUuid.get(item.implement_uuid);
        if (!relatedImplement?.imgUrl?.trim()) {
          return item;
        }
        return {
          ...item,
          implement_img_url: relatedImplement.imgUrl.trim(),
        };
      }),
    );
  }, [allImplements, implementByUuid]);

  useEffect(() => {
    if (cart.length === 0) {
      if (searchInlineError?.startsWith("Solo puedes solicitar dentro del stock disponible.")) {
        setSearchInlineError(null);
      }
      return;
    }

    const stockConflict = cart.find((item) => {
      const implement = implementByUuid.get(item.implement_uuid);
      const availableStock = implement ? getAvailableStock(implement) : null;
      return availableStock !== null && item.requested_quantity > availableStock;
    });

    if (!stockConflict) {
      if (searchInlineError?.startsWith("Solo puedes solicitar dentro del stock disponible.")) {
        setSearchInlineError(null);
      }
      return;
    }

    const implement = implementByUuid.get(stockConflict.implement_uuid);
    const availableStock = implement ? getAvailableStock(implement) : null;
    setSearchInlineError(
      `Solo puedes solicitar dentro del stock disponible. ${stockConflict.implement_name} tiene ${availableStock ?? 0} unidad(es) disponibles para esta solicitud.`,
    );
  }, [cart, implementByUuid, searchInlineError]);

  function getCurrentCartQuantity(implementUuid: string): number {
    return cart.find((item) => item.implement_uuid === implementUuid)?.requested_quantity ?? 0;
  }

  function getRemainingStockCapacity(implement: ImplementSummary): number | null {
    const availableStock = getAvailableStock(implement);
    if (availableStock === null) {
      return null;
    }
    return Math.max(availableStock - getCurrentCartQuantity(implement.uuid), 0);
  }

  function getPendingQuantity(implementUuid: string): number {
    return pendingQuantities[implementUuid] ?? 1;
  }

  function increasePendingQuantity(implementUuid: string, delta: number, maxAllowed?: number | null) {
    setPendingQuantities((previous) => {
      const current = previous[implementUuid] ?? 1;
      const nextValue = safePositiveInt(current + delta, 1);
      return {
        ...previous,
        [implementUuid]:
          typeof maxAllowed === "number" ? Math.min(nextValue, Math.max(maxAllowed, 1)) : nextValue,
      };
    });
  }

  function handleScheduleDateChange(nextDateValue: string) {
    setScheduleServerError(null);
    if (!nextDateValue) {
      setDateValue(nextDateValue);
      return;
    }
    setDateValue(nextDateValue);
    const nextMinTime = nextDateValue === earliestSelectableDate ? earliestSelectableTime : LOAN_MIN_TIME;
    if (!timeValue || timeValue < nextMinTime) {
      setTimeValue(nextMinTime);
    }
  }

  function handleScheduleTimeChange(nextTimeValue: string) {
    setScheduleServerError(null);
    setTimeValue(nextTimeValue);
  }

  function handleReturnDateChange(nextDateValue: string) {
    setScheduleServerError(null);
    if (!nextDateValue) {
      setReturnDateValue("");
      setReturnTimeValue("");
      return;
    }
    setReturnDateValue(nextDateValue);
    const nextMinTime = nextDateValue === earliestSelectableDate ? earliestSelectableTime : LOAN_MIN_TIME;
    if (returnTimeValue && returnTimeValue < nextMinTime) {
      setReturnTimeValue(nextMinTime);
    }
  }

  function handleReturnTimeChange(nextTimeValue: string) {
    setScheduleServerError(null);
    if (!nextTimeValue) {
      setReturnDateValue("");
      setReturnTimeValue("");
      return;
    }
    if (!returnDateValue) {
      setReturnDateValue(dateValue);
    }
    setReturnTimeValue(nextTimeValue);
  }

  function addImplement(implement: ImplementSummary, quantity: number) {
    const normalizedQuantity = safePositiveInt(quantity, 1);
    setSearchInlineError(null);

    const availableStock = getAvailableStock(implement);
    const existingQuantity = getCurrentCartQuantity(implement.uuid);
    if (availableStock !== null && existingQuantity + normalizedQuantity > availableStock) {
      setDuplicateWarning(null);
      setSearchInlineError("Solo puedes solicitar dentro del stock disponible.");
      return;
    }

    setCart((previous) => {
      const existing = previous.find((item) => item.implement_uuid === implement.uuid);
      if (existing) {
        setDuplicateWarning("El implemento ya estaba en tu solicitud. Se actualizo la cantidad.");
        const updated = previous.map((item) =>
          item.implement_uuid === implement.uuid
            ? {
                ...item,
                requested_quantity: item.requested_quantity + normalizedQuantity,
                implement_img_url: item.implement_img_url ?? implement.imgUrl?.trim() ?? null,
              }
            : item,
        );
        setPendingQuantities((previousValues) => ({ ...previousValues, [implement.uuid]: 1 }));
        return updated;
      }

      setDuplicateWarning(null);
      const updated = [
        ...previous,
        {
          implement_uuid: implement.uuid,
          implement_name: implement.name,
          implement_img_url: implement.imgUrl?.trim() ?? null,
          requested_quantity: normalizedQuantity,
        },
      ];
      setPendingQuantities((previousValues) => ({ ...previousValues, [implement.uuid]: 1 }));
      return updated;
    });
  }

  function adjustCartQuantity(implementUuid: string, delta: number) {
    setSearchInlineError(null);
    setCart((previous) =>
      previous.map((item) => {
        if (item.implement_uuid !== implementUuid) {
          return item;
        }

        const implement = implementByUuid.get(implementUuid);
        const nextQuantity = safePositiveInt(item.requested_quantity + delta, 1);
        const availableStock = implement ? getAvailableStock(implement) : null;
        if (availableStock !== null && nextQuantity > availableStock) {
          setSearchInlineError("Solo puedes solicitar dentro del stock disponible.");
          return item;
        }

        return { ...item, requested_quantity: nextQuantity };
      }),
    );
  }

  function removeImplement(implementUuid: string) {
    setCart((previous) => previous.filter((item) => item.implement_uuid !== implementUuid));
    setSearchInlineError(null);
  }

  function navigateCancel() {
    if (window.history.length > 1) {
      window.history.back();
      return;
    }

    if (isEditMode && editLoanUuid) {
      window.location.assign(buildLoanDetailHash(editLoanUuid, "list"));
      return;
    }

    window.location.hash = "#/inventory/prestamos";
  }

  async function submitLoan() {
    setGlobalError(null);
    setRoomInlineError(null);
    setScheduleServerError(null);
    setSearchInlineError(null);
    setDuplicateWarning(null);

    if (isEditMode && editingLoan && editingLoan.status !== "approved") {
      setGlobalError("Solo puedes modificar solicitudes en estado reservado.");
      return;
    }

    if (!roomUuid) {
      setRoomInlineError("Debes seleccionar una sala.");
      return;
    }
    if (!scheduledAt) {
      setGlobalError("Debes completar fecha y hora validas.");
      return;
    }
    if (scheduleClientError) {
      return;
    }
    if (cart.length === 0) {
      setSearchInlineError("Debes agregar al menos un implemento a la solicitud.");
      return;
    }
    if (!hasValidQuantities) {
      setSearchInlineError("Todas las cantidades deben ser enteros mayores a cero.");
      return;
    }

    const stockConflict = cart.find((item) => {
      const implement = implementByUuid.get(item.implement_uuid);
      const availableStock = implement ? getAvailableStock(implement) : null;
      return availableStock !== null && item.requested_quantity > availableStock;
    });
    if (stockConflict) {
      const implement = implementByUuid.get(stockConflict.implement_uuid);
      const availableStock = implement ? getAvailableStock(implement) : null;
      setSearchInlineError(
        `Solo puedes solicitar dentro del stock disponible. ${stockConflict.implement_name} tiene ${availableStock ?? 0} unidad(es) disponibles para esta solicitud.`,
      );
      return;
    }

    const payload: CreateLoanPayload = {
      room_uuid: roomUuid,
      subject_uuid: subjectUuid || null,
      scheduled_at: scheduledAt,
      expected_return_at: hasCustomExpectedReturn ? expectedReturnAt : null,
      notes: notes.trim() || null,
      items: cart.map((item) => ({
        implement_uuid: item.implement_uuid,
        requested_quantity: item.requested_quantity,
      })),
    };

    setSaving(true);
    try {
      let savedLoan: LoanSummary;
      if (isEditMode && editLoanUuid) {
        savedLoan = await updateLoan(editLoanUuid, payload);
      } else {
        savedLoan = await createLoan(payload);
        saveLastCreatedLoan(savedLoan);
      }
      window.location.assign(buildLoanDetailHash(savedLoan.uuid, "list"));
    } catch (requestError) {
      const payloadError = getApiErrorPayload(requestError);
      if (payloadError?.code === "LOAN_DUPLICATE_REQUEST") {
        setSearchInlineError(payloadError.message);
      } else if (
        payloadError?.code === "LOAN_SCHEDULE_PAST_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_SCHEDULE_RANGE_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_SCHEDULE_DAY_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_SCHEDULE_TIME_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_EXPECTED_RETURN_INVALID" ||
        payloadError?.code === "LOAN_EXPECTED_RETURN_PAST_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_EXPECTED_RETURN_DAY_NOT_ALLOWED" ||
        payloadError?.code === "LOAN_EXPECTED_RETURN_TIME_NOT_ALLOWED"
      ) {
        setScheduleServerError(payloadError.message);
      } else if (payloadError?.code === "LOAN_STOCK_CONFLICT") {
        setSearchInlineError(payloadError.message);
      } else if (
        payloadError?.code === "LOAN_ROOM_NOT_FOUND" ||
        payloadError?.code === "ROOM_NOT_FOUND"
      ) {
        setRoomInlineError(payloadError.message);
      } else {
        setGlobalError(
          payloadError?.message ??
            getErrorMessage(
              requestError,
              isEditMode ? "No se pudo guardar los cambios de la solicitud." : "No se pudo enviar la solicitud.",
            ),
        );
      }
    } finally {
      setSaving(false);
    }
  }

  const content = (
    <div className="loan-create-page">
      <section className="loan-create-hero">
        <p className="loan-create-hero__eyebrow">Prestamos / Solicitudes</p>
        <h1>{isEditMode ? "Modificar Prestamo" : "Nueva Solicitud de Prestamo"}</h1>
        <p>
          {isEditMode
            ? "Ajusta sala, fecha/hora e implementos para actualizar la solicitud."
            : "Completa sala, fecha/hora e implementos para enviar una solicitud al panol."}
        </p>
      </section>

      {globalError ? <div className="error-banner">{globalError}</div> : null}
      {isEditMode && loadingEditLoan ? (
        <div className="panel">
          <p className="text-muted">Cargando datos de la solicitud para modificacion...</p>
        </div>
      ) : null}

      {editBlockedMessage ? (
        <div className="panel">
          <p className="field-error">{editBlockedMessage}</p>
          <p className="text-muted">
            Vuelve al detalle de la solicitud para revisar su estado actual. Las solicitudes aprobadas o en otro estado ya no
            permiten cambios desde este formulario.
          </p>
          <div className="loan-create-summary__actions">
            <button type="button" className="button button--ghost" onClick={navigateCancel}>
              Volver al detalle
            </button>
          </div>
        </div>
      ) : (
      <section className="loan-create-layout">
        <div className="loan-create-left-column">
          <article className="panel loan-create-card">
            <header className="loan-create-card__header">
              <h2>Datos de la solicitud</h2>
            </header>

            <div className="loan-create-form-grid">
              <div className="loan-create-field">
                <label htmlFor="loan-room">
                  <BookOpenText size={14} /> Sala de uso
                </label>
                <select
                  id="loan-room"
                  value={roomUuid}
                  disabled={loadingOptions || saving}
                  onChange={(event) => {
                    setRoomUuid(event.target.value);
                    setRoomInlineError(null);
                  }}
                >
                  <option value="">Selecciona una sala</option>
                  {rooms.map((room) => (
                    <option key={room.uuid} value={room.uuid}>
                      {room.name}
                    </option>
                  ))}
                </select>
                {roomInlineError ? <p className="field-error">{roomInlineError}</p> : null}
              </div>

              <div className="loan-create-field">
                <label htmlFor="loan-subject">
                  <BookOpenText size={14} /> Asignatura (opcional)
                </label>
                <select
                  id="loan-subject"
                  value={subjectUuid}
                  disabled={loadingOptions || saving}
                  onChange={(event) => setSubjectUuid(event.target.value)}
                >
                  <option value="">Sin asignatura</option>
                  {subjects.map((subject) => (
                    <option key={subject.uuid} value={subject.uuid}>
                      {subject.code} - {subject.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="loan-create-field">
                <label htmlFor="loan-date">
                  <CalendarDays size={14} /> Fecha requerida
                </label>
                <input
                  id="loan-date"
                  type="date"
                  value={dateValue}
                  min={earliestSelectableDate}
                  max={latestSelectableDate}
                  disabled={saving}
                  onChange={(event) => handleScheduleDateChange(event.target.value)}
                />
              </div>

              <div className="loan-create-field">
                <label htmlFor="loan-time">
                  <Clock3 size={14} /> Hora de inicio
                </label>
                <input
                  id="loan-time"
                  type="time"
                  value={timeValue}
                  min={scheduledMinTime}
                  max={LOAN_MAX_TIME}
                  disabled={saving}
                  onChange={(event) => handleScheduleTimeChange(event.target.value)}
                />
              </div>

              <div className="loan-create-field">
                <label htmlFor="loan-return-date">
                  <CalendarDays size={14} /> Fecha de devolucion (opcional)
                </label>
                <input
                  id="loan-return-date"
                  type="date"
                  value={returnDateValue}
                  min={earliestSelectableDate}
                  disabled={saving}
                  onChange={(event) => handleReturnDateChange(event.target.value)}
                />
              </div>

              <div className="loan-create-field">
                <label htmlFor="loan-return-time">
                  <Clock3 size={14} /> Hora de devolucion (opcional)
                </label>
                <input
                  id="loan-return-time"
                  type="time"
                  value={returnTimeValue}
                  min={expectedReturnMinTime}
                  max={LOAN_MAX_TIME}
                  disabled={saving}
                  onChange={(event) => handleReturnTimeChange(event.target.value)}
                />
              </div>

              <div className="loan-create-field loan-create-field--full">
                <label htmlFor="loan-notes">
                  <FileText size={14} /> Notas (opcional)
                </label>
                <textarea
                  id="loan-notes"
                  rows={3}
                  value={notes}
                  maxLength={1000}
                  disabled={saving}
                  onChange={(event) => setNotes(event.target.value)}
                  placeholder="Observaciones para el panol..."
                />
              </div>

              <div className="loan-create-note loan-create-note--warning">
                en caso de que no se ingrese una fecha y hora de devolucion, se usara la misma fecha y hora de solicitud con un incremento de 2 horas
              </div>
              <div className="loan-create-note loan-create-note--warning">
                la disponibilidad se valida para esta solicitud. Si solo marcas la fecha y hora de solicitud, el sistema considerara como devolucion esa misma fecha con un incremento de 2 horas. En implementos consumibles de uso unico, una vez reservados quedan bloqueados para nuevas solicitudes hasta su entrega, cancelacion o expiracion.
              </div>
              {scheduleInlineError ? <p className="field-error loan-create-form-grid__error">{scheduleInlineError}</p> : null}
            </div>
          </article>

          <article className="panel loan-create-card">
            <header className="loan-create-card__header loan-create-card__header--between">
              <h2>Buscar implementos</h2>
              <div className="loan-create-chip-list">
                {resultCategoryChips.length === 0 ? (
                  <span className="loan-create-chip loan-create-chip--muted">Sin categorias</span>
                ) : (
                  resultCategoryChips.map((chip) => (
                    <span key={chip} className="loan-create-chip">
                      {chip}
                    </span>
                  ))
                )}
              </div>
            </header>

            <label htmlFor="loan-search-input" className="loan-create-search-box">
              <Search size={16} />
              <input
                id="loan-search-input"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Buscar por nombre, categoria o codigo"
                disabled={saving}
              />
            </label>

            {duplicateWarning ? <p className="field-hint">{duplicateWarning}</p> : null}
            {searchInlineError ? <p className="field-error">{searchInlineError}</p> : null}

            <div className="loan-create-results">
              {catalogLoading ? (
                <p className="text-muted">Cargando implementos...</p>
              ) : filteredResults.length === 0 ? (
                <p className="text-muted">No se encontraron implementos con ese criterio.</p>
              ) : (
                pagedResults.map((result) => {
                  const pendingQuantity = getPendingQuantity(result.uuid);
                  const lowStock = isLowStock(result);
                  const imageUrl = result.imgUrl?.trim();
                  const remainingCapacity = getRemainingStockCapacity(result);
                  const addDisabled = saving || remainingCapacity === 0;

                  return (
                    <article key={result.uuid} className="loan-create-result-item">
                      <div className="loan-create-result-item__thumb" aria-hidden="true">
                        {imageUrl ? (
                          <img src={imageUrl} alt={`Imagen de ${result.name}`} />
                        ) : (
                          <PackageSearch size={18} />
                        )}
                      </div>

                      <div className="loan-create-result-item__meta">
                        <strong>{result.name}</strong>
                        <p>{result.category?.name ?? "Sin categoria"}</p>
                        <small className={lowStock ? "loan-stock-label loan-stock-label--low" : "loan-stock-label"}>
                          {getStockLabel(result)}
                        </small>
                      </div>

                      <div className="loan-create-result-item__actions">
                        <div className="loan-stepper" role="group" aria-label={`Cantidad para ${result.name}`}>
                          <button
                            type="button"
                            onClick={() => increasePendingQuantity(result.uuid, -1, remainingCapacity)}
                            disabled={saving}
                            aria-label="Disminuir cantidad"
                          >
                            <Minus size={14} />
                          </button>
                          <span className="loan-stepper__value" aria-live="polite">
                            {pendingQuantity}
                          </span>
                          <button
                            type="button"
                            onClick={() => increasePendingQuantity(result.uuid, 1, remainingCapacity)}
                            disabled={saving || (typeof remainingCapacity === "number" && pendingQuantity >= Math.max(remainingCapacity, 1))}
                            aria-label="Aumentar cantidad"
                          >
                            <Plus size={14} />
                          </button>
                        </div>

                        <button
                          type="button"
                          className="loan-add-item-btn"
                          disabled={addDisabled}
                          onClick={() => addImplement(result, pendingQuantity)}
                        >
                          <Plus size={14} />
                          {remainingCapacity === 0 ? "Sin stock" : "Agregar"}
                        </button>
                      </div>
                    </article>
                  );
                })
              )}
            </div>

            {!catalogLoading && filteredResults.length > 0 ? (
              <div className="loan-results-footer">
                <p>
                  Mostrando {resultsRangeStart}-{resultsRangeEnd} de {filteredResults.length} implementos
                </p>
                <div className="loan-pagination">
                  <button
                    type="button"
                    className="loan-pagination__btn"
                    onClick={() => setResultsPage((current) => Math.max(1, current - 1))}
                    disabled={safeResultsPage <= 1}
                    aria-label="Pagina anterior"
                  >
                    <ChevronLeft size={15} />
                  </button>
                  {visiblePageNumbers.map((pageNumber) => (
                    <button
                      key={pageNumber}
                      type="button"
                      className={`loan-pagination__btn${pageNumber === safeResultsPage ? " loan-pagination__btn--active" : ""}`}
                      onClick={() => setResultsPage(pageNumber)}
                    >
                      {pageNumber}
                    </button>
                  ))}
                  <button
                    type="button"
                    className="loan-pagination__btn"
                    onClick={() => setResultsPage((current) => Math.min(totalResultPages, current + 1))}
                    disabled={safeResultsPage >= totalResultPages}
                    aria-label="Pagina siguiente"
                  >
                    <ChevronRight size={15} />
                  </button>
                </div>
              </div>
            ) : null}
          </article>
        </div>

        <aside className="loan-create-side">
          <section className="loan-create-summary">
            <header className="loan-create-summary__header">
              <div>
                <h3>
                  <ShoppingCart size={18} /> Items seleccionados
                </h3>
              </div>
              <span className="loan-create-summary__count">{cart.length} item(s)</span>
            </header>

            <div className="loan-create-summary__table-wrap">
              <table className="loan-create-summary__table">
                <thead>
                  <tr>
                    <th>Implemento</th>
                    <th>Cant.</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {cart.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="loan-create-summary__empty">
                        Aun no agregas implementos a esta solicitud.
                      </td>
                    </tr>
                  ) : (
                    cart.map((item) => (
                      (() => {
                        const implement = implementByUuid.get(item.implement_uuid);
                        const availableStock = implement ? getAvailableStock(implement) : null;
                        const canIncrease = availableStock === null || item.requested_quantity < availableStock;

                        return (
                          <tr key={item.implement_uuid}>
                            <td>
                              <div className="loan-summary-item-cell">
                                <div className="loan-summary-item-cell__thumb" aria-hidden="true">
                                  {item.implement_img_url ? (
                                    <img src={item.implement_img_url} alt={`Imagen de ${item.implement_name}`} />
                                  ) : (
                                    <PackageSearch size={16} />
                                  )}
                                </div>
                                <div className="loan-summary-item-cell__copy">
                                  <p>{item.implement_name}</p>
                                </div>
                              </div>
                            </td>
                            <td>
                              <div className="loan-stepper loan-stepper--compact">
                                <button
                                  type="button"
                                  onClick={() => adjustCartQuantity(item.implement_uuid, -1)}
                                  disabled={saving}
                                  aria-label="Disminuir cantidad"
                                >
                                  <Minus size={12} />
                                </button>
                                <span className="loan-stepper__value" aria-live="polite">
                                  {item.requested_quantity}
                                </span>
                                <button
                                  type="button"
                                  onClick={() => adjustCartQuantity(item.implement_uuid, 1)}
                                  disabled={saving || !canIncrease}
                                  aria-label="Aumentar cantidad"
                                >
                                  <Plus size={12} />
                                </button>
                              </div>
                            </td>
                            <td>
                              <button
                                type="button"
                                className="loan-remove-item-btn"
                                disabled={saving}
                                onClick={() => removeImplement(item.implement_uuid)}
                                aria-label="Eliminar implemento"
                              >
                                <Trash2 size={15} />
                              </button>
                            </td>
                          </tr>
                        );
                      })()
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <footer className="loan-create-summary__footer">
              <p>
                Estado:{" "}
                <strong>
                  {canSubmit
                    ? isEditMode
                      ? "Lista para guardar"
                      : "Lista para enviar"
                    : "Completa los campos requeridos"}
                </strong>
              </p>

              <div className="loan-create-summary__actions">
                <button
                  type="button"
                  className="loan-secondary-btn"
                  onClick={navigateCancel}
                  disabled={saving}
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  className="loan-primary-btn"
                  disabled={!canSubmit}
                  onClick={() => void submitLoan()}
                >
                  <SendHorizontal size={16} />
                  {saving
                    ? isEditMode
                      ? "Guardando..."
                      : "Enviando..."
                    : isEditMode
                      ? "Guardar cambios"
                      : "Enviar solicitud"}
                </button>
              </div>
            </footer>
          </section>
        </aside>
      </section>
      )}
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
