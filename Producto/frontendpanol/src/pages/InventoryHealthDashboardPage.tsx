import { AlertTriangle, ArrowUpDown, Boxes, RefreshCcw, ShieldCheck, Wrench } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import { fetchInventoryMovements } from "../services/movementService";
import { fetchImplementStock } from "../services/stockService";
import type { ImplementSummary, InventoryMovementDetail } from "../types/implement";

interface LowStockRow {
  implementUuid: string;
  implementName: string;
  categoryName: string;
  locationName: string;
  itemType: string;
  totalStock: number;
  minStock: number;
  available: number;
  reserved: number;
  loaned: number;
  damaged: number;
  stockGap: number;
  stockStatus: "out_of_stock" | "low_stock" | "ok";
}

interface IndividualSummary {
  available: number;
  loaned: number;
  maintenance: number;
  damaged: number;
  blocked: number;
  retired: number;
  total: number;
}

const EMPTY_INDIVIDUAL_SUMMARY: IndividualSummary = {
  available: 0,
  loaned: 0,
  maintenance: 0,
  damaged: 0,
  blocked: 0,
  retired: 0,
  total: 0,
};

function toLowStockRow(row: ImplementSummary): LowStockRow | null {
  if (!row.stock) {
    return null;
  }

  const available = row.stock.available ?? 0;
  const minStock = row.stock.min_stock ?? 0;
  const totalStock = row.stock.total_stock ?? 0;
  const reserved = row.stock.reserved ?? 0;
  const loaned = row.stock.loaned ?? 0;
  const damaged = row.stock.damaged ?? 0;

  let stockStatus: LowStockRow["stockStatus"] = "ok";
  if (available <= 0) {
    stockStatus = "out_of_stock";
  } else if (available <= minStock) {
    stockStatus = "low_stock";
  }

  return {
    implementUuid: row.uuid,
    implementName: row.name,
    categoryName: row.category?.name ?? "Sin categoria",
    locationName: row.location?.name ?? "Sin ubicacion",
    itemType: "catalogo",
    totalStock,
    minStock,
    available,
    reserved,
    loaned,
    damaged,
    stockGap: Math.max(minStock - available, 0),
    stockStatus,
  };
}

function statusClass(status: LowStockRow["stockStatus"]): string {
  if (status === "out_of_stock") return "stock-health-status stock-health-status--critical";
  if (status === "low_stock") return "stock-health-status stock-health-status--warning";
  return "stock-health-status stock-health-status--ok";
}

function statusLabel(status: LowStockRow["stockStatus"]): string {
  if (status === "out_of_stock") return "out_of_stock";
  if (status === "low_stock") return "low_stock";
  return "ok";
}

function movementLabel(action: string): string {
  const labels: Record<string, string> = {
    stock_in: "Entrada de stock",
    stock_out: "Salida de stock",
    loan_delivery: "Entrega de prestamo",
    loan_return: "Retorno de prestamo",
    damage_report: "Reporte de dano",
    manual_adjustment: "Ajuste manual",
    consumption: "Consumo",
    discard: "Descarte",
    loss: "Perdida",
  };
  return labels[action] ?? action;
}

function movementStatus(action: string): "ok" | "attention" | "warning" {
  if (action === "damage_report" || action === "loss") return "attention";
  if (action === "manual_adjustment" || action === "discard") return "warning";
  return "ok";
}

function formatDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(parsed)
    .replace(".", "");
}

function percentage(value: number, total: number): number {
  if (total <= 0) return 0;
  return Math.round((value / total) * 100);
}

function statusGroupClass(status: "ok" | "attention" | "warning"): string {
  if (status === "attention") return "stock-health-activity stock-health-activity--attention";
  if (status === "warning") return "stock-health-activity stock-health-activity--warning";
  return "stock-health-activity stock-health-activity--ok";
}

export function InventoryHealthDashboardPage({ embedded = false }: { embedded?: boolean }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<LowStockRow[]>([]);
  const [movements, setMovements] = useState<InventoryMovementDetail[]>([]);
  const [individualSummary, setIndividualSummary] = useState<IndividualSummary>(EMPTY_INDIVIDUAL_SUMMARY);

  useEffect(() => {
    let cancelled = false;

    async function loadDashboard() {
      setLoading(true);
      setError(null);
      try {
        const [implementsRows, movementRows] = await Promise.all([
          fetchImplements(),
          fetchInventoryMovements().catch(() => []),
        ]);
        if (cancelled) return;

        const lowStockRows = implementsRows
          .map(toLowStockRow)
          .filter((row): row is LowStockRow => row != null)
          .sort((left, right) => {
            const priority: Record<LowStockRow["stockStatus"], number> = {
              out_of_stock: 0,
              low_stock: 1,
              ok: 2,
            };
            if (priority[left.stockStatus] !== priority[right.stockStatus]) {
              return priority[left.stockStatus] - priority[right.stockStatus];
            }
            return right.stockGap - left.stockGap;
          });

        setRows(lowStockRows);

        const sortedMovements = [...movementRows].sort((left, right) => {
          const leftDate = new Date(left.timestamp).getTime();
          const rightDate = new Date(right.timestamp).getTime();
          return rightDate - leftDate;
        });
        setMovements(sortedMovements);

        const individualUuids = implementsRows.map((entry) => entry.uuid).slice(0, 20);

        if (individualUuids.length > 0) {
          const details = await Promise.all(
            individualUuids.map(async (implementUuid) => {
              try {
                return await fetchImplementStock(implementUuid);
              } catch {
                return null;
              }
            }),
          );
          if (cancelled) return;

          const summary = details.reduce<IndividualSummary>((acc, detail) => {
            if (!detail) return acc;
            if (detail.item_type !== "individual") return acc;
            detail.individuals.forEach((individual) => {
              acc.total += 1;
              if (individual.status === "available") acc.available += 1;
              if (individual.status === "loaned") acc.loaned += 1;
              if (individual.status === "maintenance") acc.maintenance += 1;
              if (individual.status === "damaged") acc.damaged += 1;
              if (individual.status === "blocked") acc.blocked += 1;
              if (individual.status === "retired") acc.retired += 1;
            });
            return acc;
          }, { ...EMPTY_INDIVIDUAL_SUMMARY });

          setIndividualSummary(summary);
        } else {
          setIndividualSummary(EMPTY_INDIVIDUAL_SUMMARY);
        }
      } catch (requestError) {
        if (cancelled) return;
        setError(getErrorMessage(requestError, "No se pudo cargar el dashboard de stock."));
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadDashboard();
    return () => {
      cancelled = true;
    };
  }, []);

  const outOfStockCount = useMemo(
    () => rows.filter((row) => row.stockStatus === "out_of_stock").length,
    [rows],
  );
  const lowStockCount = useMemo(
    () => rows.filter((row) => row.stockStatus === "low_stock").length,
    [rows],
  );

  const visibleAlerts = useMemo(
    () => rows.filter((row) => row.stockStatus !== "ok").slice(0, 15),
    [rows],
  );

  const activityRows = useMemo(() => movements.slice(0, 8), [movements]);

  const content = (
    <div className="stock-health-page">
      <section className="stock-health-header">
        <div>
          <h1>Panel de salud de inventario</h1>
          <p>Monitoreo de bajo stock, estado operacional y actividad reciente.</p>
        </div>
        <div className="stock-health-header__actions">
          <a href="#/inventory/implementos" className="stock-health-link-btn">Ver implementos</a>
          <button type="button" className="stock-health-link-btn stock-health-link-btn--primary" onClick={() => window.location.reload()}>
            <RefreshCcw size={14} /> Actualizar
          </button>
        </div>
      </section>

      {error ? <div className="error-banner">{error}</div> : null}

      <section className="stock-health-kpi-grid">
        <article className="stock-health-kpi stock-health-kpi--critical">
          <span>Out of stock</span>
          <strong>{outOfStockCount}</strong>
          <AlertTriangle size={18} />
        </article>
        <article className="stock-health-kpi stock-health-kpi--warning">
          <span>Low stock</span>
          <strong>{lowStockCount}</strong>
          <ArrowUpDown size={18} />
        </article>
        <article className="stock-health-kpi stock-health-kpi--total">
          <span>Total implementos</span>
          <strong>{rows.length}</strong>
          <Boxes size={18} />
        </article>
        <article className="stock-health-kpi stock-health-kpi--ok">
          <span>En mantenimiento</span>
          <strong>{individualSummary.maintenance}</strong>
          <Wrench size={18} />
        </article>
      </section>

      <section className="stock-health-layout">
        <article className="stock-health-table-card">
          <header>
            <h2>Alertas criticas de stock</h2>
            <span>v_low_stock</span>
          </header>

          {loading ? <p className="stock-health-loading">Cargando alertas...</p> : null}

          <div className="stock-health-table-wrap">
            <table className="stock-health-table">
              <thead>
                <tr>
                  <th>Implemento</th>
                  <th>Categoria</th>
                  <th>Disponible</th>
                  <th>Estado</th>
                  <th>Ubicacion</th>
                </tr>
              </thead>
              <tbody>
                {!loading && visibleAlerts.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="stock-health-empty">Sin alertas de bajo stock.</td>
                  </tr>
                ) : null}
                {!loading
                  ? visibleAlerts.map((row) => (
                      <tr key={row.implementUuid}>
                        <td>
                          <div className="stock-health-item-cell">
                            <strong>{row.implementName}</strong>
                            <small>{row.itemType}</small>
                          </div>
                        </td>
                        <td>{row.categoryName}</td>
                        <td>
                          <div className="stock-health-qty-cell">
                            <strong>{row.available}</strong>
                            <small>min {row.minStock}</small>
                          </div>
                        </td>
                        <td>
                          <span className={statusClass(row.stockStatus)}>{statusLabel(row.stockStatus)}</span>
                        </td>
                        <td>{row.locationName}</td>
                      </tr>
                    ))
                  : null}
              </tbody>
            </table>
          </div>
        </article>

        <aside className="stock-health-side">
          <article className="stock-health-summary-card">
            <header>
              <h2>Resumen individuales</h2>
              <span>v_individual_status_summary</span>
            </header>
            <div className="stock-health-bar-group">
              <p>Disponibles</p>
              <div><span style={{ width: `${percentage(individualSummary.available, individualSummary.total)}%` }} /></div>
              <small>{individualSummary.available}</small>
            </div>
            <div className="stock-health-bar-group">
              <p>Prestados</p>
              <div><span style={{ width: `${percentage(individualSummary.loaned, individualSummary.total)}%` }} /></div>
              <small>{individualSummary.loaned}</small>
            </div>
            <div className="stock-health-bar-group">
              <p>Mantenimiento</p>
              <div><span style={{ width: `${percentage(individualSummary.maintenance, individualSummary.total)}%` }} /></div>
              <small>{individualSummary.maintenance}</small>
            </div>
            <div className="stock-health-bar-group">
              <p>Danados / bloqueados</p>
              <div><span style={{ width: `${percentage(individualSummary.damaged + individualSummary.blocked, individualSummary.total)}%` }} /></div>
              <small>{individualSummary.damaged + individualSummary.blocked}</small>
            </div>
            <footer>
              <ShieldCheck size={14} /> Total auditado: {individualSummary.total}
            </footer>
          </article>

          <article className="stock-health-activity-card">
            <header>
              <h2>Actividad reciente</h2>
              <span>/api/v2/implements/movements</span>
            </header>
            {activityRows.length === 0 ? (
              <p className="stock-health-empty">Sin movimientos recientes.</p>
            ) : (
              <div className="stock-health-activity-list">
                {activityRows.map((movement) => {
                  const state = movementStatus(movement.action);
                  return (
                    <div key={movement.uuid} className={statusGroupClass(state)}>
                      <strong>{movementLabel(movement.action)}</strong>
                      <p>{movement.notes ?? "Sin observacion"}</p>
                      <small>
                        {formatDateTime(movement.timestamp)} · qty {movement.quantity}
                      </small>
                    </div>
                  );
                })}
              </div>
            )}
          </article>
        </aside>
      </section>
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}

