import { CalendarDays, ChevronLeft, ChevronRight, Search, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Input } from "../components/ui/Input";
import { Select } from "../components/ui/Select";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { getErrorMessage } from "../services/apiClient";
import { fetchInventoryMovementHistoryPage } from "../services/movementService";
import type { InventoryMovementHistoryItem, InventoryMovementHistoryPage } from "../types/implement";
import type { StockMovementType } from "../types/stock";
import {
  formatMovementDateTime,
  getMovementActionLabel,
  getMovementBadgeTone,
  MOVEMENT_ACTION_OPTIONS,
} from "../utils/movementPresentation";

const PAGE_SIZE = 15;
const DESKTOP_BREAKPOINT = 1080;
const EMPTY_PAGE: InventoryMovementHistoryPage = {
  items: [],
  page: 1,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

function formatRole(role: string | null): string {
  if (!role) {
    return "Sin rol identificado";
  }
  if (role === "COORDINADOR") return "Coordinador";
  if (role === "DIRECTOR") return "Director";
  if (role === "DOCENTE") return "Docente";
  return role;
}
function formatItemType(itemType: InventoryMovementHistoryItem["item_type"]): string {
  if (itemType === "consumable") return "Consumible";
  if (itemType === "reusable") return "Reutilizable";
  if (itemType === "individual") return "Individual";
  return "Sin tipo";
}

function summarizeNotes(notes: string | null): string {
  if (!notes || !notes.trim()) {
    return "Sin nota registrada";
  }
  const normalized = notes.trim();
  if (normalized.length <= 96) {
    return normalized;
  }
  return `${normalized.slice(0, 93)}...`;
}

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(() => window.innerWidth < DESKTOP_BREAKPOINT);

  useEffect(() => {
    function handleResize() {
      setIsMobile(window.innerWidth < DESKTOP_BREAKPOINT);
    }

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return isMobile;
}

function MovementDetail({
  movement,
  onClose,
  mobile = false,
}: {
  movement: InventoryMovementHistoryItem | null;
  onClose?: () => void;
  mobile?: boolean;
}) {
  if (!movement) {
    return (
      <div className="director-movement-detail director-movement-detail--empty">
        <div className="director-movement-detail__empty-icon">
          <CalendarDays size={22} />
        </div>
        <h3>Selecciona un movimiento</h3>
        <p>Abre una fila para revisar el contexto completo, la trazabilidad y la nota asociada.</p>
      </div>
    );
  }

  return (
    <div className={mobile ? "director-movement-detail director-movement-detail--mobile" : "director-movement-detail"}>
      <div className="director-movement-detail__header">
        <div>
          <p className="director-movement-detail__eyebrow">Detalle del movimiento</p>
          <h3>{movement.implement_name ?? "Implemento sin nombre"}</h3>
          <p className="director-movement-detail__subtitle">{formatMovementDateTime(movement.timestamp)}</p>
        </div>
        {onClose ? (
          <Button variant="ghost" size="sm" className="director-movement-detail__close" onClick={onClose}>
            <X size={16} />
          </Button>
        ) : null}
      </div>

      <div className="director-movement-detail__hero">
        <Badge tone={getMovementBadgeTone(movement.action)}>{getMovementActionLabel(movement.action)}</Badge>
        <strong>{movement.quantity} unidades</strong>
      </div>

      <div className="director-movement-detail__grid">
        <div className="director-movement-detail__card">
          <span>Implemento</span>
          <strong>{movement.implement_name ?? "Sin informacion"}</strong>
          <small>{formatItemType(movement.item_type)}</small>
        </div>
        <div className="director-movement-detail__card">
          <span>Usuario</span>
          <strong>{movement.performed_by ?? "Usuario no identificado"}</strong>
          <small>{formatRole(movement.performed_by_role)}</small>
        </div>
        <div className="director-movement-detail__card">
          <span>Categoria</span>
          <strong>{movement.category_name ?? "Sin categoria"}</strong>
          <small>{movement.location_name ?? "Sin ubicacion"}</small>
        </div>
        <div className="director-movement-detail__card">
          <span>Codigo de barras</span>
          <strong>{movement.barcode ?? "No registrado"}</strong>
          <small>{getMovementActionLabel(movement.action)}</small>
        </div>
      </div>

      <div className="director-movement-detail__notes">
        <span>Nota asociada</span>
        <p>{movement.notes?.trim() ? movement.notes.trim() : "Sin nota registrada para este movimiento."}</p>
      </div>
    </div>
  );
}

export function DirectorMovementHistoryPage({ embedded = false }: { embedded?: boolean }) {
  const [searchInput, setSearchInput] = useState("");
  const [actionFilter, setActionFilter] = useState<StockMovementType | "">("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);
  const [pageData, setPageData] = useState<InventoryMovementHistoryPage>(EMPTY_PAGE);
  const [selectedMovementId, setSelectedMovementId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const isMobile = useIsMobile();
  const normalizedSearchInput = searchInput.trim();
  const debouncedSearchInput = useDebouncedValue(normalizedSearchInput, 300);
  const effectiveSearchInput = normalizedSearchInput === "" ? "" : debouncedSearchInput;

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    async function loadHistory() {
      setLoading(true);
      try {
        const nextPage = await fetchInventoryMovementHistoryPage({
          page,
          size: PAGE_SIZE,
          search: effectiveSearchInput,
          action: actionFilter,
          from: fromDate,
          to: toDate,
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
        setError(getErrorMessage(requestError, "No se pudo cargar el historial de movimientos."));
        setPageData(EMPTY_PAGE);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadHistory();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [actionFilter, effectiveSearchInput, fromDate, page, toDate]);

  const selectedMovement = useMemo(() => {
    if (pageData.items.length === 0) {
      return null;
    }

    const explicitSelection = pageData.items.find((item) => item.id === selectedMovementId) ?? null;
    if (explicitSelection) {
      return explicitSelection;
    }

    if (!isMobile) {
      return pageData.items[0];
    }

    return null;
  }, [isMobile, pageData.items, selectedMovementId]);
  const activeFiltersCount = useMemo(() => {
    let count = 0;
    if (normalizedSearchInput) count += 1;
    if (actionFilter) count += 1;
    if (fromDate) count += 1;
    if (toDate) count += 1;
    return count;
  }, [actionFilter, fromDate, normalizedSearchInput, toDate]);

  const rangeStart = pageData.totalItems === 0 ? 0 : (pageData.page - 1) * pageData.size + 1;
  const rangeEnd = pageData.totalItems === 0 ? 0 : Math.min(rangeStart + pageData.items.length - 1, pageData.totalItems);
  const totalPages = Math.max(pageData.totalPages, 1);
  const pageNumbers = useMemo(() => {
    const windowSize = 5;
    let start = Math.max(1, pageData.page - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [pageData.page, totalPages]);

  function resetFilters() {
    setSearchInput("");
    setActionFilter("");
    setFromDate("");
    setToDate("");
    setPage(1);
  }

  function openMovementDetail(movementId: string) {
    setSelectedMovementId(movementId);
  }

  const content = (
    <section className={embedded ? "director-movement-history director-movement-history--embedded" : "director-movement-history"}>
      <div className="content-header director-header">
        <div>
          <h1>Historial de movimientos</h1>
          <p>Consulta ejecutiva de trazabilidad con detalle completo por movimiento.</p>
        </div>
      </div>

      <section className="director-movement-history__stats">
        <article className="director-kpi director-kpi--blue">
          <div className="director-kpi__icon"><CalendarDays size={20} /></div>
          <div>
            <p>Movimientos auditados</p>
            <strong>{pageData.totalItems}</strong>
          </div>
        </article>
        <article className="director-kpi director-kpi--teal">
          <div className="director-kpi__icon"><Search size={20} /></div>
          <div>
            <p>Filtros aplicados</p>
            <strong>{activeFiltersCount}</strong>
          </div>
        </article>
      </section>

      <section className="panel director-panel director-movement-history__panel">
        <div className="director-movement-history__filters">
          <div className="director-movement-history__filter director-movement-history__filter--search">
            <label>Buscar</label>
            <div className="director-movement-history__search">
              <Search size={16} />
              <Input
                value={searchInput}
                onChange={(event) => {
                  setSearchInput(event.target.value);
                  setPage(1);
                }}
                placeholder="Implemento, codigo, usuario o nota"
              />
            </div>
          </div>

          <div className="director-movement-history__filter">
            <label>Accion</label>
            <Select
              value={actionFilter}
              onChange={(event) => {
                setActionFilter(event.target.value as StockMovementType | "");
                setPage(1);
              }}
            >
              <option value="">Todas</option>
              {MOVEMENT_ACTION_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="director-movement-history__filter">
            <label>Desde</label>
            <Input
              type="date"
              value={fromDate}
              onChange={(event) => {
                setFromDate(event.target.value);
                setPage(1);
              }}
            />
          </div>

          <div className="director-movement-history__filter">
            <label>Hasta</label>
            <Input
              type="date"
              value={toDate}
              onChange={(event) => {
                setToDate(event.target.value);
                setPage(1);
              }}
            />
          </div>
        </div>

        <div className="director-movement-history__summary">
          <p>
            Mostrando <strong>{rangeStart}</strong> a <strong>{rangeEnd}</strong> de <strong>{pageData.totalItems}</strong> movimientos
          </p>
          <Button variant="ghost" onClick={resetFilters}>Limpiar filtros</Button>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        <div className="director-movement-history__layout">
          <div className="director-movement-history__list">
            {loading ? (
              <div className="director-movement-history__state">
                <p>Cargando historial de movimientos...</p>
              </div>
            ) : pageData.items.length === 0 ? (
              <div className="director-movement-history__state">
                <p>No hay movimientos que coincidan con los filtros actuales.</p>
              </div>
            ) : (
              <div className="director-movement-history__rows">
                {pageData.items.map((movement) => {
                  const active = movement.id === selectedMovement?.id;
                  return (
                    <button
                      key={movement.id}
                      type="button"
                      className={active ? "director-movement-row is-active" : "director-movement-row"}
                      onClick={() => openMovementDetail(movement.id)}
                    >
                      <div className="director-movement-row__main">
                        <div className="director-movement-row__head">
                          <div>
                            <strong>{movement.implement_name ?? "Implemento sin nombre"}</strong>
                            <p>{movement.category_name ?? "Sin categoria"} | {movement.location_name ?? "Sin ubicacion"}</p>
                          </div>
                          <Badge tone={getMovementBadgeTone(movement.action)}>{getMovementActionLabel(movement.action)}</Badge>
                        </div>

                        <div className="director-movement-row__meta">
                          <span>{formatMovementDateTime(movement.timestamp)}</span>
                          <span>{movement.quantity} unidades</span>
                          <span>{movement.performed_by ?? "Usuario no identificado"}</span>
                        </div>

                        <p className="director-movement-row__notes">{summarizeNotes(movement.notes)}</p>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            {!loading ? (
              <footer className="director-movement-history__footer">
                <p>
                  Pagina {pageData.page} de {totalPages}
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
          </div>

          {!isMobile ? (
            <aside className="director-movement-history__detail">
              <MovementDetail movement={selectedMovement} />
            </aside>
          ) : null}
        </div>
      </section>

      {isMobile && selectedMovement ? (
        <div className="director-movement-mobile-drawer" role="dialog" aria-modal="true" aria-label="Detalle del movimiento">
          <button
            type="button"
            className="director-movement-mobile-drawer__backdrop"
            aria-label="Cerrar detalle del movimiento"
            onClick={() => setSelectedMovementId(null)}
          />
          <div className="director-movement-mobile-drawer__sheet">
            <MovementDetail movement={selectedMovement} onClose={() => setSelectedMovementId(null)} mobile />
          </div>
        </div>
      ) : null}
    </section>
  );

  return embedded ? content : content;
}
