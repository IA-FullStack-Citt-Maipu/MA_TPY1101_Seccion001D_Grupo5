import { localDB } from "./localDB";
import type { Categoria } from "../types/category";
import type { ActiveCategoryOption } from "../types/categoryActive";

const CATEGORIES_COLLECTION = "categories";

export async function fetchActiveCategories(): Promise<ActiveCategoryOption[]> {
  const categories = (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
  
  return categories
    .filter((c) => c.activa)
    .map((c) => ({
      uuid: c.uuid,
      name: c.nombre,
    }));
}
