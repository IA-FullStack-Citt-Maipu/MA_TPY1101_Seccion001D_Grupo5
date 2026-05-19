- Estado del documento: vigente
- Ultima verificacion: 2026-05-17
- Fuente de verdad: `application.yaml` + `db/migration/v25/V25__schema_alignment_big_bang.sql`

# Flujo de Migraciones Flyway (Canon V25)

## Configuracion operativa actual

- Ubicacion Flyway: `classpath:db/migration/v25`
- Baseline: `25` (`v25-canonical-baseline`)
- Esquema base canonico: `db/migration/v25/V25__schema_alignment_big_bang.sql`

## Regla de versionado

1. Nuevas migraciones se crean en `src/main/resources/db/migration/v25/`.
2. Formato: `V{n}__descripcion.sql` con `n > 25`.
3. No editar scripts ya aplicados.
4. No reintroducir cadena legacy previa a V25.

## Flujo de cambio

1. Crear script nuevo en carpeta `v25`.
2. Validar local con BD limpia basada en V25.
3. Ejecutar `generate-sources` para alinear jOOQ.
4. Incluir SQL + cambios de codigo en el mismo PR.
5. Desplegar por ambientes y validar `flyway_schema_history`.

## Validaciones obligatorias

- Integridad referencial y checks del dominio.
- Compatibilidad del contrato UUID-first en API.
- Ausencia de vocabulario legacy en rutas operativas (validado por guardrail CI).
