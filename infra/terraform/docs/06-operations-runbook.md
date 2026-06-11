# 06 - Operacion y Runbook Diario

## Flujo diario en dev

1. Commit/push a `dev`.
2. GitHub Actions despliega automaticamente.
3. Validar outputs de job y dominio activo (`https://dev.panol.cl` /
   `https://api.dev.panol.cl` en dev actual, o `run.app` si no hay dominio custom).
4. Probar endpoints criticos (health, endpoints negocio).

## Promocion a prod

1. Confirmar cambios validados en dev.
2. Ejecutar workflow manual `Deploy GCP` con `environment=prod`.
3. Monitorear metricas de error/latencia y logs de arranque.

## Rollback rapido

Opcion recomendada:

- redeploy con imagen anterior (`backend_image` / `frontend_image` tag previo) y `terraform apply`.

## Validaciones post deploy

- Cloud Run revisions: revision activa esperada.
- Health endpoint backend responde.
- Frontend carga y apunta a backend correcto.
- Secret env vars presentes.
- Dominio frontend y backend resuelven al servicio correcto.
- `panol-backend-dev` conserva `minScale=1` en dev.

## Cambios en variables de infraestructura

Cuando cambie una variable de configuracion:

1. actualizar GitHub Environment var/secret
2. ejecutar deploy (push o manual)
3. verificar que la revision use los nuevos valores

## Operacion sin dominio custom

Con `backend_domain=""` y `frontend_domain=""`:

- se usan URLs nativas `run.app`.
- no hay dependencia de DNS externo.
- costo operativo mas bajo y configuracion mas simple.
