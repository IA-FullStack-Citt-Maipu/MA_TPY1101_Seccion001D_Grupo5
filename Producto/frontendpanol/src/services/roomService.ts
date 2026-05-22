import { apiClient } from "./apiClient";
import type { RoomOption } from "../types/room";

export async function fetchRooms(): Promise<RoomOption[]> {
  const response = await apiClient.get<RoomOption[]>("/api/v2/rooms");
  return response.data;
}
