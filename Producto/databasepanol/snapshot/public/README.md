# Snapshot Separado del Esquema `public`

- Fuente: `../public-schema-full.sql`
- Generado el: 2026-06-07
- Proyecto origen: `panol-dev`

## Archivos

- `00_preamble_and_schema.sql`
- `01_types.sql`
- `02_functions.sql`
- `03_tables.sql`
- `04_sequences.sql`
- `05_sequence_ownership.sql`
- `06_defaults.sql`
- `07_views.sql`
- `08_constraints.sql`
- `09_indexes.sql`
- `10_triggers.sql`
- `11_row_security.sql`
- `12_policies.sql`
- `99_footer.sql`
- `apply-all.sql`

## Uso

Para reconstruir este snapshot con `psql` en una base vacia:

```bash
psql -f apply-all.sql
```

Notas:

- este snapshot representa la estructura actual del schema `public`;
- `../public-schema-full.sql` conserva el dump raw exacto del export;
- los archivos separados estan normalizados para replay local (`CREATE SCHEMA IF NOT EXISTS`, sin `\restrict` / `\unrestrict`);
- la fuente de verdad evolutiva del proyecto sigue siendo `../../migrations/v25/`;
- el snapshot sirve como referencia y clon estructural, no como reemplazo de Flyway.
