- Estado del documento: vigente
- Ultima verificacion: 2026-05-16
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 10 - Runbook Operacional Outbox

## Señales operacionales

- `pending_count`: eventos pendientes por publicar.
- `retry_count`: eventos con al menos un reintento.
- `failed_count`: eventos en estado `FAILED`.
- `publish_success_rate`: tasa de éxito de publicación del worker.

Las métricas se reportan en logs estructurados por `OutboxWorker`:
- `outbox_event_published`
- `outbox_event_publish_failed`
- `outbox_metrics`

## Política de reintentos

- `MAX_RETRIES = 5`.
- Mientras `retry_count < 5`, el estado se mantiene `PENDING`.
- Al llegar a 5 intentos fallidos, el evento pasa a `FAILED`.

## Recuperación manual (dead-letter lógico)

1. Identificar eventos `FAILED` en `outbox_event` (o `outbox_events` por compatibilidad).
2. Corregir causa raíz (error de destino, payload inválido, red/cola, etc.).
3. Reencolar eventos fallidos:
   - set `status = 'PENDING'`
   - opcionalmente reset `retry_count = 0`
4. Monitorear logs `outbox_event_published` y `outbox_metrics`.

## Consultas útiles

```sql
SELECT status, COUNT(*) FROM outbox_event GROUP BY status;
SELECT COUNT(*) FROM outbox_event WHERE retry_count > 0;
SELECT * FROM outbox_events WHERE status = 'FAILED' ORDER BY occurred_at ASC LIMIT 100;
```
