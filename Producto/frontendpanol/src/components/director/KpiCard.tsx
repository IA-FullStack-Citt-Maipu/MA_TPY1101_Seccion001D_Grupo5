import type { LucideIcon } from "lucide-react";

export function KpiCard({
  title,
  value,
  icon: Icon,
  tone,
  trend,
}: {
  title: string;
  value: number;
  icon: LucideIcon;
  tone: "blue" | "green" | "red" | "teal";
  trend: string;
}) {
  return (
    <article className={`director-kpi director-kpi--${tone}`}>
      <div className="director-kpi__icon" aria-hidden="true">
        <Icon size={24} />
      </div>
      <div className="director-kpi__content">
        <p>{title}</p>
        <strong>{value}</strong>
      </div>
      <span className="director-kpi__trend" aria-label={`Tendencia ${trend}`}>{trend}</span>
    </article>
  );
}
