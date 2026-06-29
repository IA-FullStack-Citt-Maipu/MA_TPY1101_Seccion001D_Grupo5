# Backend Docs

- Estado del documento: vigente
- Ultima verificacion: 2026-06-28
- Fuente de verdad: controllers V2, SecurityConfig, application.yaml

## Alcance

Guia operativa del backend para rutas publicas, seguridad, errores y convenciones
de uso entre frontend y backend.

### Modelo de datos vigente

- Estado canonico de dominio: **PostgreSQL (Supabase en produccion/desarrollo segun APP_DB_ENV)**.
- El catalogo de implementos usa `item_type` con valores:
  - `consumable`
  - `reusable`
  - `individual`
- El flujo de integracion asincrona usa tabla `public.outbox_event`
  (estados `PENDING`, `PROCESSING`, `SENT`, `FAILED`).

### Regla de identidad de datos

- Persistencia interna (DB/jOOQ): `id` numerico.
- Contrato externo (API/frontend/logs de cliente): `uuid`.
- No se exponen ids internos en payloads publicos.

## API publica actual

Base publica: `/api/v2/**`

### Auth
- `POST /api/v2/auth/login`
- `POST /api/v2/auth/refresh`
- `POST /api/v2/auth/logout`
- `GET /api/v2/auth/me`
- `POST /api/v2/auth/me/bot-token`
- `GET /api/v2/auth/me/sessions`
- `DELETE /api/v2/auth/me/sessions/{sessionId}`
- `PATCH /api/v2/auth/me/email`
- `PATCH /api/v2/auth/me/password`

### Users
- `GET /api/v2/users`
- `POST /api/v2/users`
- `PUT /api/v2/users/{userUuid}`
- `PUT /api/v2/users/{userUuid}/role`
- `PATCH /api/v2/users/{userUuid}/active`
- `DELETE /api/v2/users/{userUuid}`

### Categories
- `GET /api/v2/categories/active`
- `GET /api/v2/categories/gestion`
- `GET /api/v2/categories/{categoryUuid}/associations`
- `POST /api/v2/categories`
- `PUT /api/v2/categories/{categoryUuid}`
- `PATCH /api/v2/categories/{categoryUuid}/deactivate`
- `PATCH /api/v2/categories/{categoryUuid}/activate`
- `DELETE /api/v2/categories/{categoryUuid}`

### Loans
- `GET /api/v2/loans` (`page`, `size`, `mine`, opcional `from`/`to`)
- `GET /api/v2/loans/{loanUuid}`
- `GET /api/v2/loans/{loanUuid}/state-dates`
- `GET /api/v2/loans/{loanUuid}/status-timeline`
- `GET /api/v2/loans/{loanUuid}/return-context`
- `POST /api/v2/loans`
- `PATCH /api/v2/loans/{loanUuid}`
- `PATCH /api/v2/loans/{loanUuid}/cancel`
- `POST /api/v2/loans/{loanUuid}/prepare`
- `POST /api/v2/loans/{loanUuid}/delivery`
- `POST /api/v2/loans/{loanUuid}/return`
- `POST /api/v2/loans/{loanUuid}/complete`

### Locations
- `GET /api/v2/locations`
- `GET /api/v2/locations/management`
- `GET /api/v2/locations/{locationUuid}/associations`
- `POST /api/v2/locations`
- `PUT /api/v2/locations/{locationUuid}`
- `PATCH /api/v2/locations/{locationUuid}/active`
- `DELETE /api/v2/locations/{locationUuid}`

### Implements
- `GET /api/v2/implements`
- `GET /api/v2/implements/{implementUuid}`
- `POST /api/v2/implements`
- `PUT /api/v2/implements/{implementUuid}`
- `PATCH /api/v2/implements/{implementUuid}/active`

### Stock y movimientos
- `GET /api/v2/implements/movements`
- `POST /api/v2/implements/{implementUuid}/movements`
- `GET /api/v2/implements/{implementUuid}/stock`
- `POST /api/v2/implements/{implementUuid}/stock/entries`
- `POST /api/v2/implements/{implementUuid}/stock/movements`
- `PUT /api/v2/implements/{implementUuid}/stock/individuals/{individualUuid}`
- `GET /api/v2/implements/{implementUuid}/labels/pdf`

Valores canonicos de `movement_type`/`action`:
- `STOCK_IN`
- `STOCK_OUT`
- `LOAN_DELIVERY`
- `LOAN_RETURN`
- `DAMAGE_REPORT`
- `MANUAL_ADJUSTMENT`

## Flujo operativo actual de prestamos

- La solicitud nace reservada: `POST /api/v2/loans` crea el prestamo en `approved`, que en UI se lee como `Reservado`.
- La reserva inicial es logica y no mueve stock fisico al crear o editar.
- Para `reusable` e `individual`, la disponibilidad se valida por traslape de la ventana solicitada.
- Para `consumable`, la disponibilidad reservada queda bloqueada globalmente mientras el prestamo siga en `approved` o `prepared`.
- El solicitante puede editar su propia reserva mientras siga en `approved`; `PATCH /api/v2/loans/{loanUuid}` vuelve a validar disponibilidad y reescribe el detalle completo.
- `POST /api/v2/loans/{loanUuid}/prepare` solo requiere que el prestamo este en `approved`; hoy no existe una restriccion horaria adicional en la capa HTTP/aplicacion.
- `POST /api/v2/loans/{loanUuid}/delivery` exige `prepared` y permite variar cantidades, reseleccionar `asset_codes` y agregar implementos adicionales.
- Los items `consumable` se consumen definitivamente al entregar. Si el prestamo entregado solo contiene consumibles, el sistema lo cierra automaticamente en `completed`.
- `POST /api/v2/loans/{loanUuid}/return` se usa para cierres con variacion: `reusable` vuelve por cantidad y `individual` vuelve por unidad con `return_condition = good|damaged|lost|discarded`.
- `POST /api/v2/loans/{loanUuid}/complete` cierra de una vez todos los items retornables pendientes como retorno correcto.
- El detalle publico del item expone `item_type`, `requested_quantity`, `reserved_quantity`, `delivered_quantity` y `returned_quantity`.
- En prestamos activos, `reserved_quantity` representa la reserva operativa vigente; en `cancelled` o `expired` sin entrega puede conservarse como historial aunque la reserva ya haya sido liberada.
- `returned_quantity` solo cuenta retorno util/bueno. Los cierres por `damaged`, `lost`, `discarded` o consumo parcial quedan registrados en el desglose interno del detalle y en movimientos de inventario.

## Reglas funcionales vigentes de prestamos

- `scheduled_at` no puede estar en pasado.
- `scheduled_at` solo permite fechas entre hoy y los proximos 14 dias corridos.
- `scheduled_at` y `expected_return_at` solo permiten lunes a sabado, entre `08:00` y `22:00`.
- `expected_return_at` es opcional, pero si viene debe ser posterior a `scheduled_at`.
- El sistema rechaza la solicitud completa si algun implemento no tiene disponibilidad suficiente segun su regla de disponibilidad vigente (`consumable` global, `reusable/individual` por ventana).
- El sistema bloquea duplicidad/solape del mismo solicitante contra prestamos activos en `pending`, `approved`, `prepared`, `delivered` u `overdue`.

## Trazabilidad y notificaciones

- Las transiciones se auditan en `loan_status_history`.
- La auto-reserva inicial se registra con el usuario de sistema configurado para lifecycle/outbox.
- Aunque el usuario no envie `notes`, el backend persiste una nota operativa por defecto para cada transicion relevante.
- El solicitante no recibe notificacion cuando el prestamo entra en `approved`; las notificaciones visibles parten desde estados operativos posteriores.

## Seguridad vigente

- `permitAll`: `POST /api/v2/auth/login`, `POST /api/v2/auth/logout`, `POST /api/v2/auth/refresh`, `/actuator/health` y `/actuator/info`.
- Rutas bloqueadas: `/api/v1/**` y `/internal/**`.
- Resto de rutas: autenticadas.
- `GET /api/v2/auth/me` y sus `PATCH` son parte del contrato requerido por
  la vista de configuracion del frontend.

## Formato de error publico

```json
{
  "code": "CATEGORY_NAME_DUPLICATE",
  "message": "Ya existe una categoria con el nombre 'Reactivos'",
  "timestamp": "2026-05-15T15:00:00Z"
}
```

## Nota de compatibilidad

No se deben usar rutas legacy (`/api/categorias`, `/api/implements`, `/api/v1/**`)
en clientes nuevos ni en documentacion operativa vigente.
