import {
  BookOpenText,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
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
import type { CreateLoanPayload, LoanSummary } from "../types/loan";
import type { RoomOption } from "../types/room";
import type { SubjectOption } from "../types/subject";

interface LoanCartItem {
  implement_uuid: string;
  implement_name: string;
  implement_img_url: string | null;
  requested_quantity: number;
}

const RESULTS_PAGE_SIZE = 6;

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
  const [searchInlineError, setSearchInlineError] = useState<string | null>(null);
  const [duplicateWarning, setDuplicateWarning] = useState<string | null>(null);

  const [roomUuid, setRoomUuid] = useState("");
  const [subjectUuid, setSubjectUuid] = useState("");
  const [dateValue, setDateValue] = useState("");
  const [timeValue, setTimeValue] = useState("");
  const [returnDateValue, setReturnDateValue] = useState("");
  const [returnTimeValue, setReturnTimeValue] = useState("");

  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [allImplements, setAllImplements] = useState<ImplementSummary[]>([]);
  const [pendingQuantities, setPendingQuantities] = useState<Record<string, number>>({});
  const [resultsPage, setResultsPage] = useState(1);
  const [cart, setCart] = useState<LoanCartItem[]>([]);
  const [editingLoan, setEditingLoan] = useState<LoanSummary | null>(null);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [search]);

  useEffect(() => {
    const nextAvailable = getEarliestSelectableMoment();
    setDateValue((current) => current || formatDateForInput(nextAvailable));
    setTimeValue((current) => current || formatTimeForInput(nextAvailable));
  }, []);

  useEffect(() => {
    async function bootstrap() {
      setLoadingOptions(true);
      setLoadingEditLoan(Boolean(editLoanUuid));
      setCatalogLoading(true);
      setGlobalError(null);
      try {
        const [roomsResponse, subjectsResponse, implementsResponse, editingLoanResponse] = await Promise.all([
          fetchRooms(),
          fetchSubjects(),
          fetchImplements(),
          editLoanUuid ? fetchLoanByUuid(editLoanUuid) : Promise.resolve(null),
        ]);
        setRooms(roomsResponse);
        setSubjects(subjectsResponse);
        setAllImplements(implementsResponse);

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
            setCart(
              editingLoanResponse.items.map((item) => {
                const relatedImplement = implementsResponse.find(
                  (implement) => implement.uuid === item.implement_uuid,
                );
                return {
                  implement_uuid: item.implement_uuid,
                  implement_name: item.implement_name,
                  implement_img_url: relatedImplement?.imgUrl?.trim() ?? null,
                  requested_quantity: safePositiveInt(item.requested_quantity, 1),
                };
              }),
            );
          }
        } else {
          setEditingLoan(null);
        }
      } catch (requestError) {
        setGlobalError(getErrorMessage(requestError, "No se pudieron cargar las opciones del formulario."));
      } finally {
        setLoadingOptions(false);
        setLoadingEditLoan(false);
        setCatalogLoading(false);
      }
    }

    void bootstrap();
  }, [editLoanUuid]);

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
        implement.uuid,
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
    let end = Math.min(totalResultPages, start + windowSize - 1);
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

  const scheduledAt = buildScheduledAtIso(dateValue, timeValue);
  const expectedReturnAt = returnDateValue && returnTimeValue
    ? buildScheduledAtIso(returnDateValue, returnTimeValue)
    : null;
  const hasPartialExpectedReturn = Boolean(returnDateValue || returnTimeValue) && !(returnDateValue && returnTimeValue);
  const earliestSelectableMoment = getEarliestSelectableMoment();
  const earliestSelectableDate = formatDateForInput(earliestSelectableMoment);
  const earliestSelectableTime = formatTimeForInput(earliestSelectableMoment);
  const scheduledMinTime = dateValue === earliestSelectableDate ? earliestSelectableTime : undefined;
  const expectedReturnMinTime = returnDateValue === earliestSelectableDate ? earliestSelectableTime : undefined;
  const hasValidQuantities = cart.every(
    (item) => Number.isInteger(item.requested_quantity) && item.requested_quantity > 0,
  );
  const canSubmit = Boolean(
    roomUuid && scheduledAt && cart.length > 0 && hasValidQuantities && !saving && !loadingEditLoan,
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

  function getPendingQuantity(implementUuid: string): number {
    return pendingQuantities[implementUuid] ?? 1;
  }

  function increasePendingQuantity(implementUuid: string, delta: number) {
    setPendingQuantities((previous) => {
      const current = previous[implementUuid] ?? 1;
      return {
        ...previous,
        [implementUuid]: safePositiveInt(current + delta, 1),
      };
    });
  }

  function addImplement(implement: ImplementSummary, quantity: number) {
    const normalizedQuantity = safePositiveInt(quantity, 1);
    setSearchInlineError(null);

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
    setCart((previous) =>
      previous.map((item) =>
        item.implement_uuid === implementUuid
          ? { ...item, requested_quantity: safePositiveInt(item.requested_quantity + delta, 1) }
          : item,
      ),
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
      window.location.hash = `#/inventory/prestamos/${editLoanUuid}`;
      return;
    }

    window.location.hash = "#/inventory/prestamos";
  }

  async function submitLoan() {
    setGlobalError(null);
    setRoomInlineError(null);
    setSearchInlineError(null);
    setDuplicateWarning(null);

    if (!roomUuid) {
      setRoomInlineError("Debes seleccionar una sala.");
      return;
    }
    if (!scheduledAt) {
      setGlobalError("Debes completar fecha y hora validas.");
      return;
    }
    if (isIsoInPast(scheduledAt)) {
      setGlobalError("La fecha y hora de solicitud no puede estar en el pasado.");
      return;
    }
    if (hasPartialExpectedReturn) {
      setGlobalError("Si ingresas una fecha de devolucion, tambien debes ingresar su hora.");
      return;
    }
    if (expectedReturnAt && isIsoInPast(expectedReturnAt)) {
      setGlobalError("La fecha y hora de devolucion no puede estar en el pasado.");
      return;
    }
    if (expectedReturnAt && new Date(expectedReturnAt).getTime() <= new Date(scheduledAt).getTime()) {
      setGlobalError("La fecha y hora de devolucion debe ser posterior a la fecha y hora de solicitud.");
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

    const payload: CreateLoanPayload = {
      room_uuid: roomUuid,
      subject_uuid: subjectUuid || null,
      scheduled_at: scheduledAt,
      expected_return_at: expectedReturnAt,
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
      window.location.hash = `#/inventory/prestamos/${savedLoan.uuid}`;
    } catch (requestError) {
      const payloadError = getApiErrorPayload(requestError);
      if (payloadError?.code === "LOAN_DUPLICATE_REQUEST") {
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
      {isEditMode && editingLoan ? (
        <div className="field-hint">Editando solicitud {editingLoan.uuid}</div>
      ) : null}

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
                  disabled={saving}
                  onChange={(event) => setDateValue(event.target.value)}
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
                  disabled={saving}
                  onChange={(event) => setTimeValue(event.target.value)}
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
                  onChange={(event) => setReturnDateValue(event.target.value)}
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
                  disabled={saving}
                  onChange={(event) => setReturnTimeValue(event.target.value)}
                />
              </div>

              <div className="loan-create-note loan-create-note--warning">
                en caso de que no se ingrese una fecha y hora de devolucion se dara un plazo de 2 horas despues de la entrega de los implementos
              </div>
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
                placeholder="Buscar por nombre, categoria o ID"
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
                            onClick={() => increasePendingQuantity(result.uuid, -1)}
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
                            onClick={() => increasePendingQuantity(result.uuid, 1)}
                            disabled={saving}
                            aria-label="Aumentar cantidad"
                          >
                            <Plus size={14} />
                          </button>
                        </div>

                        <button
                          type="button"
                          className="loan-add-item-btn"
                          disabled={saving}
                          onClick={() => addImplement(result, pendingQuantity)}
                        >
                          <Plus size={14} />
                          Agregar
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
                              disabled={saving}
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
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}
