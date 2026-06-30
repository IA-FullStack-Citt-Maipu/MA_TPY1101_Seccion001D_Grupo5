# 02 - Estructura Terraform y Contratos

## Estructura de carpetas

```text
infra/terraform
├─ environments
│  ├─ dev
│  └─ prod
├─ modules
│  ├─ artifact_registry
│  ├─ cloud_run_service
│  ├─ iam
│  └─ secret_manager
├─ global/shared
└─ scripts
```

## Root modules por ambiente

Cada ambiente contiene:

- `versions.tf`: versión de Terraform y providers.
- `providers.tf`: provider google/google-beta.
- `backend.tf`: configuración backend remoto (GCS).
- `variables.tf`: contrato de entrada.
- `main.tf`: composición de módulos.
- `outputs.tf`: contrato de salida.
- `terraform.tfvars.example`: ejemplo de parámetros.

## Módulos reutilizables

### `artifact_registry`

Crea repositorio Docker por ambiente (`panol-apps-<env>`).

### `iam`

Crea Service Account runtime y asigna roles mínimos para operación de servicios.

### `secret_manager`

Crea contenedores de secretos (sin versión/valor), para inyección segura en runtime.

### `cloud_run_service`

Provisiona servicio Cloud Run con:
- imagen
- puertos
- env vars
- secret env vars
- autoscaling
- timeout/concurrency
- opcional domain mapping

## Variables clave (contrato)

- `gcp_project_id`
- `region`
- `environment`
- `artifact_registry_location`
- `backend_image`
- `frontend_image`
- `supabase_db_*`
- `backend_domain` / `frontend_domain`
- parámetros de escala y performance (`min/max`, timeout, concurrency)

## Outputs clave

- `backend_url`
- `frontend_url`
- `backend_service_name`
- `frontend_service_name`
- `artifact_registry_repository`
- `secret_ids`

## Convenciones operativas

- `backend_domain`, `bot_domain` y `frontend_domain` vacíos => usa `run.app` y omite domain mapping.
- No commitear `terraform.tfvars` reales.
- No almacenar secretos en `variables.tf`, `tfvars`, ni README.
