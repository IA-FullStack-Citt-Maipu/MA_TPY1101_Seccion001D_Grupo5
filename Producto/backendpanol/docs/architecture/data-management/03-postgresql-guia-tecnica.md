# 03 - PostgreSQL: Guia Tecnica V25

- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `db/migration/v25/V25__schema_alignment_big_bang.sql` + repositorios jOOQ

## Rol de PostgreSQL

PostgreSQL mantiene el estado canonico de usuarios, catalogo, stock, prestamos, auditoria y outbox.

## Regla de identidad de datos

- `id` numerico: uso interno de persistencia (joins, FKs, indices, jOOQ).
- `uuid`: contrato externo de API/frontend para evitar exposicion de ids internos.

## Enums canonicos V25

- `item_type_enum`: `fungible`, `no_fungible`
- `individual_status_enum`: `available`, `loaned`, `maintenance`, `damaged`
- `individual_condition_enum`: `good`, `fair`, `poor`
- `loan_status_enum`: `pending`, `approved`, `rejected`, `delivered`, `completed`, `cancelled`
- `inventory_movement_type_enum`: `STOCK_IN`, `STOCK_OUT`, `LOAN_DELIVERY`, `LOAN_RETURN`, `DAMAGE_REPORT`, `MANUAL_ADJUSTMENT`
- `outbox_status_enum`: `PENDING`, `PROCESSING`, `SENT`, `FAILED`

## Estructura clave consolidada

- `implement`: `UNIQUE(name, category_id)`.
- `room`: `active` para soft-delete.
- `individual`: columna `notes`.
- `loan_detail`: checks de consistencia de cantidades.
- `stock`: checks de no-negatividad e invariante de contadores.
- `outbox_event`: tabla canonica de integracion asincrona.

## Outbox en PostgreSQL

- Tabla operativa: `outbox_event`.
- Flujo de estado: `PENDING -> PROCESSING -> SENT/FAILED`.
- Campos de control: `retry_count`, `occurred_at`, `processed_at`.
