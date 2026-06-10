# Backend Docs

- Estado del documento: vigente
- Ultima verificacion: 2026-06-10
- Fuente de verdad: controllers V2, SecurityConfig, application.yaml

## Alcance

Guia operativa del backend para rutas publicas, seguridad, errores y convenciones
de uso entre frontend y backend.

### Modelo de datos vigente

- Estado canonico de dominio: **PostgreSQL (Supabase en produccion/desarrollo segun APP_DB_ENV)**.
- El catalogo de implementos usa `item_type` con valores:
  - `fungible`
  - `no_fungible`
- El flujo de integracion asincrona usa tabla `public.outbox_event`
  (estados `PENDING`, `PROCESSING`, `SENT`, `FAILED`).

### Regla de identidad de datos

- Persistencia interna (DB/jOOQ): `id` numerico.
- Contrato externo (API/frontend/logs de cliente): `uuid`.
- No se exponen ids internos en payloads publicos.

## API publica actual

Base publica: `/api/v2/**`

### Auth
- `POST /api/v2/auth/login`
- `POST /api/v2/auth/logout`
- `GET /api/v2/auth/me`
- `PATCH /api/v2/auth/me/email`
- `PATCH /api/v2/auth/me/password`

### Users
- `GET /api/v2/users`
- `POST /api/v2/users`
- `PUT /api/v2/users/{userUuid}`
- `PUT /api/v2/users/{userUuid}/role`
- `PATCH /api/v2/users/{userUuid}/active`
- `DELETE /api/v2/users/{userUuid}`

### Categories
- `GET /api/v2/categories/active`
- `GET /api/v2/categories/gestion`
- `GET /api/v2/categories/{categoryUuid}/associations`
- `POST /api/v2/categories`
- `PUT /api/v2/categories/{categoryUuid}`
- `PATCH /api/v2/categories/{categoryUuid}/deactivate`
- `PATCH /api/v2/categories/{categoryUuid}/activate`
- `DELETE /api/v2/categories/{categoryUuid}`

### Loans
- `GET /api/v2/loans`
- `POST /api/v2/loans`
- `PATCH /api/v2/loans/{loanUuid}/review`
- `POST /api/v2/loans/{loanUuid}/delivery`
- `POST /api/v2/loans/{loanUuid}/return`

### Locations
- `GET /api/v2/locations`
- `GET /api/v2/locations/management`
- `POST /api/v2/locations`
- `PUT /api/v2/locations/{locationUuid}`
- `PATCH /api/v2/locations/{locationUuid}/active`

### Implements
- `GET /api/v2/implements`
- `GET /api/v2/implements/{implementUuid}`
- `POST /api/v2/implements`
- `PUT /api/v2/implements/{implementUuid}`
- `PATCH /api/v2/implements/{implementUuid}/active`

### Stock y movimientos
- `GET /api/v2/implements/movements`
- `POST /api/v2/implements/{implementUuid}/movements`
- `GET /api/v2/implements/{implementUuid}/stock`
- `POST /api/v2/implements/{implementUuid}/stock/entries`
- `POST /api/v2/implements/{implementUuid}/stock/movements`
- `PUT /api/v2/implements/{implementUuid}/stock/individuals/{individualUuid}`
- `GET /api/v2/implements/{implementUuid}/labels/pdf`

Valores canonicos de `movement_type`/`action`:
- `STOCK_IN`
- `STOCK_OUT`
- `LOAN_DELIVERY`
- `LOAN_RETURN`
- `DAMAGE_REPORT`
- `MANUAL_ADJUSTMENT`

## Seguridad vigente

- `permitAll`: solo `POST /api/v2/auth/login` + `/actuator/health` y `/actuator/info`.
- Rutas bloqueadas: `/api/v1/**` y `/internal/**`.
- Resto de rutas: autenticadas.
- `GET /api/v2/auth/me` y sus `PATCH` son parte del contrato requerido por
  la vista de configuracion del frontend.

## Formato de error publico

```json
{
  "code": "CATEGORY_NAME_DUPLICATE",
  "message": "Ya existe una categoria con el nombre 'Reactivos'",
  "timestamp": "2026-05-15T15:00:00Z"
}
```

## Nota de compatibilidad

No se deben usar rutas legacy (`/api/categorias`, `/api/implements`, `/api/v1/**`)
en clientes nuevos ni en documentacion operativa vigente.
