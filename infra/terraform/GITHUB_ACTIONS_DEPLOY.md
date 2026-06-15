# GitHub Actions Auto Deploy (GCP + Terraform + WIF)

Este documento describe el bootstrap para enlazar este repositorio con GCP sin usar llaves JSON (Workload Identity Federation).

## 1) Crear Service Accounts (dev/staging-perf/prod)

```bash
gcloud iam service-accounts create gha-deploy-dev \
  --project="PANOL_DEV_PROJECT_ID" \
  --display-name="GitHub Deploy Dev"

gcloud iam service-accounts create gha-deploy-prod \
  --project="PANOL_PROD_PROJECT_ID" \
  --display-name="GitHub Deploy Prod"

gcloud iam service-accounts create gha-deploy-staging-perf \
  --project="PANOL_STAGING_PERF_PROJECT_ID" \
  --display-name="GitHub Deploy Staging Perf"
```

## 2) Permisos mínimos de deploy

```bash
# DEV
SA_DEV="gha-deploy-dev@PANOL_DEV_PROJECT_ID.iam.gserviceaccount.com"
PROJECT_DEV="PANOL_DEV_PROJECT_ID"

gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/artifactregistry.admin"
gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/secretmanager.admin"
gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/iam.serviceAccountAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/resourcemanager.projectIamAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_DEV" --member="serviceAccount:$SA_DEV" --role="roles/storage.admin"

# PROD
SA_PROD="gha-deploy-prod@PANOL_PROD_PROJECT_ID.iam.gserviceaccount.com"
PROJECT_PROD="PANOL_PROD_PROJECT_ID"

gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/artifactregistry.admin"
gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/secretmanager.admin"
gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/iam.serviceAccountAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/resourcemanager.projectIamAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_PROD" --member="serviceAccount:$SA_PROD" --role="roles/storage.admin"

# STAGING-PERF
SA_STAGING_PERF="gha-deploy-staging-perf@PANOL_STAGING_PERF_PROJECT_ID.iam.gserviceaccount.com"
PROJECT_STAGING_PERF="PANOL_STAGING_PERF_PROJECT_ID"

gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/artifactregistry.admin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/secretmanager.admin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/iam.serviceAccountAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/resourcemanager.projectIamAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/storage.admin"
gcloud projects add-iam-policy-binding "$PROJECT_STAGING_PERF" --member="serviceAccount:$SA_STAGING_PERF" --role="roles/cloudsql.admin"
```

## 3) Crear Workload Identity Pool + Provider

Haz esto en un proyecto de identidad (puede ser dev o uno compartido):

```bash
IDENTITY_PROJECT_ID="PANOL_DEV_PROJECT_ID"
POOL_ID="github-pool"
PROVIDER_ID="github-provider"
GITHUB_ORG="IA-FullStack-Citt-Maipu"
GITHUB_REPO="MA_TPY1101_Seccion001D_Grupo5"

gcloud iam workload-identity-pools create "$POOL_ID" \
  --project="$IDENTITY_PROJECT_ID" \
  --location="global" \
  --display-name="GitHub Pool"

gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
  --project="$IDENTITY_PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --display-name="GitHub Provider" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository=='$GITHUB_ORG/$GITHUB_REPO'"
```

Obtén el resource name del provider:

```bash
gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$IDENTITY_PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --format="value(name)"
```

## 4) Permitir impersonación desde GitHub hacia SAs

```bash
WIF_PRINCIPAL="principalSet://iam.googleapis.com/projects/IDENTITY_PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/IA-FullStack-Citt-Maipu/MA_TPY1101_Seccion001D_Grupo5"

gcloud iam service-accounts add-iam-policy-binding "$SA_DEV" \
  --project="$PROJECT_DEV" \
  --role="roles/iam.workloadIdentityUser" \
  --member="$WIF_PRINCIPAL"

gcloud iam service-accounts add-iam-policy-binding "$SA_PROD" \
  --project="$PROJECT_PROD" \
  --role="roles/iam.workloadIdentityUser" \
  --member="$WIF_PRINCIPAL"

gcloud iam service-accounts add-iam-policy-binding "$SA_STAGING_PERF" \
  --project="$PROJECT_STAGING_PERF" \
  --role="roles/iam.workloadIdentityUser" \
  --member="$WIF_PRINCIPAL"
```

## 5) Configurar GitHub Secrets (Repository)

- `GCP_WIF_PROVIDER_DEV`: resource name del provider
- `GCP_SERVICE_ACCOUNT_DEV`: `gha-deploy-dev@...`
- `DB_SUPABASE_PASSWORD_DEV`
- `JWT_ISSUER_URI_DEV`
- `VITE_SUPABASE_PUBLISHABLE_KEY_DEV`

- `GCP_WIF_PROVIDER_STAGING_PERF`: resource name del provider
- `GCP_SERVICE_ACCOUNT_STAGING_PERF`: `gha-deploy-staging-perf@...`
- `DB_CLOUDSQL_PASSWORD_STAGING_PERF`
- `APP_AUTH_JWT_SECRET_STAGING_PERF`

- `GCP_WIF_PROVIDER_PROD`: resource name del provider
- `GCP_SERVICE_ACCOUNT_PROD`: `gha-deploy-prod@...`
- `DB_SUPABASE_PASSWORD_PROD`
- `JWT_ISSUER_URI_PROD`
- `VITE_SUPABASE_PUBLISHABLE_KEY_PROD`

## 6) Configurar GitHub Variables (Repository)

DEV:
- `GCP_PROJECT_ID_DEV`
- `GCP_REGION_DEV` (ej: `us-central1`)
- `GCP_ARTIFACT_REGISTRY_LOCATION_DEV` (ej: `us-central1`)
- `GCP_TFSTATE_BUCKET_DEV`
- `SUPABASE_DB_HOST_DEV`
- `SUPABASE_DB_PORT_DEV`
- `SUPABASE_DB_NAME_DEV`
- `SUPABASE_DB_USER_DEV`

STAGING-PERF:
- `GCP_PROJECT_ID_STAGING_PERF`
- `GCP_REGION_STAGING_PERF`
- `GCP_ARTIFACT_REGISTRY_LOCATION_STAGING_PERF`
- `GCP_TFSTATE_BUCKET_STAGING_PERF`
- `BACKEND_IMAGE_STAGING_PERF`
- `FRONTEND_IMAGE_STAGING_PERF`
- `CLOUDSQL_INSTANCE_NAME_STAGING_PERF`
- `CLOUDSQL_DB_NAME_STAGING_PERF`
- `CLOUDSQL_DB_USER_STAGING_PERF`
- `CLOUDSQL_DB_TIER_STAGING_PERF`
- `CLOUDSQL_DB_DISK_SIZE_GB_STAGING_PERF`
- `CLOUDSQL_DB_AVAILABILITY_TYPE_STAGING_PERF`
- `CLOUDSQL_IP_TYPES_STAGING_PERF`
- `APP_SECURITY_ENABLED_STAGING_PERF`
- `APP_AUTH_JWT_ISSUER_STAGING_PERF`
- `APP_AUTH_JWT_EXPIRATION_SECONDS_STAGING_PERF`
- `APP_AUTH_REFRESH_EXPIRATION_SECONDS_STAGING_PERF`
- `APP_AUTH_COOKIE_SECURE_STAGING_PERF`
- `APP_AUTH_COOKIE_SAME_SITE_STAGING_PERF`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED_STAGING_PERF`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS_STAGING_PERF`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS_STAGING_PERF`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE_STAGING_PERF`

PROD:
- `GCP_PROJECT_ID_PROD`
- `GCP_REGION_PROD`
- `GCP_ARTIFACT_REGISTRY_LOCATION_PROD`
- `GCP_TFSTATE_BUCKET_PROD`
- `SUPABASE_DB_HOST_PROD`
- `SUPABASE_DB_PORT_PROD`
- `SUPABASE_DB_NAME_PROD`
- `SUPABASE_DB_USER_PROD`

## 7) Flujo de deploy

- `push` a `dev`: despliegue automático a `dev`.
- `workflow_dispatch` con `environment=staging-perf`: despliegue manual aislado para performance.
- `workflow_dispatch` con `environment=prod`: despliegue manual a `prod`.

## 8) Optimizaciones de costo aplicadas

- Secretos no se versionan en cada push (evita costos innecesarios en Secret Manager).
- Ejecuciones en progreso se cancelan al llegar un commit más nuevo a `dev` (`concurrency cancel-in-progress`).

Workflow creado:
- `.github/workflows/deploy-gcp.yml`
