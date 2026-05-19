- Estado del documento: vigente
- Ultima verificacion: 2026-05-16
- Fuente de verdad: ver matriz canonica vigente y arquitectura v25

# 08 - Baseline Remediacion Arquitectonica

## Rama de trabajo

- `chore/architecture-hardening-full`

## Incumplimientos detectados al inicio

- Dependencias directas de `application` a `infrastructure`.
- Uso de `DSLContext` en `api`/`application`.
- Dependencias de framework en `domain`.
- Ausencia de tabla/worker Outbox.
- Reglas ArchUnit insuficientes para capas hexagonales y contratos cross-modulo.

## Estado de cierre aplicado

- `auth`: puertos de dominio (`UserAuthPort`, `TokenRevocationPort`, `AuditLogPort`) + adapters JOOQ.
- `users`: persistencia SQL extraida a `UserAdminRepository` + `UserAdminJooqRepository`.
- `catalog/stock`: persistencia por repositorio de dominio (`stock`, movimientos, individuos) y contratos cruzados controlados.
- `catalog/implement`: consulta de movimientos via contrato explicito (`InventoryMovementQueryContract`).
- `domain` de movimientos desacoplado de framework.
- Outbox operativo base:
  - migracion `db/migration/v25/V25__schema_alignment_big_bang.sql` (`outbox_event` como tabla base + vista `outbox_events` compatibilidad),
  - escritura de eventos desde casos críticos (`auth`, `users`, inventario/movimientos),
  - worker con reintentos e idempotencia por `event_id`.
- ArchUnit endurecido para bloquear regresiones por capas y por frontera de módulo.

## Criterio de salida y pipeline

- [x] Cero `DSLContext` en `api`.
- [x] Cero imports `..infrastructure..` en `..application..`.
- [x] `..domain..` sin dependencias `spring/jooq/jakarta`.
- [x] `api`/`application` cross-modulo solo via `application.contract` (o excepción documentada).
- [x] `domain` sin dependencia a `api/application/infrastructure`.
- [x] Outbox table + worker implementados.
- [x] Documentacion arquitectonica actualizada.
- [x] Pipeline debe fallar ante violacion ArchUnit.
