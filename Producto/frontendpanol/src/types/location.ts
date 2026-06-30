export interface LocationOption {
  uuid: string;
  name: string;
  description?: string | null;
  active?: boolean;
}

export interface LocationAssociationSummary {
  locationUuid: string;
  associationCount: number;
  implementCount: number;
  individualCount: number;
  canDelete: boolean;
}
