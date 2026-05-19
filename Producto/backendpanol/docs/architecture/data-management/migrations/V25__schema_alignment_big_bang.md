- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `src/main/resources/db/migration/v25/V25__schema_alignment_big_bang.sql`

# V25__schema_alignment_big_bang.sql

## Motivo

Consolidar un unico modelo PostgreSQL/Supabase y eliminar el legado no vigente.

## Alcance

- Baseline canonic de esquema en PostgreSQL.
- Enums y restricciones de dominio alineadas a V25.
- RLS con `app.current_user_uuid`.
- Outbox canonica en `outbox_event`.
- Regla de identidad: `id` interno / `uuid` externo.

## Riesgo controlado

Migracion destructiva pensada para entorno no productivo o ventanas controladas.
