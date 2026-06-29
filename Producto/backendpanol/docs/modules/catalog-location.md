# Modulo: catalog/location

- Estado del documento: vigente
- Ultima verificacion: 2026-06-28
- Fuente de verdad: `LocationV2Controller`, `LocationService`

## Responsabilidad

Gestion de ubicaciones del catalogo y validaciones de activacion para consumo de otros modulos.

## API vigente

Base path: `/api/v2/locations`

- `GET /`
- `GET /management`
- `GET /{locationUuid}/associations`
- `POST /`
- `PUT /{locationUuid}`
- `PATCH /{locationUuid}/active`
- `DELETE /{locationUuid}`

## Notas

- IDs publicos UUID.
- Validaciones y errores con `code` estable.
- Eliminacion bloqueada cuando la ubicacion sigue asociada a implementos o unidades individuales.
- Contratos de validacion para consumo cross-modulo.
