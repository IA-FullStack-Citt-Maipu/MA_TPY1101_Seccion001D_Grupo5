# 04 - GitHub Actions: Deploy e IaC Terraform

## Workflows activos

- Deploy de aplicaciones e infraestructura base: `.github/workflows/deploy-gcp.yml`
- Gobierno IaC (fmt, validate, plan, apply por entorno): `.github/workflows/terraform-plan-apply.yml`

## Modelo operativo recomendado

- `deploy-gcp.yml`: pipeline de build y despliegue del stack de aplicacion en `dev`. Construye y publica tres imagenes: `backend`, `frontend` y `bot`, y luego aplica Terraform con esas referencias.
- `terraform-plan-apply.yml`: pipeline de gobernanza Terraform para `dev` y `prod`, usado para validar, planificar y aplicar IaC con variables/secrets por entorno.

## Triggers principales

### `deploy-gcp.yml`

- `push` a `dev` cuando cambian `infra/terraform`, `Producto/backendpanol`, `Producto/frontendpanol`, `Producto/bot-panol` o el propio workflow.
- `workflow_dispatch` manual para `dev`.

### `terraform-plan-apply.yml`

- `push` a `dev` para cambios IaC.
- `workflow_dispatch` con:
  - `environment` (`dev` | `prod`)
  - `apply` (`true` | `false`)

## Seguridad de autenticacion

- `permissions: id-token: write`
- `google-github-actions/auth@v2` con Workload Identity Federation
- No se usan llaves JSON persistidas en GitHub.

## Flujo Terraform recomendado

1. `terraform fmt -check`
2. `terraform init`
3. `terraform validate`
4. `terraform plan`
5. `terraform apply`

## Convenciones de configuracion

### Secretos sensibles

Se propagan via `GitHub Secrets -> TF_VAR_* -> Secret Manager -> Cloud Run`:

- `DB_SUPABASE_PASSWORD`
- `APP_AUTH_JWT_SECRET`
- `APP_SECURITY_AI_AGENT_SECRET`
- `GOOGLE_API_KEY`

### Variables no sensibles

Se propagan via `GitHub Variables -> TF_VAR_* -> env_vars de Cloud Run`:

- `JWT_ISSUER_URI`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `BOT_DOMAIN`
- `GEMINI_MODEL`
- `BOT_MIN_INSTANCES`
- `BOT_MAX_INSTANCES`
- `BOT_TIMEOUT_SECONDS`
- `BOT_CONCURRENCY`
- `BOT_LLM_TIMEOUT_SECONDS`
- `BOT_BACKEND_TIMEOUT_SECONDS`
- `BOT_BACKEND_RETRY_COUNT`
- host/port/name/user de Supabase y flags del backend

## Contrato operativo actual

- El frontend cloud debe buildarse con:
  - `VITE_API_BASE_URL`
  - `VITE_BOT_API_BASE_URL`
- El bot se publica como servicio Cloud Run independiente con dominio propio por entorno.
- El backend y el bot comparten `APP_SECURITY_AI_AGENT_SECRET` para la autenticacion de herramientas internas.

## Requisitos en GitHub

- Environments `dev` y `prod` creados.
- Variables/secrets completos por entorno.
- Para `prod`, aprobacion manual y proteccion de rama recomendada.

## Buenas practicas

- No editar secretos manualmente en Cloud Run para recursos gobernados.
- No hacer cambios en consola para recursos administrados por Terraform.
- Si hay una intervencion manual de emergencia, regularizarla en Terraform en el siguiente PR.
