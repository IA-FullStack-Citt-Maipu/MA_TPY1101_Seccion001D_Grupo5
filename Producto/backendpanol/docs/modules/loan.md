# Modulo: loan

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `LoanV2Controller`, `SolicitarPrestamoUseCase`, `GestionPrestamoUseCase`, `LoanJooqAdapter`, migraciones `V43` a `V51`

## Responsabilidad

Gestion del flujo completo de prestamos: solicitud, reserva automatica, preparacion, entrega, devolucion, cierre, timeline y contexto operativo de retorno.

## API vigente

Base path: `/api/v2/loans`

- `GET /`
- `GET /{loanUuid}`
- `GET /{loanUuid}/state-dates`
- `GET /{loanUuid}/status-timeline`
- `GET /{loanUuid}/return-context`
- `POST /`
- `PATCH /{loanUuid}`
- `PATCH /{loanUuid}/cancel`
- `POST /{loanUuid}/prepare`
- `POST /{loanUuid}/delivery`
- `POST /{loanUuid}/return`
- `POST /{loanUuid}/complete`

## Flujo operacional vigente

### 1. Solicitud y reserva automatica

- `POST /api/v2/loans` crea el prestamo directamente en `approved`.
- En frontend, `approved` se muestra como `Reservado`.
- La reserva no mueve stock fisico al crear o editar.
- Para `reusable` e `individual`, la disponibilidad se bloquea por traslape de `scheduled_at .. expected_return_at`.
- Para `consumable`, la disponibilidad reservada queda bloqueada globalmente mientras el prestamo siga en `approved` o `prepared`, porque es stock de un solo uso y no volvera por rango.
- Si un solo implemento no alcanza disponibilidad, la solicitud completa falla.
- Se registra historial `null -> approved` con nota operativa de reserva automatica.

### 2. Edicion o cancelacion de la reserva

- El solicitante solo puede editar su propio prestamo mientras siga en `approved`.
- La edicion libera la reserva logica anterior, revalida disponibilidad excluyendo el mismo prestamo y vuelve a reservar el detalle completo.
- El docente puede cancelar su propio prestamo en estados `approved` o `prepared`.
- El coordinador puede cancelar prestamos visibles segun su acceso actual.

### 3. Preparacion

- `POST /api/v2/loans/{loanUuid}/prepare` solo esta disponible para coordinador.
- El backend solo valida estado `approved`; actualmente no aplica una restriccion horaria extra para preparar.
- La preparacion representa la separacion fisica del material reservado.
- En esta etapa el backend reconcilia `stock.reserved` contra lo que ya estaba reservado logicamente para el prestamo.
- Para items `individual`, la preparacion deja asignadas las unidades que estaban reservadas para ese prestamo.

### 4. Entrega

- `POST /api/v2/loans/{loanUuid}/delivery` solo acepta prestamos en `prepared`.
- La entrega es el punto flexible del flujo:
  - puede variar cantidades
  - puede reseleccionar `asset_codes`
  - puede agregar implementos adicionales
- En frontend, `delivered` se muestra como `En uso`.
- Si un item es `consumable`, la entrega lo consume en ese mismo paso y ya no queda pendiente de retorno.
- Si despues de entregar no quedan items retornables pendientes, el sistema cierra automaticamente el prestamo en `completed`.

### 5. Devolucion y cierre

- `GET /api/v2/loans/{loanUuid}/return-context` entrega el contexto operativo para devolucion:
  - items pendientes de retorno
  - `pending_return_quantity`
  - individuales entregados aun abiertos (`individual_uuid`, `asset_code`)
- `POST /api/v2/loans/{loanUuid}/return` se usa cuando hay variaciones reales en el cierre.
- `POST /api/v2/loans/{loanUuid}/complete` cierra todos los items retornables pendientes como retorno correcto, sin desglose por item.
- En frontend, `completed` se muestra como `Finalizado`.

## Diferenciacion por tipo de implemento

### `consumable`

- Se entrega por cantidad.
- No vuelve en devolucion.
- Mientras el prestamo siga en `approved` o `prepared`, su reserva ya descuenta disponibilidad global para nuevas solicitudes, aunque esten fuera del rango horario.
- Al entregar, su cantidad pendiente pasa a `consumed_quantity`, baja `loaned` y baja `total_stock`.

### `reusable`

- Se entrega por cantidad.
- En el request de devolucion se informa cantidad devuelta con el campo legacy `consumable_returns`.
- Aunque el nombre del campo es historico, hoy aplica a items `reusable`.
- La parte devuelta sube `returned_quantity` y vuelve a `available`.
- La diferencia no devuelta se registra como `consumed_quantity` y descuenta stock real.

### `individual`

- Se entrega por unidad fisica con `asset_code`.
- En frontend se rotula como `Activo`, pero en backend el `item_type` sigue siendo `individual`.
- La devolucion exige informar cada unidad con `return_condition`.

## Significado de `return_condition` en individuales

- `good`: la unidad vuelve a `available` y cuenta en `returned_quantity`.
- `damaged`: la unidad cambia a estado `damaged`; no vuelve a disponibilidad operativa.
- `lost`: la unidad cambia a estado `blocked`; no vuelve a disponibilidad operativa.
- `discarded`: la unidad cambia a estado `retired`; no vuelve a disponibilidad operativa.

## Campos operativos del detalle

- `requested_quantity`: referencia original solicitada.
- `reserved_quantity`: cantidad reservada logicamente para el prestamo.
- `delivered_quantity`: cantidad realmente entregada.
- `returned_quantity`: solo retorno util/bueno ya cerrado.

Notas:

- En prestamos activos (`approved`, `prepared`, `delivered`, `overdue`), `reserved_quantity` refleja la reserva operativa vigente.
- En prestamos `cancelled` o `expired` sin entrega, `reserved_quantity` puede conservar el valor solicitado como historial aunque la reserva ya haya sido liberada.
- El detalle publico no expone `damaged_quantity`, `lost_quantity`, `consumed_quantity` ni `discarded_quantity`, pero esos contadores si existen internamente en `loan_detail`.
- Por eso un item puede quedar cerrado completamente aunque `returned_quantity` sea menor que `delivered_quantity`.

## Validaciones relevantes

- `scheduled_at` no puede estar en pasado.
- `scheduled_at` solo permite solicitudes entre hoy y los proximos 14 dias corridos.
- `scheduled_at` y `expected_return_at` solo permiten lunes a sabado entre `08:00` y `22:00`.
- `expected_return_at` es opcional, pero si viene debe ser posterior a `scheduled_at`.
- No se permiten implementos duplicados en la misma solicitud.
- El sistema bloquea solapes del mismo solicitante contra prestamos activos en `pending`, `approved`, `prepared`, `delivered` y `overdue`.

## Trazabilidad, notas y notificaciones

- Toda transicion relevante queda registrada en `loan_status_history`.
- La reserva automatica usa el usuario de sistema configurado por lifecycle/outbox.
- Si no llegan `notes`, el backend persiste una nota operativa por defecto para mantener trazabilidad.
- El solicitante recibe notificacion cuando la solicitud queda en `approved` y cuando el prestamo cambia a estados operativos relevantes posteriores.
- En correo al docente, `approved` se comunica como reserva confirmada y `prepared` como implementos listos para retiro.
- La notificacion de nueva solicitud se mantiene para coordinacion al crear la solicitud.
- El director queda fuera del flujo de prestamos y solo conserva alertas de stock.
