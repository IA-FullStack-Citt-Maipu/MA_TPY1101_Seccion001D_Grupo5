import { useCallback, useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { ImplementFormModal } from "../components/implements/ImplementFormModal";
import { ImplementEditModal } from "../components/implements/ImplementEditModal";
import { createImplement, fetchImplements } from "../services/implementService";
import { fetchActiveCategories } from "../services/activeCategoryService";
import { getErrorMessage } from "../services/apiClient";
import type { ActiveCategoryOption } from "../types/categoryActive";
import type { ImplementFilters, ImplementStockFilterStatus, ImplementSummary } from "../types/implement";
import { STOCK_STATUS_LABELS } from "../types/implement";
import { getUserRoleFromToken, type UserRole } from "../utils/auth";

export function InventoryItemsPage({ embedded = false }: { embedded?: boolean }) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingUuid, setEditingUuid] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [implementos, setImplementos] = useState<ImplementSummary[]>([]);
  const [totalImplements, setTotalImplements] = useState(0);
  const [categoryOptions, setCategoryOptions] = useState<ActiveCategoryOption[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [userRole, setUserRole] = useState<UserRole>("UNKNOWN");
  const [filters, setFilters] = useState<ImplementFilters>({
    name: "",
    categoryUuid: null,
    stockStatus: "all",
  });
  const [debouncedNameFilter, setDebouncedNameFilter] = useState(filters.name ?? "");

  const refreshImplements = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [rows, allRows] = await Promise.all([
        fetchImplements({
          name: debouncedNameFilter.trim() || undefined,
          categoryUuid: filters.categoryUuid ?? null,
          stockStatus: filters.stockStatus,
        }),
        fetchImplements(),
      ]);

      setImplementos(rows);
      setTotalImplements(allRows.length);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar el listado de implementos."));
    } finally {
      setLoading(false);
    }
  }, [debouncedNameFilter, filters.categoryUuid, filters.stockStatus]);

  const hasActiveFilters =
    (filters.name ?? "").trim().length > 0 ||
    filters.categoryUuid != null ||
    (filters.stockStatus ?? "all") !== "all";

  function clearFilters() {
    setFilters({
      name: "",
      categoryUuid: null,
      stockStatus: "all",
    });
  }

  useEffect(() => {
    refreshImplements();
  }, [refreshImplements]);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedNameFilter(filters.name ?? ""), 350);
    return () => window.clearTimeout(timeout);
  }, [filters.name]);

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
    setUserRole(getUserRoleFromToken());
  }, []);

  const isCoordinator = userRole === "COORDINADOR";

  async function handleSubmit(payload: {
    name: string;
    categoryUuid: string;
    itemType: "fungible" | "no_fungible";
    locationUuid: string;
    description: string | null;
    barcode: string | null;
    imgUrl: string | null;
    minStock: number;
    observations: string | null;
  }) {
    setSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const created = await createImplement({
        name: payload.name,
        categoryUuid: payload.categoryUuid,
        item_type: payload.itemType,
        locationUuid: payload.locationUuid,
        description: payload.description,
        barcode: payload.barcode,
        img_url: payload.imgUrl,
        min_stock: payload.minStock,
        observations: payload.observations,
      });
      try {
        window.sessionStorage.setItem("inventory.justCreatedImplementId", created.uuid);
      } catch {
        // Si el storage no esta disponible, el flujo principal de creacion debe continuar.
      }
      setIsCreateOpen(false);
      window.location.hash = `#/inventory/implementos/${created.uuid}`;
    } finally {
      setSaving(false);
    }
  }

  async function handleSaved() {
    await refreshImplements();
    setSuccess("Implemento actualizado correctamente.");
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Inventario</h1>
          <p>Alta de producto.</p>
        </div>

        <div className="content-header__actions">
          <button type="button" className="button" onClick={() => setIsCreateOpen(true)}>
            <Plus size={16} />
            Nuevo implemento
          </button>
        </div>
      </section>

      <section className="panel">
        <div className="panel__head">
          <div>
            <h2>Implementos</h2>
            <p>Usa "Nuevo implemento" para crear un producto con categoria y ubicacion obligatorias.</p>
          </div>
        </div>

        <div className="catalog-filters">
          <div className={filters.categoryUuid != null ? "catalog-filters__item catalog-filters__item--active" : "catalog-filters__item"}>
            <label htmlFor="catalog-filter-category">Categoria</label>
            <select
              id="catalog-filter-category"
              value={filters.categoryUuid == null ? "" : filters.categoryUuid}
              onChange={(event) =>
                setFilters((prev) => ({
                  ...prev,
                  categoryUuid: event.target.value ? event.target.value : null,
                }))
              }
              disabled={categoriesLoading || categoryOptions.length === 0}
            >
              {categoryOptions.length > 0 ? (
                <>
                  <option value="">Todas las categorias</option>
                  {categoryOptions.map((category) => (
                    <option key={category.uuid} value={category.uuid}>
                      {category.name}
                    </option>
                  ))}
                </>
              ) : (
                <option value="">Sin categorias</option>
              )}
            </select>
            {filters.categoryUuid != null ? <span className="filter-badge">Filtro activo</span> : null}
          </div>

          {isCoordinator ? (
            <div className={filters.stockStatus && filters.stockStatus !== "all" ? "catalog-filters__item catalog-filters__item--active" : "catalog-filters__item"}>
              <label htmlFor="catalog-filter-status">Estado stock</label>
              <select
                id="catalog-filter-status"
                value={filters.stockStatus ?? "all"}
                onChange={(event) =>
                  setFilters((prev) => ({
                    ...prev,
                    stockStatus: event.target.value as ImplementStockFilterStatus,
                  }))
                }
              >
                <option value="all">Todos los estados</option>
                <option value="available">Disponible</option>
                <option value="reserved">Reservado</option>
                <option value="loaned">Prestado</option>
                <option value="damaged">Da�ado</option>
              </select>
              {filters.stockStatus && filters.stockStatus !== "all" ? (
                <span className="filter-badge">{STOCK_STATUS_LABELS[filters.stockStatus]}</span>
              ) : null}
            </div>
          ) : null}

          <div className="catalog-filters__item catalog-filters__item--search">
            <label htmlFor="catalog-filter-name">Buscar</label>
            <input
              id="catalog-filter-name"
              type="search"
              placeholder="Buscar por nombre..."
              value={filters.name ?? ""}
              onChange={(event) =>
                setFilters((prev) => ({
                  ...prev,
                  name: event.target.value,
                }))
              }
            />
          </div>
        </div>

        <div className="catalog-filters__summary">
          <p>
            Mostrando <strong>{implementos.length}</strong> de <strong>{totalImplements}</strong> implementos
          </p>
          {hasActiveFilters ? (
            <button type="button" className="button button--ghost button--table" onClick={clearFilters}>
              Limpiar filtros
            </button>
          ) : null}
        </div>

        {loading ? <div className="field-hint">Cargando implementos...</div> : null}
        {error ? <div className="error-banner">{error}</div> : null}
        {success ? <div className="success-banner">{success}</div> : null}

        {implementos.length === 0 && !loading ? (
          <div className="empty-state">No se encontraron implementos con los filtros aplicados</div>
        ) : null}

        <div className="table-wrapper">
          <table className="category-table">
            <thead>
              <tr>
                <th>Miniatura</th>
                <th>Nombre</th>
                <th>Categoria</th>
                <th>Ubicacion</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading
                ? Array.from({ length: 7 }).map((_, index) => (
                    <tr key={`skeleton-implement-${index}`}>
                      <td>
                        <div className="skeleton skeleton-thumb" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--md" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton skeleton-line skeleton-line--sm" />
                      </td>
                      <td>
                        <div className="skeleton-actions">
                          <div className="skeleton skeleton-btn" />
                          <div className="skeleton skeleton-btn" />
                        </div>
                      </td>
                    </tr>
                  ))
                : null}
              {!loading
                ? implementos.map((row) => (
                    <tr key={row.uuid}>
                      <td>
                        <img
                          src={(row.imgUrl ?? (row as any).img_url) ?? "https://placehold.co/56x56/e9edf5/4d6284?text=Sin+img"}
                          alt={row.name}
                          className="implement-thumb"
                        />
                      </td>
                      <td>{row.name}</td>
                      <td>
                        {row.category
                          ? `${row.category.name}${row.category.active ? "" : " [Categoria inactiva]"}`
                          : "Sin categoria"}
                      </td>
                      <td>{row.location ? row.location.name : "Sin ubicacion"}</td>
                      <td>
                        <div className="table-actions">
                          <button
                            type="button"
                            className="button button--table button--ghost"
                            onClick={() => setEditingUuid(row.uuid)}
                          >
                            Editar
                          </button>
                          <a className="button button--table button--ghost" href={`#/inventory/implementos/${row.uuid}`}>
                            Ver ficha
                          </a>
                        </div>
                      </td>
                    </tr>
                  ))
                : null}
            </tbody>
          </table>
        </div>
      </section>

      <ImplementFormModal
        isOpen={isCreateOpen}
        saving={saving}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={handleSubmit}
      />

      <ImplementEditModal
        implementUuid={editingUuid}
        isOpen={editingUuid != null}
        onClose={() => setEditingUuid(null)}
        onSaved={handleSaved}
      />
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="items">{content}</InventoryLayout>;
}
