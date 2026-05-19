# Arquitectura Backend

- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: estructura de paquetes, `ArchitectureTest`, guias en `docs/architecture`

## Definicion

El backend sigue:

- Monolito modular
- Hexagonal por modulo (ports and adapters)
- Integracion entre modulos por contratos y eventos

## Estructura global

- `bootstrap`: configuracion transversal
- `modules`: dominios funcionales
- `shared`: componentes transversales estables

## Modulos implementados

- `auth`
- `users`
- `catalog/category`
- `catalog/location`
- `catalog/implement`
- `catalog/stock`
- `loan`

## Reglas vigentes de dependencia

1. `modules` no depende de `bootstrap`.
2. `shared` no depende de `modules`.
3. `domain` no depende de `api/application/infrastructure`.
4. Dependencias cross-modulo solo por `application.contract` o `domain.port`.
5. `api` no depende de `api` de otro modulo.

Validacion automatica:
- `src/test/java/com/panol_project/backendpanol/ArchitectureTest.java`

## Referencias

- `docs/architecture/00-matriz-canonica-vigente.md`
- `docs/architecture/00-overview.md`
- `docs/architecture/development-guidelines/README.md`
- `docs/modules/README.md`
