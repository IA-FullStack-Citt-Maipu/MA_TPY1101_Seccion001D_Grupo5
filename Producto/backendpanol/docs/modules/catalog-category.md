# Modulo: catalog/category

- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: `CategoryV2Controller`, `CategoriaService`, `ArchitectureTest`

## Responsabilidad

Gestion de categorias de implementos con reglas de unicidad, activacion/desactivacion y validaciones de uso por otros modulos.

## API vigente

Base path: `/api/v2/categories`

- `GET /active`
- `GET /gestion`
- `GET /{categoryUuid}/associations`
- `POST /`
- `PUT /{categoryUuid}`
- `PATCH /{categoryUuid}/deactivate`
- `PATCH /{categoryUuid}/activate`
- `DELETE /{categoryUuid}`

## Fronteras del modulo

- Persistencia encapsulada en infraestructura (jOOQ).
- Contratos cross-modulo expuestos por paquetes de contrato, no por API.
- No usar rutas legacy en integraciones nuevas.

## Reglas funcionales relevantes

- Nombre unico (case-insensitive).
- Desactivacion con validacion de implementos activos asociados.
- Eliminacion bloqueada si existen asociaciones.
- Errores con `code` estable.
- Activacion (`PATCH /{categoryUuid}/activate`) es idempotente: si la categoría ya se encuentra activa, responde exitosamente con el estado actual sin producir error.

