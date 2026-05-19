import { useEffect, useMemo, useState } from "react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import {
  fetchInventoryMovements,
  registerManualMovement,
  type ManualMovementType,
} from "../services/movementService";
import type { ImplementSummary, InventoryMovementDetail } from "../types/implement";
import { getUserRoleFromToken } from "../utils/auth";
import { Button } from "../components/ui/Button";
import { Select } from "../components/ui/Select";
import { Input } from "../components/ui/Input";
import { Card } from "../components/ui/Card";
import { Badge } from "../components/ui/Badge";
import { Table } from "../components/ui/Table";

const ACTION_LABELS: Record<string, string> = {
  STOCK_IN: "Ingreso de stock",
  STOCK_OUT: "Salida de stock",
  LOAN_DELIVERY: "Entrega de préstamo",
  LOAN_RETURN: "Devolución de préstamo",
  DAMAGE_REPORT: "Reporte de daño",
  MANUAL_ADJUSTMENT: "Ajuste manual",
};

function toDateStart(value: string): Date | null {
  if (!value) return null;
  const d = new Date(`${value}T00:00:00`);
  return Number.isNaN(d.getTime()) ? null : d;
}

function toDateEnd(value: string): Date | null {
  if (!value) return null;
  const d = new Date(`${value}T23:59:59.999`);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function InventoryMovesPage({ embedded = false }: { embedded?: boolean }) {
  const [implementsList, setImplementsList] = useState<ImplementSummary[]>([]);
  const [movements, setMovements] = useState<InventoryMovementDetail[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingMovements, setLoadingMovements] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [implementNameFilter, setImplementNameFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [userFilter, setUserFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const [manualImplementUuid, setManualImplementUuid] = useState<string>("");
  const [action, setAction] = useState<ManualMovementType>("STOCK_IN");
  const [quantity, setQuantity] = useState("1");
  const [notes, setNotes] = useState("");

  const role = getUserRoleFromToken();
  const canCreateMovement = role === "COORDINADOR";

  useEffect(() => {
    async function bootstrap() {
      setError(null);
      setLoadingList(true);
      setLoadingMovements(true);
      try {
        const [list, movementRows] = await Promise.all([
          fetchImplements(),
          fetchInventoryMovements(),
        ]);
        setImplementsList(list);
        setMovements(movementRows);
      } catch (requestError) {
        setError(getErrorMessage(requestError, "No se pudo cargar la información de movimientos."));
      } finally {
        setLoadingList(false);
        setLoadingMovements(false);
      }
    }

    void bootstrap();
  }, []);

  const implementByUuid = useMemo(() => {
    const map = new Map<string, ImplementSummary>();
    implementsList.forEach((item) => map.set(item.uuid, item));
    return map;
  }, [implementsList]);

  const categoryOptions = useMemo(() => {
    const map = new Map<string, string>();
    implementsList.forEach((item) => {
      if (item.category) map.set(item.category.uuid, item.category.name);
    });
    return Array.from(map.entries()).map(([uuid, name]) => ({ uuid, name }));
  }, [implementsList]);

  const filteredMovements = useMemo(() => {
    const implementQuery = implementNameFilter.trim().toLowerCase();
    const userQuery = userFilter.trim().toLowerCase();
    const fromDate = toDateStart(dateFrom);
    const toDate = toDateEnd(dateTo);

    return movements.filter((movement) => {
      const implementInfo = movement.implement_uuid ? implementByUuid.get(movement.implement_uuid) : undefined;
      const implementName = implementInfo?.name ?? "";
      const categoryUuid = implementInfo?.category?.uuid ?? null;
      const movementDate = new Date(movement.timestamp);

      if (implementQuery && !implementName.toLowerCase().includes(implementQuery)) return false;
      if (categoryFilter && String(categoryUuid ?? "") !== categoryFilter) return false;
      const performedBy = (movement.performed_by ?? "").toLowerCase();
      if (userQuery && !performedBy.includes(userQuery)) return false;
      if (fromDate && movementDate < fromDate) return false;
      if (toDate && movementDate > toDate) return false;

      return true;
    });
  }, [movements, implementByUuid, implementNameFilter, categoryFilter, userFilter, dateFrom, dateTo]);

  const moveStats = useMemo(() => {
    return {
      total: filteredMovements.length,
      ingresos: filteredMovements.filter((m) => m.action === "STOCK_IN").length,
      ajustes: filteredMovements.filter((m) => m.action === "MANUAL_ADJUSTMENT").length,
      implements: new Set(filteredMovements.map((m) => m.implement_uuid).filter((uuid): uuid is string => Boolean(uuid))).size,
    };
  }, [filteredMovements]);

  function clearFilters() {
    setImplementNameFilter("");
    setCategoryFilter("");
    setUserFilter("");
    setDateFrom("");
    setDateTo("");
  }

  async function reloadMovements() {
    setLoadingMovements(true);
    try {
      const rows = await fetchInventoryMovements();
      setMovements(rows);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo recargar movimientos."));
    } finally {
      setLoadingMovements(false);
    }
  }

  async function submitMovement() {
    const implementUuid = manualImplementUuid.trim();
    if (!implementUuid) {
      setError("Debes seleccionar un implemento para registrar el movimiento.");
      return;
    }

    const qty = Number(quantity);
    if (!Number.isFinite(qty) || qty <= 0 || !Number.isInteger(qty)) {
      setError("La cantidad debe ser un entero positivo.");
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await registerManualMovement(implementUuid, {
        action,
        quantity: qty,
        notes: notes.trim() ? notes.trim() : null,
      });
      await reloadMovements();
      setSuccess("Movimiento registrado correctamente.");
      setQuantity("1");
      setNotes("");
    } catch (requestError) {
      const payload = getApiErrorPayload(requestError);
      setError(payload?.message ?? getErrorMessage(requestError, "No se pudo registrar movimiento."));
    } finally {
      setSaving(false);
    }
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Movimientos</h1>
          <p>Historial de movimientos de inventario registrados en PostgreSQL.</p>
        </div>
      </section>

      <section className="stat-grid">
        <article className="stat-card stat-card--blue">
          <p>Movimientos totales</p>
          <strong>{moveStats.total}</strong>
        </article>
        <article className="stat-card stat-card--green">
          <p>Ingresos</p>
          <strong>{moveStats.ingresos}</strong>
        </article>
        <article className="stat-card stat-card--orange">
          <p>Ajustes</p>
          <strong>{moveStats.ajustes}</strong>
        </article>
        <article className="stat-card stat-card--cyan">
          <p>Implementos con movimiento</p>
          <strong>{moveStats.implements}</strong>
        </article>
      </section>

      <section className="panel">
        {error ? <div className="error-banner">{error}</div> : null}
        {success ? <div className="success-banner">{success}</div> : null}

        <div className="catalog-filters catalog-filters--moves">
          <div className="catalog-filters__item catalog-filters__item--search">
            <label>Nombre implemento</label>
            <Input
              value={implementNameFilter}
              onChange={(e) => setImplementNameFilter(e.target.value)}
              placeholder="Buscar por nombre"
            />
          </div>
          <div className="catalog-filters__item">
            <label>Categoría</label>
            <Select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)} disabled={loadingList}>
              <option value="">Todas</option>
              {categoryOptions.map((category) => (
                <option key={category.uuid} value={category.uuid}>
                  {category.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="catalog-filters__item catalog-filters__item--search">
            <label>Usuario</label>
            <Input value={userFilter} onChange={(e) => setUserFilter(e.target.value)} placeholder="Ej: Ana Pérez" />
          </div>
          <div className="catalog-filters__item">
            <label>Desde</label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </div>
          <div className="catalog-filters__item">
            <label>Hasta</label>
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </div>
        </div>

        <div className="catalog-filters__summary">
          <p>
            Mostrando <strong>{filteredMovements.length}</strong> de <strong>{movements.length}</strong> movimientos
          </p>
          <Button variant="ghost" onClick={clearFilters}>Limpiar filtros</Button>
        </div>

        {canCreateMovement ? (
          <Card className="inventory-motion-card">
            <h3>Registrar movimiento manual</h3>
            <div className="stock-actions-grid">
              <div>
                <label>Implemento</label>
                <Select value={manualImplementUuid} onChange={(e) => setManualImplementUuid(e.target.value)} disabled={loadingList}>
                  <option value="">Selecciona un implemento</option>
                  {implementsList.map((item) => (
                    <option key={item.uuid} value={item.uuid}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <label>Acción</label>
                <Select value={action} onChange={(e) => setAction(e.target.value as ManualMovementType)}>
                  <option value="STOCK_IN">Ingreso de stock</option>
                  <option value="STOCK_OUT">Salida de stock</option>
                  <option value="LOAN_DELIVERY">Entrega de prestamo</option>
                  <option value="LOAN_RETURN">Devolucion de prestamo</option>
                  <option value="DAMAGE_REPORT">Reporte de dano</option>
                  <option value="MANUAL_ADJUSTMENT">Ajuste manual</option>
                </Select>
              </div>
              <div>
                <label>Cantidad</label>
                <Input value={quantity} onChange={(e) => setQuantity(e.target.value)} type="number" min={1} />
              </div>
            </div>
            <label>Notas</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Opcional"
              style={{ width: "100%", marginTop: 6 }}
            />
            <div className="modal-actions">
              <Button onClick={() => void submitMovement()} disabled={saving}>
                {saving ? "Guardando..." : "Registrar movimiento"}
              </Button>
            </div>
          </Card>
        ) : null}

        <Table>
          <thead>
            <tr>
              <th>Implemento</th>
              <th>Categoría</th>
              <th>Fecha</th>
              <th>Acción</th>
              <th>Cantidad</th>
              <th>Usuario</th>
              <th>Notas</th>
            </tr>
          </thead>
          <tbody>
            {loadingMovements ? (
              <tr>
                <td colSpan={7} className="table-hint">Cargando movimientos...</td>
              </tr>
            ) : filteredMovements.length === 0 ? (
              <tr>
                <td colSpan={7} className="table-hint">No hay movimientos para mostrar.</td>
              </tr>
            ) : (
              filteredMovements.map((m: InventoryMovementDetail) => {
                const implementInfo = m.implement_uuid ? implementByUuid.get(m.implement_uuid) : undefined;
                const shortUuid = m.implement_uuid ? m.implement_uuid.slice(0, 8) : "sin-uuid";
                return (
                  <tr key={m.uuid} className="table-row-hover">
                    <td>{implementInfo?.name ?? `Implemento #${shortUuid}`}</td>
                    <td>{implementInfo?.category?.name ?? "Sin categoría"}</td>
                    <td>{new Date(m.timestamp).toLocaleString()}</td>
                    <td>
                      <Badge tone={m.action === "STOCK_IN" ? "active" : m.action === "MANUAL_ADJUSTMENT" ? "warn" : "inactive"}>
                        {ACTION_LABELS[m.action] ?? m.action}
                      </Badge>
                    </td>
                    <td>{m.quantity}</td>
                    <td>{m.performed_by || "Usuario no identificado"}</td>
                    <td>{m.notes ?? "-"}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </Table>
      </section>
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="moves">{content}</InventoryLayout>;
}
