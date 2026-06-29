import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Plus, RefreshCcw } from "lucide-react";
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

const PAGE_SIZE = 10;

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
  const [page, setPage] = useState(1);

  useEffect(() => {
    void load();
  }, [load]);

  const sortedCategories = useMemo(
    () => [...categories].sort((a, b) => Number(b.activa) - Number(a.activa) || a.nombre.localeCompare(b.nombre)),
    [categories],
  );

  const totalPages = useMemo(() => Math.max(1, Math.ceil(sortedCategories.length / PAGE_SIZE)), [sortedCategories.length]);
  const safePage = useMemo(() => {
    if (page < 1) {
      return 1;
    }
    if (page > totalPages) {
      return totalPages;
    }
    return page;
  }, [page, totalPages]);
  const pageStart = (safePage - 1) * PAGE_SIZE;
  const pagedCategories = sortedCategories.slice(pageStart, pageStart + PAGE_SIZE);
  const rangeStart = sortedCategories.length === 0 ? 0 : pageStart + 1;
  const rangeEnd = sortedCategories.length === 0 ? 0 : Math.min(pageStart + pagedCategories.length, sortedCategories.length);
  const pageNumbers = useMemo(() => {
    const windowSize = 5;
    let start = Math.max(1, safePage - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [safePage, totalPages]);

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
          "La categoria tiene implementos activos asociados. ¿Deseas forzar la desactivacion?",
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
          <h1>Gestion de inventario</h1>
          <p>Administra categorias de implementos para organizar el catalogo por tipo de material o uso.</p>
        </div>

        <div className="content-header__actions content-header__actions--mobile-visible">
          <button type="button" className="button button--ghost" onClick={() => void load()}>
            <RefreshCcw size={16} />
            Refrescar
          </button>
          <button type="button" className="button" onClick={() => setModal({ type: "create" })}>
            <Plus size={16} />
            Nueva categoria
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
            <h2>Categorias</h2>
            <p>Vista de gestion para crear, editar, desactivar y eliminar categorias.</p>
          </div>
        </div>

        {error ? <div className="error-banner">{error}</div> : null}

        <CategoryTable
          categories={pagedCategories}
          associations={associations}
          loading={loading}
          onEdit={(category) => setModal({ type: "edit", category })}
          onActivate={(category) => setModal({ type: "activate", category })}
          onDeactivate={(category) => setModal({ type: "deactivate", category })}
          onDelete={(category) => setModal({ type: "delete", category })}
        />

        {!loading && sortedCategories.length > 0 ? (
          <div className="inventory-table-footer">
            <p>
              Mostrando {rangeStart} a {rangeEnd} de {sortedCategories.length} categorias
            </p>
            <div className="inventory-pagination">
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                disabled={safePage <= 1}
              >
                <ChevronLeft size={16} />
              </button>
              {pageNumbers.map((pageNumber) => (
                <button
                  key={pageNumber}
                  type="button"
                  className={pageNumber === safePage ? "inventory-pagination__btn inventory-pagination__btn--active" : "inventory-pagination__btn"}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </button>
              ))}
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                disabled={safePage >= totalPages}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        ) : null}
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
        title="Activar categoria"
        message="La categoria volvera a estar disponible para nuevas asignaciones de implementos."
        confirmLabel="Activar"
        loading={saving}
        onClose={closeModal}
        onConfirm={handleActivate}
      />

      <ConfirmModal
        isOpen={modal.type === "deactivate"}
        title="Desactivar categoria"
        message="La categoria quedara inactiva para nuevas asignaciones de implementos."
        confirmLabel="Desactivar"
        tone="warn"
        loading={saving}
        onClose={closeModal}
        onConfirm={async () => handleDeactivate(false)}
      />

      <ConfirmModal
        isOpen={modal.type === "forceDeactivate"}
        title="Categoria con implementos activos"
        message={
          modal.message ??
          "Existen implementos activos vinculados. Si confirmas, la categoria quedara inactiva igualmente."
        }
        confirmLabel="Forzar desactivacion"
        tone="warn"
        loading={saving}
        onClose={closeModal}
        onConfirm={async () => handleDeactivate(true)}
      />

      <ConfirmModal
        isOpen={modal.type === "delete"}
        title="Eliminar categoria"
        message="Esta accion elimina la categoria de forma permanente."
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
