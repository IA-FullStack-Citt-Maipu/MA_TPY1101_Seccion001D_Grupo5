import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { Categoria } from "../../types/category";

interface CategoryFormModalProps {
  mode: "create" | "edit";
  category?: Categoria;
  isOpen: boolean;
  saving: boolean;
  fieldError: string | null;
  onClose: () => void;
  onSubmit: (name: string, description: string) => Promise<void>;
}

export function CategoryFormModal({
  mode,
  category,
  isOpen,
  saving,
  fieldError,
  onClose,
  onSubmit,
}: CategoryFormModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setName(category?.nombre ?? "");
    setDescription(category?.descripcion ?? "");
  }, [category?.nombre, category?.descripcion, isOpen]);

  if (!isOpen) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit(name.trim(), description);
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal">
        <h3>{mode === "create" ? "Nueva categoría" : "Editar categoría"}</h3>
        <p>Define un nombre único para organizar el catálogo de inventario.</p>

        <form onSubmit={handleSubmit}>
          <label htmlFor="category-name">Nombre</label>
          <input
            id="category-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Ej: Equipos clínicos"
            maxLength={100}
            required
          />
          <label htmlFor="category-description">Descripción</label>
          <textarea
            id="category-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Ej: Equipamiento para prácticas clínicas."
            maxLength={255}
            rows={3}
          />
          {fieldError ? <p className="field-error">{fieldError}</p> : null}

          <div className="modal-actions">
            <button type="button" className="button button--ghost" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="button" disabled={saving || name.trim().length === 0}>
              {saving ? "Guardando..." : "Guardar"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
