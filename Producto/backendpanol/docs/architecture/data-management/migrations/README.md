# Registro de Migraciones Vigentes

- Estado del documento: vigente
- Ultima verificacion: 2026-06-07
- Fuente de verdad: `Producto/databasepanol/migrations/v25/`

## Cadena aplicada en el repositorio (baseline + refinamientos)

1. `V25__schema_alignment_big_bang.sql`
2. `V26__loan_request_notification_function.sql`
3. `V27__loan_status_enum_add_expired.sql`
4. `V29__loan_flow_data_refinement.sql`
5. `V30__loan_flow_operational_refinement.sql`
6. `V31__fix_loan_function_column_aliases.sql`
7. `V32__ensure_return_condition_enum_and_dedup_loan_history_index.sql`
8. `V33__auth_login_user_summary.sql`
9. `V34__auth_find_user_by_rut_support_normalized_input.sql`
10. `V35__drop_legacy_varchar_auth_lookup_function.sql`

## Nota de versionado

- No existe un archivo `V28__*.sql` vigente en el repositorio actual.
- Si aparece una referencia historica a `V28__seed_health_school_rooms_subjects.sql`, debe tratarse como desalineacion documental y no como fuente activa.

## Indice disponible en docs

- [V25__schema_alignment_big_bang.md](V25__schema_alignment_big_bang.md)
- Para el detalle consolidado historico de esquema, revisar [../16-catalogo-bd-v31.md](../16-catalogo-bd-v31.md).

## Convencion

- Nuevas migraciones se agregan en `Producto/databasepanol/migrations/v25` con version mayor a `35`.
- No se editan migraciones ya aplicadas en ambientes compartidos.
- No se reintroduce cadena legacy previa al baseline V25.
