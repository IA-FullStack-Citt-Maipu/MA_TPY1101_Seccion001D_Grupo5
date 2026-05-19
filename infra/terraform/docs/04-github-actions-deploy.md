# 04 - GitHub Actions: Deploy e IaC Terraform

## Workflows activos

- Deploy de aplicaciones e infraestructura: `.github/workflows/deploy-gcp.yml`
- Gobierno IaC (plan/apply): `.github/workflows/terraform-plan-apply.yml`

## Modelo operativo recomendado

- `deploy-gcp.yml`: pipeline de build/deploy del stack (backend/frontend + Terraform).
- `terraform-plan-apply.yml`: pipeline de gobernanza Terraform para validacion, plan y apply controlado por entorno.

## Triggers principales

### `deploy-gcp.yml`

- `push` en `dev` (segun paths configurados)
- `workflow_dispatch` para ejecucion manual en `dev` o `prod`

### `terraform-plan-apply.yml`

- `push` en `dev` para cambios IaC
- `workflow_dispatch` con:
  - `environment` (`dev` | `prod`)
  - `apply` (`true` | `false`)

## Seguridad de autenticacion

- `permissions: id-token: write`
- `google-github-actions/auth@v2` con Workload Identity Federation
- No se usan llaves JSON persistidas en GitHub.

## Flujo Terraform (recomendado)

1. `terraform fmt -check`
2. `terraform init`
3. `terraform validate`
4. `terraform plan`
5. `terraform apply` (automatico en `dev`, manual/aprobado en `prod`)

## Convenciones de configuracion

- Secretos sensibles via GitHub Secrets -> `TF_VAR_*` -> Secret Manager:
  - `DB_SUPABASE_PASSWORD`
  - `APP_AUTH_JWT_SECRET`
- Configuracion no sensible via GitHub Variables -> `TF_VAR_*` -> `env_vars` de Cloud Run:
  - `JWT_ISSUER_URI`
  - `VITE_SUPABASE_PUBLISHABLE_KEY`
  - host/port/name/user DB y flags de app

## Requisitos en GitHub

- Environments `dev` y `prod` creados.
- Reglas de proteccion recomendadas:
  - `dev`: branch `dev`.
  - `prod`: aprobacion manual obligatoria y branch protegida.
- Variables/secrets completos por entorno (ver docs 05 y 09).

## Buenas practicas

- No editar secretos manualmente en Cloud Run para recursos gobernados.
- No hacer cambios en consola para recursos ya administrados por Terraform.
- Si hay emergencia manual, regularizar en Terraform en el siguiente PR.
