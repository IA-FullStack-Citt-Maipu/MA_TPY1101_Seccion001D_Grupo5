# GitHub Actions Auto Deploy (GCP + Terraform + WIF)

Este documento describe el bootstrap para enlazar este repositorio con GCP usando Workload Identity Federation, Terraform y despliegue de tres servicios Cloud Run: `backend`, `frontend` y `bot`.

## 1) Crear Service Accounts (dev/prod)

```bash
gcloud iam service-accounts create gha-deploy-dev \
  --project="PANOL_DEV_PROJECT_ID" \
  --display-name="GitHub Deploy Dev"

gcloud iam service-accounts create gha-deploy-prod \
  --project="PANOL_PROD_PROJECT_ID" \
  --display-name="GitHub Deploy Prod"
```

## 2) Permisos minimos de deploy

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
```

## 3) Crear Workload Identity Pool + Provider

Haz esto en un proyecto de identidad, que puede ser `dev` o uno compartido:

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

Obtén luego el resource name del provider:

```bash
gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$IDENTITY_PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --format="value(name)"
```

## 4) Permitir impersonacion desde GitHub hacia las Service Accounts

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
```

## 5) Configurar GitHub Secrets

### DEV

- `GCP_WIF_PROVIDER_DEV`
- `GCP_SERVICE_ACCOUNT_DEV`
- `DB_SUPABASE_PASSWORD_DEV`
- `APP_AUTH_JWT_SECRET_DEV`
- `APP_SECURITY_AI_AGENT_SECRET_DEV`
- `GOOGLE_API_KEY_DEV`

### PROD

- `GCP_WIF_PROVIDER_PROD`
- `GCP_SERVICE_ACCOUNT_PROD`
- `DB_SUPABASE_PASSWORD_PROD`
- `APP_AUTH_JWT_SECRET_PROD`
- `APP_SECURITY_AI_AGENT_SECRET_PROD`
- `GOOGLE_API_KEY_PROD`

## 6) Configurar GitHub Variables

### DEV

- `GCP_PROJECT_ID_DEV`
- `GCP_REGION_DEV`
- `GCP_ARTIFACT_REGISTRY_LOCATION_DEV`
- `GCP_TFSTATE_BUCKET_DEV`
- `BACKEND_IMAGE_DEV`
- `FRONTEND_IMAGE_DEV`
- `BOT_IMAGE_DEV`
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
- `GEMINI_MODEL_DEV`
- `BOT_MIN_INSTANCES_DEV`
- `BOT_MAX_INSTANCES_DEV`
- `BOT_TIMEOUT_SECONDS_DEV`
- `BOT_CONCURRENCY_DEV`
- `BOT_LLM_TIMEOUT_SECONDS_DEV`
- `BOT_LLM_TOTAL_TIMEOUT_SECONDS_DEV`
- `BOT_BACKEND_TIMEOUT_SECONDS_DEV`
- `BOT_BACKEND_RETRY_COUNT_DEV`

`BOT_DOMAIN_DEV` queda opcional mientras `dev` opere temporalmente con la URL
`run.app` del bot.

### PROD

- `GCP_PROJECT_ID_PROD`
- `GCP_REGION_PROD`
- `GCP_ARTIFACT_REGISTRY_LOCATION_PROD`
- `GCP_TFSTATE_BUCKET_PROD`
- `BACKEND_IMAGE_PROD`
- `FRONTEND_IMAGE_PROD`
- `BOT_IMAGE_PROD`
- `SUPABASE_DB_HOST_PROD`
- `SUPABASE_DB_PORT_PROD`
- `SUPABASE_DB_NAME_PROD`
- `SUPABASE_DB_USER_PROD`
- `APP_SECURITY_ENABLED_PROD`
- `APP_AUTH_MAX_FAILED_ATTEMPTS_PROD`
- `APP_AUTH_LOCK_MINUTES_PROD`
- `APP_AUTH_JWT_ISSUER_PROD`
- `APP_AUTH_JWT_EXPIRATION_SECONDS_PROD`
- `JWT_ISSUER_URI_PROD`
- `VITE_SUPABASE_PUBLISHABLE_KEY_PROD`
- `BOT_DOMAIN_PROD`
- `GEMINI_MODEL_PROD`
- `BOT_MIN_INSTANCES_PROD`
- `BOT_MAX_INSTANCES_PROD`
- `BOT_TIMEOUT_SECONDS_PROD`
- `BOT_CONCURRENCY_PROD`
- `BOT_LLM_TIMEOUT_SECONDS_PROD`
- `BOT_LLM_TOTAL_TIMEOUT_SECONDS_PROD`
- `BOT_BACKEND_TIMEOUT_SECONDS_PROD`
- `BOT_BACKEND_RETRY_COUNT_PROD`

## 7) Flujo de deploy

- `push` a `dev`:
  - `deploy-gcp.yml` construye y publica `backend` y `bot`
  - aplica Terraform sobre `dev` para dejar disponible el bot en Cloud Run
  - resuelve la URL `run.app` del bot
  - builda y publica `frontend` con `VITE_BOT_API_BASE_URL` apuntando a esa URL
  - ejecuta el `apply` final de Terraform en `dev`
- `workflow_dispatch` en `terraform-plan-apply.yml`:
  - permite `plan/apply` en `dev` o `prod`

## 8) Contratos importantes

- El frontend debe buildarse con `VITE_API_BASE_URL` y `VITE_BOT_API_BASE_URL`.
- En `dev`, el bot usa temporalmente su `run.app` y no depende de `bot.dev.panol.cl`.
- En `prod`, el bot mantiene dominio publico propio:
  - `bot.panol.cl`
- `BACKEND_CLIENT_SECRET` del bot reutiliza `APP_SECURITY_AI_AGENT_SECRET`.
- `JWT_SECRET_KEY` del bot reutiliza `APP_AUTH_JWT_SECRET`.

## 9) Incidente conocido en dev

- El bloqueo original no estaba en la creacion de `panol-bot-dev`.
- El fallo ocurria al crear el `DomainMapping` de `bot.dev.panol.cl` por falta de
  autorizacion del dominio en GCP/Search Console.
- Mientras esa autorizacion no exista, `dev` se despliega usando `run.app`.

## 10) Optimizaciones y buenas practicas

- Secretos no se guardan en `terraform.tfvars` versionados.
- Las ejecuciones en progreso se cancelan cuando llega un commit mas nuevo a `dev`.
- Si se agrega un cuarto servicio, primero se actualizan Terraform, GitHub Variables/Secrets y luego los workflows.

Workflows relevantes:

- `.github/workflows/deploy-gcp.yml`
- `.github/workflows/terraform-plan-apply.yml`
