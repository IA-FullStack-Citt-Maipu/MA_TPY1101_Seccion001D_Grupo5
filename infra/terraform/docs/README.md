# Infraestructura y Deploy (Indice)

Este directorio documenta de forma integral la infraestructura Terraform y el
despliegue automatico hacia GCP.

## Documentos

1. [01-architecture.md](./01-architecture.md)
   - Arquitectura de alto nivel, componentes y flujo de datos.
2. [02-terraform-structure.md](./02-terraform-structure.md)
   - Estructura de carpetas, modulos y contratos de variables/outputs.
3. [03-gcp-bootstrap.md](./03-gcp-bootstrap.md)
   - Preparacion de GCP: proyectos, APIs, bucket tfstate, IAM y WIF.
4. [04-github-actions-deploy.md](./04-github-actions-deploy.md)
   - Workflows de deploy y gobernanza Terraform (plan/apply).
5. [05-secrets-and-config.md](./05-secrets-and-config.md)
   - Modelo oficial de secretos y variables por entorno.
6. [06-operations-runbook.md](./06-operations-runbook.md)
   - Operacion diaria: plan/apply, promocion, rollback y validaciones.
7. [07-cost-optimization.md](./07-cost-optimization.md)
   - Costos, tradeoffs y controles para minimizar gasto.
8. [08-troubleshooting.md](./08-troubleshooting.md)
   - Fallas comunes y resolucion paso a paso.
9. [09-env-vars-and-secrets-guide.md](./09-env-vars-and-secrets-guide.md)
   - Guia practica para agregar variables/secrets de forma consistente.
10. [10-inventory-import-governance.md](./10-inventory-import-governance.md)
   - Inventario de recursos existentes, import a Terraform y politica anti-drift.
11. [11-terraform-github-secrets-operations.md](./11-terraform-github-secrets-operations.md)
   - Guia operativa completa para trabajar con Terraform + GitHub Secrets/Variables.

## Alcance actual

- Runtime: Cloud Run.
- Imagenes: Artifact Registry.
- Estado Terraform: GCS bucket remoto.
- Secretos runtime: Secret Manager.
- Integracion CI/CD: GitHub Actions + Workload Identity Federation (OIDC).
- Base de datos: Supabase externa (no provisionada por Terraform).

## Estado de ambientes

- `dev`: apply automatico para IaC en cambios de infra sobre rama `dev`.
- `dev`: actualmente usa dominios `https://dev.panol.cl` y `https://api.dev.panol.cl`.
- `dev`: backend con `min instances = 1` para reducir cold start de login.
- `prod`: apply manual con aprobacion.
- Si `backend_domain` y `frontend_domain` quedan vacios, Cloud Run opera con
  URLs nativas `*.run.app`.
