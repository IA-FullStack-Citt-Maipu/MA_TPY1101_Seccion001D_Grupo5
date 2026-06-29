# Modulo: catalog/implement

- Estado del documento: vigente
- Ultima verificacion: 2026-06-28
- Fuente de verdad: `ImplementV2Controller`, `ImplementService`, `ImplementJooqRepository`, contratos `application.contract`

## Responsabilidad

Gestion del catalogo de implementos y su integracion con categoria, ubicacion y vistas de movimientos recientes por contratos cross-modulo.

## API vigente

Base path: `/api/v2/implements`

- `GET /`
- `GET /{implementUuid}`
- `POST /`
- `PUT /{implementUuid}`
- `PATCH /{implementUuid}/active`

Relacionadas:
- `GET /api/v2/implements/movements`
- `POST /api/v2/implements/{implementUuid}/movements`

Notas de contrato:
- `GET /` acepta hoy `name`, `categoryUuid`, `stockStatus`, `scheduledAt`, `expectedReturnAt` y `excludeLoanUuid`.
- `stockStatus` es un filtro operativo exclusivo de coordinacion.
- El summary de `GET /` expone `individual_asset_codes` para soportar busqueda por codigos de unidades individuales desde frontend.
- El filtro `name` de `GET /` se usa como termino de busqueda general y hoy matchea contra nombre, `barcode` del implemento y `asset_code` de unidades individuales activas.
- Cuando `scheduledAt`/`expectedReturnAt` vienen informados, la disponibilidad del summary se calcula con la misma funcion de disponibilidad usada por prestamos.
- En ese calculo, `consumable` usa reserva global activa y `reusable`/`individual` usan disponibilidad por traslape de ventana.

## Contratos y acoplamiento

- Dependencias cross-modulo via `application.contract`.
- No dependencia a API de otros modulos.
- DTO HTTP propio de modulo `implement`.

## Validaciones relevantes

- Categoria y ubicacion validadas por contrato.
- Reglas de nombre y estado activo/inactivo.
- Respuesta de error con `code` estable.
