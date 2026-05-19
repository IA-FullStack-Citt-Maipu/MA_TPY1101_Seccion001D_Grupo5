import { useEffect, useMemo, useState } from "react";
import { Plus, RefreshCcw } from "lucide-react";
import { CategoryTable } from "../components/categories/CategoryTable";
import { ConfirmModal } from "../components/categories/ConfirmModal";
import { CategoryFormModal } from "../components/categories/CategoryFormModal";
import { StatCards } from "../components/categories/StatCards";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { useCategories } from "../hooks/useCategories";
import type { Categoria } from "../types/category";

interface ModalState {
  type: "none" | "create" | "edit" | "activate" | "deactivate" | "forceDeactivate" | "delete";
  category?: Categoria;
  message?: string;
}

export function InventoryCategoriesPage({ embedded = false }: { embedded?: boolean }) {
  const {
    categories,
    associations,
    loading,
    saving,
    error,
    fieldError,
    stats,
    load,
    create,
    update,
    activate,
    deactivate,
    remove,
    clearFieldError,
  } = useCategories();

  const [modal, setModal] = useState<ModalState>({ type: "none" });

  useEffect(() => {
    void load();
  }, [load]);

  const sortedCategories = useMemo(
    () => [...categories].sort((a, b) => Number(b.activa) - Number(a.activa) || a.nombre.localeCompare(b.nombre)),
    [categories],
  );

  function closeModal() {
    clearFieldError();
    setModal({ type: "none" });
  }

  async function handleSubmitForm(name: string, description: string) {
    if (modal.type === "create") {
      const ok = await create(name, description);
      if (ok) {
        closeModal();
      }
      return;
    }

    if (modal.type === "edit" && modal.category) {
      const ok = await update(modal.category.uuid, name, description);
      if (ok) {
        closeModal();
      }
    }
  }

  async function handleDeactivate(force: boolean) {
    if (!modal.category) {
      return;
    }

    const result = await deactivate(modal.category.uuid, force);
    if (result.ok) {
      closeModal();
      return;
    }

    if (result.forceRequired) {
      setModal({
        type: "forceDeactivate",
        category: modal.category,
        message:
          result.message ??
          "La categoría tiene implementos activos asociados. ¿Deseas forzar la desactivación?",
      });
    }
  }

  async function handleDelete() {
    if (!modal.category) {
      return;
    }

    const ok = await remove(modal.category.uuid);
    if (ok) {
      closeModal();
    }
  }

  async function handleActivate() {
    if (!modal.category) {
      return;
    }

    const ok = await activate(modal.category.uuid);
    if (ok) {
      closeModal();
    }
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Gestión de Inventario</h1>
          <p>Administra categorías de implementos para organizar el catálogo por tipo de material o uso.</p>
        </div>

        <div className="content-header__actions">
          <button type="button" className="button button--ghost" onClick={() => void load()}>
            <RefreshCcw size={16} />
            Refrescar
          </button>
          <button type="button" className="button" onClick={() => setModal({ type: "create" })}>
            <Plus size={16} />
            Nueva categoría
          </button>
        </div>
      </section>

      <StatCards
        total={stats.total}
        active={stats.active}
        inactive={stats.inactive}
        implementCount={stats.implementCount}
      />

      <section className="panel">
        <div className="panel__head">
          <div>
            <h2>Categorías</h2>
            <p>Vista de gestión para crear, editar, desactivar y eliminar categorías.</p>
          </div>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        <CategoryTable
          categories={sortedCategories}
          associations={associations}
          loading={loading}
          onEdit={(category) => setModal({ type: "edit", category })}
          onActivate={(category) => setModal({ type: "activate", category })}
          onDeactivate={(category) => setModal({ type: "deactivate", category })}
          onDelete={(category) => setModal({ type: "delete", category })}
        />
      </section>

      <CategoryFormModal
        mode={modal.type === "edit" ? "edit" : "create"}
        category={modal.category}
        isOpen={modal.type === "create" || modal.type === "edit"}
        saving={saving}
        fieldError={fieldError}
        onClose={closeModal}
        onSubmit={handleSubmitForm}
      />

      <ConfirmModal
        isOpen={modal.type === "activate"}
        title="Activar categorÃ­a"
        message="La categorÃ­a volverÃ¡ a estar disponible para nuevas asignaciones de implementos."
        confirmLabel="Activar"
        loading={saving}
        onClose={closeModal}
        onConfirm={handleActivate}
      />

      <ConfirmModal
        isOpen={modal.type === "deactivate"}
        title="Desactivar categoría"
        message="La categoría quedará inactiva para nuevas asignaciones de implementos."
        confirmLabel="Desactivar"
        tone="warn"
        loading={saving}
        onClose={closeModal}
        onConfirm={async () => handleDeactivate(false)}
      />

      <ConfirmModal
        isOpen={modal.type === "forceDeactivate"}
        title="Categoría con implementos activos"
        message={
          modal.message ??
          "Existen implementos activos vinculados. Si confirmas, la categoría quedará inactiva igualmente."
        }
        confirmLabel="Forzar desactivación"
        tone="warn"
        loading={saving}
        onClose={closeModal}
        onConfirm={async () => handleDeactivate(true)}
      />

      <ConfirmModal
        isOpen={modal.type === "delete"}
        title="Eliminar categoría"
        message="Esta acción elimina la categoría de forma permanente."
        confirmLabel="Eliminar"
        tone="danger"
        loading={saving}
        onClose={closeModal}
        onConfirm={handleDelete}
      />
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="categories">{content}</InventoryLayout>;
}

