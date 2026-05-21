import { useCallback, useMemo, useState } from "react";
import {
  createCategoria,
  activateCategoria,
  deactivateCategoria,
  deleteCategoria,
  fetchCategoriaAssociation,
  fetchCategoriasGestion,
  updateCategoria,
} from "../services/categoryServiceLocal";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import type { Categoria, CategoriaAssociationSummary } from "../types/category";

interface CategoryHookState {
  categories: Categoria[];
  associations: Record<string, CategoriaAssociationSummary>;
  loading: boolean;
  saving: boolean;
  error: string | null;
  fieldError: string | null;
}

export function useCategories() {
  const [state, setState] = useState<CategoryHookState>({
    categories: [],
    associations: {},
    loading: true,
    saving: false,
    error: null,
    fieldError: null,
  });

  const load = useCallback(async () => {
    setState((prev) => ({ ...prev, loading: true, error: null }));

    try {
      const categories = await fetchCategoriasGestion();
      const associationEntries = await Promise.all(
        categories.map(async (category) => {
          const summary = await fetchCategoriaAssociation(category.uuid);
          return [category.uuid, summary] as const;
        }),
      );

      const associationMap = Object.fromEntries(associationEntries);

      setState((prev) => ({
        ...prev,
        categories,
        associations: associationMap,
        loading: false,
      }));
    } catch (error) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: getErrorMessage(error, "No se pudo cargar las categorías."),
      }));
    }
  }, []);

  const create = useCallback(async (nombre: string, descripcion: string) => {
    setState((prev) => ({ ...prev, saving: true, fieldError: null, error: null }));

    try {
      await createCategoria({ nombre, descripcion: descripcion.trim() || null });
      await load();
    } catch (error) {
      const apiError = getApiErrorPayload(error);
      if (apiError?.code === "CATEGORY_NAME_DUPLICATE") {
        setState((prev) => ({ ...prev, fieldError: apiError.message, saving: false }));
        return false;
      }

      setState((prev) => ({
        ...prev,
        saving: false,
        error: getErrorMessage(error, "No se pudo crear la categoría."),
      }));
      return false;
    }

    setState((prev) => ({ ...prev, saving: false }));
    return true;
  }, [load]);

  const update = useCallback(async (categoryUuid: string, nombre: string, descripcion: string) => {
    setState((prev) => ({ ...prev, saving: true, fieldError: null, error: null }));

    try {
      await updateCategoria(categoryUuid, { nombre, descripcion: descripcion.trim() || null });
      await load();
    } catch (error) {
      const apiError = getApiErrorPayload(error);
      if (apiError?.code === "CATEGORY_NAME_DUPLICATE") {
        setState((prev) => ({ ...prev, fieldError: apiError.message, saving: false }));
        return false;
      }

      setState((prev) => ({
        ...prev,
        saving: false,
        error: getErrorMessage(error, "No se pudo actualizar la categoría."),
      }));
      return false;
    }

    setState((prev) => ({ ...prev, saving: false }));
    return true;
  }, [load]);

  const activate = useCallback(async (categoryUuid: string) => {
    setState((prev) => ({ ...prev, saving: true, error: null }));

    try {
      await activateCategoria(categoryUuid);
      await load();
      setState((prev) => ({ ...prev, saving: false }));
      return true;
    } catch (error) {
      setState((prev) => ({
        ...prev,
        saving: false,
        error: getErrorMessage(error, "No se pudo activar la categorÃ­a."),
      }));
      return false;
    }
  }, [load]);

  const deactivate = useCallback(async (categoryUuid: string, force = false) => {
    setState((prev) => ({ ...prev, saving: true, error: null }));

    try {
      await deactivateCategoria(categoryUuid);
      await load();
      setState((prev) => ({ ...prev, saving: false }));
      return { ok: true, forceRequired: false, message: null };
    } catch (error) {
      const apiError = getApiErrorPayload(error);

      if (!force && apiError?.code === "CATEGORY_HAS_ACTIVE_IMPLEMENTS") {
        setState((prev) => ({ ...prev, saving: false }));
        return { ok: false, forceRequired: true, message: apiError.message };
      }

      setState((prev) => ({
        ...prev,
        saving: false,
        error: getErrorMessage(error, "No se pudo desactivar la categoría."),
      }));
      return { ok: false, forceRequired: false, message: null };
    }
  }, [load]);

  const remove = useCallback(async (categoryUuid: string) => {
    setState((prev) => ({ ...prev, saving: true, error: null }));

    try {
      await deleteCategoria(categoryUuid);
      await load();
      setState((prev) => ({ ...prev, saving: false }));
      return true;
    } catch (error) {
      setState((prev) => ({
        ...prev,
        saving: false,
        error: getErrorMessage(error, "No se pudo eliminar la categoría."),
      }));
      return false;
    }
  }, [load]);

  const stats = useMemo(() => {
    const total = state.categories.length;
    const active = state.categories.filter((category) => category.activa).length;
    const inactive = total - active;

    const implementCount = Object.values(state.associations).reduce(
      (acc, summary) => acc + summary.implementCount,
      0,
    );

    return {
      total,
      active,
      inactive,
      implementCount,
    };
  }, [state.associations, state.categories]);

  const clearFieldError = useCallback(() => {
    setState((prev) => ({ ...prev, fieldError: null }));
  }, []);

  return {
    ...state,
    stats,
    load,
    create,
    update,
    activate,
    deactivate,
    remove,
    clearFieldError,
  };
}

