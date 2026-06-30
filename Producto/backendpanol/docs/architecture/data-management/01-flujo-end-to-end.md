# 01 - Flujo End-to-End de Datos (Vigente)

- Estado del documento: vigente
- Ultima verificacion: 2026-06-30
- Fuente de verdad: casos de uso V2 + `shared/outbox` + `Producto/databasepanol/migrations/v25/V25..V53` + `03-postgresql-guia-tecnica.md`

## Flujo operativo actual

1. Cliente llama endpoint `/api/v2/**` con UUIDs.
2. Backend autentica, autoriza y valida reglas de negocio.
3. Caso de uso resuelve UUID externo a `id` interno cuando necesita relacionar tablas.
4. Se persiste estado canonico en PostgreSQL.
5. Si aplica integracion asincrona, se inserta evento en `outbox_event` en la misma transaccion.
6. Commit: estado de negocio y outbox quedan consistentes.
7. Worker procesa outbox con ciclo `PENDING -> PROCESSING -> SENT` o `FAILED`.

Nota operativa de prestamos:

- La solicitud nueva se persiste directamente en `approved` como reserva tecnica.
- `pending` queda solo como compatibilidad legacy, no como estado normal del flujo V2.

## Regla de identidad

- Interno DB/jOOQ: `id`.
- Externo API/frontend: `uuid`.

## Manejo de fallas

- Si falla SQL: no hay commit ni outbox.
- Si SQL confirma y falla publicacion: evento queda pendiente para retry.
- Si supera reintentos: estado `FAILED` para tratamiento operativo.

## Compatibilidad de rutas

No usar rutas legacy (`/api/categorias`, `/api/implements`, `/api/v1/**`) en contratos vigentes.
