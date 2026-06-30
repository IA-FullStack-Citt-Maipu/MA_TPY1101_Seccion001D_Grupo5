# Init scripts

Esta carpeta queda reservada para bootstrap opcional de PostgreSQL local.

Actualmente contiene solo bootstrap seguro de extensiones requeridas por el esquema:

- `001_extensions.sql`: crea `pgcrypto` para soportar `gen_random_uuid()` en migraciones y snapshots.

El usuario y la base se crean con variables del contenedor Postgres. El esquema y las funciones de negocio siguen aplicandose con Flyway desde `../migrations/v25` cuando arranca el backend.

Si en el futuro se agregan scripts aqui, deben ser idempotentes y pensados solo para entornos locales.
