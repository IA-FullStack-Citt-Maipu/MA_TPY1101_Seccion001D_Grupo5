export function DashboardSkeleton() {
  return (
    <div className="director-skeleton" aria-hidden="true">
      <div className="director-skeleton-kpis">
        {Array.from({ length: 4 }).map((_, idx) => <div key={idx} className="skeleton director-skeleton-card" />)}
      </div>
      <div className="director-skeleton-grid">
        <div className="skeleton director-skeleton-panel" />
        <div className="skeleton director-skeleton-panel" />
      </div>
      <div className="director-skeleton-grid">
        <div className="skeleton director-skeleton-panel" />
        <div className="skeleton director-skeleton-panel" />
      </div>
      <div className="skeleton director-skeleton-panel" style={{ height: 220 }} />
    </div>
  );
}
