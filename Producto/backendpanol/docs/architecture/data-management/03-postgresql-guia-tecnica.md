# 03 - PostgreSQL: Guia Tecnica V31

- Estado del documento: vigente
- Ultima verificacion: 2026-05-31
- Fuente de verdad: `db/migration/v25/V25..V31` + `16-catalogo-bd-v31.md`

## Rol de PostgreSQL

PostgreSQL mantiene el estado canonico de usuarios, catalogo, stock, prestamos, auditoria, notificaciones y outbox.

## Regla de identidad de datos

- `id` numerico: uso interno de persistencia (joins, FKs, indices, jOOQ).
- `uuid`: contrato externo de API/frontend para evitar exposicion de ids internos.

## Enums canonicos vigentes

- `loan_status_enum`: `pending`, `approved`, `prepared`, `delivered`, `completed`, `rejected`, `cancelled`, `expired`, `overdue`.
- `item_type_enum`: `consumable`, `reusable`, `individual`.
- `individual_status_enum`: `available`, `loaned`, `maintenance`, `damaged`, `blocked`, `retired`.
- `individual_condition_enum`: `good`, `damaged_repairable`, `damaged_no_diagnosis`, `irreparable`.
- `individual_allocation_status_enum`: `reserved`, `prepared`, `delivered`, `returned`, `cancelled`.
- `return_condition_enum`: `good`, `damaged`, `lost`, `discarded`.
- `inventory_movement_type_enum`: `stock_in`, `stock_out`, `loan_delivery`, `loan_return`, `damage_report`, `manual_adjustment`, `consumption`, `discard`, `loss`.
- `outbox_status_enum`: `PENDING`, `PROCESSING`, `SENT`, `FAILED`.

## Flujo DB-first de prestamos

Las transiciones y el stock se ejecutan en funciones SQL:

- `fn_approve_loan`
- `fn_prepare_loan`
- `fn_deliver_loan`
- `fn_complete_loan`
- `fn_cancel_loan`
- `fn_expire_pending_loans`
- `fn_mark_overdue_loans`
- `fn_loan_change_status`

Trazabilidad:

- `loan_status_history`
- `v_loan_state_dates`
- `v_loan_status_timeline`

## Vistas operativas relevantes

- `v_loan_requests_summary`
- `v_loan_requests_calendar`
- `v_active_loan_details`
- `v_low_stock`
- `v_individual_status_summary`
- `outbox_events`

## Integracion asincrona (Outbox)

- Tabla operativa: `outbox_event`.
- Flujo de estado: `PENDING -> PROCESSING -> SENT/FAILED`.
- Triggers de negocio que publican a outbox/notificacion desde `loan_status_history`.

## Referencia de detalle

Para estructura completa (tablas, columnas, constraints, indices, funciones, triggers, politicas RLS y migraciones), revisar:

- [16-catalogo-bd-v31.md](./16-catalogo-bd-v31.md)
