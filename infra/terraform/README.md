# Terraform (GCP Cloud Run + Supabase external)

This folder contains Infrastructure as Code for deploying backend and frontend to GCP using Cloud Run, Artifact Registry and Secret Manager.

## Layout

- `environments/dev`: root module for dev.
- `environments/prod`: root module for prod.
- `modules/*`: reusable Terraform modules.
- `global/shared`: optional shared stack template.

## Prerequisites

- Terraform >= 1.8
- GCP project with billing enabled
- APIs enabled:
  - `run.googleapis.com`
  - `artifactregistry.googleapis.com`
  - `secretmanager.googleapis.com`
  - `iam.googleapis.com`
  - `cloudresourcemanager.googleapis.com`

## Remote state

Each environment uses `backend "gcs"`.
Create the bucket first (one-time), then edit `backend.tf` in each env:

- `bucket = "<project>-tfstate-dev"`
- `bucket = "<project>-tfstate-prod"`

## Quick start

```bash
cd infra/terraform/environments/dev
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

## Configuration model

`secret_env_vars` (Secret Manager) only for sensitive values:

- `DB_SUPABASE_PASSWORD`
- `APP_AUTH_JWT_SECRET`

`env_vars` (non-sensitive) for runtime/public configuration:

- `JWT_ISSUER_URI`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `DB_SUPABASE_HOST`, `DB_SUPABASE_PORT`, `DB_SUPABASE_NAME`, `DB_SUPABASE_USER`
- app flags/timeouts (`APP_SECURITY_ENABLED`, `APP_AUTH_*`)

## CI/CD deploy

- Deploy workflow: `.github/workflows/deploy-gcp.yml`
- Terraform governance workflow: `.github/workflows/terraform-plan-apply.yml`
- Bootstrap guide: `infra/terraform/GITHUB_ACTIONS_DEPLOY.md`

## Full documentation

- Start here: `infra/terraform/docs/README.md`
