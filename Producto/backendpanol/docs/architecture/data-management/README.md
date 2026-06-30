# Gestion de Datos: Canon Operativo Vigente

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `Producto/databasepanol/migrations/v25/V25..V51` + `03-postgresql-guia-tecnica.md`

## Documentos principales de base de datos

1. [03-postgresql-guia-tecnica.md](./03-postgresql-guia-tecnica.md)  
   Guia tecnica vigente del modelo y reglas operativas.
2. [migrations/README.md](./migrations/README.md)  
   Cadena Flyway realmente versionada en el repositorio.

## Referencia historica complementaria

1. [16-catalogo-bd-v31.md](./16-catalogo-bd-v31.md)  
   Catalogo tecnico congelado hasta V31. No debe usarse como fuente operativa primaria.

## Documentos operativos complementarios

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

- No se consideran vigentes documentos/artefactos previos al baseline V25.
- Las extensiones funcionales posteriores al baseline quedan trazadas por Flyway (`V26..V51`) y se contrastan, solo cuando hace falta contexto historico, con `16-catalogo-bd-v31.md`.
