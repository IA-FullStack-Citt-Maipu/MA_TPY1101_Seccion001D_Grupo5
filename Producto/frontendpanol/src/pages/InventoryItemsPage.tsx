import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Boxes, Check, ChevronDown, ChevronLeft, ChevronRight, ClipboardCheck, Eye, PencilLine, Plus, Search, ShieldAlert, TriangleAlert, X } from "lucide-react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { fetchImplements } from "../services/implementService";
import { fetchActiveCategories } from "../services/activeCategoryService";
import { getErrorMessage } from "../services/apiClient";
import type { ActiveCategoryOption } from "../types/categoryActive";
import type { ImplementSummary } from "../types/implement";
import { getSessionUserRole, type UserRole } from "../utils/auth";

type StockHealth = "healthy" | "low" | "critical" | "unknown";
type FilterTagKey = "name" | "categoryUuid" | "stockStatus";
type StockFilterOption = Exclude<StockHealth, "unknown">;

const STOCK_FILTER_OPTIONS: StockFilterOption[] = ["healthy", "low", "critical"];
const STOCK_FILTER_LABELS: Record<StockFilterOption, string> = {
  healthy: "Disponible",
  low: "Bajo stock",
  critical: "Critico",
};

const STOCK_HEALTH_LABELS: Record<StockHealth, string> = {
  healthy: "Disponible",
  low: "Bajo stock",
  critical: "Critico",
  unknown: "Sin datos",
};

function getStockHealth(row: ImplementSummary): StockHealth {
  const available = row.stock?.available;
  const minStock = row.stock?.min_stock;

  if (available == null) {
    return "unknown";
  }
  if (available <= 0) {
    return "critical";
  }
  if (minStock != null && available <= minStock) {
    return "low";
  }
  return "healthy";
}

function getStockProgressPercent(row: ImplementSummary): number {
  const available = row.stock?.available ?? 0;
  const minStock = row.stock?.min_stock ?? 0;
  const totalStock = row.stock?.total_stock ?? 0;

  if (available <= 0) {
    return 4;
  }

  const baseline = Math.max(totalStock, minStock * 2, 1);
  const percent = (available / baseline) * 100;
  return Math.max(6, Math.min(100, Math.round(percent)));
}

function hashToTone(value: string): 1 | 2 | 3 | 4 {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0;
  }
  const bucket = Math.abs(hash) % 4;
  return (bucket + 1) as 1 | 2 | 3 | 4;
}

export function InventoryItemsPage({ embedded = false }: { embedded?: boolean }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [allImplements, setAllImplements] = useState<ImplementSummary[]>([]);
  const [totalImplements, setTotalImplements] = useState(0);
  const [categoryOptions, setCategoryOptions] = useState<ActiveCategoryOption[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const userRole: UserRole = getSessionUserRole();
  const [searchFilter, setSearchFilter] = useState("");
  const [selectedCategoryUuids, setSelectedCategoryUuids] = useState<string[]>([]);
  const [selectedStockStatuses, setSelectedStockStatuses] = useState<StockFilterOption[]>([]);
  const [isCategoryMenuOpen, setIsCategoryMenuOpen] = useState(false);
  const [isStatusMenuOpen, setIsStatusMenuOpen] = useState(false);
  const [debouncedNameFilter, setDebouncedNameFilter] = useState(searchFilter);
  const categoryMenuRef = useRef<HTMLDivElement | null>(null);
  const statusMenuRef = useRef<HTMLDivElement | null>(null);

  const refreshImplements = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const rows = await fetchImplements();
      setAllImplements(rows);
      setTotalImplements(rows.length);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar el listado de implementos."));
    } finally {
      setLoading(false);
    }
  }, []);

  const hasActiveFilters =
    searchFilter.trim().length > 0 ||
    selectedCategoryUuids.length > 0 ||
    selectedStockStatuses.length > 0;

  function clearFilters() {
    setSearchFilter("");
    setSelectedCategoryUuids([]);
    setSelectedStockStatuses([]);
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshImplements();
  }, [refreshImplements]);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedNameFilter(searchFilter), 300);
    return () => window.clearTimeout(timeout);
  }, [searchFilter]);

  useEffect(() => {
    async function loadActiveCategories() {
      setCategoriesLoading(true);
      try {
        const categories = await fetchActiveCategories();
        setCategoryOptions(categories);
      } catch {
        setCategoryOptions([]);
      } finally {
        setCategoriesLoading(false);
      }
    }

    loadActiveCategories();
  }, []);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node;
      if (categoryMenuRef.current && !categoryMenuRef.current.contains(target)) {
        setIsCategoryMenuOpen(false);
      }
      if (statusMenuRef.current && !statusMenuRef.current.contains(target)) {
        setIsStatusMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const isCoordinator = userRole === "COORDINADOR";

  const metrics = useMemo(() => {
    let loanedUnits = 0;
    let lowStock = 0;
    let criticalStock = 0;

    for (const row of allImplements) {
      loanedUnits += row.stock?.loaned ?? 0;

      const health = getStockHealth(row);
      if (health === "low") {
        lowStock += 1;
      }
      if (health === "critical") {
        criticalStock += 1;
      }
    }

    return {
      total: allImplements.length,
      loanedUnits,
      lowStock,
      criticalStock,
    };
  }, [allImplements]);

  const activeFilterTags = useMemo(() => {
    const tags: Array<{ key: FilterTagKey; label: string }> = [];
    const byName = searchFilter.trim();
    if (byName.length > 0) {
      tags.push({ key: "name", label: `Busqueda: ${byName}` });
    }
    if (selectedCategoryUuids.length > 0) {
      selectedCategoryUuids.forEach((categoryUuid) => {
        const category = categoryOptions.find((entry) => entry.uuid === categoryUuid);
        tags.push({ key: "categoryUuid", label: `Categoria: ${category?.name ?? "Seleccionada"}` });
      });
    }
    if (selectedStockStatuses.length > 0) {
      selectedStockStatuses.forEach((status) => {
        tags.push({ key: "stockStatus", label: `Estado: ${STOCK_FILTER_LABELS[status]}` });
      });
    }
    return tags;
  }, [categoryOptions, searchFilter, selectedCategoryUuids, selectedStockStatuses]);

  const implementos = useMemo(() => {
    const query = debouncedNameFilter.trim().toLowerCase();

    return allImplements.filter((row) => {
      const nameMatch = row.name?.toLowerCase().includes(query);
      const barcodeMatch = row.barcode?.toLowerCase().includes(query) ?? false;
      const uuidMatch = row.uuid.toLowerCase().includes(query);
      const queryMatch = query.length === 0 || nameMatch || barcodeMatch || uuidMatch;

      const categoryMatch =
        selectedCategoryUuids.length === 0 || (row.category?.uuid != null && selectedCategoryUuids.includes(row.category.uuid));

      const health = getStockHealth(row);
      const statusMatch =
        selectedStockStatuses.length === 0 ||
        (health !== "unknown" && selectedStockStatuses.includes(health));

      return queryMatch && categoryMatch && statusMatch;
    });
  }, [allImplements, debouncedNameFilter, selectedCategoryUuids, selectedStockStatuses]);

  const totalPages = useMemo(() => {
    const pageSize = 20;
    return Math.max(1, Math.ceil(totalImplements / pageSize));
  }, [totalImplements]);

  const paginationButtons = useMemo(() => {
    return Array.from({ length: Math.min(3, totalPages) }, (_, index) => index + 1);
  }, [totalPages]);

  function clearFilterTag(key: FilterTagKey) {
    if (key === "name") {
      setSearchFilter("");
      return;
    }
    if (key === "categoryUuid") {
      setSelectedCategoryUuids([]);
      return;
    }
    setSelectedStockStatuses([]);
  }

  function toggleCategorySelection(categoryUuid: string) {
    setSelectedCategoryUuids((current) =>
      current.includes(categoryUuid)
        ? current.filter((uuid) => uuid !== categoryUuid)
        : [...current, categoryUuid],
    );
  }

  function toggleStockStatusSelection(status: StockFilterOption) {
    setSelectedStockStatuses((current) =>
      current.includes(status)
        ? current.filter((entry) => entry !== status)
        : [...current, status],
    );
  }

  const selectedCategoryLabel = useMemo(() => {
    if (selectedCategoryUuids.length === 0) {
      return "Todas";
    }
    if (selectedCategoryUuids.length === 1) {
      const category = categoryOptions.find((entry) => entry.uuid === selectedCategoryUuids[0]);
      return category?.name ?? "1 categoria";
    }
    return `${selectedCategoryUuids.length} categorias`;
  }, [categoryOptions, selectedCategoryUuids]);

  const selectedStatusLabel = useMemo(() => {
    if (selectedStockStatuses.length === 0) {
      return "Todos";
    }
    if (selectedStockStatuses.length === 1) {
      return STOCK_FILTER_LABELS[selectedStockStatuses[0]];
    }
    return `${selectedStockStatuses.length} estados`;
  }, [selectedStockStatuses]);

  const content = (
    <div className="inventory-items-page">
      <section className="content-header inventory-items-header">
        <div>
          <p className="inventory-items-header__eyebrow">Inventario</p>
          <h1>Gestion de Implementos</h1>
          <p>Controla stock, ubicaciones y estado de cada implemento en tiempo real.</p>
        </div>
      </section>

      <section className="inventory-kpi-grid" aria-label="Resumen de inventario">
        <article className="inventory-kpi inventory-kpi--total">
          <span className="inventory-kpi__strip" />
          <div className="inventory-kpi__content">
            <p>Total Implementos</p>
            <strong>{metrics.total}</strong>
          </div>
          <div className="inventory-kpi__icon">
            <Boxes size={22} />
          </div>
        </article>

        <article className="inventory-kpi inventory-kpi--loaned">
          <span className="inventory-kpi__strip" />
          <div className="inventory-kpi__content">
            <p>En Uso / Prestamo</p>
            <strong>{metrics.loanedUnits}</strong>
          </div>
          <div className="inventory-kpi__icon">
            <ClipboardCheck size={22} />
          </div>
        </article>

        <article className="inventory-kpi inventory-kpi--warning">
          <span className="inventory-kpi__strip" />
          <div className="inventory-kpi__content">
            <p>Bajo Stock</p>
            <strong>{metrics.lowStock}</strong>
          </div>
          <div className="inventory-kpi__icon">
            <TriangleAlert size={22} />
          </div>
        </article>

        <article className="inventory-kpi inventory-kpi--critical">
          <span className="inventory-kpi__strip" />
          <div className="inventory-kpi__content">
            <p>Stock Critico</p>
            <strong>{metrics.criticalStock}</strong>
          </div>
          <div className="inventory-kpi__icon">
            <ShieldAlert size={22} />
          </div>
        </article>
      </section>

      <section className="panel inventory-catalog-panel">
        <div className="inventory-filter-shell">
          <div className="catalog-filters inventory-catalog-filters">
            <div className="catalog-filters__item catalog-filters__item--search inventory-search-field">
              <label htmlFor="catalog-filter-name">Filtrar</label>
              <div className="inventory-search-input-wrap">
                <Search size={18} />
                <input
                  id="catalog-filter-name"
                  type="search"
                  placeholder="Filtrar por nombre o ID..."
                  value={searchFilter}
                  onChange={(event) => setSearchFilter(event.target.value)}
                />
              </div>
            </div>

            <div
              ref={categoryMenuRef}
              className={selectedCategoryUuids.length > 0 ? "catalog-filters__item catalog-filters__item--active inventory-multiselect" : "catalog-filters__item inventory-multiselect"}
            >
              <label htmlFor="catalog-filter-category">Categoria</label>
              <button
                id="catalog-filter-category"
                type="button"
                className="inventory-multiselect__trigger"
                onClick={() => setIsCategoryMenuOpen((current) => !current)}
                aria-expanded={isCategoryMenuOpen}
                aria-haspopup="listbox"
              >
                <span>Categoria: {selectedCategoryLabel}</span>
                <ChevronDown size={16} />
              </button>
              {isCategoryMenuOpen ? (
                <div className="inventory-multiselect__menu" role="listbox" aria-multiselectable="true">
                  {categoriesLoading ? <p className="inventory-multiselect__hint">Cargando categorias...</p> : null}
                  {!categoriesLoading && categoryOptions.length === 0 ? <p className="inventory-multiselect__hint">Sin categorias activas</p> : null}
                  {!categoriesLoading && categoryOptions.map((category) => {
                    const checked = selectedCategoryUuids.includes(category.uuid);
                    return (
                      <label key={category.uuid} className="inventory-multiselect__option">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleCategorySelection(category.uuid)}
                        />
                        <span>{category.name}</span>
                        {checked ? <Check size={14} /> : null}
                      </label>
                    );
                  })}
                </div>
              ) : null}
            </div>

            <div
              ref={statusMenuRef}
              className={selectedStockStatuses.length > 0 ? "catalog-filters__item catalog-filters__item--active inventory-multiselect" : "catalog-filters__item inventory-multiselect"}
            >
              <label htmlFor="catalog-filter-status">Estado</label>
              <button
                id="catalog-filter-status"
                type="button"
                className="inventory-multiselect__trigger"
                onClick={() => setIsStatusMenuOpen((current) => !current)}
                aria-expanded={isStatusMenuOpen}
                aria-haspopup="listbox"
              >
                <span>Estado: {selectedStatusLabel}</span>
                <ChevronDown size={16} />
              </button>
              {isStatusMenuOpen ? (
                <div className="inventory-multiselect__menu" role="listbox" aria-multiselectable="true">
                  {STOCK_FILTER_OPTIONS.map((status) => {
                    const checked = selectedStockStatuses.includes(status);
                    return (
                      <label key={status} className="inventory-multiselect__option">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleStockStatusSelection(status)}
                        />
                        <span>{STOCK_FILTER_LABELS[status]}</span>
                        {checked ? <Check size={14} /> : null}
                      </label>
                    );
                  })}
                </div>
              ) : null}
            </div>

            <div className="catalog-filters__item inventory-toolbar-create">
              <label className="inventory-toolbar-create__label" aria-hidden="true">Accion</label>
              <button
                type="button"
                className="button button--primary button--lg inventory-toolbar-create__button"
                onClick={() => {
                  window.location.hash = "#/inventory/implementos/nuevo";
                }}
              >
                <Plus size={18} />
                Nuevo Implemento
              </button>
            </div>
          </div>
        </div>

        <div className="catalog-filters__summary inventory-catalog-summary">
          <p>
            Mostrando <strong>{implementos.length}</strong> de <strong>{totalImplements}</strong> implementos
          </p>
          {hasActiveFilters ? <span className="inventory-catalog-summary__hint">Filtros activos</span> : null}
        </div>

        {activeFilterTags.length > 0 ? (
          <div className="inventory-active-filters" aria-label="Filtros activos">
            {activeFilterTags.map((tag) => (
              <span key={tag.label} className="inventory-filter-chip">
                <span>{tag.label}</span>
                <button
                  type="button"
                  className="inventory-filter-chip__remove"
                  onClick={() => clearFilterTag(tag.key)}
                  aria-label={`Quitar ${tag.label}`}
                >
                  <X size={13} />
                </button>
              </span>
            ))}
            <button type="button" className="inventory-clear-all" onClick={clearFilters}>
              Limpiar todo
            </button>
          </div>
        ) : null}

        {loading ? <div className="field-hint">Cargando implementos...</div> : null}
        {error ? <div className="error-banner">{error}</div> : null}
        {implementos.length === 0 && !loading ? (
          <div className="empty-state">No se encontraron implementos con los filtros aplicados</div>
        ) : null}

        <div className="table-wrapper">
          <table className="category-table inventory-items-table">
            <thead>
              <tr>
                <th>Implemento</th>
                <th>Categoria</th>
                <th>Ubicacion</th>
                <th>Stock</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading
                ? Array.from({ length: 7 }).map((_, index) => (
                    <tr key={`skeleton-implement-${index}`}>
                      <td>
                        <div className="inventory-item-cell">
                          <div className="skeleton skeleton-thumb" />
                          <div className="inventory-item-cell__copy">
                            <div className="skeleton skeleton-line skeleton-line--md" />
                            <div className="skeleton skeleton-line skeleton-line--sm" />
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton-actions">
                          <div className="skeleton skeleton-btn skeleton-btn--icon" />
                          <div className="skeleton skeleton-btn skeleton-btn--icon" />
                        </div>
                      </td>
                    </tr>
                  ))
                : null}
              {!loading
                ? implementos.map((row) => {
                    const health = getStockHealth(row);
                    const progress = getStockProgressPercent(row);
                    return (
                      <tr key={row.uuid} className="inventory-row">
                        <td>
                          <div className="inventory-item-cell">
                            <img
                              src={(row.imgUrl ?? row.img_url) ?? "https://placehold.co/56x56/e9edf5/4d6284?text=Sin+img"}
                              alt={row.name}
                              className="implement-thumb"
                            />
                            <div className="inventory-item-cell__copy">
                              <strong>{row.name}</strong>
                              <span className="inventory-item-cell__id">
                                ID: {(row.barcode ?? row.uuid.slice(0, 8)).toUpperCase()}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td>
                          {row.category ? (
                            <span
                              className={`inventory-chip inventory-chip--tone-${hashToTone(row.category.uuid)}${row.category.active ? "" : " inventory-chip--inactive"}`}
                            >
                              {row.category.name}
                            </span>
                          ) : (
                            <span className="text-muted">Sin categoria</span>
                          )}
                        </td>
                        <td>
                          {row.location ? (
                            <div className="inventory-location-cell">
                              <strong>{row.location.name}</strong>
                              {row.location.description ? <span>{row.location.description}</span> : null}
                            </div>
                          ) : (
                            <span className="text-muted">Sin ubicacion</span>
                          )}
                        </td>
                        <td>
                          {row.stock?.available != null ? (
                            <div className="inventory-stock-cell">
                              <div className="inventory-stock-cell__meta">
                                <strong>{row.stock.available} units</strong>
                                <span>min: {row.stock.min_stock ?? 0}</span>
                              </div>
                              <div className="inventory-stock-cell__bar">
                                <span className={`inventory-stock-cell__fill inventory-stock-cell__fill--${health}`} style={{ width: `${progress}%` }} />
                              </div>
                            </div>
                          ) : (
                            <span className="text-muted">Sin stock</span>
                          )}
                        </td>
                        <td>
                          <span className={`stock-badge stock-badge--${health}`}>
                            <span className={`stock-badge__dot stock-badge__dot--${health}`} />
                            {STOCK_HEALTH_LABELS[health]}
                          </span>
                        </td>
                        <td>
                          <div className="table-actions inventory-actions">
                            <a
                              className="button button--table button--ghost inventory-action-btn"
                              href={`#/inventory/implementos/${row.uuid}`}
                              aria-label={`Ver ficha de ${row.name}`}
                              title="Ver ficha"
                            >
                              <Eye size={16} />
                            </a>
                            {isCoordinator ? (
                              <button
                                type="button"
                                className="button button--table button--ghost inventory-action-btn"
                                onClick={() => {
                                  window.location.hash = `#/inventory/implementos/${row.uuid}/editar`;
                                }}
                                aria-label={`Editar ${row.name}`}
                                title="Editar"
                              >
                                <PencilLine size={16} />
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                : null}
            </tbody>
          </table>
        </div>

        <div className="inventory-table-footer">
          <p>
            Mostrando {implementos.length === 0 ? 0 : 1} a {implementos.length} de {totalImplements} implementos
          </p>
          <div className="inventory-pagination">
            <button type="button" className="inventory-pagination__btn" disabled>
              <ChevronLeft size={16} />
            </button>
            {paginationButtons.map((page) => (
              <button
                key={page}
                type="button"
                className={page === 1 ? "inventory-pagination__btn inventory-pagination__btn--active" : "inventory-pagination__btn"}
              >
                {page}
              </button>
            ))}
            <button type="button" className="inventory-pagination__btn" disabled={totalPages <= 1}>
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      <button
        type="button"
        className="inventory-fab"
        aria-label="Crear nuevo implemento"
        onClick={() => {
          window.location.hash = "#/inventory/implementos/nuevo";
        }}
      >
        <Plus size={28} />
      </button>
    </div>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="items">{content}</InventoryLayout>;
}
