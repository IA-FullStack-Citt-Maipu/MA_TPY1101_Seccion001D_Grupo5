# Backend Panol

- Estado del documento: vigente
- Ultima verificacion: 2026-06-07
- Fuente de verdad: controllers V2, SecurityConfig, application.yaml, ArchitectureTest, `Producto/databasepanol/migrations/v25/`
- Arquitectura de datos: PostgreSQL (Supabase en nube / PostgreSQL local en Docker / Cloud SQL en GCP) como estado transaccional can?nico.

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
- Prestamos: `/api/v2/loans`

No existen controladores legacy publicos en runtime (`/api/categorias`, `/api/implements`, `/api/v1/**`).

## Seguridad

- Seguridad habilitada por defecto: `APP_SECURITY_ENABLED=true`.
- `permitAll` solo para `POST /api/v2/auth/login` y endpoints de salud/info de actuator.
- `/api/v1/**` y `/internal/**` estan denegados.

## Entornos de base de datos

Selector:
- `APP_DB_ENV=docker` usa `DB_DOCKER_*`
- `APP_DB_ENV=supabase` usa `DB_SUPABASE_*`
- `APP_DB_ENV=cloudsql` usa `DB_CLOUDSQL_*`

## Docker Compose

- `Producto/databasepanol/docker-compose.yaml`: PostgreSQL local desacoplado.
- `Producto/docker-compose.yaml`: stack frontend + backend (sin postgres embebido).
- `Producto/backendpanol/docker-compose.yaml`: backend only.

Importante:

- no existen dos compose distintos del backend, uno para Supabase y otro para PostgreSQL local;
- los compose de aplicacion son los mismos y el backend decide a que BD conectarse segun `APP_DB_ENV`;
- `Producto/databasepanol/docker-compose.yaml` solo se usa cuando quieres levantar PostgreSQL local.

Escenarios:

- `APP_DB_ENV=supabase`: usa el compose de aplicacion y no levantes `databasepanol`.
- `APP_DB_ENV=docker`: levanta primero `databasepanol` y luego el compose de aplicacion.

## Migraciones y jOOQ

- Fuente de verdad SQL: `Producto/databasepanol/migrations/v25`.
- Flyway sigue cargando en runtime desde `classpath:db/migration/v25`.
- Outbox base en `V25__schema_alignment_big_bang.sql` (tabla actual `outbox_event` y vista de compatibilidad `outbox_events`).
- Estados can?nicos de outbox: `PENDING`, `PROCESSING`, `SENT`, `FAILED`.
- Codegen jOOQ con `scripts/generate-jooq.ps1` o `./mvnw generate-sources`.
- `JOOQ_DB_*` es independiente de `APP_DB_ENV`: el runtime puede usar PostgreSQL local o Cloud SQL y el build seguir introspectando otra base, segun como configures esas variables.

## Documentacion relacionada

- `ARCHITECTURE.md`
- `docs/BACKEND.md`
- `docs/ENVIRONMENT.md`
- `docs/DEPLOYMENT.md`
- `docs/architecture/00-overview.md`
- `docs/architecture/00-matriz-canonica-vigente.md`
- `../databasepanol/README.md`
