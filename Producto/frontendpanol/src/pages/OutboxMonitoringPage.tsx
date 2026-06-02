import { AlertTriangle, CheckCircle2, RefreshCcw, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { fetchInventoryMovements } from "../services/movementService";
import type { InventoryMovementDetail } from "../types/implement";

interface MonitoringRow {
  eventId: string;
  aggregateType: string;
  aggregateId: string | null;
  eventType: string;
  payloadPreview: string;
  occurredAt: string;
  status: "PENDING" | "PROCESSING" | "SENT" | "FAILED";
  retryCount: number;
}

function mapMovementToRow(movement: InventoryMovementDetail): MonitoringRow {
  const status = resolveStatusFromAction(movement.action);
  return {
    eventId: movement.uuid,
    aggregateType: "implement",
    aggregateId: movement.implement_uuid,
    eventType: movement.action,
    payloadPreview: JSON.stringify({
      notes: movement.notes,
      quantity: movement.quantity,
      performer: movement.performed_by,
    }),
    occurredAt: movement.timestamp,
    status,
    retryCount: status === "FAILED" ? 1 : 0,
  };
}

function resolveStatusFromAction(action: string): MonitoringRow["status"] {
  if (action === "damage_report" || action === "loss") return "FAILED";
  if (action === "manual_adjustment" || action === "discard") return "PROCESSING";
  if (action === "stock_out" || action === "loan_delivery") return "PENDING";
  return "SENT";
}

function statusClass(status: MonitoringRow["status"]): string {
  if (status === "FAILED") return "outbox-monitoring-status outbox-monitoring-status--failed";
  if (status === "PROCESSING") return "outbox-monitoring-status outbox-monitoring-status--processing";
  if (status === "PENDING") return "outbox-monitoring-status outbox-monitoring-status--pending";
  return "outbox-monitoring-status outbox-monitoring-status--sent";
}

function formatDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(parsed);
}

function prettyEventName(value: string): string {
  return value
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function shorten(value: string, max: number): string {
  if (value.length <= max) return value;
  return `${value.slice(0, max - 3)}...`;
}

export function OutboxMonitoringPage({ embedded = false }: { embedded?: boolean }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<MonitoringRow[]>([]);
  const [query, setQuery] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadRows() {
      setLoading(true);
      setError(null);
      try {
        const movements = await fetchInventoryMovements();
        if (cancelled) return;
        const mapped = movements
          .map(mapMovementToRow)
          .sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime());
        setRows(mapped);
      } catch (requestError) {
        if (cancelled) return;
        setError(getErrorMessage(requestError, "No se pudo cargar el monitoreo tecnico."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadRows();
    return () => {
      cancelled = true;
    };
  }, []);

  const filteredRows = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return rows;
    return rows.filter((row) => {
      const rowText = [row.eventId, row.aggregateType, row.aggregateId ?? "", row.eventType, row.payloadPreview]
        .join(" ")
        .toLowerCase();
      return rowText.includes(normalized);
    });
  }, [query, rows]);

  const metrics = useMemo(() => {
    const sent = rows.filter((row) => row.status === "SENT").length;
    const pending = rows.filter((row) => row.status === "PENDING" || row.status === "PROCESSING").length;
    const failed = rows.filter((row) => row.status === "FAILED").length;
    const successRate = rows.length === 0 ? 100 : Math.round((sent / rows.length) * 100);
    return { sent, pending, failed, successRate };
  }, [rows]);

  const content = (
    <div className="outbox-monitoring-page">
      <section className="outbox-monitoring-header">
        <div>
          <h1>Monitoreo de Eventos</h1>
          <p>Panel tecnico para seguimiento del flujo de eventos operacionales.</p>
          <small>
            Actualmente usa datos reales de <code>/api/v2/implements/movements</code> como fuente operativa.
          </small>
        </div>
        <button type="button" className="outbox-monitoring-refresh" onClick={() => window.location.reload()}>
          <RefreshCcw size={14} /> Refrescar
        </button>
      </section>

      {error ? <div className="error-banner">{error}</div> : null}

      <section className="outbox-monitoring-kpi-grid">
        <article className="outbox-monitoring-kpi">
          <span>Total eventos</span>
          <strong>{rows.length}</strong>
        </article>
        <article className="outbox-monitoring-kpi outbox-monitoring-kpi--pending">
          <span>Pending / Processing</span>
          <strong>{metrics.pending}</strong>
        </article>
        <article className="outbox-monitoring-kpi outbox-monitoring-kpi--failed">
          <span>Failed</span>
          <strong>{metrics.failed}</strong>
        </article>
        <article className="outbox-monitoring-kpi outbox-monitoring-kpi--sent">
          <span>Success rate</span>
          <strong>{metrics.successRate}%</strong>
        </article>
      </section>

      <section className="outbox-monitoring-card">
        <div className="outbox-monitoring-card__head">
          <label className="outbox-monitoring-search">
            <Search size={15} />
            <input
              type="search"
              placeholder="Buscar por event_id, agregado o tipo..."
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <a href="#/inventory/moves" className="outbox-monitoring-link">Ver movimientos completos</a>
        </div>

        {loading ? <p className="outbox-monitoring-loading">Cargando eventos...</p> : null}

        <div className="outbox-monitoring-table-wrap">
          <table className="outbox-monitoring-table">
            <thead>
              <tr>
                <th>Event ID</th>
                <th>Aggregate</th>
                <th>Event type</th>
                <th>Payload</th>
                <th>Occurred at</th>
                <th>Status</th>
                <th>Retries</th>
              </tr>
            </thead>
            <tbody>
              {!loading && filteredRows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="outbox-monitoring-empty">
                    <AlertTriangle size={15} />
                    Sin eventos para los filtros aplicados.
                  </td>
                </tr>
              ) : null}
              {!loading
                ? filteredRows.map((row) => (
                    <tr key={row.eventId}>
                      <td>{shorten(row.eventId, 18)}</td>
                      <td>
                        <div className="outbox-monitoring-aggregate-cell">
                          <strong>{row.aggregateType}</strong>
                          <small>{shorten(row.aggregateId ?? "-", 16)}</small>
                        </div>
                      </td>
                      <td>{prettyEventName(row.eventType)}</td>
                      <td>
                        <code>{shorten(row.payloadPreview, 84)}</code>
                      </td>
                      <td>{formatDateTime(row.occurredAt)}</td>
                      <td>
                        <span className={statusClass(row.status)}>{row.status}</span>
                      </td>
                      <td>{row.retryCount}</td>
                    </tr>
                  ))
                : null}
            </tbody>
          </table>
        </div>
      </section>

      <section className="outbox-monitoring-footnote">
        <CheckCircle2 size={14} />
        Si necesitas monitoreo estricto de <code>outbox_event</code>, se debe exponer un endpoint de solo lectura desde backend.
      </section>
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}

