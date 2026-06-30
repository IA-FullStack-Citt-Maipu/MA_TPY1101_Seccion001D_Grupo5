# Modulo: catalog/stock

- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `StockV2Controller`, `InventoryMovementV2Controller`, `BarcodeLabelV2Controller`

## Responsabilidad

Gestion de stock por implemento, movimientos de inventario y generacion de etiquetas.

## API vigente

- `GET /api/v2/implements/{implementUuid}/stock`
- `POST /api/v2/implements/{implementUuid}/stock/entries`
- `POST /api/v2/implements/{implementUuid}/stock/movements`
- `PUT /api/v2/implements/{implementUuid}/stock/individuals/{individualUuid}`
- `GET /api/v2/implements/movements`
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
