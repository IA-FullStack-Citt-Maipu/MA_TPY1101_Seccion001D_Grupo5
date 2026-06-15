# Database Panol

- Estado del documento: vigente
- Ultima verificacion: 2026-06-07
- Fuente de verdad SQL: `Producto/databasepanol/migrations/v25/`

## Objetivo

Esta carpeta centraliza la base de datos del proyecto fuera del backend. Aqui vive:

- el SQL canonico de esquema y migraciones;
- el compose para levantar PostgreSQL local;
- la base operativa para pruebas locales futuras de rendimiento o integracion.

No replica servicios completos de Supabase. Solo levanta PostgreSQL local.

Tampoco reemplaza los compose de aplicacion: esta carpeta solo agrega la opcion de una BD local cuando el backend se ejecuta con `APP_DB_ENV=docker`.

## Estructura

- `docker-compose.yaml`: PostgreSQL local desacoplado del backend.
- `.env.example`: variables base para la instancia local.
- `snapshot/`: exportes estructurales del esquema actual de Supabase (`public`), en version raw y separada por tipo de objeto.
- `migrations/v25/`: cadena Flyway vigente (`beforeMigrate.sql`, `V25`..`V35`).
- `seeds/`: semillas locales minimas para arrancar un flujo funcional sin usar datos productivos.
- `init/`: bootstrap opcional del contenedor Postgres.
- `secrets/`: espacio documentado para secretos locales no versionados.

## Levantar PostgreSQL local

```bash
cd Producto/databasepanol
cp .env.example .env
# ajustar password si quieres

docker compose up -d
```

El servicio queda expuesto en `localhost:${POSTGRES_PORT}` y en la red Docker `panol-local` con hostname `panol-postgres`.

Nota:

- el tag local por defecto es `postgres:17` para acercarse al major actual de Supabase;
- si ya levantaste antes una instancia con otro major y el volumen persiste, recrea el volumen antes de cambiar de version.

## Cuando no usar esta carpeta

Si vas a correr la app contra Supabase:

- no necesitas levantar `Producto/databasepanol/docker-compose.yaml`;
- usa el compose de aplicacion habitual;
- deja `APP_DB_ENV=supabase` en `Producto/backendpanol/.env.local`.

## Inicializar esquema

El esquema no se aplica desde este compose. La inicializacion se hace con Flyway cuando arranca el backend.

### Si el backend corre en Docker

1. Levanta la BD desde esta carpeta.
2. En `Producto/backendpanol/.env.local`, deja `APP_DB_ENV=docker` y un `DB_DOCKER_PASSWORD` que coincida con `POSTGRES_PASSWORD`.
3. Arranca el backend con cualquiera de estos comandos:

```bash
cd Producto
docker compose up --build backend
```

o

```bash
cd Producto/backendpanol
docker compose up --build backend
```

En los compose del backend, `DB_DOCKER_HOST` se inyecta como `panol-postgres` para usar la red compartida `panol-local`.

Nota:

- el compose de aplicacion sigue siendo el mismo;
- no existe un compose distinto de backend solo para Supabase y otro solo para local.

### Si el backend corre desde IDE o Maven en tu host

Usa `APP_DB_ENV=docker` y configura en `Producto/backendpanol/.env.local`:

```properties
DB_DOCKER_HOST=localhost
DB_DOCKER_PORT=5432
DB_DOCKER_NAME=panol
DB_DOCKER_USER=panol_user
DB_DOCKER_PASSWORD=replace_me
DB_DOCKER_SSL_MODE=disable
```

## Relacion con el backend

- Flyway sigue leyendo en runtime desde `classpath:db/migration/v25`.
- El backend empaqueta ese classpath desde esta carpeta durante el build.
- La fuente de verdad del SQL ya no vive en `backendpanol/src/main/resources/db/migration`.
- `APP_DB_ENV` selecciona el datasource runtime.
- `JOOQ_DB_*` controla la introspeccion usada en build para jOOQ y puede apuntar a Supabase o a tu base local segun necesidad.

## Snapshot y semillas locales

- `snapshot/public-schema-full.sql`: dump raw del esquema `public` actual de Supabase, solo estructura.
- `snapshot/public/`: mismo snapshot separado por concern (`types`, `functions`, `tables`, `indexes`, `triggers`, etc.).
- `seeds/00_local_initial_flow_seed.sql`: datos minimos para login QA, catalogo base, stock y prestamos demo locales.
- `migrations/v25/apply-all.sql`: utilidad para aplicar las migraciones con `psql` fuera de Flyway cuando se quiera validar una BD desde cero.

## Cargar semilla local

Una vez aplicada la cadena de migraciones sobre una base local, puedes cargar la semilla minima con `psql`:

```bash
psql -f Producto/databasepanol/seeds/00_local_initial_flow_seed.sql
```

Esto agrega catalogo base, stock inicial, movimientos demo y tres usuarios QA locales de prueba.

## Seed adicional para rendimiento

Para ampliar el catalogo local antes de correr k6, puedes aplicar tambien:

```bash
psql -f Producto/databasepanol/seeds/01_local_perf_catalog_seed.sql
```

Este seed agrega implementos extra orientados a consultas de catalogo y stock sin tocar datos productivos.

## Regla de cambios

- Nuevas migraciones se crean en `Producto/databasepanol/migrations/v25/`.
- No editar scripts ya aplicados en ambientes compartidos.
- Mantener sincronizadas las docs de backend cuando cambie la cadena de migraciones.
