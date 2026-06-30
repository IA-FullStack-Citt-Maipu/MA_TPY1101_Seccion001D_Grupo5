# Entorno y Secrets del Backend

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `application.yaml`, `Producto/databasepanol/.env.example`, `.env`, `.env.local.example`, compose vigentes

## Selector de entorno de BD

- `APP_DB_ENV=docker` -> usa `DB_DOCKER_*`
- `APP_DB_ENV=supabase` -> usa `DB_SUPABASE_*`

Si no se define, Spring usa perfil `docker` por defecto.

## Seguridad

- `APP_SECURITY_ENABLED=true` es el default vigente.
- Si `APP_SECURITY_ENABLED=false`, se activa configuracion sin autenticacion (solo para casos de debug controlado).

## Archivos de entorno

En `Producto/backendpanol`:
- `.env` (base local del modulo)
- `.env.local` (local no versionado)
- `.env.local.example` (plantilla versionada)
- `secrets/application-secrets.properties` (secretos runtime)

En `Producto/databasepanol`:
- `.env` (local no versionado para PostgreSQL)
- `.env.example` (plantilla versionada)
- `secrets/README.md` (guia de secretos locales)

En `Producto`:
- `.env` (variables de compose cuando aplique)

## Variables clave

Comunes:
- `APP_PORT`
- `APP_DB_ENV`
- `APP_SECURITY_ENABLED`
- `APP_AUTH_MAX_FAILED_ATTEMPTS`
- `APP_AUTH_LOCK_MINUTES`
- `APP_AUTH_JWT_ISSUER`
- `APP_AUTH_JWT_EXPIRATION_SECONDS`
- `APP_AUTH_REFRESH_EXPIRATION_SECONDS`
- `APP_AUTH_REFRESH_TEMPORARY_EXPIRATION_SECONDS`
- `APP_AUTH_BOT_TOKEN_EXPIRATION_SECONDS`
- `APP_AUTH_BOT_TOKEN_AUDIENCE`
- `APP_AUTH_COOKIE_SECURE`
- `APP_AUTH_COOKIE_SAME_SITE`
- `APP_AUTH_JWT_SECRET`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE`
- `APP_EMAIL_ENABLED`
- `APP_EMAIL_PROVIDER`
- `APP_EMAIL_RESEND_API_KEY`
- `APP_EMAIL_FROM_NAME`
- `APP_EMAIL_FROM_ADDRESS`
- `APP_EMAIL_WORKER_DELAY_MS`
- `APP_EMAIL_BATCH_SIZE`
- `APP_EMAIL_MAX_RETRIES`
- `APP_EMAIL_CONNECT_TIMEOUT_MS`
- `APP_EMAIL_READ_TIMEOUT_MS`

Docker DB:
- `DB_DOCKER_HOST`
- `DB_DOCKER_PORT`
- `DB_DOCKER_NAME`
- `DB_DOCKER_USER`
- `DB_DOCKER_PASSWORD`
- `DB_DOCKER_SSL_MODE`

Supabase:
- `DB_SUPABASE_HOST`
- `DB_SUPABASE_PORT`
- `DB_SUPABASE_NAME`
- `DB_SUPABASE_USER`
- `DB_SUPABASE_PASSWORD`
- `DB_SUPABASE_SSL_MODE`

jOOQ (build-time):
- `JOOQ_DB_URL`
- `JOOQ_DB_USER`
- `JOOQ_DB_PASSWORD`

## Auth cookies y TTL

- `APP_AUTH_JWT_EXPIRATION_SECONDS`
  - TTL del access token JWT.
  - Default: `3600` segundos.
- `APP_AUTH_REFRESH_EXPIRATION_SECONDS`
  - TTL de la sesion refresh y del `Max-Age` persistente de la cookie refresh.
  - Default: `604800` segundos.
- `APP_AUTH_REFRESH_TEMPORARY_EXPIRATION_SECONDS`
  - TTL server-side de la sesion temporal cuando `rememberMe=false`.
  - La cookie sigue siendo de sesion del navegador, pero el backend ya no la
    renueva por 7 dias.
  - Default: `86400` segundos.
- `APP_AUTH_BOT_TOKEN_EXPIRATION_SECONDS`
  - TTL del token puente emitido para `bot-panol`.
  - Default: `300` segundos.
- `APP_AUTH_BOT_TOKEN_AUDIENCE`
  - Audience esperado por `bot-panol` para validar el token puente.
  - Default: `bot-panol`.
- `APP_AUTH_COOKIE_SECURE`
  - Si `true`, el navegador solo enviara las cookies por HTTPS.
  - En localhost HTTP normalmente debe ser `false`.
  - En entornos desplegados HTTPS debe ser `true`.
- `APP_AUTH_COOKIE_SAME_SITE`
  - Politica `SameSite` de las cookies de auth.
  - Default: `Lax`.
  - Valor vigente recomendado para el despliegue actual same-site.

## Cleanup de `token_revocation`

- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED`
  - habilita o deshabilita el worker de purge.
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS`
  - espera inicial despues del arranque.
  - Default: `300000` ms = 5 minutos.
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS`
  - intervalo entre corridas.
  - Default del codigo: `1800000` ms = 30 minutos.
  - Override local dejado en los `.env` del repo: `86400000` ms = 1 dia.
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE`
  - maximo de filas expiradas borradas por corrida.
  - Default: `500`.

## Notificaciones por correo

- `APP_EMAIL_ENABLED`
  - habilita o deshabilita el pipeline de correo.
  - Default: `false`.
- `APP_EMAIL_PROVIDER`
  - proveedor de entrega configurado.
  - Default actual: `resend`.
- `APP_EMAIL_RESEND_API_KEY`
  - API key usada por el cliente `ResendEmailClient`.
  - Obligatoria cuando `APP_EMAIL_ENABLED=true` y `APP_EMAIL_PROVIDER=resend`.
- `APP_EMAIL_FROM_NAME`
  - nombre visible del remitente.
  - Default: `Panol`.
- `APP_EMAIL_FROM_ADDRESS`
  - direccion visible del remitente.
  - Default: `notificaciones@panol.cl`.
- `APP_EMAIL_WORKER_DELAY_MS`
  - intervalo del worker que procesa la cola de correo.
  - Default: `5000` ms.
- `APP_EMAIL_BATCH_SIZE`
  - maximo de correos procesados por ciclo.
  - Default: `20`.
- `APP_EMAIL_MAX_RETRIES`
  - maximo de reintentos por correo fallido.
  - Default: `5`.
- `APP_EMAIL_CONNECT_TIMEOUT_MS`
  - timeout de conexion al proveedor.
  - Default: `5000` ms.
- `APP_EMAIL_READ_TIMEOUT_MS`
  - timeout de lectura de la respuesta del proveedor.
  - Default: `10000` ms.

## Compose y entorno

- `Producto/databasepanol/docker-compose.yaml` levanta PostgreSQL local.
- `Producto/docker-compose.yaml` levanta `frontend + backend`.
- `Producto/backendpanol/docker-compose.yaml` levanta `backend only`.

`APP_DB_ENV` define a que base conecta la app, no que servicios crea Docker Compose.

En backend:

- `.env` define una base local del modulo.
- `.env.local` puede sobreescribir valores de `.env`.
- en `Producto/docker-compose.yaml`, el backend carga ambos archivos via `env_file`.

### Modo Supabase

- usa cualquiera de los compose de aplicacion;
- deja `APP_DB_ENV=supabase`;
- configura `DB_SUPABASE_*`;
- no necesitas levantar `Producto/databasepanol/docker-compose.yaml`.

### Modo PostgreSQL local

- levanta primero `Producto/databasepanol/docker-compose.yaml`;
- deja `APP_DB_ENV=docker`;
- configura `DB_DOCKER_*`;
- cuando el backend corre dentro de Docker Compose, los compose inyectan `DB_DOCKER_HOST=panol-postgres`;
- cuando el backend corre desde IDE o Maven en tu host, usa `DB_DOCKER_HOST=localhost`.

### jOOQ en build

`JOOQ_DB_URL`, `JOOQ_DB_USER` y `JOOQ_DB_PASSWORD` se usan solo para code generation / introspeccion en build.

No dependen automaticamente de `APP_DB_ENV`. Eso significa:

- puedes correr la app contra PostgreSQL local (`APP_DB_ENV=docker`) y seguir generando jOOQ desde Supabase;
- o puedes apuntar `JOOQ_DB_*` tambien a tu base local si quieres un ciclo completamente offline.

## Flujo recomendado para base local

1. Levantar `Producto/databasepanol/docker-compose.yaml`.
2. Configurar en `Producto/backendpanol/.env.local` el modo `APP_DB_ENV=docker` y una `DB_DOCKER_PASSWORD` que coincida con `POSTGRES_PASSWORD`.
3. Arrancar el backend; Flyway aplicara el esquema desde `Producto/databasepanol/migrations/v25` empaquetado en el jar.

## Flujos rapidos

Supabase:

```bash
cd Producto
docker compose up --build
```

con `APP_DB_ENV=supabase`.

Base local:

```bash
cd Producto/databasepanol
docker compose up -d
```

despues:

```bash
cd Producto
docker compose up --build
```

con `APP_DB_ENV=docker`.
