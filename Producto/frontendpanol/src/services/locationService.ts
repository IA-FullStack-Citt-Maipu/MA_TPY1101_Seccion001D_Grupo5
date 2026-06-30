import { apiClient } from "./apiClient";
import type { LocationAssociationSummary, LocationOption } from "../types/location";

export async function fetchLocations(): Promise<LocationOption[]> {
  const response = await apiClient.get<LocationOption[]>("/api/v2/locations");
  return response.data;
}

export async function fetchLocationsForManagement(): Promise<LocationOption[]> {
  const response = await apiClient.get<LocationOption[]>("/api/v2/locations/management");
  return response.data;
}

export async function createLocation(payload: { name: string; description: string | null }): Promise<LocationOption> {
  const response = await apiClient.post<LocationOption>("/api/v2/locations", payload);
  return response.data;
}

export async function updateLocation(
  locationUuid: string,
  payload: { name: string; description: string | null },
): Promise<LocationOption> {
  const response = await apiClient.put<LocationOption>(`/api/v2/locations/${locationUuid}`, payload);
  return response.data;
}

export async function setLocationActive(locationUuid: string, active: boolean): Promise<LocationOption> {
  const response = await apiClient.patch<LocationOption>(`/api/v2/locations/${locationUuid}/active`, null, { params: { active } });
  return response.data;
}

export async function fetchLocationAssociation(locationUuid: string): Promise<LocationAssociationSummary> {
  const response = await apiClient.get<{
    location_uuid?: string;
    association_count?: number;
    implement_count?: number;
    individual_count?: number;
    can_delete: boolean;
  }>(`/api/v2/locations/${locationUuid}/associations`);

  return {
    locationUuid: response.data.location_uuid ?? locationUuid,
    associationCount: response.data.association_count ?? 0,
    implementCount: response.data.implement_count ?? 0,
    individualCount: response.data.individual_count ?? 0,
    canDelete: response.data.can_delete,
  };
}

export async function deleteLocation(locationUuid: string): Promise<void> {
  await apiClient.delete(`/api/v2/locations/${locationUuid}`);
}
