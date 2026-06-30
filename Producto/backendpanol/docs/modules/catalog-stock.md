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
