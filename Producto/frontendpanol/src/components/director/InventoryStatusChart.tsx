import type { InventoryStatusItem } from "./directorMockData";

export function InventoryStatusChart({ rows }: { rows: InventoryStatusItem[] }) {
  const total = rows.reduce((acc, row) => acc + row.value, 0);
  let offset = 0;
  const segments = rows
    .map((row) => {
      const pct = total === 0 ? 0 : (row.value / total) * 100;
      const segment = `${row.color} ${offset.toFixed(2)}% ${(offset + pct).toFixed(2)}%`;
      offset += pct;
      return segment;
    })
    .join(", ");

  return (
    <section className="panel director-panel">
      <div className="panel__head"><h2>Estado del Inventario</h2></div>
      <div className="director-inventory-status">
        <div className="director-donut" style={{ background: `conic-gradient(${segments})` }}>
          <div className="director-donut__center">
            <strong>{total}</strong>
            <span>Total</span>
          </div>
        </div>
        <ul className="director-legend" aria-label="Leyenda estado inventario">
          {rows.map((row) => (
            <li key={row.key}>
              <span className="director-legend__dot" style={{ background: row.color }} aria-hidden="true" />
              <span>{row.label}</span>
              <strong>{row.value}</strong>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
