export interface Categoria {
  uuid: string;
  nombre: string;
  descripcion: string | null;
  activa: boolean;
  createdAt: string;
}

export interface CategoriaAssociationSummary {
  categoryUuid: string;
  implementCount: number;
  canDelete: boolean;
}

export interface CategoriaPayload {
  nombre: string;
  descripcion: string | null;
}

export interface DeactivateConflict {
  code: string;
  message: string;
}

