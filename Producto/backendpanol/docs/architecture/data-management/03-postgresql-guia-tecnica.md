# 03 - PostgreSQL: Guia Tecnica Vigente

- Estado del documento: vigente
- Ultima verificacion: 2026-06-28
- Fuente de verdad: `Producto/databasepanol/migrations/v25/V25..V47` + `migrations/README.md`

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

- `fn_prepare_loan`
- `fn_deliver_loan`
- `fn_complete_loan`
- `fn_cancel_loan`
- `fn_expire_pending_loans`
- `fn_mark_overdue_loans`
- `fn_loan_change_status`

Compatibilidad tecnica:

- `fn_approve_loan` sigue existiendo como funcion legacy/compatibilidad y para migraciones historicas, pero ya no es la ruta operativa normal de creacion de prestamos V2.

Flujo vigente para prestamos nuevos:

- Los prestamos nuevos se insertan directamente en `approved`; `pending` queda como literal legacy del enum, no como estado operativo del flujo V2.
- `approved` representa reserva logica completa, no aprobacion manual visible en UI.
- `approved -> prepared` ocurre por accion explicita del coordinador y hoy no tiene ventana horaria adicional en la capa de aplicacion.
- `prepared -> delivered` ocurre al entregar y permite variaciones reales de cantidades, `asset_codes` e implementos adicionales.
- Los `consumable` entregados se cierran en la entrega; si no quedan retornables pendientes, el prestamo pasa a `completed` automaticamente.
- `delivered/overdue -> completed` ocurre por cierre directo o por payload de devolucion con desglose de retorno.

Reglas relevantes:

- Si la reserva completa no cabe en la ventana horaria, la creacion completa falla.
- La reserva al crear/editar no mueve stock fisico; eso ocurre en `fn_prepare_loan`.
- `fn_get_implement_availability` diferencia disponibilidad por tipo:
  - `consumable`: descuenta reservas activas globales en `approved/prepared`, aun fuera del rango solicitado.
  - `reusable` e `individual`: descuentan solo prestamos con traslape real en la ventana.
- `fn_prepare_loan` reconcilia `stock.reserved` con lo que ya estaba reservado logicamente por `loan_detail.reserved_quantity`.
- `loan_detail.returned_quantity` pasa a ser parte canonica del detalle operativo.
- `loan_detail.returned_quantity` solo cuenta retorno util/bueno; `damaged_quantity`, `lost_quantity`, `consumed_quantity` y `discarded_quantity` mantienen el resto del cierre.
- `loan_detail.reserved_quantity` puede preservarse con valor historico en `cancelled` y `expired` sin entrega, aunque la reserva operativa ya haya sido liberada.
- Para `individual`, `return_condition_enum` mapea a `individual.status` asi: `good -> available`, `damaged -> damaged`, `lost -> blocked`, `discarded -> retired`.
- Los triggers de notificacion deben omitir avisos al docente cuando `to_status = approved`.

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

- [migrations/README.md](./migrations/README.md)
- [16-catalogo-bd-v31.md](./16-catalogo-bd-v31.md) como catalogo historico congelado hasta V31
