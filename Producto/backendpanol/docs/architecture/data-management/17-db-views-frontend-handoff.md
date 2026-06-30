# Handoff IA Frontend - Vistas BD Vigentes

- Fecha: 2026-06-28
- Fuente: `tmp/db-introspect/views.txt` y `tmp/db-introspect/columns.txt`
- Alcance: todas las vistas SQL disponibles hoy en `public`
- Objetivo: documento para que otro agente IA construya pantallas frontend basadas en estas vistas

## 1. Resumen de vistas disponibles

| Vista | Filas por | Uso principal sugerido |
|---|---|---|
| `outbox_events` | evento outbox | Operacion/soporte interno de integraciones |
| `v_active_loan_details` | item por prestamo activo | Panel operativo de prestamos activos |
| `v_individual_status_summary` | implemento (agregado) | Dashboard de estado de individuales |
| `v_loan_requests_calendar` | item por prestamo | Vista calendario/agenda operacional |
| `v_loan_requests_summary` | prestamo (agregado) | Listado principal de prestamos |
| `v_loan_state_dates` | prestamo | Fechas clave por estado de prestamo |
| `v_loan_status_timeline` | transicion de estado | Timeline/auditoria de estados |
| `v_low_stock` | implemento en stock | Alertas y tablero de bajo stock |

## 2. Vistas expuestas por API hoy

| Vista | Endpoint backend actual |
|---|---|
| `v_loan_state_dates` | `GET /api/v2/loans/{loanUuid}/state-dates` |
| `v_loan_status_timeline` | `GET /api/v2/loans/{loanUuid}/status-timeline` |
| `v_loan_requests_summary` | No expuesta directamente (hoy el listado usa query propia sobre tablas) |
| `v_loan_requests_calendar` | No expuesta directamente |
| `v_active_loan_details` | No expuesta directamente |
| `v_low_stock` | No expuesta directamente |
| `v_individual_status_summary` | No expuesta directamente |
| `outbox_events` | No expuesta directamente |

Nota: para frontend web productivo, consumir endpoints backend. Si una vista no tiene endpoint, debe exponerse desde backend antes de usarla en UI.

## 3. Detalle de cada vista

## 3.1 `outbox_events`

- Proposito: lectura simplificada de `outbox_event` para monitoreo de integraciones/eventos.
- Granularidad: 1 fila por evento.
- Columnas:

| Columna | Tipo |
|---|---|
| `event_id` | `uuid` |
| `aggregate_type` | `varchar` |
| `aggregate_id` | `uuid` |
| `event_type` | `varchar` |
| `payload` | `text` |
| `occurred_at` | `timestamptz` |
| `processed_at` | `timestamptz` |
| `retry_count` | `int4` |
| `status` | `outbox_status_enum` |

- UI sugerida: tabla de monitoreo tecnico (no para usuario final docente).

## 3.2 `v_active_loan_details`

- Proposito: detalle operativo de items de prestamos en estados activos (`approved`, `prepared`, `delivered`, `overdue`).
- Granularidad: 1 fila por item de prestamo activo.
- Columnas:

| Columna | Tipo |
|---|---|
| `loan_id` | `int8` |
| `loan_uuid` | `uuid` |
| `status` | `loan_status_enum` |
| `scheduled_at` | `timestamptz` |
| `expected_return_at` | `timestamptz` |
| `requester_id` | `int8` |
| `requester_name` | `varchar` |
| `implement_id` | `int8` |
| `implement_name` | `varchar` |
| `item_type` | `item_type_enum` |
| `requested_quantity` | `int4` |
| `reserved_quantity` | `int4` |
| `delivered_quantity` | `int4` |
| `returned_quantity` | `int4` |

- Nota operativa: `returned_quantity` solo cuenta retorno util/bueno. El cierre por dano, perdida, descarte o consumo parcial vive en columnas internas de `loan_detail` y no se refleja en este contador.

- UI sugerida: tablero coordinador para seguimiento de items en curso.

## 3.3 `v_individual_status_summary`

- Proposito: resumen por implemento de unidades individuales por estado.
- Granularidad: 1 fila por implemento.
- Columnas:

| Columna | Tipo |
|---|---|
| `implement_id` | `int8` |
| `implement_name` | `varchar` |
| `total_individuals` | `int4` |
| `available_count` | `int4` |
| `loaned_count` | `int4` |
| `maintenance_count` | `int4` |
| `damaged_count` | `int4` |
| `blocked_count` | `int4` |
| `retired_count` | `int4` |

- UI sugerida: cards + barras apiladas para salud de individuales.

## 3.4 `v_loan_requests_calendar`

- Proposito: dataset de agenda de prestamos con detalle por item.
- Granularidad: 1 fila por item de prestamo.
- Columnas:

| Columna | Tipo |
|---|---|
| `loan_id` | `int8` |
| `loan_uuid` | `uuid` |
| `status` | `loan_status_enum` |
| `status_label` | `text` |
| `scheduled_at` | `timestamptz` |
| `expected_return_at` | `timestamptz` |
| `created_at` | `timestamptz` |
| `requester_id` | `int8` |
| `requester_name` | `varchar` |
| `requester_email` | `varchar` |
| `room_id` | `int8` |
| `room_name` | `varchar` |
| `subject_id` | `int8` |
| `subject_code` | `varchar` |
| `subject_name` | `varchar` |
| `implement_id` | `int8` |
| `implement_name` | `varchar` |
| `item_type` | `item_type_enum` |
| `requested_quantity` | `int4` |
| `reserved_quantity` | `int4` |
| `delivered_quantity` | `int4` |
| `approved_at` | `timestamptz` |
| `prepared_at` | `timestamptz` |
| `delivered_at` | `timestamptz` |
| `completed_at` | `timestamptz` |
| `returned_quantity` | `int4` |

- UI sugerida: calendario semanal/mensual con drawer de detalle de items.

## 3.5 `v_loan_requests_summary`

- Proposito: resumen agregado por prestamo (incluye detalle JSON de items).
- Granularidad: 1 fila por prestamo.
- Columnas:

| Columna | Tipo |
|---|---|
| `loan_id` | `int8` |
| `loan_uuid` | `uuid` |
| `status` | `loan_status_enum` |
| `status_label` | `text` |
| `scheduled_at` | `timestamptz` |
| `expected_return_at` | `timestamptz` |
| `created_at` | `timestamptz` |
| `requester_id` | `int8` |
| `requester_name` | `varchar` |
| `requester_email` | `varchar` |
| `room_id` | `int8` |
| `room_name` | `varchar` |
| `subject_id` | `int8` |
| `subject_code` | `varchar` |
| `subject_name` | `varchar` |
| `total_implement_types` | `int4` |
| `total_requested_quantity` | `int4` |
| `total_reserved_quantity` | `int4` |
| `total_delivered_quantity` | `int4` |
| `approved_at` | `timestamptz` |
| `prepared_at` | `timestamptz` |
| `delivered_at` | `timestamptz` |
| `completed_at` | `timestamptz` |
| `details` | `jsonb` |
| `total_returned_quantity` | `int4` |

- Campo `details` (jsonb): arreglo de objetos con:
  - `implement_id`
  - `implement_name`
  - `item_type`
  - `requested_quantity`
  - `reserved_quantity`
  - `delivered_quantity`
  - `returned_quantity`

- Nota operativa: `returned_quantity` y `total_returned_quantity` suman solo retorno util/bueno; no agregan cierres por dano, perdida, descarte o consumo.

- UI sugerida: tabla principal de prestamos + expansion por fila para ver items.

## 3.6 `v_loan_state_dates`

- Proposito: fechas pivoteadas por estado para un prestamo.
- Granularidad: 1 fila por prestamo.
- Columnas:

| Columna | Tipo |
|---|---|
| `loan_id` | `int8` |
| `approved_at` | `timestamptz` |
| `prepared_at` | `timestamptz` |
| `delivered_at` | `timestamptz` |
| `completed_at` | `timestamptz` |
| `rejected_at` | `timestamptz` |
| `cancelled_at` | `timestamptz` |
| `expired_at` | `timestamptz` |
| `overdue_at` | `timestamptz` |

- Endpoint actual para frontend: `GET /api/v2/loans/{loanUuid}/state-dates`.
- UI sugerida: bloque "hitos del prestamo" en vista detalle.

## 3.7 `v_loan_status_timeline`

- Proposito: trazabilidad completa de transiciones de estado.
- Granularidad: 1 fila por cambio de estado.
- Columnas:

| Columna | Tipo |
|---|---|
| `history_id` | `int8` |
| `loan_id` | `int8` |
| `loan_uuid` | `uuid` |
| `from_status` | `loan_status_enum` |
| `to_status` | `loan_status_enum` |
| `actor_user_id` | `int8` |
| `actor_name` | `varchar` |
| `actor_email` | `varchar` |
| `notes` | `text` |
| `changed_at` | `timestamptz` |

- Endpoint actual para frontend: `GET /api/v2/loans/{loanUuid}/status-timeline`.
- UI sugerida: timeline vertical (fecha, actor, transicion, nota).

## 3.8 `v_low_stock`

- Proposito: detectar implementos en bajo stock o sin stock.
- Granularidad: 1 fila por implemento con registro en `stock`.
- Columnas:

| Columna | Tipo |
|---|---|
| `implement_id` | `int8` |
| `implement_uuid` | `uuid` |
| `implement_name` | `varchar` |
| `category_id` | `int8` |
| `category_name` | `varchar` |
| `location_id` | `int8` |
| `location_name` | `varchar` |
| `item_type` | `item_type_enum` |
| `total_stock` | `int4` |
| `min_stock` | `int4` |
| `available` | `int4` |
| `reserved` | `int4` |
| `loaned` | `int4` |
| `damaged` | `int4` |
| `stock_gap` | `int4` |
| `stock_status` | `text` (`out_of_stock`, `low_stock`, `ok`) |
| `updated_at` | `timestamptz` |

- UI sugerida: tabla de alertas + chips por `stock_status`.

## 4. Reglas de implementacion para el agente IA (frontend)

1. No consumir BD directa desde frontend productivo; consumir backend API.
2. Si la vista no tiene endpoint hoy, primero pedir/implementar endpoint backend de solo lectura.
3. Respetar enums vigentes:
   - `loan_status_enum`: `pending, approved, prepared, delivered, completed, rejected, cancelled, expired, overdue`
   - `item_type_enum`: `consumable, reusable, individual`
   - `outbox_status_enum`: `PENDING, PROCESSING, SENT, FAILED`
4. No inferir transiciones de estado en UI; la validez la decide backend.
5. Fechas en UI: usar timezone del usuario y mostrar formato local.

## 5. Prompt corto recomendado para otro agente IA

```text
Construye pantallas frontend usando estas vistas SQL como contrato de lectura.
Prioriza:
1) Listado de prestamos (v_loan_requests_summary)
2) Calendario de prestamos (v_loan_requests_calendar)
3) Detalle de prestamo con hitos y timeline (v_loan_state_dates + v_loan_status_timeline)
4) Alertas de bajo stock (v_low_stock)
5) Resumen de individuales (v_individual_status_summary)

No inventes transiciones de estado ni calculos de stock en frontend.
Si un dataset no tiene endpoint backend, marca dependencia de API de solo lectura.
```

