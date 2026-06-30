# 03 - Bootstrap GCP (Paso a paso)

## 1. Proyectos y billing

Se recomienda separar:

- `panol-dev`
- `panol-prod`

Ambos con facturación activa.

## 2. APIs requeridas

Habilitar en cada proyecto:

- `run.googleapis.com`
- `artifactregistry.googleapis.com`
- `secretmanager.googleapis.com`
- `iam.googleapis.com`
- `cloudresourcemanager.googleapis.com`

## 3. Bucket remoto de Terraform state

Crear bucket por ambiente (ejemplo):

- `panol-dev-tfstate`
- `panol-prod-tfstate`

Buenas prácticas:

- Uniform bucket-level access
- Public access prevention
- Versioning habilitado

## 4. Workload Identity Federation (WIF)

Objetivo: permitir que GitHub Actions asuma identidad en GCP sin llaves JSON.

Pasos:

1. Crear Workload Identity Pool (`github-pool`).
2. Crear OIDC Provider (`github-provider`) contra `https://token.actions.githubusercontent.com`.
3. Restringir por `assertion.repository` al repo exacto.
4. Dar `roles/iam.workloadIdentityUser` a service accounts de deploy.

## 5. Service Accounts de deploy

Crear una por ambiente (ejemplo):

- `gha-deploy-dev@...`
- `gha-deploy-prod@...`

Asignar roles de deploy según guía en:

- `infra/terraform/GITHUB_ACTIONS_DEPLOY.md`

## 6. Verificación mínima

- `gcloud iam workload-identity-pools providers describe ...` devuelve resource name.
- IAM policy de SA incluye principalSet del repo.
- Bucket de tfstate existe y es accesible por la SA de deploy.
