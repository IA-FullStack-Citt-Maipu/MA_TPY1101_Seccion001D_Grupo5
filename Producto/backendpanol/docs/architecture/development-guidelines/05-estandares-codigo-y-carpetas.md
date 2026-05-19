- Estado del documento: vigente
- Ultima verificacion: 2026-05-16
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 05 - Estándares de Código y Estructura

## Naming

- Casos de uso: `VerbNounUseCase` (o `Service` si ya existe estándar).
- Puertos: `XRepository`, `XPublisher`, `XGateway`.
- Adaptadores: `<Tecnologia><Puerto>` (ej. `JooqLoanRepository`).

## DTOs

- `api/dto`: contratos HTTP.
- `application/dto`: contratos internos si se requiere.
- No exponer entidades de dominio directo por API.

## Errores

- Errores de negocio con código estable (`ApiException` + `code`).
- No filtrar mensajes técnicos al cliente.

## Persistencia

- SQL en adapters de infraestructura.
- Queries complejas encapsuladas por repositorio.
- Evitar lógica de negocio en SQL salvo invariantes transaccionales.

## Testing

- Unit: `application/domain` (rápido, sin DB).
- Integración: adapters SQL.
- Contract/API: endpoints críticos.

## Documentación

Cada cambio relevante debe incluir:
- impacto en arquitectura,
- decisión tomada,
- deuda técnica (si aplica).
