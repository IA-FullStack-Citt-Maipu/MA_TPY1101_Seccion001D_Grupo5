import { apiClient } from "./apiClient";
import type { Categoria, CategoriaAssociationSummary, CategoriaPayload } from "../types/category";
const basePathV2 = "/api/v2/categories";

export async function fetchCategoriasGestion(): Promise<Categoria[]> {
  const response = await apiClient.get<Categoria[]>(`${basePathV2}/gestion`);
  return response.data;
}

export async function fetchCategoriaAssociation(
  categoryUuid: string,
): Promise<CategoriaAssociationSummary> {
  const response = await apiClient.get<{
    categoryUuid?: string;
    implementCount?: number;
    canDelete: boolean;
  }>(
    `${basePathV2}/${categoryUuid}/associations`,
  );

  return {
    categoryUuid: response.data.categoryUuid ?? categoryUuid,
    implementCount: response.data.implementCount ?? 0,
    canDelete: response.data.canDelete,
  };
}

export async function createCategoria(payload: CategoriaPayload): Promise<Categoria> {
  const response = await apiClient.post<Categoria>(basePathV2, payload);
  return response.data;
}

export async function updateCategoria(
  categoryUuid: string,
  payload: CategoriaPayload,
): Promise<Categoria> {
  const response = await apiClient.put<Categoria>(`${basePathV2}/${categoryUuid}`, payload);
  return response.data;
}

export async function activateCategoria(categoryUuid: string): Promise<Categoria> {
  const response = await apiClient.patch<Categoria>(`${basePathV2}/${categoryUuid}/activate`);
  return response.data;
}

export async function deactivateCategoria(
  categoryUuid: string,
  force: boolean,
): Promise<Categoria> {
  const response = await apiClient.patch<Categoria>(
    `${basePathV2}/${categoryUuid}/deactivate?force=${force}`,
  );
  return response.data;
}

export async function deleteCategoria(categoryUuid: string): Promise<void> {
  await apiClient.delete(`${basePathV2}/${categoryUuid}`);
}

