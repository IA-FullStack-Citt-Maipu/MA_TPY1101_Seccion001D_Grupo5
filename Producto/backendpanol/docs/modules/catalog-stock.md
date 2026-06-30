# Modulo: catalog/stock

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `StockV2Controller`, `InventoryMovementV2Controller`, `BarcodeLabelV2Controller`

## Responsabilidad

Gestion de stock por implemento, movimientos de inventario y generacion de etiquetas.

## API vigente

- `GET /api/v2/implements/{implementUuid}/stock`
- `POST /api/v2/implements/{implementUuid}/stock/entries`
- `POST /api/v2/implements/{implementUuid}/stock/movements`
- `PUT /api/v2/implements/{implementUuid}/stock/individuals/{individualUuid}`
- `GET /api/v2/implements/movements`
- `GET /api/v2/implements/movements/history`
- `GET /api/v2/implements/movements/summary`
- `POST /api/v2/implements/{implementUuid}/movements`
- `GET /api/v2/implements/{implementUuid}/labels/pdf`

### Payload vigente de `POST /api/v2/implements/{implementUuid}/stock/entries`

- Siempre acepta `quantity` y opcionalmente `notes`.
- Para ingresos simples o lote homogeneo de activos puede recibir:
  - `asset_codes`
  - `status`
  - `condition`
  - `current_location_uuid`
  - `remaining_life`
  - `asset_code_reprint_required`
- Para lotes de activos con datos distintos por unidad puede recibir `individual_entries`, donde cada item expone:
  - `asset_code`
  - `status`
  - `condition`
  - `current_location_uuid`
  - `remaining_life`
  - `asset_code_reprint_required`
- Si `individual_entries` no viene, el backend conserva compatibilidad con `asset_codes` y los campos compartidos del request.

### Lectura ejecutiva

- `GET /api/v2/implements/movements/history`
  - lectura paginada para `COORDINADOR` y `DIRECTOR`
  - filtros vigentes: `page`, `size`, `search`, `action`, `from`, `to`
  - devuelve contexto enriquecido del movimiento, implemento y actor
- `GET /api/v2/implements/movements/summary`
  - lectura agregada para `COORDINADOR` y `DIRECTOR`
  - expone `total_movements`, `top_users` y `top_implements`

### Operacion manual

- `POST /api/v2/implements/{implementUuid}/movements`
  - registra un movimiento manual
  - solo disponible para `COORDINADOR`

## Fronteras

- Colaboracion con `implement` via contratos cross-modulo.
- Sin dependencia a API de otros modulos.
- Emision de eventos de stock/movimientos via outbox.

## Contrato de identidad y tipos

- El modulo resuelve UUID externo a `id` interno para operaciones SQL.
- `movement_type` y `action` aceptan solo:
  - `STOCK_IN`
  - `STOCK_OUT`
  - `LOAN_DELIVERY`
  - `LOAN_RETURN`
  - `DAMAGE_REPORT`
  - `MANUAL_ADJUSTMENT`
  - `CONSUMPTION`
  - `DISCARD`
  - `LOSS`
