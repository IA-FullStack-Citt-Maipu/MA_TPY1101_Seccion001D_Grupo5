- Estado del documento: vigente
- Ultima verificacion: 2026-05-16
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 04 - Eventos, Outbox y Consistencia

## Propósito

Tener eventos confiables sin comprometer consistencia transaccional.

## Regla actual

- SQL primero (commit), luego encolado y publicación del evento.

## Regla objetivo

- Patrón Outbox:
  1. Caso de uso escribe estado SQL + evento outbox en la misma transacción (`outbox_event`).
  2. Worker publica evento a destino de integración/cola.
  3. Worker marca estado como `SENT` o `FAILED` según resultado.

## Tipos de eventos

- **Dominio**: cambios relevantes de negocio (`LoanApproved`, `StockReserved`).
- **Integración**: formato estable para consumidores.
- **Auditoría**: quién hizo qué (`audit_log`), no reemplaza tablas maestras.

## Estados

`PENDING`, `PROCESSING`, `SENT`, `FAILED`.

## Idempotencia

- Cada evento con `event_id` único.
- Consumidores toleran reintento.
- `outbox_id` y `event_id` permiten deduplicación donde aplique.

## Errores

- Falla SQL => no hay evento.
- SQL ok y falla publicación => outbox retry.

## Observabilidad mínima

- métricas: eventos pendientes, retries, dead-letter.
- trazas: `event_id`, `aggregate_id`, `occurred_at`.
