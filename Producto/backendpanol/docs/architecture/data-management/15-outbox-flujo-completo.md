# 15 - Outbox: Flujo Completo y Limites

- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `OutboxService`, `OutboxWorker`, `OutboxJooqRepository`, `db/migration/v25/V25__schema_alignment_big_bang.sql`

## Objetivo

Definir el uso operativo de outbox en modelo PostgreSQL-only.

## Componentes

- `outbox_event`: tabla canonica de integracion eventual.
- `OutboxService.enqueue`: insercion transaccional del evento.
- `OutboxWorker`: procesamiento asincrono y reintentos.
- `OutboxJooqRepository`: acceso SQL type-safe.

## Flujo canonico

1. Caso de uso persiste negocio en SQL.
2. En la misma transaccion inserta evento en `outbox_event` con `PENDING`.
3. Commit confirma negocio + evento.
4. Worker procesa y pasa a `PROCESSING`.
5. Resultado: `SENT` o `FAILED` (con `retry_count`).

## No reemplaza otras capas

- `outbox_event`: integracion eventual.
- `audit_log`: auditoria funcional.
- logs tecnicos: observabilidad de infraestructura.
