# 01 - Arquitectura de Infraestructura

## Objetivo

Desplegar backend y frontend del proyecto en GCP con IaC, separando responsabilidades
de infraestructura, secretos y pipeline.

## Componentes principales

- **Cloud Run (backend)**: ejecuta la API Java/Spring Boot.
- **Cloud Run (frontend)**: ejecuta el frontend estatico servido por Nginx.
- **Artifact Registry**: almacena imagenes Docker versionadas.
- **Secret Manager**: almacena secretos sensibles del runtime.
- **GCS (tfstate)**: backend remoto de Terraform.
- **IAM + Workload Identity Federation**: autenticacion segura GitHub -> GCP sin llaves JSON.

## Flujo general

1. Desarrollador hace push a `dev`.
2. GitHub Actions autentica con GCP usando OIDC/WIF.
3. Workflow construye imagenes backend/frontend.
4. Workflow publica imagenes en Artifact Registry.
5. Terraform aplica cambios en Cloud Run usando imagenes nuevas.
6. Cloud Run expone URLs nativas `run.app` o dominios custom, segun
   `backend_domain` y `frontend_domain`.

## Diagrama logico (simplificado)

```text
GitHub Repo
  `-- GitHub Actions (deploy-gcp.yml)
      |-- OIDC -> Workload Identity Provider (GCP)
      |-- Impersonate Service Account (deploy)
      |-- docker build/push -> Artifact Registry
      `-- terraform apply -> Cloud Run + Secret Manager + IAM

Cloud Run (frontend) ---> Cloud Run (backend) ---> Supabase (externo)
```

## Principios aplicados

- **Sin secretos hardcodeados** en codigo o tfvars.
- **IaC modular** por ambientes (`dev`/`prod`) con modulos reutilizables.
- **Menor superficie de credenciales** (OIDC en vez de keys JSON).
- **Dev con backend caliente** (`min instances = 1`) para evitar cold starts
  severos en autenticacion.
