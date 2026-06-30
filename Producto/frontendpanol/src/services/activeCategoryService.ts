import { apiClient } from "./apiClient";
import type { ActiveCategoryOption } from "../types/categoryActive";

async function fetchFrom(path: string): Promise<ActiveCategoryOption[]> {
  const response = await apiClient.get<ActiveCategoryOption[]>(path);
  return response.data;
}

export async function fetchActiveCategories(): Promise<ActiveCategoryOption[]> {
  return await fetchFrom("/api/v2/categories/active");
}

