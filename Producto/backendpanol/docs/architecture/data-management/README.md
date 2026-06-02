# Gestion de Datos: Canon Operativo V31

- Estado del documento: vigente
- Ultima verificacion: 2026-05-31
- Fuente de verdad: `db/migration/v25/V25..V31` + introspeccion de esquema `public` en Supabase

## Documento principal de base de datos

1. [16-catalogo-bd-v31.md](./16-catalogo-bd-v31.md)  
   Catalogo tecnico completo: tablas, vistas, enums, funciones, triggers, politicas RLS, indices y migraciones aplicadas.

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
- Las extensiones funcionales posteriores al baseline quedan trazadas por Flyway (`V26..V31`) y reflejadas en el catalogo V31.
