# 07 - Optimización de Costos

## Fuentes de costo principales

- Cloud Run (CPU/RAM por request o instancia mínima)
- Artifact Registry (almacenamiento de imágenes)
- Secret Manager (secretos/versiones y operaciones)
- GCS tfstate (almacenamiento + operaciones)
- Egress de red

## Ajustes aplicados

- Backend dev con `min_instance_count=1` para evitar cold starts en login.
- Frontend dev con `min_instance_count=0` para mantener costo bajo donde la latencia inicial no rompe el flujo.
- Concurrency y timeout moderados en dev.
- Cancelación de pipelines redundantes.
- Rotación de secretos no automática en cada push.

## Recomendaciones adicionales

1. **Retention de imágenes** en Artifact Registry:
   - conservar últimas N imágenes por servicio.
2. Reducir frecuencia de deploy en dev:
   - agrupar commits cuando sea posible.
3. Mantener `min_instance_count > 0` solo en servicios donde el cold start afecte el flujo principal.
4. Medir cold starts vs costo antes de subir mínimos en otros servicios o en prod.

## Política sugerida por entorno

- `dev`: backend tibio para UX (`backend min=1`) y resto con costo contenido (`frontend min=0`, max bajo)
- `prod`: balance costo/latencia (definir min instances según SLA)

## Señales para revisar costo

- aumento rápido de storage en registry
- gran número de versiones de secretos
- picos de egress no esperados
- muchos deploys redundantes por commit frecuente
