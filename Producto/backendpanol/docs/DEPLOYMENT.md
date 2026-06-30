# Despliegue (Docker y Kubernetes)

- Estado del documento: vigente
- Ultima verificacion: 2026-06-30
- Fuente de verdad: `Producto/databasepanol/docker-compose.yaml`, `Producto/docker-compose.yaml`, `Producto/backendpanol/docker-compose.yaml`, `deploy/k8s/*.yaml`

## Opciones

1. `Producto/databasepanol/docker-compose.yaml`
- PostgreSQL local desacoplado.
- Solo se usa para entornos locales con `APP_DB_ENV=docker`.

2. `Producto/docker-compose.yaml`
- Stack `frontend + backend`.
- No incluye postgres local embebido.
- Sirve tanto para `APP_DB_ENV=supabase` como para `APP_DB_ENV=docker`.

3. `Producto/backendpanol/docker-compose.yaml`
- Backend only.
- Tambien sirve tanto para `APP_DB_ENV=supabase` como para `APP_DB_ENV=docker`.

4. `Producto/backendpanol/deploy/k8s/*.yaml`
- Despliegue en Kubernetes.

## Comandos

Base local:
```bash
cd Producto/databasepanol
docker compose up -d
```

Stack completo usando Supabase:
```bash
cd Producto
docker compose up --build
```

Backend only usando Supabase:
```bash
cd Producto/backendpanol
docker compose up --build
```

Stack completo usando PostgreSQL local:
```bash
cd Producto/databasepanol
docker compose up -d

cd ../
docker compose up --build
```

Backend only usando PostgreSQL local:
```bash
cd Producto/databasepanol
docker compose up -d

cd ../backendpanol
docker compose up --build
```

## Configuracion de BD

- `APP_DB_ENV=supabase`: backend usa `DB_SUPABASE_*`.
- `APP_DB_ENV=docker`: backend usa `DB_DOCKER_*`.
- Cuando el backend corre en Docker y `APP_DB_ENV=docker`, los compose inyectan `DB_DOCKER_HOST=panol-postgres` para usar la red compartida `panol-local`.
- Cuando el backend corre desde el host/IDE y `APP_DB_ENV=docker`, `DB_DOCKER_HOST` debe quedar en `localhost`.

## Nota de build

El runtime usa `APP_DB_ENV`, pero el build de jOOQ usa `JOOQ_DB_*`.

Si quieres que el build introspecte la base local en vez de Supabase, debes cambiar explicitamente `JOOQ_DB_URL`, `JOOQ_DB_USER` y `JOOQ_DB_PASSWORD`.

## Seguridad en despliegue

- `APP_SECURITY_ENABLED=true` por defecto.
- Endpoints publicos de auth:
  - `POST /api/v2/auth/login`
  - `POST /api/v2/auth/password-recovery/request`
  - `POST /api/v2/auth/password-recovery/verify`
  - `POST /api/v2/auth/password-recovery/reset`
  - `POST /api/v2/auth/logout`
  - `POST /api/v2/auth/refresh`

## Nota operativa

No documentar ni usar rutas legacy en despliegues actuales (`/api/categorias`, `/api/implements`, `/api/v1/**`).
