- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 09 - Contratos Cross-Modulo

## Dueno de orquestacion

| Flujo | Modulo dueno | Contrato usado | Tipo |
|---|---|---|---|
| Ficha detalle de implemento + movimientos + nombres de usuario | `catalog/implement` | `ImplementDetailFacade` + `InventoryMovementQueryContract` | Contratos de aplicacion |
| Registro de movimiento de inventario | `catalog/stock` | `InventoryMovementService` + `OutboxService` | Caso de uso + evento |
| Etiquetas PDF de inventario | `catalog/stock` | `ImplementLookupContract` | Contrato de lectura cross-modulo |
| Auditoria y autenticacion | `auth` | `AuditLogPort`, `UserAuthPort`, `TokenRevocationPort` | Puertos de dominio |
| Administracion de usuarios | `users` | `UserAdminRepository` + `OutboxService` | Puerto + evento |

## Convencion unica de contratos

1. Todo contrato cross-modulo de capa aplicacion se publica en `application.contract`.
2. Si la colaboracion es de dominio, usar `domain.port` (cuando exista el puerto en el modulo dueno).
3. `api` y `application` solo pueden consumir clases de otro modulo desde esos paquetes de contrato/puerto.
4. Se prohibe depender de clases concretas de `application` de otro modulo.
5. Se prohibe depender de `api` de otro modulo.

## Reglas obligatorias

1. `domain` no depende de `api`, `application` ni `infrastructure`.
2. `domain` no depende de `domain` de otro modulo.
3. `application` no depende de `api` (ni propio ni ajeno).
4. `application` no depende de `application` de otro modulo salvo `application.contract`.
5. `application` no depende de `domain` de otro modulo salvo excepciones documentadas.
6. `api` no depende de `application` de otro modulo salvo `application.contract`.
7. `api` no depende de `api` de otro modulo.

## Excepciones permitidas (minimas y justificadas)

1. `users.application -> auth.domain.AuditLogPort`
Justificacion: puerto de auditoria transversal mientras se formaliza estandar unico `domain.port` compartido.

## Ejemplos de mapeo

1. `api -> application`
- Controller recibe `request` HTTP DTO.
- Controller mapea a `Command` interno (`application.dto`).
- Service opera con `Command` y devuelve `Result` interno.

2. `application -> domain`
- Service depende de puertos/repositorios de dominio de su propio modulo.
- Si requiere otro modulo, consume contrato en `application.contract` o puerto `domain.port`.

