# 01 - Flujo End-to-End de Datos (V25)

- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: casos de uso V2 + `shared/outbox` + `db/migration/v25/V25__schema_alignment_big_bang.sql`

## Flujo operativo actual

1. Cliente llama endpoint `/api/v2/**` con UUIDs.
2. Backend autentica, autoriza y valida reglas de negocio.
3. Caso de uso resuelve UUID externo a `id` interno cuando necesita relacionar tablas.
4. Se persiste estado canonico en PostgreSQL.
5. Si aplica integracion asincrona, se inserta evento en `outbox_event` en la misma transaccion.
6. Commit: estado de negocio y outbox quedan consistentes.
7. Worker procesa outbox con ciclo `PENDING -> PROCESSING -> SENT` o `FAILED`.

## Regla de identidad

- Interno DB/jOOQ: `id`.
- Externo API/frontend: `uuid`.

## Manejo de fallas

- Si falla SQL: no hay commit ni outbox.
- Si SQL confirma y falla publicacion: evento queda pendiente para retry.
- Si supera reintentos: estado `FAILED` para tratamiento operativo.

## Compatibilidad de rutas

No usar rutas legacy (`/api/categorias`, `/api/implements`, `/api/v1/**`) en contratos vigentes.
