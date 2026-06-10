# Frontend Docs

- Estado del documento: vigente
- Ultima verificacion: 2026-06-10
- Fuente de verdad: `src/pages/*`, `src/services/*`, controllers backend V2

## Objetivo

Frontend para gestion operativa de inventario consumiendo API v2 del backend.

## Arquitectura

- `pages/InventoryCategoriesPage.tsx`
- `pages/InventoryImplementCreatePage.tsx`
- `pages/LoanCalendarPage.tsx`
- `pages/SettingsPage.tsx`
- `services/authService.ts`
- `services/profileService.ts`
- `services/categoryService.ts`
- `services/apiClient.ts`
- `components/layout/*`
- `components/categories/*`
- `utils/auth.ts`
- `utils/theme.ts`
- `types/*`

## Flujos vigentes relevantes

### CRUD de categorias

1. `GET /api/v2/categories/gestion`
2. `GET /api/v2/categories/{categoryUuid}/associations`
3. `POST /api/v2/categories`
4. `PUT /api/v2/categories/{categoryUuid}`
5. `PATCH /api/v2/categories/{categoryUuid}/deactivate?force=false|true`
6. `DELETE /api/v2/categories/{categoryUuid}`

### Configuracion de usuario

1. `GET /api/v2/auth/me`
2. `PATCH /api/v2/auth/me/email`
3. `PATCH /api/v2/auth/me/password`
4. Preferencia visual persistida en `localStorage` mediante `utils/theme.ts`

### Implementos

- Creacion y edicion usan la misma vista `InventoryImplementCreatePage.tsx`.
- La edicion ya no usa modal: se carga por ruta dedicada con los datos del
  implemento precargados.

### Agenda de prestamos

- `LoanCalendarPage.tsx` muestra calendario mensual y detalle por dia.
- El detalle de una solicitud se abre en modal sobre la agenda, sin navegar
  a otra vista.

## Errores

El frontend consume payload uniforme:

```json
{
  "code": "...",
  "message": "...",
  "timestamp": "..."
}
```

## Variables de entorno

- `VITE_API_BASE_URL`
  - local: `http://localhost:18080`
  - dev desplegado: `https://api.dev.panol.cl`

## Despliegue local recomendado

Desde `Producto/`:
```bash
docker compose up --build
```

Este compose levanta frontend + backend.
