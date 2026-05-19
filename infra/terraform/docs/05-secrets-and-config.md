# 05 - Secrets y Configuracion por Entorno

## Principio de diseno

- Terraform es la fuente de verdad de infraestructura y configuracion runtime.
- Los valores sensibles entran por GitHub Secrets (`TF_VAR_*`) y se publican en Secret Manager.
- Los valores no sensibles entran por GitHub Variables (`TF_VAR_*`) y se inyectan como `env_vars`.
- No se guardan secretos en el repositorio.

## Clasificacion oficial

### Secretos reales (Secret Manager)

- `DB_SUPABASE_PASSWORD`
- `APP_AUTH_JWT_SECRET`

### Variables no sensibles (Cloud Run env vars)

- `JWT_ISSUER_URI`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `DB_SUPABASE_HOST`
- `DB_SUPABASE_PORT`
- `DB_SUPABASE_NAME`
- `DB_SUPABASE_USER`
- `APP_SECURITY_ENABLED`
- `APP_AUTH_MAX_FAILED_ATTEMPTS`
- `APP_AUTH_LOCK_MINUTES`
- `APP_AUTH_JWT_ISSUER`
- `APP_AUTH_JWT_EXPIRATION_SECONDS`

## GitHub Secrets requeridos

### dev

- `GCP_WIF_PROVIDER_DEV`
- `GCP_SERVICE_ACCOUNT_DEV`
- `DB_SUPABASE_PASSWORD_DEV`
- `APP_AUTH_JWT_SECRET_DEV`

### prod

- `GCP_WIF_PROVIDER_PROD`
- `GCP_SERVICE_ACCOUNT_PROD`
- `DB_SUPABASE_PASSWORD_PROD`
- `APP_AUTH_JWT_SECRET_PROD`

## GitHub Variables requeridas

### dev

- `GCP_PROJECT_ID_DEV`
- `GCP_REGION_DEV`
- `GCP_ARTIFACT_REGISTRY_LOCATION_DEV`
- `GCP_TFSTATE_BUCKET_DEV`
- `SUPABASE_DB_HOST_DEV`
- `SUPABASE_DB_PORT_DEV`
- `SUPABASE_DB_NAME_DEV`
- `SUPABASE_DB_USER_DEV`
- `APP_SECURITY_ENABLED_DEV`
- `APP_AUTH_MAX_FAILED_ATTEMPTS_DEV`
- `APP_AUTH_LOCK_MINUTES_DEV`
- `APP_AUTH_JWT_ISSUER_DEV`
- `APP_AUTH_JWT_EXPIRATION_SECONDS_DEV`
- `JWT_ISSUER_URI_DEV`
- `VITE_SUPABASE_PUBLISHABLE_KEY_DEV`
- `BACKEND_IMAGE_DEV`
- `FRONTEND_IMAGE_DEV`

### prod

Misma estructura con sufijo `_PROD`.

## Comportamiento esperado en apply

- Secreto inexistente: Terraform crea el secreto y su version.
- Secreto sin cambio: no hay cambios.
- Secreto con nuevo valor: Terraform crea nueva version `latest`.

## Consideraciones de seguridad

- Principio de minimo privilegio para cuentas de deploy/runtime.
- No exponer secretos en outputs/logs.
- Revisar IAM bindings periodicamente.
- Mantener aprobacion manual para `prod`.
