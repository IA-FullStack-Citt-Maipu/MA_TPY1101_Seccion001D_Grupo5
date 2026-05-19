- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 03 - Reglas Hexagonal (Puertos y Adaptadores)

## Capas y responsabilidades

- `api`: transforma request/response, nada de negocio.
- `application`: orquesta caso de uso y transacciones.
- `domain`: entidades, invariantes, interfaces (puertos).
- `infrastructure`: implementación técnica de puertos.

## Regla de dependencia

- `domain` no conoce Spring, JOOQ, HTTP ni detalles de infraestructura concreta.
- `application` no conoce detalles de wire-protocol.
- `infrastructure` implementa puertos de `domain`.

## Guía práctica

1. Si necesitas datos: crea/usa un puerto en `domain`.
2. Implementa el puerto en `infrastructure`.
3. Inyecta interfaz en `application`.

## Ejemplo de puerto

- `UserRepository`
- `LoanRepository`
- `StockRepository`
- `EventPublisher`

## Excepciones permitidas (temporales)

Si un servicio actual usa `DSLContext` directo:
- documentar como deuda,
- crear ticket de extracción a puerto,
- no propagar el patrón a código nuevo.

## Definición de “hecho” hexagonal

Un caso de uso se considera alineado cuando:
- compila sin depender de infraestructura concreta,
- tiene tests unitarios con dobles de puertos,
- su adapter se prueba por integración separada.
