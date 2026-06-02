# Registro de Migraciones Vigentes

- Estado del documento: vigente
- Ultima verificacion: 2026-05-31
- Fuente de verdad: `src/main/resources/db/migration/v25/`

## Cadena aplicada (baseline + refinamientos)

1. `V25__schema_alignment_big_bang.sql`
2. `V26__loan_request_notification_function.sql`
3. `V27__loan_status_enum_add_expired.sql`
4. `V28__seed_health_school_rooms_subjects.sql`
5. `V29__loan_flow_data_refinement.sql`
6. `V30__loan_flow_operational_refinement.sql`
7. `V31__fix_loan_function_column_aliases.sql`

## Indice disponible en docs

- [V25__schema_alignment_big_bang.md](V25__schema_alignment_big_bang.md)
- Para el detalle consolidado de V25..V31 revisar [../16-catalogo-bd-v31.md](../16-catalogo-bd-v31.md).

## Convencion

- Nuevas migraciones se agregan en `db/migration/v25` con version mayor a `31`.
- No se editan migraciones ya aplicadas en ambientes compartidos.
- No se reintroduce cadena legacy previa al baseline V25.
