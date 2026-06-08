-- Ejecutar con psql desde esta carpeta: psql -f apply-all.sql ...
\ir ./00_preamble_and_schema.sql
\ir ./01_types.sql
\ir ./02_functions.sql
\ir ./03_tables.sql
\ir ./04_sequences.sql
\ir ./05_sequence_ownership.sql
\ir ./06_defaults.sql
\ir ./07_views.sql
\ir ./08_constraints.sql
\ir ./09_indexes.sql
\ir ./10_triggers.sql
\ir ./11_row_security.sql
\ir ./12_policies.sql
\ir ./99_footer.sql
