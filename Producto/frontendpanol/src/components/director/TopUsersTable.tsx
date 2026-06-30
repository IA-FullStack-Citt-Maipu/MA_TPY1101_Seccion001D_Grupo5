import type { TopUserRow } from "./directorMockData";

function delayTone(delays: number) {
  if (delays === 0) return "ok";
  if (delays <= 2) return "warn";
  return "critical";
}

export function TopUsersTable({ rows }: { rows: TopUserRow[] }) {
  return (
    <section className="panel director-panel">
      <div className="panel__head"><h2>Usuarios con mas movimientos</h2></div>
      <div className="table-wrapper director-table-wrapper">
        <table className="category-table director-table director-table--users">
          <thead>
            <tr>
              <th scope="col">Usuario</th>
              <th scope="col">Rol</th>
              <th scope="col">Movimientos</th>
              <th scope="col">Atrasos estimados</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.name || "usuario-sin-nombre"}>
                <td data-label="Usuario">
                  <div className="director-user-cell">
                    <span className="director-avatar" aria-hidden="true">{(row.name || "Usuario").split(" ").map((x) => x?.[0] ?? "").filter(Boolean).slice(0, 2).join("").toUpperCase()}</span>
                    <span>{row.name || "Usuario"}</span>
                  </div>
                </td>
                <td data-label="Rol"><span className="badge badge--inactive">{row.role}</span></td>
                <td data-label="Movimientos">{row.requests}</td>
                <td data-label="Atrasos estimados"><span className={`director-delay director-delay--${delayTone(row.delays)}`}>{row.delays}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
