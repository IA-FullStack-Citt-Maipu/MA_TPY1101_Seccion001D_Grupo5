import { localDB } from "./localDB";
import type { Categoria, CategoriaAssociationSummary, CategoriaPayload } from "../types/category";

const CATEGORIES_COLLECTION = "categories";

interface RawCategory {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

// Mapear datos del JSON al formato esperado
function mapToCategoria(raw: RawCategory): Categoria {
  return {
    uuid: raw.id,
    nombre: raw.name,
    descripcion: raw.description || null,
    activa: true,
    createdAt: raw.createdAt,
  };
}



// Cargar categorías iniciales desde el JSON
async function initializeCategories() {
  const existing = await localDB.get<Categoria[]>(CATEGORIES_COLLECTION);
  if (!existing || existing.length === 0) {
    try {
      const data = await localDB.loadJSON<{ categories: RawCategory[] }>("/db/categories.json");
      const mapped = data.categories.map(mapToCategoria);
      await localDB.set(CATEGORIES_COLLECTION, mapped);
    } catch (error) {
      console.error("Error initializing categories:", error);
    }
  }
}

// Llamar inicialización al cargar el módulo
initializeCategories();

export async function fetchCategoriasGestion(): Promise<Categoria[]> {
  const categories = await localDB.get<Categoria[]>(CATEGORIES_COLLECTION);
  return categories || [];
}

export async function fetchCategoriaAssociation(
  categoryUuid: string,
): Promise<CategoriaAssociationSummary> {
  // En modo local, siempre podemos borrar
  return {
    categoryUuid,
    implementCount: 0,
    canDelete: true,
  };
}

export async function createCategoria(payload: CategoriaPayload): Promise<Categoria> {
  const newCategoria: Categoria = {
    uuid: `cat-${Date.now()}`,
    nombre: payload.nombre,
    descripcion: payload.descripcion,
    activa: true,
    createdAt: new Date().toISOString(),
  };

  await localDB.addItem(CATEGORIES_COLLECTION, newCategoria);
  return newCategoria;
}

export async function updateCategoria(
  categoryUuid: string,
  payload: CategoriaPayload,
): Promise<Categoria> {
  const categories = (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
  const categoria = categories.find((c) => c.uuid === categoryUuid);

  if (!categoria) {
    throw new Error(`Categoría con uuid ${categoryUuid} no encontrada`);
  }

  const updated: Categoria = {
    ...categoria,
    nombre: payload.nombre,
    descripcion: payload.descripcion,
  };
  await localDB.updateItem(CATEGORIES_COLLECTION, categoryUuid, updated);
  return updated;
}

export async function activateCategoria(categoryUuid: string): Promise<Categoria> {
  const categories = (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
  const categoria = categories.find((c) => c.uuid === categoryUuid);

  if (!categoria) {
    throw new Error(`Categoría con uuid ${categoryUuid} no encontrada`);
  }

  const updated = { ...categoria, activa: true };
  await localDB.updateItem(CATEGORIES_COLLECTION, categoryUuid, updated);
  return updated;
}

export async function deactivateCategoria(
  categoryUuid: string,
): Promise<Categoria> {
  const categories = (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
  const categoria = categories.find((c) => c.uuid === categoryUuid);

  if (!categoria) {
    throw new Error(`Categoría con uuid ${categoryUuid} no encontrada`);
  }

  const updated = { ...categoria, activa: false };
  await localDB.updateItem(CATEGORIES_COLLECTION, categoryUuid, updated);
  return updated;
}

export async function deleteCategoria(categoryUuid: string): Promise<void> {
  const categories = (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
  const filtered = categories.filter((c) => c.uuid !== categoryUuid);
  await localDB.set(CATEGORIES_COLLECTION, filtered);
}
