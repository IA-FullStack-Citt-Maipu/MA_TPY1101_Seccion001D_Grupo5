# 08 - Troubleshooting

## 1) `terraform init` falla por backend GCS

Síntomas:
- bucket no existe o acceso denegado.

Acciones:
- verificar `GCP_TFSTATE_BUCKET_<ENV>`
- confirmar bucket creado y proyecto correcto
- validar permisos de la SA de deploy en storage

## 2) Error de autenticación en workflow (`auth` step)

Síntomas:
- `google-github-actions/auth` falla.

Acciones:
- revisar `GCP_WIF_PROVIDER_<ENV>`
- revisar `GCP_SERVICE_ACCOUNT_<ENV>`
- validar `roles/iam.workloadIdentityUser` en SA
- validar condición de repo en provider OIDC

## 3) Deploy falla al crear secretos

Síntomas:
- error en `gcloud secrets versions add`.

Acciones:
- confirmar existencia de secret container
- confirmar secretos en GitHub Environment
- validar permisos `secretmanager.*` en SA deploy

## 4) Build backend falla por jOOQ

Síntomas:
- error conexión JDBC en build docker backend.

Acciones:
- validar `SUPABASE_DB_HOST/PORT/NAME/USER`
- validar `DB_SUPABASE_PASSWORD_<ENV>`
- confirmar acceso de red a Supabase

## 5) Frontend apunta a API incorrecta

Síntomas:
- UI carga pero API falla por CORS o endpoint inválido.

Acciones:
- revisar `VITE_API_BASE_URL` efectiva en Cloud Run frontend
- revisar `FRONTEND_ORIGIN` en backend cuando aplique
- revisar logs de backend sobre origen/CORS

## 6) No se dispara deploy en push a dev

Acciones:
- confirmar workflow en rama `dev`
- confirmar paths del commit (`infra/terraform/**`, `Producto/backendpanol/**`, `Producto/frontendpanol/**`)
- revisar restricciones de environment branch rules

## 7) Costos inesperados

Acciones:
- revisar `min_instance_count`
- revisar número de imágenes en Artifact Registry
- revisar número de versiones de secretos
- activar limpieza periódica de imágenes

## 8) Verificación rápida de estado

- GitHub Actions: run completo en verde.
- Terraform outputs disponibles.
- Cloud Run backend/frontend activos con revisión nueva.
- Health endpoint backend responde 200.
