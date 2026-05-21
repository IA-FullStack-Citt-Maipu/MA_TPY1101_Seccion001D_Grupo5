import { useEffect, useMemo, useState } from "react";
import { Plus, RefreshCcw, Search } from "lucide-react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import {
  createLocation,
  fetchLocationsForManagement,
  setLocationActive,
  updateLocation,
} from "../services/locationServiceLocal";
import type { LocationOption } from "../types/location";

interface FormState {
  name: string;
  description: string;
}

type ModalMode = "create" | "edit" | null;

function normalize(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function InventoryLocationsPage({ embedded = false }: { embedded?: boolean }) {
  const [locations, setLocations] = useState<LocationOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | "active" | "inactive">("all");

  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [selected, setSelected] = useState<LocationOption | null>(null);
  const [form, setForm] = useState<FormState>({ name: "", description: "" });
  const [fieldError, setFieldError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchLocationsForManagement();
      setLocations(data);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo cargar ubicaciones."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const hasModalOpen = modalMode !== null;
    if (!hasModalOpen) {
      return;
    }
    document.body.classList.add("modal-open");
    return () => {
      document.body.classList.remove("modal-open");
    };
  }, [modalMode]);

  const filtered = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return locations.filter((location) => {
      if (statusFilter === "active" && location.active === false) return false;
      if (statusFilter === "inactive" && location.active !== false) return false;
      if (!normalizedQuery) return true;
      return (
        location.name.toLowerCase().includes(normalizedQuery) ||
        (location.description ?? "").toLowerCase().includes(normalizedQuery)
      );
    });
  }, [locations, query, statusFilter]);

  const stats = useMemo(() => {
    const active = locations.filter((l) => l.active !== false).length;
    const inactive = locations.length - active;
    return { total: locations.length, active, inactive };
  }, [locations]);

  function openCreate() {
    setModalMode("create");
    setSelected(null);
    setForm({ name: "", description: "" });
    setFieldError(null);
  }

  function openEdit(location: LocationOption) {
    setModalMode("edit");
    setSelected(location);
    setForm({
      name: location.name,
      description: location.description ?? "",
    });
    setFieldError(null);
  }

  function closeModal() {
    setModalMode(null);
    setSelected(null);
    setFieldError(null);
  }

  function validate(): string | null {
    if (form.name.trim().length === 0) return "El nombre es obligatorio.";
    if (form.name.trim().length > 120) return "El nombre no puede superar 120 caracteres.";
    if (form.description.trim().length > 255) return "La descripciÃ³n no puede superar 255 caracteres.";
    return null;
  }

  async function submit() {
    const validation = validate();
    if (validation) {
      setFieldError(validation);
      return;
    }

    setSaving(true);
    setFieldError(null);
    setSuccess(null);
    try {
      const payload = { name: form.name.trim(), description: normalize(form.description) };
      if (modalMode === "create") {
        await createLocation(payload);
        setSuccess("UbicaciÃ³n creada correctamente.");
      } else if (modalMode === "edit" && selected?.uuid) {
        await updateLocation(selected.uuid, payload);
        setSuccess("UbicaciÃ³n actualizada correctamente.");
      }
      closeModal();
      await load();
    } catch (requestError) {
      const payload = getApiErrorPayload(requestError);
      setFieldError(payload?.message ?? getErrorMessage(requestError, "No se pudo guardar la ubicaciÃ³n."));
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(location: LocationOption) {
    if (!location.uuid) {
      setError("La ubicación seleccionada no tiene identificador válido.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await setLocationActive(location.uuid, !(location.active !== false));
      setSuccess(location.active === false ? "UbicaciÃ³n activada." : "UbicaciÃ³n desactivada.");
      await load();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo actualizar el estado de la ubicaciÃ³n."));
    } finally {
      setSaving(false);
    }
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Ubicaciones</h1>
          <p>Gestiona las ubicaciones fÃ­sicas para asignar implementos y unidades.</p>
        </div>
        <div className="content-header__actions">
          <button type="button" className="button button--ghost" onClick={() => void load()} disabled={loading}>
            <RefreshCcw size={16} /> Refrescar
          </button>
          <button type="button" className="button" onClick={openCreate}>
            <Plus size={16} /> Nueva ubicaciÃ³n
          </button>
        </div>
      </section>

      <section className="stat-grid">
        <article className="stat-card stat-card--blue"><p>Total</p><strong>{stats.total}</strong></article>
        <article className="stat-card stat-card--green"><p>Activas</p><strong>{stats.active}</strong></article>
        <article className="stat-card stat-card--orange"><p>Inactivas</p><strong>{stats.inactive}</strong></article>
      </section>

      <section className="panel">
        <div className="catalog-filters">
          <div className="catalog-filters__item">
            <label htmlFor="locations-status">Estado</label>
            <select id="locations-status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as "all" | "active" | "inactive")}>
              <option value="all">Todas</option>
              <option value="active">Activas</option>
              <option value="inactive">Inactivas</option>
            </select>
          </div>
          <div className="catalog-filters__item catalog-filters__item--active" style={{ gridColumn: "span 2" }}>
            <label htmlFor="locations-search">Buscar</label>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Search size={16} />
              <input id="locations-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por nombre o descripciÃ³n" />
            </div>
          </div>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}
        {success ? <div className="success-banner">{success}</div> : null}

        <div className="table-wrapper">
          <table className="category-table">
            <thead>
              <tr>
                <th>UUID</th>
                <th>Nombre</th>
                <th>DescripciÃ³n</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} className="table-hint">Cargando ubicaciones...</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={5} className="table-hint">No hay ubicaciones para el filtro actual.</td></tr>
              ) : (
                filtered.map((location) => (
                  <tr key={location.uuid ?? location.name}>
                    <td>{location.uuid ?? "-"}</td>
                    <td>{location.name}</td>
                    <td>{location.description ?? "-"}</td>
                    <td>
                      <span className={`badge ${location.active === false ? "badge--inactive" : "badge--active"}`}>
                        {location.active === false ? "Inactiva" : "Activa"}
                      </span>
                    </td>
                    <td className="table-actions">
                      <button type="button" className="button button--table" onClick={() => openEdit(location)} disabled={saving}>
                        Editar
                      </button>
                      <button
                        type="button"
                        className={`button button--table ${location.active === false ? "" : "button--warn"}`}
                        onClick={() => void toggleActive(location)}
                        disabled={saving}
                      >
                        {location.active === false ? "Activar" : "Desactivar"}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {modalMode ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>{modalMode === "create" ? "Nueva ubicaciÃ³n" : "Editar ubicaciÃ³n"}</h3>
            <p>{modalMode === "create" ? "Crea una ubicaciÃ³n para asignar implementos." : "Actualiza la informaciÃ³n de la ubicaciÃ³n."}</p>
            {fieldError ? <p className="field-error">{fieldError}</p> : null}

            <label htmlFor="location-name">Nombre</label>
            <input
              id="location-name"
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
              maxLength={120}
              placeholder="Ej: Estante A"
            />

            <label htmlFor="location-description">DescripciÃ³n</label>
            <textarea
              id="location-description"
              value={form.description}
              onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
              maxLength={255}
              placeholder="Opcional"
            />

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeModal} disabled={saving}>Cancelar</button>
              <button type="button" className="button" onClick={() => void submit()} disabled={saving}>
                {saving ? "Guardando..." : "Guardar"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="locations">{content}</InventoryLayout>;
}



