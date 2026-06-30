import type { Categoria, CategoriaAssociationSummary } from "../../types/category";

interface CategoryTableProps {
  categories: Categoria[];
  associations: Record<string, CategoriaAssociationSummary>;
  loading: boolean;
  onEdit: (category: Categoria) => void;
  onActivate: (category: Categoria) => void;
  onDeactivate: (category: Categoria) => void;
  onDelete: (category: Categoria) => void;
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString("es-CL", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function CategoryTable({
  categories,
  associations,
  loading,
  onEdit,
  onActivate,
  onDeactivate,
  onDelete,
}: CategoryTableProps) {
  if (loading) {
    return <div className="empty-state">Cargando categorías...</div>;
  }

  if (categories.length === 0) {
    return <div className="empty-state">Aún no hay categorías creadas.</div>;
  }

  return (
    <div className="table-wrapper">
      <table className="category-table">
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Descripción</th>
            <th>Estado</th>
            <th>Implementos asociados</th>
            <th>Creación</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {categories.map((category) => {
            const association = associations[category.uuid];
            const implementCount = association?.implementCount ?? 0;
            const canDelete = association?.canDelete ?? false;

            return (
              <tr key={category.uuid}>
                <td>
                  <strong>{category.nombre}</strong>
                </td>
                <td>{category.descripcion?.trim() ? category.descripcion : "Sin descripción"}</td>
                <td>
                  <span className={category.activa ? "badge badge--active" : "badge badge--inactive"}>
                    {category.activa ? "Activa" : "Inactiva"}
                  </span>
                </td>
                <td>{implementCount}</td>
                <td>{formatDate(category.createdAt)}</td>
                <td>
                  <div className="table-actions">
                    <button type="button" className="button button--table" onClick={() => onEdit(category)}>
                      Editar
                    </button>

                    {category.activa ? (
                      <button
                        type="button"
                        className="button button--table button--warn"
                        onClick={() => onDeactivate(category)}
                      >
                        Desactivar
                      </button>
                    ) : null}

                    {!category.activa ? (
                      <button
                        type="button"
                        className="button button--table"
                        onClick={() => onActivate(category)}
                      >
                        Activar
                      </button>
                    ) : null}

                    {canDelete ? (
                      <button
                        type="button"
                        className="button button--table button--danger"
                        onClick={() => onDelete(category)}
                      >
                        Eliminar
                      </button>
                    ) : (
                      <span className="table-hint">No eliminable</span>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
