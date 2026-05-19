# Gestion de Datos: Canon Operativo V25

- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `db/migration/v25/V25__schema_alignment_big_bang.sql` + servicios backend V2

## Documentos vigentes

1. [01-flujo-end-to-end.md](./01-flujo-end-to-end.md)
2. [02-responsabilidades-por-capa.md](./02-responsabilidades-por-capa.md)
3. [03-postgresql-guia-tecnica.md](./03-postgresql-guia-tecnica.md)
4. [05-backend-integracion-datos.md](./05-backend-integracion-datos.md)
5. [06-runbook-operacional-datos.md](./06-runbook-operacional-datos.md)
6. [08-flujo-migraciones-flyway.md](./08-flujo-migraciones-flyway.md)
7. [15-outbox-flujo-completo.md](./15-outbox-flujo-completo.md)
8. [migrations/README.md](./migrations/README.md)

## Regla de identidad de datos

- Persistencia interna: `id` numerico para joins, FKs y operaciones SQL/jOOQ.
- Contrato externo API/UI/log cliente: `uuid` para evitar exposicion de ids internos.

## Politica de legado

- Este repositorio elimina artefactos legacy no vigentes (persistencia historica no operativa, fases UUID-only intermedias y enums antiguos).
- Solo se mantiene documentacion operativa alineada a V25.
