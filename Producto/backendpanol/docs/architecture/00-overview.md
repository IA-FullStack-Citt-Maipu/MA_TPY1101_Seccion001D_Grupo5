# Arquitectura Overview

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `ArchitectureTest`, estructura de paquetes en `src/main/java`, `Producto/databasepanol/migrations/v25/V25..V51` y `docs/architecture/data-management/03-postgresql-guia-tecnica.md`

Nota operativa: PostgreSQL es la base canónica y única del sistema en este tramo.

## Patrones adoptados

1. Monolito modular.
2. Hexagonal por modulo.
3. Integracion entre modulos por contratos y eventos.

## Estructura global

- `bootstrap/`
- `modules/`
- `shared/`

## Reglas estructurales

1. `modules` no depende de `bootstrap`.
2. `shared` no depende de `modules`.
3. `domain` no depende de `api/application/infrastructure`.
4. Cross-modulo por `application.contract` o `domain.port`.

## Modulos implementados

- `auth`
- `users`
- `catalog/category`
- `catalog/location`
- `catalog/implement`
- `catalog/stock`
- `loan`

## Referencias

- `docs/architecture/00-matriz-canonica-vigente.md`
- `docs/architecture/development-guidelines/09-contratos-cross-modulo.md`
- `src/test/java/com/panol_project/backendpanol/ArchitectureTest.java`
