# 11 - Guia Operativa Terraform + GitHub Secrets/Variables

Esta guia explica el flujo diario para operar Terraform con control total de configuracion y secretos.

## 1) Regla base

- Terraform define que recursos existen y como se configuran.
- GitHub Secrets/Variables proveen los valores por entorno.
- Cloud no se modifica manualmente para recursos gobernados.

## 2) Cuando agregar una configuracion nueva

### Si es sensible

1. Definir variable sensible en `variables.tf` del entorno (`sensitive = true`).
2. Conectar esa variable en `module.secret_manager.secret_values`.
3. Referenciar secreto en `secret_env_vars` del servicio que lo usa.
4. Crear GitHub Secret `_DEV` y `_PROD`.
5. Mapearlo en workflow a `TF_VAR_*`.
6. Ejecutar `Terraform Plan/Apply`.

### Si no es sensible

1. Definir variable en `variables.tf`.
2. Conectar en `locals.backend_env` o `locals.frontend_env`.
3. Crear GitHub Variable `_DEV` y `_PROD`.
4. Mapearla en workflow a `TF_VAR_*`.
5. Ejecutar `Terraform Plan/Apply`.

## 3) Flujo de trabajo recomendado (PR)

1. Cambiar codigo Terraform en rama feature.
2. Ejecutar localmente (`fmt`, `validate`, `plan`) si tienes Terraform instalado.
3. Abrir PR.
4. Merge a `dev` para apply automatico en entorno dev.
5. Verificar servicio/logs.
6. Promover a `prod` por `workflow_dispatch` con aprobacion.

## 4) Rotacion de secretos (sin tocar consola)

1. Actualizar valor en GitHub Secret del entorno.
2. Lanzar workflow `Terraform Plan/Apply` con `apply=true`.
3. Terraform publicara nueva version del secreto (`latest`).
4. Verificar nueva revision de Cloud Run.

## 5) Controles para evitar fallos

- No dejar `TF_VAR_*` requerido sin valor.
- Mantener nombres consistentes entre Terraform y GitHub.
- No mezclar dato sensible en GitHub Variable publica.
- No usar `-target` salvo recuperacion puntual.

## 6) Drift y gobernanza

- Si alguien cambia cloud manualmente, aparecera drift en `plan`.
- Corregir siempre por codigo Terraform.
- Si hay recurso existente fuera de state, inventariar e importar (`terraform import`).

## 7) Checklist rapido antes de aplicar

1. Variables/secrets del entorno completos.
2. `terraform validate` sin errores.
3. `terraform plan` esperado.
4. En `prod`, aprobacion manual activa.

## 8) Checklist post-apply

1. Secretos con version `latest` esperada.
2. Cloud Run con revision nueva y estable.
3. Logs sin errores de configuracion o secretos faltantes.
4. Login y endpoints principales funcionando.
