# Seeds Locales

- Estado del documento: vigente
- Ultima verificacion: 2026-06-07

## Objetivo

Estas semillas entregan una base minima y controlada para levantar el sistema localmente con:

- roles;
- usuarios QA locales;
- catalogo base;
- stock inicial;
- movimientos de inventario;
- prestamos demo listos para probar vistas y transiciones.

## Archivo actual

- `00_local_initial_flow_seed.sql`

## Criterios

- No replica datos operativos reales de Supabase.
- Usa datos sinteticos o QA pensados para desarrollo local.
- Evita dejar credenciales efectivas de ambientes compartidos dentro del repositorio.
- Puede ejecutarse sobre una base vacia ya migrada con Flyway.

## Nota de base

La migracion `V25__schema_alignment_big_bang.sql` ya crea un usuario tecnico `SISTEMA_OUTBOX` para flujos internos.

Por eso, despues de aplicar migraciones + `00_local_initial_flow_seed.sql`, la base queda con:

- 1 usuario tecnico de sistema;
- 3 usuarios QA locales para pruebas manuales.
