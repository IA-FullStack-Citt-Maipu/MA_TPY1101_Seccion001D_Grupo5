# 09 - Guia de Variables de Entorno y Secretos

Esta guia define el proceso estandar para agregar configuracion nueva al stack sin romper deploys de Terraform, Cloud Run ni los workflows de GitHub Actions.

## Objetivo

- Mantener consistencia entre Terraform, GitHub Actions, Secret Manager y Cloud Run.
- Evitar errores comunes: variable faltante, secreto no publicado o nombres inconsistentes.
- Reducir el riesgo de exponer secretos en codigo o en pipelines.

## Regla principal: env var o secret

- Usa `env var` normal si el dato no es sensible: flags, puertos, dominios publicos, claves publicables frontend, timeouts, concurrencia.
- Usa `Secret Manager` si el dato es sensible: passwords, API keys privadas, secrets de firma, secretos de integracion entre servicios.

## Clasificacion oficial actual

### Secretos reales (GitHub Secrets -> TF_VAR_* -> Secret Manager -> Cloud Run)

- `DB_SUPABASE_PASSWORD`
- `APP_AUTH_JWT_SECRET`
- `APP_SECURITY_AI_AGENT_SECRET`
- `GOOGLE_API_KEY`

### Variables no sensibles (GitHub Variables -> TF_VAR_* -> Cloud Run)

- `JWT_ISSUER_URI`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `VITE_BOT_API_BASE_URL`
- `DB_SUPABASE_HOST`
- `DB_SUPABASE_PORT`
- `DB_SUPABASE_NAME`
- `DB_SUPABASE_USER`
- `APP_SECURITY_ENABLED`
- `APP_AUTH_MAX_FAILED_ATTEMPTS`
- `APP_AUTH_LOCK_MINUTES`
- `APP_AUTH_JWT_ISSUER`
- `APP_AUTH_JWT_EXPIRATION_SECONDS`
- `GEMINI_MODEL`
- `CORS_ALLOWED_ORIGINS`
- `BOT_MIN_INSTANCES`
- `BOT_MAX_INSTANCES`
- `BOT_TIMEOUT_SECONDS`
- `BOT_CONCURRENCY`
- `BOT_LLM_TIMEOUT_SECONDS`
- `BOT_BACKEND_TIMEOUT_SECONDS`
- `BOT_BACKEND_RETRY_COUNT`

## Estandar de nombres

- `APP_*` para configuracion de backend.
- `VITE_*` para configuracion publica consumida por el frontend en build-time.
- `BOT_*` para scaling y timeouts operativos del microservicio de IA.
- Secretos en mayusculas con `_`: ejemplo `APP_AUTH_JWT_SECRET`.
- Sufijo por entorno solo en GitHub (`*_DEV`, `*_PROD`), nunca en el nombre del secreto dentro de GCP.

## Flujo para agregar una nueva env var no sensible

1. Agregar la variable en `locals.backend_env`, `locals.bot_env` o `locals.frontend_env` dentro de `infra/terraform/environments/dev/main.tf` y `prod/main.tf`.
2. Declarar la entrada en `variables.tf` de ambos entornos.
3. Mapear la variable como `TF_VAR_*` en `.github/workflows/terraform-plan-apply.yml` y, si corresponde al despliegue de imagen, tambien en `.github/workflows/deploy-gcp.yml`.
4. Ejecutar `terraform fmt`, `terraform validate` y `terraform plan`.

## Flujo para agregar un nuevo secreto sensible

1. Declarar la variable sensible en `variables.tf` (`sensitive = true`).
2. Agregar el secreto en `module "secret_manager"` dentro de `secrets` y `secret_values`.
3. Referenciarlo desde `secret_env_vars` del servicio Cloud Run que lo consume.
4. Crear el GitHub Secret por entorno (`*_DEV`, `*_PROD`) y mapearlo a `TF_VAR_*` en los workflows.
5. Ejecutar `Terraform Plan/Apply`.

Resultado esperado:

- Si no existe el secreto: Terraform lo crea.
- Si existe y el valor no cambia: no hay drift.
- Si cambia el valor: Terraform publica una nueva version y Cloud Run toma `latest`.

## Contrato actual por servicio

### Backend

- Variables no sensibles:
  - `APP_SECURITY_ENABLED`
  - `APP_AUTH_MAX_FAILED_ATTEMPTS`
  - `APP_AUTH_LOCK_MINUTES`
  - `APP_AUTH_JWT_ISSUER`
  - `APP_AUTH_JWT_EXPIRATION_SECONDS`
  - `JWT_ISSUER_URI`
  - `FRONTEND_ORIGIN`
  - `CORS_ALLOWED_ORIGINS`
  - `DB_SUPABASE_HOST`
  - `DB_SUPABASE_PORT`
  - `DB_SUPABASE_NAME`
  - `DB_SUPABASE_USER`
  - `DB_SUPABASE_SSL_MODE`
- Secretos:
  - `DB_SUPABASE_PASSWORD`
  - `APP_AUTH_JWT_SECRET`
  - `APP_SECURITY_AI_AGENT_SECRET`

### Bot

- Variables no sensibles:
  - `GEMINI_MODEL`
  - `LLM_TIMEOUT_SECONDS`
  - `BACKEND_BASE_URL`
  - `BACKEND_TIMEOUT_SECONDS`
  - `BACKEND_RETRY_COUNT`
  - `JWT_ISSUER`
  - `CORS_ALLOWED_ORIGINS`
- Secretos:
  - `GOOGLE_API_KEY`
  - `BACKEND_CLIENT_SECRET` (desde `APP_SECURITY_AI_AGENT_SECRET`)
  - `JWT_SECRET_KEY` (desde `APP_AUTH_JWT_SECRET`)

### Frontend

- Variables no sensibles:
  - `VITE_API_BASE_URL`
  - `VITE_BOT_API_BASE_URL`
  - `VITE_SUPABASE_PUBLISHABLE_KEY`

## Checklist anti-errores

1. El nombre del secreto coincide en `variables.tf`, `module.secret_manager`, `secret_env_vars` y GitHub Secrets.
2. Existe GitHub Variable/Secret requerido para el entorno correcto.
3. `terraform validate` y `terraform plan` pasan en `dev` y `prod`.
4. La runtime service account mantiene `roles/secretmanager.secretAccessor`.
5. No se hacen cambios manuales en consola sobre recursos gobernados por Terraform.

## Verificacion rapida post-apply

1. Verificar versiones de secretos:
   - `gcloud secrets versions list <SECRET> --project <project-id>`
2. Verificar revision desplegada:
   - `gcloud run services describe <service> --region <region> --project <project-id>`
3. Verificar logs:
   - `gcloud run services logs read <service> --region <region> --project <project-id>`
4. Verificar frontend:
   - revisar que la build del frontend reciba `VITE_BOT_API_BASE_URL`
5. Verificar bot:
   - `GET /health`
   - `OPTIONS /api/v1/chat` con `Origin` del frontend
   - `POST /api/v1/chat` autenticado

## Recomendacion de seguridad

- No guardar secretos reales en `terraform.tfvars` versionados.
- Usar GitHub Secrets + `TF_VAR_*` para valores sensibles.
- Mantener `GOOGLE_API_KEY` y secretos JWT solo en Secret Manager / GitHub Secrets.
- Exigir aprobacion manual para `prod`.
