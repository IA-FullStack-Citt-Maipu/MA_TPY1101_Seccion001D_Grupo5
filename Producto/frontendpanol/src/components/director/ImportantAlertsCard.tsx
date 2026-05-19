import { AlertTriangle, ChevronRight, Clock3, Wrench } from "lucide-react";
import type { AlertItem } from "./directorMockData";

function iconBySeverity(severity: AlertItem["severity"]) {
  if (severity === "critical") return AlertTriangle;
  if (severity === "warning") return Clock3;
  return Wrench;
}

export function ImportantAlertsCard({ alerts }: { alerts: AlertItem[] }) {
  return (
    <section className="panel director-panel">
      <div className="panel__head"><h2>Alertas importantes</h2></div>
      <ul className="director-alert-list">
        {alerts.map((alert) => {
          const Icon = iconBySeverity(alert.severity);
          return (
            <li key={alert.uuid} className={`director-alert director-alert--${alert.severity}`} tabIndex={0}>
              <Icon size={16} aria-hidden="true" />
              <span>{alert.text}</span>
              <ChevronRight size={16} aria-hidden="true" />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
