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

### Asistente IA

- Widget disponible para roles `COORDINADOR` y `DIRECTOR`.
- El frontend consume `POST /api/v1/chat` del microservicio `bot-panol`.
- Antes de llamar al bot, el frontend solicita `POST /api/v2/auth/me/bot-token`
  al backend autenticado por cookies.
- Ese token puente vive solo en memoria y se envia como
  `Authorization: Bearer <token>` al bot.
- La base del bot se configura con `VITE_BOT_API_BASE_URL`.
- Si `VITE_BOT_API_BASE_URL` no existe, el cliente usa `VITE_API_BASE_URL` como fallback.
- La respuesta del bot mantiene `response` como texto Markdown y puede incluir
  `ui_blocks` opcional para renderizar listas compactas y metricas del asistente.
- El frontend no debe mostrar UUIDs por defecto en el chat; si el backend
  entrega un identificador tecnico permitido, debe presentarlo solo como dato
  tecnico explicito y no como parte visual principal de las tarjetas.

### CRUD de categorias

1. `GET /api/v2/categories/gestion`
2. `GET /api/v2/categories/{categoryUuid}/associations`
3. `POST /api/v2/categories`
4. `PUT /api/v2/categories/{categoryUuid}`
5. `PATCH /api/v2/categories/{categoryUuid}/deactivate?force=false|true`
6. `DELETE /api/v2/categories/{categoryUuid}`

### Configuracion de usuario

1. `GET /api/v2/auth/me`
2. `POST /api/v2/auth/refresh`
3. `POST /api/v2/auth/logout`
4. `GET /api/v2/auth/me/sessions`
5. `DELETE /api/v2/auth/me/sessions/{sessionId}`
6. `PATCH /api/v2/auth/me/email`
7. `PATCH /api/v2/auth/me/password`
8. Preferencia visual persistida en `localStorage` mediante `utils/theme.ts`

- `SettingsPage.tsx` muestra una tarjeta full-width de sesiones activas.
- La identificacion de dispositivo (`PC`, `Celular`, `Tablet`) se resuelve en
  frontend con heuristica liviana sobre `userAgent`.
- Si el usuario cierra su sesion actual desde Configuracion, el frontend limpia
  `auth_user` y redirige a `#/login`.

### Sesion web

- El frontend ya no guarda JWT en `localStorage` ni `sessionStorage`.
- El backend setea `panol_access_token` y `panol_refresh_token` como cookies
  HTTP-only.
- `utils/auth.ts` conserva solo `auth_user` como snapshot no sensible.
- `services/apiClient.ts` usa `withCredentials: true` y hace refresh silencioso
  ante `401`.
- El token puente del bot no se persiste; si expira, el frontend pide uno nuevo
  al backend y reintenta la llamada al asistente una vez.

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
- `VITE_BOT_API_BASE_URL`
  - opcional
  - usar cuando `bot-panol` no comparte la misma base publica que `VITE_API_BASE_URL`
  - si se omite, el frontend usa `VITE_API_BASE_URL`
- En local, mantener frontend y backend en el mismo host visible (`localhost`
  o `127.0.0.1`) para no romper envio de cookies.

## Despliegue local recomendado

Desde `Producto/`:
```bash
docker compose up --build
```

Este compose levanta `frontend`, `backend` y `bot-panol`.

### Requisitos para chat IA operativo

- `VITE_BOT_API_BASE_URL` debe apuntar a la base publica del bot.
- En local, el compose lo publica por defecto en `http://localhost:18082`.
- Las credenciales runtime del bot se leen desde `Producto/bot-panol/.env` y pueden
  complementarse desde `Producto/.env`.
- `bot-panol` requiere `GOOGLE_API_KEY` valida para responder consultas reales.
- Si `GOOGLE_API_KEY` falta o es invalida, el widget puede abrir y enviar mensajes,
  pero el backend del bot respondera `503` y el chat no se considera operativo.
