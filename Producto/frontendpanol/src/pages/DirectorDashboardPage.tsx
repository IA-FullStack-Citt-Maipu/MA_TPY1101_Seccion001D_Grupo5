import { AlertTriangle, Box, ClipboardList, Clock3 } from "lucide-react";
import { useEffect, useState } from "react";
import { DashboardSkeleton } from "../components/director/DashboardSkeleton";
import { EmptyState } from "../components/director/EmptyState";
import { ErrorState } from "../components/director/ErrorState";
import { ImportantAlertsCard } from "../components/director/ImportantAlertsCard";
import { InventoryStatusChart } from "../components/director/InventoryStatusChart";
import { KpiCard } from "../components/director/KpiCard";
import { MostRequestedItemsTable } from "../components/director/MostRequestedItemsTable";
import { TopUsersTable } from "../components/director/TopUsersTable";
import { getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import { fetchInventoryMovements } from "../services/movementService";
import { listUsers } from "../services/userAdminService";
import type { AlertItem, InventoryStatusItem, MostRequestedItemRow, TopUserRow } from "../components/director/directorMockData";

const ICONS = [Box, Clock3, AlertTriangle, ClipboardList] as const;

interface DashboardData {
  kpis: Array<{ key: string; title: string; value: number; tone: "blue" | "green" | "red" | "teal"; trend: string }>;
  inventory: InventoryStatusItem[];
  alerts: AlertItem[];
  topUsers: TopUserRow[];
  topImplements: MostRequestedItemRow[];
}

function mapRole(roleRaw: string): "Docente" | "Coordinador" {
  return roleRaw === "COORDINADOR" ? "Coordinador" : "Docente";
}

function buildDashboardData(
  implementsRows: Awaited<ReturnType<typeof fetchImplements>>,
  movementRows: Awaited<ReturnType<typeof fetchInventoryMovements>>,
  usersRows: Awaited<ReturnType<typeof listUsers>>,
): DashboardData {
  const inventory = [
    {
      key: "available" as const,
      label: "Disponibles",
      value: implementsRows.reduce((acc, row) => acc + (row.stock?.available ?? 0), 0),
      color: "#0f7f9f",
    },
    {
      key: "loaned" as const,
      label: "En prestamo",
      value: implementsRows.reduce((acc, row) => acc + (row.stock?.loaned ?? 0), 0),
      color: "#1c66d8",
    },
    {
      key: "maintenance" as const,
      label: "Mantenimiento",
      value: implementsRows.reduce((acc, row) => acc + (row.stock?.reserved ?? 0), 0),
      color: "#ef9f1b",
    },
    {
      key: "unavailable" as const,
      label: "No disponibles",
      value: implementsRows.reduce((acc, row) => acc + (row.stock?.damaged ?? 0), 0),
      color: "#7083a8",
    },
  ];

  const lowStockImplements = implementsRows.filter(
    (row) => (row.stock?.available ?? 0) <= (row.stock?.min_stock ?? 0),
  );

  const alerts: AlertItem[] = [
    ...lowStockImplements.slice(0, 3).map((row, idx) => ({
      uuid: `low-stock-${row.uuid}-${idx}`,
      severity: "critical" as const,
      text: `${row.name} bajo stock minimo`,
    })),
    ...(movementRows.length > 0
      ? [
          {
            uuid: "movement-count",
            severity: "info" as const,
            text: `${movementRows.length} movimientos registrados`,
          },
        ]
      : []),
  ];

  const movementByUser = new Map<string, { requests: number; delays: number }>();
  for (const row of movementRows) {
    const performer = row.performed_by?.trim() || "Usuario no identificado";
    const current = movementByUser.get(performer) ?? { requests: 0, delays: 0 };
    current.requests += 1;
    movementByUser.set(performer, current);
  }

  const topUsers: TopUserRow[] = Array.from(movementByUser.entries())
    .map(([name, stats]) => {
      const matching = usersRows.find((u) => u.name.toLowerCase() === name.toLowerCase());
      return {
        name,
        role: mapRole(matching?.role ?? "DOCENTE"),
        requests: stats.requests,
        delays: 0,
      };
    })
    .sort((a, b) => b.requests - a.requests)
    .slice(0, 5);

  const implementNameById = new Map<string, string>(implementsRows.map((row) => [row.uuid, row.name]));
  const movementByImplement = new Map<string, number>();
  for (const row of movementRows) {
    if (!row.implement_uuid) {
      continue;
    }
    movementByImplement.set(row.implement_uuid, (movementByImplement.get(row.implement_uuid) ?? 0) + 1);
  }

  const topImplements: MostRequestedItemRow[] = Array.from(movementByImplement.entries())
    .map(([implementId, total]) => {
      const implement = implementsRows.find((row) => row.uuid === implementId);
      const available = implement?.stock?.available ?? 0;
      const stockTone: "ok" | "warn" | "critical" = available <= 3 ? "critical" : available <= 10 ? "warn" : "ok";
      return {
        implement: implementNameById.get(implementId) ?? `Implemento #${implementId}`,
        requests: total,
        rejects: 0,
        stock: `${available} unidades`,
        stockTone,
      };
    })
    .sort((a, b) => b.requests - a.requests)
    .slice(0, 6);

  const totalImplements = implementsRows.length;
  const totalLoaned = inventory.find((x) => x.key === "loaned")?.value ?? 0;
  const totalAlerts = alerts.length;

  const kpis = [
    { key: "total", title: "Implementos totales", value: totalImplements, tone: "blue" as const, trend: `${totalImplements}` },
    { key: "loaned", title: "En prestamo", value: totalLoaned, tone: "blue" as const, trend: `${totalLoaned}` },
    { key: "alerts", title: "Alertas criticas", value: totalAlerts, tone: "red" as const, trend: `${totalAlerts}` },
    { key: "movements", title: "Movimientos registrados", value: movementRows.length, tone: "green" as const, trend: `${movementRows.length}` },
  ];

  return { kpis, inventory, alerts, topUsers, topImplements };
}

export function DirectorDashboardPage({ embedded = false }: { embedded?: boolean }) {
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [errorMessage, setErrorMessage] = useState<string>("No fue posible cargar el dashboard ejecutivo.");
  const [data, setData] = useState<DashboardData | null>(null);

  async function load() {
    setStatus("loading");
    try {
      const [implementsRows, movementRows, usersRows] = await Promise.all([
        fetchImplements(),
        fetchInventoryMovements(),
        listUsers(),
      ]);
      setData(buildDashboardData(implementsRows, movementRows, usersRows));
      setStatus("ready");
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "No fue posible cargar el dashboard ejecutivo."));
      setStatus("error");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const content = (
    <>
      <section className="content-header director-header">
        <div>
          <h1>Dashboard - Director de Carrera</h1>
          <p>Resumen general del pañol y estado operativo.</p>
        </div>
      </section>

      {status === "error" ? <ErrorState message={errorMessage} onRetry={() => void load()} /> : null}
      {status === "loading" ? <DashboardSkeleton /> : null}

      {status === "ready" && data ? (
        <>
          <section className="director-kpi-grid">
            {data.kpis.map((item, index) => {
              const Icon = ICONS[index];
              return (
                <KpiCard
                  key={item.key}
                  title={item.title}
                  value={item.value}
                  icon={Icon}
                  tone={item.tone}
                  trend={item.trend}
                />
              );
            })}
          </section>

          <section className="director-two-col">
            <InventoryStatusChart rows={data.inventory} />
            <ImportantAlertsCard alerts={data.alerts} />
          </section>

          <section className="director-two-col">
            <TopUsersTable rows={data.topUsers} />
            <MostRequestedItemsTable rows={data.topImplements} />
          </section>

          {data.topImplements.length === 0 ? <EmptyState message="Sin datos suficientes de movimientos para mostrar ranking de implementos." /> : null}
        </>
      ) : null}
    </>
  );

  return embedded ? content : content;
}
