# 09 - Guia de Variables de Entorno y Secretos

Esta guia define el proceso estandar para agregar una nueva configuracion al sistema sin romper deploys.

## Objetivo

- Mantener consistencia entre Terraform, GitHub Actions y Cloud Run.
- Evitar errores comunes: variable faltante, secreto no definido o nombre inconsistente.
- Reducir riesgo de exposicion de secretos.

## Regla principal: env var o secret

- Usa `env var` normal si el dato no es sensible (flags, puertos, URLs publicas, claves publicables frontend).
- Usa `Secret Manager` si el dato es sensible (passwords, URIs con credenciales, secrets de firma).

## Clasificacion oficial (actual)

Secretos reales (Secret Manager):

- `DB_SUPABASE_PASSWORD`
- `APP_AUTH_JWT_SECRET`

Variables no sensibles (env vars Terraform / Cloud Run):

- `JWT_ISSUER_URI`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `DB_SUPABASE_HOST`, `DB_SUPABASE_PORT`, `DB_SUPABASE_NAME`, `DB_SUPABASE_USER`
- `APP_SECURITY_ENABLED`, `APP_AUTH_MAX_FAILED_ATTEMPTS`, `APP_AUTH_LOCK_MINUTES`, `APP_AUTH_JWT_ISSUER`, `APP_AUTH_JWT_EXPIRATION_SECONDS`

## Estandar de nombres

- `APP_*` para configuracion de app.
- `VITE_*` para configuracion publica del frontend.
- Secretos en mayusculas con `_`: ejemplo `APP_AUTH_JWT_SECRET`.
- Sufijo por entorno solo en GitHub (`*_DEV`, `*_PROD`), no en el nombre del secreto en GCP.

## Flujo para agregar una nueva env var no sensible

1. Agregar la variable en `locals.backend_env` o `locals.frontend_env` en `environments/dev/main.tf` y `environments/prod/main.tf`.
2. Agregar variable de entrada en `variables.tf` de ambos entornos.
3. Mapear `TF_VAR_*` en workflows de Terraform.
4. Ejecutar `terraform plan` y luego `terraform apply`.

## Flujo para agregar un nuevo secreto sensible

1. Agregar variable sensible en `variables.tf` (`sensitive = true`).
2. Agregar secreto en `module "secret_manager"` dentro de `secrets` y `secret_values`.
3. Referenciar secreto en `secret_env_vars` del servicio Cloud Run que lo consume.
4. Definir GitHub Secret por entorno (`*_DEV`, `*_PROD`) y mapearlo a `TF_VAR_*` en workflows.
5. Ejecutar workflow `Terraform Plan/Apply`.

Resultado esperado:

- Si no existe el secreto: Terraform lo crea.
- Si existe y no cambia el valor: sin cambios.
- Si cambia el valor: Terraform crea nueva version de secreto (latest).

## Checklist anti-errores

1. Nombre del secreto identico en `secrets`, `secret_values` y `secret_env_vars`.
2. Existe GitHub Secret/Variable requerido para el entorno.
3. `terraform validate` y `terraform plan` sin errores.
4. Runtime service account con `roles/secretmanager.secretAccessor`.
5. No usar cambios manuales en consola para recursos gobernados.

## Verificacion rapida post-apply

1. Verificar versiones de secretos:
   - `gcloud secrets versions list <SECRET> --project <project-id>`
2. Verificar revision desplegada:
   - `gcloud run services describe <service> --region <region> --project <project-id>`
3. Verificar logs:
   - `gcloud run services logs read <service> --region <region> --project <project-id>`

## Recomendacion de seguridad

- Evitar guardar secretos en `terraform.tfvars` versionados.
- Usar GitHub Secrets + `TF_VAR_*` para valores sensibles.
- Aplicar politicas de aprobacion para `prod`.
