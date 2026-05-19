export interface KpiItem {
  key: string;
  title: string;
  value: number;
  tone: "blue" | "green" | "red" | "teal";
  trend: string;
}

export interface InventoryStatusItem {
  key: "available" | "loaned" | "maintenance" | "unavailable";
  label: string;
  value: number;
  color: string;
}

export interface AlertItem {
  uuid: string;
  severity: "critical" | "warning" | "info";
  text: string;
}

export interface SubjectRequest {
  subject: string;
  requests: number;
}

export interface TopUserRow {
  name: string;
  role: "Docente" | "Coordinador";
  requests: number;
  delays: number;
}

export interface MostRequestedItemRow {
  implement: string;
  requests: number;
  rejects: number;
  stock: string;
  stockTone: "ok" | "warn" | "critical";
}

export const kpiData: KpiItem[] = [
  { key: "total", title: "Implementos totales", value: 540, tone: "blue", trend: "+4.2%" },
  { key: "loaned", title: "En prestamo", value: 95, tone: "blue", trend: "+1.1%" },
  { key: "alerts", title: "Alertas criticas", value: 8, tone: "red", trend: "-2" },
  { key: "pending", title: "Solicitudes pendientes", value: 14, tone: "green", trend: "+3" },
];

export const inventoryStatusData: InventoryStatusItem[] = [
  { key: "available", label: "Disponibles", value: 390, color: "#0f7f9f" },
  { key: "loaned", label: "En prestamo", value: 95, color: "#1c66d8" },
  { key: "maintenance", label: "Mantenimiento", value: 32, color: "#ef9f1b" },
  { key: "unavailable", label: "No disponibles", value: 23, color: "#7083a8" },
];

export const alertsData: AlertItem[] = [
  { uuid: "a1", severity: "critical", text: "Guantes talla M bajo stock minimo" },
  { uuid: "a2", severity: "warning", text: "5 prestamos con atraso" },
  { uuid: "a3", severity: "critical", text: "2 implementos criticos no disponibles" },
  { uuid: "a4", severity: "info", text: "Balanza digital enviada a mantenimiento 4 veces" },
];

export const requestsBySubjectData: SubjectRequest[] = [
  { subject: "Simulacion Clinica", requests: 85 },
  { subject: "Enfermeria Basica", requests: 64 },
  { subject: "Urgencias", requests: 48 },
  { subject: "Procedimientos", requests: 32 },
];

export const topUsersData: TopUserRow[] = [
  { name: "Maria Fernanda Lopez", role: "Docente", requests: 28, delays: 3 },
  { name: "Carlos Andres Ramirez", role: "Docente", requests: 22, delays: 2 },
  { name: "Juliana Perez", role: "Coordinador", requests: 18, delays: 0 },
  { name: "Juan Camilo Torres", role: "Docente", requests: 15, delays: 1 },
  { name: "Laura Medina", role: "Docente", requests: 12, delays: 0 },
];

export const mostRequestedItemsData: MostRequestedItemRow[] = [
  { implement: "Tensiometro", requests: 72, rejects: 3, stock: "18 unidades", stockTone: "ok" },
  { implement: "Fonendoscopio", requests: 58, rejects: 2, stock: "26 unidades", stockTone: "ok" },
  { implement: "Guantes", requests: 51, rejects: 6, stock: "24 cajas", stockTone: "warn" },
  { implement: "Simulador de inyeccion", requests: 38, rejects: 1, stock: "7 unidades", stockTone: "critical" },
];
