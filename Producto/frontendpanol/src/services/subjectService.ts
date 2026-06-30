import { apiClient } from "./apiClient";
import type { SubjectOption } from "../types/subject";

export async function fetchSubjects(): Promise<SubjectOption[]> {
  const response = await apiClient.get<SubjectOption[]>("/api/v2/subjects");
  return response.data;
}
