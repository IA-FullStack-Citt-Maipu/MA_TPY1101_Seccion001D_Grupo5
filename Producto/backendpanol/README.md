# Backend Panol

- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: controllers V2, SecurityConfig, application.yaml, ArchitectureTest
- Arquitectura de datos: PostgreSQL (Supabase) como estado transaccional canónico.

## Resumen

Backend del proyecto Panol Salud, implementado como monolito modular con arquitectura hexagonal por modulo y contratos/eventos para integracion interna.

## API publica vigente

Base publica: `/api/v2/**`

Modulos y rutas:
- Auth: `/api/v2/auth`
- Usuarios: `/api/v2/users`
- Categorias: `/api/v2/categories`
- Ubicaciones: `/api/v2/locations`
- Implementos: `/api/v2/implements`
- Stock y movimientos: `/api/v2/implements/{implementUuid}/stock`, `/api/v2/implements/movements`, `/api/v2/implements/{implementUuid}/labels/pdf`

No existen controladores legacy publicos en runtime (`/api/categorias`, `/api/implements`, `/api/v1/**`).

## Seguridad

- Seguridad habilitada por defecto: `APP_SECURITY_ENABLED=true`.
- `permitAll` solo para `POST /api/v2/auth/login` y endpoints de salud de actuator.
- `/api/v1/**` y `/internal/**` estan denegados.

## Entornos de base de datos

Selector:
- `APP_DB_ENV=docker` usa `DB_DOCKER_*`
- `APP_DB_ENV=supabase` usa `DB_SUPABASE_*`

## Docker Compose

- `Producto/docker-compose.yaml`: stack frontend + backend (sin postgres local).
- `Producto/backendpanol/docker-compose.yaml`: backend only.

## Migraciones y jOOQ

- Migraciones SQL en `src/main/resources/db/migration`.
- Outbox base en `V25__schema_alignment_big_bang.sql` (tabla actual `outbox_event`
  y vista de compatibilidad `outbox_events`).
- Estados canónicos de outbox: `PENDING`, `PROCESSING`, `SENT`, `FAILED`.
- Codegen jOOQ con `scripts/generate-jooq.ps1` o `./mvnw generate-sources`.

## Documentacion relacionada

- `ARCHITECTURE.md`
- `docs/BACKEND.md`
- `docs/ENVIRONMENT.md`
- `docs/DEPLOYMENT.md`
- `docs/architecture/00-overview.md`
- `docs/architecture/00-matriz-canonica-vigente.md`
