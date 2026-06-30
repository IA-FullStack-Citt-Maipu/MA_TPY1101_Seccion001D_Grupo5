# 10 - Inventario, Import y Gobernanza Terraform

Esta guía establece el flujo para que Terraform tenga control total de recursos en cloud y evitar drift.

## Objetivo

- Detectar recursos existentes fuera de Terraform.
- Importarlos al estado para gobernarlos desde código.
- Operar cambios solo por PR + CI/CD.

## Flujo estándar

1. Inventariar recursos existentes por entorno (`dev` y `prod`).
2. Comparar inventario con lo definido en `infra/terraform/environments/<env>`.
3. Agregar o ajustar bloques `.tf` faltantes.
4. Ejecutar `terraform import` de cada recurso no gestionado.
5. Ejecutar `terraform plan` hasta obtener un plan limpio y esperado.
6. Habilitar política operativa: no cambios manuales en consola para recursos gestionados.

## Recursos a inventariar (mínimo)

- Cloud Run services (`backend`, `frontend`).
- Secret Manager secrets y versiones.
- Service Accounts e IAM bindings usados por runtime/deploy.
- Artifact Registry repositories.
- Cualquier recurso adicional asociado al stack (por ejemplo dominios o DNS si se agregan).

## Import por entorno

Ejemplo (dev):

```bash
cd infra/terraform/environments/dev
terraform init
# agregar aquí los imports necesarios según inventario
terraform import <address> <resource-id>
terraform plan
```

Repetir en `prod` con su backend/state.

## Criterio de cierre de importación

El entorno queda “gobernado” cuando:

- Todos los recursos críticos están en state.
- `terraform plan` no muestra cambios inesperados.
- Los cambios posteriores se aplican exclusivamente vía pipeline.

## Política anti-drift

- Cambios manuales en consola: solo emergencia, con ticket y post-mortem.
- Toda emergencia debe traducirse a código Terraform en el siguiente PR.
- Etiquetas obligatorias en recursos: `managed_by=terraform`, `environment`, `project`.

## Runbook de actualización de secretos

1. Actualizar valor en GitHub Secret del entorno.
2. Ejecutar workflow `Terraform Plan/Apply` (dev/prod).
3. Verificar nueva versión del secreto en Secret Manager.
4. Verificar rollout de revisión en Cloud Run.

## Verificaciones operativas

- `terraform validate` y `terraform plan` por entorno.
- Logs de Cloud Run sin errores de lectura de secretos.
- Recursos críticos con labels esperados.
