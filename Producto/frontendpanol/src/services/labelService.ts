import { apiClient } from "./apiClient";

export type LabelScope = "GENERAL" | "INDIVIDUAL";

export async function fetchLabelsPdfBlob(
  implementUuid: string,
  scope: LabelScope,
  quantity = 1,
  individualUuid?: string,
): Promise<Blob> {
  const response = await apiClient.get(`/api/v2/implements/${implementUuid}/labels/pdf`, {
    params: {
      quantity,
      scope,
      ...(individualUuid != null ? { individual_uuid: individualUuid } : {}),
    },
    responseType: "blob",
  });
  return response.data as Blob;
}
