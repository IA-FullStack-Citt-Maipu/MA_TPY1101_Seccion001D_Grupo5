# 00 - Matriz Canonica Vigente

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: controllers V2, `SecurityConfig`, `application.yaml`, compose files, `ArchitectureTest`, `Producto/databasepanol/migrations/v25/V25..V52`,
  `docs/architecture/data-management/03-postgresql-guia-tecnica.md`

## Rutas publicas vigentes

- Base: `/api/v2/**`
- Auth: `/api/v2/auth/*`
- Users: `/api/v2/users/*`
- Categories: `/api/v2/categories/*`
- Locations: `/api/v2/locations/*`
- Implements/Stock: `/api/v2/implements/*`
- Loans: `/api/v2/loans/*`

## Seguridad vigente

- `permitAll`: `/actuator/health`, `/actuator/info`, `POST /api/v2/auth/login`, `POST /api/v2/auth/logout`, `POST /api/v2/auth/refresh`.
- `denyAll`: `/internal/**`, `/api/v1/**`.
- Todo lo demas requiere autenticacion.
- `APP_SECURITY_ENABLED=true` por defecto.

## Flujo de datos vigente

1. SQL como estado canonico.
2. Identidad de datos: `id` interno en DB/jOOQ, `uuid` externo en API/frontend.
3. Evento outbox en misma transaccion cuando aplica.
4. Worker publica eventos al destino de integración/observabilidad.
5. Reintentos y estado en `public.outbox_event` con estados canónicos:
   `PENDING`, `PROCESSING`, `SENT`, `FAILED` (compatibilidad: vista `outbox_events`).
6. `loan`: los prestamos nuevos nacen reservados (`approved`), pasan por `prepared`, `delivered` y `completed`, con `pending` relegado a compatibilidad legacy.
7. La disponibilidad de `consumable` reservado se bloquea globalmente mientras el prestamo siga en `approved` o `prepared`; `reusable` e `individual` siguen evaluandose por traslape de ventana.

## Compose vigente

- `Producto/databasepanol/docker-compose.yaml`: PostgreSQL local desacoplado.
- `Producto/docker-compose.yaml`: frontend + backend (sin postgres local).
- `Producto/backendpanol/docker-compose.yaml`: backend only.

## Semantica documental

- `Estado del documento: vigente` => guia operativa actual.
- `Estado del documento: historico` => contexto pasado, no fuente operativa primaria.
- Todo documento debe incluir:
  - `Ultima verificacion`
  - `Fuente de verdad`
  - `Estado del documento`
