# Flujo de Migraciones Flyway (Canon V31)

- Estado del documento: vigente
- Ultima verificacion: 2026-05-31
- Fuente de verdad: `application.yaml` + `src/main/resources/db/migration/v25/`

## Configuracion operativa actual

- Ubicacion Flyway: `classpath:db/migration/v25`
- Baseline: `25` (`v25-canonical-baseline`)
- Cadena vigente aplicada en Supabase: `V25` a `V31`

## Regla de versionado

1. Nuevas migraciones se crean en `src/main/resources/db/migration/v25/`.
2. Formato: `V{n}__descripcion.sql` con `n > 31`.
3. No editar scripts ya aplicados.
4. No reintroducir cadena legacy previa al baseline V25.

## Flujo de cambio

1. Crear script nuevo en carpeta `v25`.
2. Validar local con BD limpia y ejecutar `flyway:validate`.
3. Ejecutar `generate-sources` para alinear jOOQ cuando aplique.
4. Incluir SQL + cambios de codigo en el mismo PR.
5. Desplegar por ambientes y validar `flyway_schema_history`.

## Validaciones obligatorias

- Integridad referencial y checks del dominio.
- Compatibilidad del contrato UUID-first en API.
- Ausencia de vocabulario legacy en endpoints/DTOs.
- Confirmacion de resultados funcionales en flujo E2E de prestamos.

## Referencia de estado aplicado

- [16-catalogo-bd-v31.md](./16-catalogo-bd-v31.md) (seccion `Migraciones Canonicas Aplicadas`).
