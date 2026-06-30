# 05 - Backend: Integracion con Datos

- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: servicios de aplicacion, `OutboxService`, `OutboxWorker`, `outbox_event`

## Patron vigente

1. Caso de uso valida input y permisos.
2. Escribe estado de negocio en SQL.
3. Encola evento outbox en la misma transaccion si corresponde.
4. Confirma transaccion.
5. Worker asincrono publica evento/proyeccion y actualiza estado (`PENDING`/`PROCESSING`/`SENT`/`FAILED`).

## Idempotencia

- `event_id` estable por evento.
- Publicacion con `upsert` por identificador logico en destino.
- Reintentos sin duplicar efecto funcional.

## Observabilidad minima

- Conteo por estado de outbox.
- Seguimiento de retries y estados de error (`FAILED`).
- Correlacion por evento y agregado.

## Alcances

- Endpoints operativos principales consumen estado SQL.
- Vistas de trazabilidad/eventos consumen proyecciones asincrónicas del mecanismo de outbox.

## Compatibilidad

Este documento refleja el modelo canónico actual: estado transaccional en PostgreSQL con integración eventual mediante `outbox_event`.
