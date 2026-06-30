import type { SubjectRequest } from "./directorMockData";

export function RequestsBySubjectChart({ rows }: { rows: SubjectRequest[] }) {
  const max = Math.max(...rows.map((row) => row.requests), 1);
  return (
    <section className="panel director-panel">
      <div className="panel__head">
        <h2>Solicitudes por asignatura</h2>
        <button type="button" className="button button--ghost button--table">Ultimos 30 dias</button>
      </div>
      <div className="director-bar-chart" role="img" aria-label="Solicitudes por asignatura ultimos 30 dias">
        {rows.map((row) => (
          <div key={row.subject} className="director-bar-chart__item">
            <div className="director-bar-chart__bar-wrap">
              <span>{row.requests}</span>
              <div className="director-bar-chart__bar" style={{ height: `${Math.round((row.requests / max) * 100)}%` }} />
            </div>
            <p>{row.subject}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
